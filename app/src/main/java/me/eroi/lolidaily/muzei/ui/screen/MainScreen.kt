package me.eroi.lolidaily.muzei.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlinx.coroutines.launch
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import me.eroi.lolidaily.muzei.ui.screen.pages.*
import me.eroi.lolidaily.muzei.ui.theme.ColorSource
import me.eroi.lolidaily.muzei.ui.theme.ColorStyle
import me.eroi.lolidaily.muzei.ui.theme.ThemeMode

private const val KEY_LAST_TAB = "settings_last_tab"

/**
 * MD3 main screen for the Loli Daily Muzei plugin.
 *
 * Four destinations via bottom NavigationBar (phone) or NavigationRail (tablet/foldable):
 * - Today: current day's artwork gallery
 * - Bookmarks: all previously cached artwork
 * - Submit: upload new artwork to Loli Commons
 * - Settings: tag filters, account, theme, debug
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MainScreen(
    selectedTags: Set<String>,
    onTagsChanged: (Set<String>) -> Unit,
    todayArtwork: List<ArtworkPreview>,
    bookmarkArtwork: List<ArtworkPreview>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    isLoggedIn: Boolean = false,
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    onReactionClick: (token: String, emojiValue: Int) -> Unit = { _, _ -> },
    bgmUsername: String? = null,
    bgmNickname: String? = null,
    bgmAvatarUrl: String? = null,
    bgmDomain: String = "chii.in",
    onDomainChanged: (String) -> Unit = {},
    lcBadge: String? = null,
    isSourceActivated: Boolean = false,
    isMuzeiInstalled: Boolean = false,
    onOpenMuzei: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChanged: (ThemeMode) -> Unit = {},
    onOpenDebug: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onBookmarkToggle: (token: String, fileName: String, bookmarked: Boolean) -> Unit = { _, _, _ -> },
    onRemoveBookmark: (ArtworkPreview) -> Unit = {},
    refreshProgress: Float? = null,
    initialTab: Int? = null,
    colorSource: ColorSource = ColorSource.DEFAULT,
    onColorSourceChanged: (ColorSource) -> Unit = {},
    colorStyle: ColorStyle = ColorStyle.NEUTRAL,
    onColorStyleChanged: (ColorStyle) -> Unit = {},
    manualColorArgb: Int = 0xFF6B4DA3.toInt(),
    onManualColorChanged: (Int) -> Unit = {},
    sourceColorArgb: Int? = null,
    onBadgeChanged: (String) -> Unit = {},
    onTodayPageOpened: () -> Unit = {},
    initialSourceUrl: String? = null,
) {
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as android.app.Activity)
    val isExpandedScreen = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val prefs = remember { context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, android.content.Context.MODE_PRIVATE) }
    var selectedTab by remember {
        mutableIntStateOf(initialTab ?: prefs.getInt(KEY_LAST_TAB, 0))
    }
    var todayPagerPage by remember { mutableIntStateOf(0) }
    var fullscreenPreview by remember { mutableStateOf<ArtworkPreview?>(null) }
    var bookmarkSearchQuery by remember { mutableStateOf("") }
    var bookmarkSelectedTag by remember { mutableStateOf<String?>(null) }
    var railExpanded by remember { mutableStateOf(false) }
    val railState = rememberWideNavigationRailState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedTab) {
        prefs.edit { putInt(KEY_LAST_TAB, selectedTab) }
    }

    // NavigationRail item definitions (shared between rail and bar)
    val navigationItems =
        listOf(
            Triple(
                if (selectedTab == 0) Icons.Filled.Image else Icons.Outlined.Image,
                stringResource(R.string.tab_today),
                0,
            ),
            Triple(
                if (selectedTab == 1) Icons.Filled.Bookmarks else Icons.Outlined.Bookmarks,
                stringResource(R.string.tab_bookmark),
                1,
            ),
            Triple(
                if (selectedTab == 2) Icons.Filled.Upload else Icons.Outlined.Upload,
                stringResource(R.string.tab_submit),
                2,
            ),
            Triple(
                if (selectedTab == 3) Icons.Filled.Settings else Icons.Outlined.Settings,
                stringResource(R.string.tab_settings),
                3,
            ),
        )

    if (isExpandedScreen) {
        // Tablet/Foldable: WideNavigationRail on the left (M3 official component)
        Row(modifier = modifier.fillMaxSize()) {
            WideNavigationRail(
                state = railState,
                header = {
                    IconButton(
                        modifier = Modifier.padding(start = 24.dp),
                        onClick = {
                            scope.launch {
                                if (railState.targetValue == WideNavigationRailValue.Expanded) {
                                    railState.collapse()
                                } else {
                                    railState.expand()
                                }
                            }
                        },
                    ) {
                        if (railState.targetValue == WideNavigationRailValue.Expanded) {
                            Icon(Icons.AutoMirrored.Filled.MenuOpen, "Collapse rail")
                        } else {
                            Icon(Icons.Default.Menu, "Expand rail")
                        }
                    }
                },
            ) {
                val isExpanded = railState.targetValue == WideNavigationRailValue.Expanded
                navigationItems.forEach { (icon, label, index) ->
                    WideNavigationRailItem(
                        railExpanded = isExpanded,
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                            )
                        },
                        label = { Text(label) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                    )
                }
            }

            // Content area
            Column(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surface)) {
                // Top bar for settings tab
                if (selectedTab == 3) {
                    CenterAlignedTopAppBar(
                        title = { Text(stringResource(R.string.title_settings)) },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                    )
                }

                // Tab content — SubmitPage always stays in composition to preserve form state
                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        0 ->
                            TodayPage(
                                todayArtwork = todayArtwork,
                                isLoggedIn = isLoggedIn,
                                onFullscreenImage = { fullscreenPreview = it },
                                onReactionClick = onReactionClick,
                                onRefresh = onRefresh,
                                onBookmarkToggle = onBookmarkToggle,
                                refreshProgress = refreshProgress,
                                initialPage = todayPagerPage,
                                onPageChanged = { todayPagerPage = it },
                                windowSizeClass = windowSizeClass.widthSizeClass,
                                onPageOpened = onTodayPageOpened,
                            )
                        1 ->
                            BookmarkPage(
                                cachedArtwork = bookmarkArtwork,
                                onFullscreenImage = { fullscreenPreview = it },
                                onRemoveBookmark = onRemoveBookmark,
                                emptyMessage = stringResource(R.string.msg_no_bookmarks),
                                searchQuery = bookmarkSearchQuery,
                                selectedTag = bookmarkSelectedTag,
                                onSearchQueryChange = { bookmarkSearchQuery = it },
                                onTagSelected = { bookmarkSelectedTag = it },
                                windowSizeClass = windowSizeClass.widthSizeClass,
                            )
                        3 ->
                            SettingsPage(
                                selectedTags = selectedTags,
                                onTagsChanged = onTagsChanged,
                                isLoggedIn = isLoggedIn,
                                onLogin = onLogin,
                                onLogout = onLogout,
                                bgmUsername = bgmUsername,
                                bgmNickname = bgmNickname,
                                bgmAvatarUrl = bgmAvatarUrl,
                                bgmDomain = bgmDomain,
                                onDomainChanged = onDomainChanged,
                                lcBadge = lcBadge,
                                onBadgeChanged = onBadgeChanged,
                                isSourceActivated = isSourceActivated,
                                isMuzeiInstalled = isMuzeiInstalled,
                                onOpenMuzei = onOpenMuzei,
                                themeMode = themeMode,
                                onThemeModeChanged = onThemeModeChanged,
                                colorSource = colorSource,
                                onColorSourceChanged = onColorSourceChanged,
                                colorStyle = colorStyle,
                                onColorStyleChanged = onColorStyleChanged,
                                manualColorArgb = manualColorArgb,
                                onManualColorChanged = onManualColorChanged,
                                sourceColorArgb = sourceColorArgb,
                                onOpenDebug = onOpenDebug,
                                onOpenAbout = onOpenAbout,
                                todayArtwork = todayArtwork,
                                windowSizeClass = windowSizeClass.widthSizeClass,
                            )
                    }
                    // SubmitPage stays mounted regardless of selected tab
                    SubmitPage(
                        isLoggedIn = isLoggedIn,
                        onLogin = onLogin,
                        initialSourceUrl = initialSourceUrl,
                        windowSizeClass = windowSizeClass.widthSizeClass,
                        modifier = if (selectedTab == 2) Modifier else Modifier.size(0.dp),
                    )
                }
            }
        }
    } else {
        // Phone: Bottom NavigationBar
        Scaffold(
            topBar = {
                if (selectedTab == 3) {
                    CenterAlignedTopAppBar(
                        title = { Text(stringResource(R.string.title_settings)) },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                    )
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = {
                            Crossfade(targetState = selectedTab == 0, label = "today_icon") {
                                Icon(
                                    imageVector = if (it) Icons.Filled.Image else Icons.Outlined.Image,
                                    contentDescription = stringResource(R.string.tab_today),
                                )
                            }
                        },
                        label = { Text(stringResource(R.string.tab_today)) },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            Crossfade(targetState = selectedTab == 1, label = "bookmark_icon") {
                                Icon(
                                    imageVector = if (it) Icons.Filled.Bookmarks else Icons.Outlined.Bookmarks,
                                    contentDescription = stringResource(R.string.tab_bookmark),
                                )
                            }
                        },
                        label = { Text(stringResource(R.string.tab_bookmark)) },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = {
                            Crossfade(targetState = selectedTab == 2, label = "submit_icon") {
                                Icon(
                                    imageVector = if (it) Icons.Filled.Upload else Icons.Outlined.Upload,
                                    contentDescription = stringResource(R.string.tab_submit),
                                )
                            }
                        },
                        label = { Text(stringResource(R.string.tab_submit)) },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = {
                            Crossfade(targetState = selectedTab == 3, label = "settings_icon") {
                                Icon(
                                    imageVector = if (it) Icons.Filled.Settings else Icons.Outlined.Settings,
                                    contentDescription = stringResource(R.string.tab_settings),
                                )
                            }
                        },
                        label = { Text(stringResource(R.string.tab_settings)) },
                    )
                }
            },
            modifier = modifier,
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                when (selectedTab) {
                    0 ->
                        TodayPage(
                            todayArtwork = todayArtwork,
                            isLoggedIn = isLoggedIn,
                            onFullscreenImage = { fullscreenPreview = it },
                            onReactionClick = onReactionClick,
                            onRefresh = onRefresh,
                            onBookmarkToggle = onBookmarkToggle,
                            refreshProgress = refreshProgress,
                            initialPage = todayPagerPage,
                            onPageChanged = { todayPagerPage = it },
                            windowSizeClass = windowSizeClass.widthSizeClass,
                            onPageOpened = onTodayPageOpened,
                        )
                    1 ->
                        BookmarkPage(
                            cachedArtwork = bookmarkArtwork,
                            onFullscreenImage = { fullscreenPreview = it },
                            onRemoveBookmark = onRemoveBookmark,
                            emptyMessage = stringResource(R.string.msg_no_bookmarks),
                            searchQuery = bookmarkSearchQuery,
                            selectedTag = bookmarkSelectedTag,
                            onSearchQueryChange = { bookmarkSearchQuery = it },
                            onTagSelected = { bookmarkSelectedTag = it },
                            windowSizeClass = windowSizeClass.widthSizeClass,
                        )
                    3 ->
                        Box(Modifier.padding(top = padding.calculateTopPadding())) {
                            SettingsPage(
                                selectedTags = selectedTags,
                                onTagsChanged = onTagsChanged,
                                isLoggedIn = isLoggedIn,
                                onLogin = onLogin,
                                onLogout = onLogout,
                                bgmUsername = bgmUsername,
                                bgmNickname = bgmNickname,
                                bgmAvatarUrl = bgmAvatarUrl,
                                bgmDomain = bgmDomain,
                                onDomainChanged = onDomainChanged,
                                lcBadge = lcBadge,
                                onBadgeChanged = onBadgeChanged,
                                isSourceActivated = isSourceActivated,
                                isMuzeiInstalled = isMuzeiInstalled,
                                onOpenMuzei = onOpenMuzei,
                                themeMode = themeMode,
                                onThemeModeChanged = onThemeModeChanged,
                                colorSource = colorSource,
                                onColorSourceChanged = onColorSourceChanged,
                                colorStyle = colorStyle,
                                onColorStyleChanged = onColorStyleChanged,
                                manualColorArgb = manualColorArgb,
                                onManualColorChanged = onManualColorChanged,
                                sourceColorArgb = sourceColorArgb,
                                onOpenDebug = onOpenDebug,
                                onOpenAbout = onOpenAbout,
                                todayArtwork = todayArtwork,
                            )
                        }
                }
                SubmitPage(
                    isLoggedIn = isLoggedIn,
                    onLogin = onLogin,
                    initialSourceUrl = initialSourceUrl,
                    windowSizeClass = windowSizeClass.widthSizeClass,
                    modifier = if (selectedTab == 2) Modifier else Modifier.size(0.dp),
                )
            }
        }
    }

    fullscreenPreview?.let { preview ->
        FullscreenImageOverlay(preview = preview, onDismiss = { fullscreenPreview = null })
    }
}
