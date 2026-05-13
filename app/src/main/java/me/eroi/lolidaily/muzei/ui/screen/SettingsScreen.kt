package me.eroi.lolidaily.muzei.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import me.eroi.lolidaily.muzei.ui.screen.components.*
import me.eroi.lolidaily.muzei.ui.screen.gallery.*
import me.eroi.lolidaily.muzei.ui.theme.ThemeMode
import me.eroi.lolidaily.muzei.util.SectionTitle

private const val KEY_BANNER_DISMISSED = "banner_dismissed_status"
private const val KEY_LAST_TAB = "settings_last_tab"

/**
 * MD3 settings screen for the Loli Daily Muzei plugin.
 *
 * Three destinations via bottom NavigationBar:
 * - Today: current day's artwork gallery
 * - History: all previously cached artwork
 * - Settings: tag filters, account, theme, debug
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
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
    bgmDomain: String = "chii.in",
    onDomainChanged: (String) -> Unit = {},
    isSourceActivated: Boolean = false,
    isMuzeiInstalled: Boolean = false,
    onOpenMuzei: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChanged: (ThemeMode) -> Unit = {},
    onOpenDebug: () -> Unit = {},
    onBookmarkToggle: (token: String, fileName: String, bookmarked: Boolean) -> Unit = { _, _, _ -> },
    onRemoveBookmark: (ArtworkPreview) -> Unit = {},
    initialTab: Int? = null,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, android.content.Context.MODE_PRIVATE) }
    var selectedTab by remember {
        mutableIntStateOf(initialTab ?: prefs.getInt(KEY_LAST_TAB, 0))
    }
    var todayPagerPage by remember { mutableIntStateOf(0) }
    var fullscreenPreview by remember { mutableStateOf<ArtworkPreview?>(null) }
    var bookmarkSearchQuery by remember { mutableStateOf("") }
    var bookmarkSelectedTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedTab) {
        prefs.edit().putInt(KEY_LAST_TAB, selectedTab).apply()
    }

    Scaffold(
        topBar = {
            if (selectedTab == 2) {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.title_settings)) },
                    colors =
                        TopAppBarDefaults.centerAlignedTopAppBarColors(
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
                        val selected = selectedTab == 0
                        Crossfade(targetState = selected, label = "today_icon") {
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
                        val selected = selectedTab == 1
                        Crossfade(targetState = selected, label = "bookmark_icon") {
                            Icon(
                                imageVector =
                                    if (it) Icons.Filled.Bookmarks else Icons.Outlined.Bookmarks,
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
                        val selected = selectedTab == 2
                        Crossfade(targetState = selected, label = "settings_icon") {
                            Icon(
                                imageVector =
                                    if (it) Icons.Filled.Settings else Icons.Outlined.Settings,
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
                        onLogin = onLogin,
                        onFullscreenImage = { fullscreenPreview = it },
                        onReactionClick = onReactionClick,
                        onRefresh = onRefresh,
                        onBookmarkToggle = onBookmarkToggle,
                        initialPage = todayPagerPage,
                        onPageChanged = { todayPagerPage = it },
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
                        emptyMessage =
                            stringResource(R.string.msg_no_bookmarks),
                        isToday = false,
                        searchQuery = bookmarkSearchQuery,
                        selectedTag = bookmarkSelectedTag,
                        onSearchQueryChange = { bookmarkSearchQuery = it },
                        onTagSelected = { bookmarkSelectedTag = it },
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
                        bgmDomain = bgmDomain,
                        onDomainChanged = onDomainChanged,
                        isSourceActivated = isSourceActivated,
                        isMuzeiInstalled = isMuzeiInstalled,
                        onOpenMuzei = onOpenMuzei,
                        themeMode = themeMode,
                        onThemeModeChanged = onThemeModeChanged,
                        onOpenDebug = onOpenDebug,
                    )
                }
        }
    }

    fullscreenPreview?.let { preview ->
        FullscreenImageOverlay(preview = preview, onDismiss = { fullscreenPreview = null })
    }
}

// ── Settings (Preferences) ─────────────────────────────────────

@Composable
private fun PreferenceTab(
    selectedTags: Set<String>,
    onTagsChanged: (Set<String>) -> Unit,
    isLoggedIn: Boolean = false,
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    bgmDomain: String = "chii.in",
    onDomainChanged: (String) -> Unit = {},
    isSourceActivated: Boolean = false,
    isMuzeiInstalled: Boolean = false,
    onOpenMuzei: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChanged: (ThemeMode) -> Unit = {},
    onOpenDebug: () -> Unit = {},
) {
    val context = LocalContext.current
    val showBanner = (!isMuzeiInstalled || !isSourceActivated)

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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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

        item { SectionTitle(stringResource(R.string.section_muzei_wallpaper)) }

        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    FilterOption(
                        label = stringResource(R.string.label_show_all),
                        selected = selectedTags.isEmpty(),
                        onClick = { onTagsChanged(emptySet()) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    FilterOption(
                        label = stringResource(R.string.label_tag_lc0_lc_yj),
                        selected = selectedTags == setOf("LC0", "LC YJ"),
                        onClick = { onTagsChanged(setOf("LC0", "LC YJ")) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    FilterOption(
                        label = stringResource(R.string.label_tag_lc_es),
                        selected = selectedTags.contains("LC ES"),
                        onClick = { onTagsChanged(setOf("LC ES")) },
                    )
                }
            }
        }

        item {
            SourceStatusCard(
                isSourceActivated = isSourceActivated,
                isMuzeiInstalled = isMuzeiInstalled,
                onClick = onOpenMuzei,
            )
        }

        item { SectionTitle(stringResource(R.string.section_account)) }

        item {
            AccountCard(
                isLoggedIn = isLoggedIn,
                bgmDomain = bgmDomain,
                onLogin = onLogin,
                onLogout = onLogout,
                onDomainChanged = onDomainChanged,
            )
        }

        item { SectionTitle(stringResource(R.string.section_theme)) }

        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    ThemeOption(
                        label = stringResource(R.string.label_theme_system),
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeModeChanged(ThemeMode.SYSTEM) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    ThemeOption(
                        label = stringResource(R.string.label_theme_light),
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { onThemeModeChanged(ThemeMode.LIGHT) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    ThemeOption(
                        label = stringResource(R.string.label_theme_dark),
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { onThemeModeChanged(ThemeMode.DARK) },
                    )
                }
            }
        }

        item { SectionTitle(stringResource(R.string.section_debug)) }

        item {
            Surface(
                onClick = onOpenDebug,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.title_debug_settings), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = stringResource(R.string.label_debug_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
