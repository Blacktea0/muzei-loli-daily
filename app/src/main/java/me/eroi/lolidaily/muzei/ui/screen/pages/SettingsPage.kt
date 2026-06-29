package me.eroi.lolidaily.muzei.ui.screen.pages

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import me.eroi.lolidaily.muzei.BuildConfig
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import me.eroi.lolidaily.muzei.ui.screen.components.*
import me.eroi.lolidaily.muzei.ui.theme.ColorSource
import me.eroi.lolidaily.muzei.ui.theme.ColorStyle
import me.eroi.lolidaily.muzei.ui.theme.ThemeMode

private const val KEY_BANNER_DISMISSED = "banner_dismissed_status"

private enum class SettingsSheet {
    WALLPAPER,
    ACCOUNT,
    THEME,
}

// ── Settings ─────────────────────────────────────

@Composable
fun SettingsPage(
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
    onOpenAbout: () -> Unit = {},
    todayArtwork: List<ArtworkPreview> = emptyList(),
    windowSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
) {
    val context = LocalContext.current
    val showBanner = (!isMuzeiInstalled || !isSourceActivated)
    var openSheet by remember { mutableStateOf<SettingsSheet?>(null) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showBatteryBanner by remember { mutableStateOf(false) }

    LaunchedEffect(isMuzeiInstalled, isSourceActivated) {
        val prefs =
            context.getSharedPreferences(
                LoliDailyArtWorker.PREFS_NAME,
                Context.MODE_PRIVATE,
            )
        val stored = prefs.getString(KEY_BANNER_DISMISSED, null)
        if (
            stored != null && stored != "installed=$isMuzeiInstalled,activated=$isSourceActivated"
        ) {
            prefs.edit { remove(KEY_BANNER_DISMISSED) }
        }
    }

    LaunchedEffect(Unit) {
        updateInfo = checkForUpdate(context)
        showBatteryBanner = !isBatteryBannerDismissed(context)
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

        updateInfo?.let { info ->
            item {
                UpdateBanner(
                    latestVersion = info.latestVersion,
                    downloadUrl = info.downloadUrl,
                    releaseNotes = info.releaseNotes,
                    onDismiss = { updateInfo = null },
                )
            }
        }

        if (showBatteryBanner) {
            item {
                BatteryBanner(onDismiss = { showBatteryBanner = false })
            }
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
                        )} · ${colorSourceLabel(colorSource)} · ${colorStyleLabel(if (colorSource == ColorSource.DEFAULT) ColorStyle.NEUTRAL else colorStyle)}",
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
                    onClick = onOpenDebug,
                )
            }
        }

        item {
            SegmentedSettingsGroup {
                GroupedSettingsRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.title_about),
                    subtitle = stringResource(R.string.about_version_info, BuildConfig.VERSION_NAME),
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onOpenAbout,
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
