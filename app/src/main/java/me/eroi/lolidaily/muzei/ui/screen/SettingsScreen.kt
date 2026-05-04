package me.eroi.lolidaily.muzei.ui.screen

import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.unit.dp
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
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
    historyArtwork: List<ArtworkPreview>,
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
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, android.content.Context.MODE_PRIVATE) }
    var selectedTab by remember {
        mutableIntStateOf(prefs.getInt(KEY_LAST_TAB, 0))
    }
    var todayPagerPage by remember { mutableIntStateOf(0) }
    var fullscreenPreview by remember { mutableStateOf<ArtworkPreview?>(null) }

    LaunchedEffect(selectedTab) {
        prefs.edit().putInt(KEY_LAST_TAB, selectedTab).apply()
    }

    Scaffold(
        topBar = {
            if (selectedTab != 0) {
                CenterAlignedTopAppBar(
                    title = { Text("Loli Daily Settings") },
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
                                contentDescription = "Today",
                            )
                        }
                    },
                    label = { Text("Today") },
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
                                contentDescription = "Bookmark",
                            )
                        }
                    },
                    label = { Text("Bookmark") },
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
                                contentDescription = "Settings",
                            )
                        }
                    },
                    label = { Text("Settings") },
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
                        initialPage = todayPagerPage,
                        onPageChanged = { todayPagerPage = it },
                    )
                }

            1 ->
                Box(modifier = Modifier.padding(padding)) {
                    ArtworkGallery(
                        cachedArtwork = historyArtwork,
                        onFullscreenImage = { fullscreenPreview = it },
                        isLoggedIn = isLoggedIn,
                        onLogin = onLogin,
                        onReactionClick = onReactionClick,
                        emptyMessage =
                            "No historical artwork saved yet.\nArtwork accumulates as new daily batches are fetched.",
                        isToday = false,
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

        item { SectionTitle("MUZEI WALLPAPER") }

        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    FilterOption(
                        label = "Show all tags (no filter)",
                        selected = selectedTags.isEmpty(),
                        onClick = { onTagsChanged(emptySet()) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    FilterOption(
                        label = "LC0",
                        selected = selectedTags.contains("LC0"),
                        onClick = { onTagsChanged(setOf("LC0")) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    FilterOption(
                        label = "LC ES",
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

        item { SectionTitle("ACCOUNT") }

        item {
            AccountCard(
                isLoggedIn = isLoggedIn,
                bgmDomain = bgmDomain,
                onLogin = onLogin,
                onLogout = onLogout,
                onDomainChanged = onDomainChanged,
            )
        }

        item { SectionTitle("THEME") }

        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    ThemeOption(
                        label = "Follow system",
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeModeChanged(ThemeMode.SYSTEM) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    ThemeOption(
                        label = "Light",
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { onThemeModeChanged(ThemeMode.LIGHT) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    ThemeOption(
                        label = "Dark",
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { onThemeModeChanged(ThemeMode.DARK) },
                    )
                }
            }
        }

        item { SectionTitle("DEBUG") }

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
                        Text(text = "Debug Settings", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Developer options and tools",
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
