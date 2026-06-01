package me.eroi.lolidaily.muzei.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import me.eroi.lolidaily.muzei.ui.screen.components.*
import me.eroi.lolidaily.muzei.ui.screen.gallery.*
import me.eroi.lolidaily.muzei.ui.theme.ColorSource
import me.eroi.lolidaily.muzei.ui.theme.ColorStyle
import me.eroi.lolidaily.muzei.ui.theme.ThemeMode
import me.eroi.lolidaily.muzei.util.M3SchemeGenerator

private val DEFAULT_SOURCE_COLOR = 0xFFF09199.toInt()

private const val KEY_BANNER_DISMISSED = "banner_dismissed_status"
private const val KEY_LAST_TAB = "settings_last_tab"

/**
 * MD3 main screen for the Loli Daily Muzei plugin.
 *
 * Three destinations via bottom NavigationBar (phone) or NavigationRail (tablet/foldable):
 * - Today: current day's artwork gallery
 * - History: all previously cached artwork
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
        prefs.edit().putInt(KEY_LAST_TAB, selectedTab).apply()
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
                if (selectedTab == 2) Icons.Filled.Settings else Icons.Outlined.Settings,
                stringResource(R.string.tab_settings),
                2,
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
                if (selectedTab == 2) {
                    CenterAlignedTopAppBar(
                        title = { Text(stringResource(R.string.title_settings)) },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                    )
                }

                // Tab content
                when (selectedTab) {
                    0 ->
                        TodayGallery(
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
                        ArtworkGallery(
                            cachedArtwork = bookmarkArtwork,
                            onFullscreenImage = { fullscreenPreview = it },
                            isLoggedIn = isLoggedIn,
                            onLogin = onLogin,
                            onReactionClick = onReactionClick,
                            onRemoveBookmark = onRemoveBookmark,
                            emptyMessage = stringResource(R.string.msg_no_bookmarks),
                            isToday = false,
                            searchQuery = bookmarkSearchQuery,
                            selectedTag = bookmarkSelectedTag,
                            onSearchQueryChange = { bookmarkSearchQuery = it },
                            onTagSelected = { bookmarkSelectedTag = it },
                            windowSizeClass = windowSizeClass.widthSizeClass,
                        )

                    2 ->
                        PreferenceTab(
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
                            todayArtwork = todayArtwork,
                            windowSizeClass = windowSizeClass.widthSizeClass,
                        )
                }
            }
        }
    } else {
        // Phone: Bottom NavigationBar
        Scaffold(
            topBar = {
                if (selectedTab == 2) {
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
                            Crossfade(targetState = selectedTab == 2, label = "settings_icon") {
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
            when (selectedTab) {
                0 ->
                    Box(modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
                        TodayGallery(
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
                    }

                1 ->
                    Box(modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
                        ArtworkGallery(
                            cachedArtwork = bookmarkArtwork,
                            onFullscreenImage = { fullscreenPreview = it },
                            isLoggedIn = isLoggedIn,
                            onLogin = onLogin,
                            onReactionClick = onReactionClick,
                            onRemoveBookmark = onRemoveBookmark,
                            emptyMessage = stringResource(R.string.msg_no_bookmarks),
                            isToday = false,
                            searchQuery = bookmarkSearchQuery,
                            selectedTag = bookmarkSelectedTag,
                            onSearchQueryChange = { bookmarkSearchQuery = it },
                            onTagSelected = { bookmarkSelectedTag = it },
                            windowSizeClass = windowSizeClass.widthSizeClass,
                        )
                    }

                2 ->
                    Box(modifier = Modifier.padding(padding)) {
                        PreferenceTab(
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
                            todayArtwork = todayArtwork,
                        )
                    }
            }
        }
    }

    fullscreenPreview?.let { preview ->
        FullscreenImageOverlay(preview = preview, onDismiss = { fullscreenPreview = null })
    }
}

private enum class SettingsSheet {
    WALLPAPER,
    ACCOUNT,
    THEME,
}

@Composable
private fun SegmentedSettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
private fun GroupedSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconContentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    leadingContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    endIcon: ImageVector = Icons.Filled.ChevronRight,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        ListItem(
            modifier = Modifier.defaultMinSize(minHeight = 76.dp),
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent =
                if (leadingContent != null) {
                    { leadingContent() }
                } else {
                    {
                        SettingsIconContainer(
                            icon = icon,
                            containerColor = iconContainerColor,
                            contentColor = iconContentColor,
                        )
                    }
                },
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    trailing?.invoke()
                    Icon(
                        imageVector = endIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun StatusBadge(
    text: String,
    active: Boolean,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    val color =
        if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        }
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = letterSpacing,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SettingsIconContainer(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun PaletteDots(
    argb: Int,
    style: ColorStyle,
    dark: Boolean,
) {
    val scheme = remember(argb, style, dark) { M3SchemeGenerator.fromSourceColor(argb, dark, style) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEach { color ->
                Box(
                    modifier =
                        Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(color),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun SheetTitle(
    icon: ImageVector,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        SettingsIconContainer(
            icon = icon,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(text = text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WallpaperSheet(
    selectedTags: Set<String>,
    onTagsChanged: (Set<String>) -> Unit,
    isSourceActivated: Boolean,
    isMuzeiInstalled: Boolean,
    onOpenMuzei: () -> Unit,
    isLoggedIn: Boolean = false,
    lcBadge: String? = null,
) {
    val hasEsBadge = lcBadge?.contains("ES") == true
    val showTagSelector = isLoggedIn && !lcBadge.isNullOrBlank() && hasEsBadge

    SheetTitle(Icons.Filled.Photo, stringResource(R.string.title_muzei_wallpaper))
    if (showTagSelector) {
        SettingsChoiceGroup {
            ChoiceRowWithBadge(
                badgeAsset = "lc_badges/LC0.svg",
                selected = selectedTags == setOf("LC0", "LC YJ"),
                onClick = { onTagsChanged(setOf("LC0", "LC YJ")) },
            )
            ChoiceRowWithBadge(
                badgeAsset = "lc_badges/LC ES.svg",
                selected = selectedTags.contains("LC ES"),
                onClick = { onTagsChanged(setOf("LC ES")) },
            )
        }
    }
    SourceStatusCard(
        isSourceActivated = isSourceActivated,
        isMuzeiInstalled = isMuzeiInstalled,
        onClick = onOpenMuzei,
    )
}

@Composable
private fun AccountSheet(
    isLoggedIn: Boolean,
    bgmDomain: String,
    bgmUsername: String?,
    bgmNickname: String?,
    bgmAvatarUrl: String?,
    lcBadge: String?,
    onBadgeChanged: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onDomainChanged: (String) -> Unit,
) {
    var showDomainPicker by remember { mutableStateOf(false) }
    var showBadgePicker by remember { mutableStateOf(false) }

    SheetTitle(Icons.Filled.AccountCircle, stringResource(R.string.title_bangumi_account))
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AccountAvatar(
                    avatarUrl = if (isLoggedIn) bgmAvatarUrl else null,
                    nickname = bgmNickname ?: bgmUsername,
                    size = 52.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            if (isLoggedIn) {
                                bgmNickname ?: bgmUsername ?: stringResource(R.string.status_logged_in)
                            } else {
                                stringResource(R.string.status_not_logged_in)
                            },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            if (isLoggedIn && bgmUsername != null) {
                                "@$bgmUsername · $bgmDomain"
                            } else {
                                stringResource(R.string.label_via_domain, bgmDomain)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (isLoggedIn && !lcBadge.isNullOrBlank()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                LcBadgeImage(
                    badge = lcBadge,
                    onClick = { showBadgePicker = true },
                )
            }
        }
    }

    if (isLoggedIn) {
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_logout))
        }
    } else {
        FilledTonalButton(onClick = { showDomainPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_login))
        }
    }

    if (showDomainPicker) {
        DomainPickerDialog(
            currentDomain = bgmDomain,
            onDomainSelected = { domain ->
                showDomainPicker = false
                onDomainChanged(domain)
                onLogin()
            },
            onDismiss = { showDomainPicker = false },
        )
    }

    if (showBadgePicker) {
        BadgePickerDialog(
            currentBadge = lcBadge ?: "LC0",
            onBadgeSelected = { newBadge ->
                showBadgePicker = false
                onBadgeChanged(newBadge)
            },
            onDismiss = { showBadgePicker = false },
        )
    }
}

@Composable
private fun AccountAvatar(
    avatarUrl: String?,
    nickname: String?,
    size: Dp,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(size),
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(50)),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = nickname?.firstOrNull()?.uppercase() ?: "B",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun LcBadgeImage(
    badge: String,
    onClick: (() -> Unit)? = null,
) {
    val svgUri = "file:///android_asset/lc_badges/$badge.svg"
    AsyncImage(
        model = svgUri,
        contentDescription = badge,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

private fun computeBadge(
    q1: Int,
    q2: Int,
    pg: Boolean,
): String {
    if (q1 == 0 && q2 == 1) return "LC0"
    val parts = mutableListOf<String>()
    if (q2 == 0) parts += "YJ"
    if (q1 >= 1) {
        parts += "ES"
        if (q1 >= 2) parts += "NC"
        if (pg) parts += "PG"
        if (q1 >= 3) parts += "GR"
    }
    return "LC ${parts.joinToString("-")}"
}

private fun parseBadgeQuestions(badge: String): Triple<Int, Int, Boolean> {
    val hasYJ = badge.contains("YJ")
    val q2 = if (hasYJ) 0 else 1
    val hasPG = badge.contains("PG")
    val q1 =
        when {
            badge.contains("GR") -> 3
            badge.contains("NC") -> 2
            badge.contains("ES") -> 1
            else -> 0
        }
    return Triple(q1, q2, hasPG)
}

@Composable
private fun BadgePickerDialog(
    currentBadge: String,
    onBadgeSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val (initQ1, initQ2, initPg) = remember { parseBadgeQuestions(currentBadge) }
    var q1 by remember { mutableIntStateOf(initQ1) }
    var q2 by remember { mutableIntStateOf(initQ2) }
    var pg by remember { mutableStateOf(initPg) }
    val resultBadge = remember(q1, q2, pg) { computeBadge(q1, q2, pg) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.title_badge_picker),
                    style = MaterialTheme.typography.titleMedium,
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.badge_q1),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf(
                        R.string.badge_q1_a0,
                        R.string.badge_q1_a1,
                        R.string.badge_q1_a2,
                        R.string.badge_q1_a3,
                    ).forEachIndexed { index, resId ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        q1 = index
                                        if (index == 0) pg = false
                                    }
                                    .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = q1 == index, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(resId),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.badge_q2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf(
                        R.string.badge_q2_a0,
                        R.string.badge_q2_a1,
                    ).forEachIndexed { index, resId ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { q2 = index }
                                    .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = q2 == index, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(resId),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }

                if (q1 >= 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.badge_q3),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { pg = true }
                                    .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = pg, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.badge_q3_a0),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { pg = false }
                                    .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = !pg, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.badge_q3_a1),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }

                AsyncImage(
                    model = "file:///android_asset/lc_badges/$resultBadge.svg",
                    contentDescription = resultBadge,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    FilledTonalButton(onClick = { onBadgeSelected(resultBadge) }) {
                        Text(stringResource(R.string.action_confirm))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeSheet(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    colorSource: ColorSource,
    onColorSourceChanged: (ColorSource) -> Unit,
    colorStyle: ColorStyle,
    onColorStyleChanged: (ColorStyle) -> Unit,
    manualColorArgb: Int,
    onManualColorChanged: (Int) -> Unit,
    sourceColorArgb: Int?,
    artworkAvailable: Boolean,
) {
    val darkPreview =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val previewArgb = sourceColorArgb ?: if (colorSource == ColorSource.MANUAL) manualColorArgb else DEFAULT_SOURCE_COLOR

    SheetTitle(Icons.Filled.Palette, stringResource(R.string.title_theme_colors))

    SettingsSubhead(stringResource(R.string.label_appearance_mode))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = themeMode == mode,
                onClick = { onThemeModeChanged(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
            ) {
                Text(themeModeLabel(mode), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    SettingsSubhead(stringResource(R.string.section_color_source))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ColorSource.entries.forEach { source ->
            FilterChip(
                selected = colorSource == source,
                onClick = { onColorSourceChanged(source) },
                leadingIcon = {
                    Icon(colorSourceIcon(source), contentDescription = null, modifier = Modifier.size(18.dp))
                },
                label = { Text(colorSourceLabel(source)) },
            )
        }
    }

    if (colorSource == ColorSource.IMAGE && !artworkAvailable) {
        Text(
            text = stringResource(R.string.hint_no_artwork_for_color),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (colorSource == ColorSource.MANUAL) {
        ManualColorPickerRow(
            currentColorArgb = manualColorArgb,
            onColorChanged = onManualColorChanged,
        )
    }

    if (colorSource != ColorSource.DEFAULT) {
        SettingsSubhead(stringResource(R.string.title_color_style_preview))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(colorStyleOptions()) { style ->
                ColorStylePreviewCard(
                    style = style,
                    selected = colorStyle == style,
                    sourceArgb = previewArgb,
                    dark = darkPreview,
                    onClick = { onColorStyleChanged(style) },
                )
            }
        }
    }
}

@Composable
private fun SettingsSubhead(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun ColorStylePreviewCard(
    style: ColorStyle,
    selected: Boolean,
    sourceArgb: Int,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val scheme = remember(style, sourceArgb, dark) { M3SchemeGenerator.fromSourceColor(sourceArgb, dark, style) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border =
            BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        modifier = Modifier.width(112.dp),
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(68.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(scheme.primary),
                )
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(scheme.secondary),
                )
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(scheme.tertiary),
                )
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(scheme.surface),
                )
            }
            Text(
                text = colorStyleLabel(style),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingsChoiceGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(4.dp), content = content)
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun ChoiceRowWithBadge(
    badgeAsset: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = "file:///android_asset/$badgeAsset",
            contentDescription = null,
            modifier = Modifier.height(36.dp).weight(1f),
        )
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun selectedTagsLabel(tags: Set<String>): String {
    return when {
        tags == setOf("LC0", "LC YJ") -> "LC0 / LC YJ"
        tags.contains("LC ES") -> "LC ES"
        tags.isEmpty() -> stringResource(R.string.label_all)
        else -> tags.joinToString(" / ")
    }
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String {
    return when (mode) {
        ThemeMode.SYSTEM -> stringResource(R.string.label_theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.label_theme_light)
        ThemeMode.DARK -> stringResource(R.string.label_theme_dark)
    }
}

@Composable
private fun colorSourceLabel(source: ColorSource): String {
    return when (source) {
        ColorSource.DEFAULT -> stringResource(R.string.label_color_source_default)
        ColorSource.IMAGE -> stringResource(R.string.label_color_source_image)
        ColorSource.MANUAL -> stringResource(R.string.label_color_source_manual)
    }
}

private fun colorSourceIcon(source: ColorSource): ImageVector {
    return when (source) {
        ColorSource.DEFAULT -> Icons.Filled.AutoAwesome
        ColorSource.IMAGE -> Icons.Filled.ImageSearch
        ColorSource.MANUAL -> Icons.Filled.ColorLens
    }
}

@Composable
private fun colorStyleLabel(style: ColorStyle): String {
    return when (style) {
        ColorStyle.TONAL_SPOT -> stringResource(R.string.label_style_tonal_spot)
        ColorStyle.VIBRANT -> stringResource(R.string.label_style_vibrant)
        ColorStyle.CONTENT -> stringResource(R.string.label_style_content)
        ColorStyle.FIDELITY -> stringResource(R.string.label_style_fidelity)
        ColorStyle.EXPRESSIVE -> stringResource(R.string.label_style_expressive)
        ColorStyle.MONOCHROME -> stringResource(R.string.label_style_monochrome)
        ColorStyle.NEUTRAL -> stringResource(R.string.label_style_neutral)
        ColorStyle.RAINBOW -> stringResource(R.string.label_style_rainbow)
        ColorStyle.FRUIT_SALAD -> stringResource(R.string.label_style_fruit_salad)
    }
}

private fun colorStyleOptions(): List<ColorStyle> =
    listOf(
        ColorStyle.TONAL_SPOT,
        ColorStyle.VIBRANT,
        ColorStyle.CONTENT,
        ColorStyle.FIDELITY,
        ColorStyle.EXPRESSIVE,
        ColorStyle.MONOCHROME,
        ColorStyle.RAINBOW,
        ColorStyle.FRUIT_SALAD,
        ColorStyle.NEUTRAL,
    )

// ── Settings (Preferences) ─────────────────────────────────────

@Composable
private fun PreferenceTab(
    selectedTags: Set<String>,
    onTagsChanged: (Set<String>) -> Unit,
    isLoggedIn: Boolean = false,
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    bgmUsername: String? = null,
    bgmNickname: String? = null,
    bgmAvatarUrl: String? = null,
    bgmDomain: String = "chii.in",
    onDomainChanged: (String) -> Unit = {},
    lcBadge: String? = null,
    onBadgeChanged: (String) -> Unit = {},
    isSourceActivated: Boolean = false,
    isMuzeiInstalled: Boolean = false,
    onOpenMuzei: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChanged: (ThemeMode) -> Unit = {},
    colorSource: ColorSource = ColorSource.DEFAULT,
    onColorSourceChanged: (ColorSource) -> Unit = {},
    colorStyle: ColorStyle = ColorStyle.NEUTRAL,
    onColorStyleChanged: (ColorStyle) -> Unit = {},
    manualColorArgb: Int = 0xFF6B4DA3.toInt(),
    onManualColorChanged: (Int) -> Unit = {},
    sourceColorArgb: Int? = null,
    onOpenDebug: () -> Unit = {},
    todayArtwork: List<ArtworkPreview> = emptyList(),
    windowSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
) {
    val context = LocalContext.current
    val showBanner = (!isMuzeiInstalled || !isSourceActivated)
    var openSheet by remember { mutableStateOf<SettingsSheet?>(null) }

    LaunchedEffect(isMuzeiInstalled, isSourceActivated) {
        val prefs =
            context.getSharedPreferences(
                LoliDailyArtWorker.PREFS_NAME,
                android.content.Context.MODE_PRIVATE,
            )
        val stored = prefs.getString(KEY_BANNER_DISMISSED, null)
        if (
            stored != null && stored != "installed=$isMuzeiInstalled,activated=$isSourceActivated"
        ) {
            prefs.edit().remove(KEY_BANNER_DISMISSED).apply()
        }
    }

    val isExpandedScreen = windowSizeClass == WindowWidthSizeClass.Expanded
    val horizontalPadding = if (isExpandedScreen) 32.dp else 16.dp

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = horizontalPadding, top = 12.dp, end = horizontalPadding, bottom = 28.dp),
    ) {
        if (showBanner) {
            item {
                SetupBanner(
                    isMuzeiInstalled = isMuzeiInstalled,
                    isSourceActivated = isSourceActivated,
                    onOpenMuzei = onOpenMuzei,
                )
            }
        }

        item {
            BatteryBanner()
        }

        item {
            SegmentedSettingsGroup {
                GroupedSettingsRow(
                    icon = Icons.Filled.Photo,
                    title = stringResource(R.string.title_muzei_wallpaper),
                    subtitle = selectedTagsLabel(selectedTags),
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailing = {
                        StatusBadge(
                            text =
                                if (isSourceActivated) {
                                    stringResource(R.string.status_enabled_short)
                                } else {
                                    stringResource(R.string.status_not_enabled_short)
                                },
                            active = isSourceActivated,
                        )
                    },
                    onClick = { openSheet = SettingsSheet.WALLPAPER },
                )
            }
        }

        item {
            SegmentedSettingsGroup {
                GroupedSettingsRow(
                    icon = Icons.Filled.AccountCircle,
                    title = stringResource(R.string.title_bangumi_account),
                    subtitle =
                        if (isLoggedIn) {
                            "${bgmNickname ?: bgmUsername ?: stringResource(R.string.status_logged_in)} · $bgmDomain"
                        } else {
                            stringResource(R.string.label_login_to_react)
                        },
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    leadingContent =
                        if (isLoggedIn) {
                            {
                                AccountAvatar(
                                    avatarUrl = bgmAvatarUrl,
                                    nickname = bgmNickname ?: bgmUsername,
                                    size = 40.dp,
                                )
                            }
                        } else {
                            null
                        },
                    trailing = {
                        if (isLoggedIn && !lcBadge.isNullOrBlank()) {
                            StatusBadge(text = lcBadge, active = true, letterSpacing = (-0.5).sp)
                        }
                    },
                    onClick = { openSheet = SettingsSheet.ACCOUNT },
                )
            }
        }

        item {
            SegmentedSettingsGroup {
                GroupedSettingsRow(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.title_theme_colors),
                    subtitle =
                        "${themeModeLabel(
                            themeMode,
                        )} · ${colorStyleLabel(if (colorSource == ColorSource.DEFAULT) ColorStyle.NEUTRAL else colorStyle)}",
                    iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    trailing = {
                        PaletteDots(
                            argb = sourceColorArgb ?: if (colorSource == ColorSource.MANUAL) manualColorArgb else DEFAULT_SOURCE_COLOR,
                            style = if (colorSource == ColorSource.DEFAULT) ColorStyle.NEUTRAL else colorStyle,
                            dark = themeMode == ThemeMode.DARK,
                        )
                    },
                    onClick = { openSheet = SettingsSheet.THEME },
                )
                GroupedSettingsRow(
                    icon = Icons.Filled.Settings,
                    title = stringResource(R.string.title_debug_settings),
                    subtitle = stringResource(R.string.label_debug_subtitle),
                    iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    endIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = onOpenDebug,
                )
            }
        }
    }

    openSheet?.let { sheet ->
        SettingsBottomSheet(onDismiss = { openSheet = null }) {
            when (sheet) {
                SettingsSheet.WALLPAPER ->
                    WallpaperSheet(
                        selectedTags = selectedTags,
                        onTagsChanged = onTagsChanged,
                        isSourceActivated = isSourceActivated,
                        isMuzeiInstalled = isMuzeiInstalled,
                        onOpenMuzei = onOpenMuzei,
                        isLoggedIn = isLoggedIn,
                        lcBadge = lcBadge,
                    )
                SettingsSheet.ACCOUNT ->
                    AccountSheet(
                        isLoggedIn = isLoggedIn,
                        bgmDomain = bgmDomain,
                        bgmUsername = bgmUsername,
                        bgmNickname = bgmNickname,
                        bgmAvatarUrl = bgmAvatarUrl,
                        lcBadge = lcBadge,
                        onBadgeChanged = onBadgeChanged,
                        onLogin = onLogin,
                        onLogout = onLogout,
                        onDomainChanged = onDomainChanged,
                    )
                SettingsSheet.THEME ->
                    ThemeSheet(
                        themeMode = themeMode,
                        onThemeModeChanged = onThemeModeChanged,
                        colorSource = colorSource,
                        onColorSourceChanged = onColorSourceChanged,
                        colorStyle = colorStyle,
                        onColorStyleChanged = onColorStyleChanged,
                        manualColorArgb = manualColorArgb,
                        onManualColorChanged = onManualColorChanged,
                        sourceColorArgb = sourceColorArgb,
                        artworkAvailable = todayArtwork.isNotEmpty(),
                    )
            }
        }
    }
}
