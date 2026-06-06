package me.eroi.lolidaily.muzei.ui.screen.pages

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.ui.screen.components.SourceStatusCard

@Composable
fun WallpaperSheet(
    selectedTags: Set<String>,
    onTagsChanged: (Set<String>) -> Unit,
    isSourceActivated: Boolean,
    isMuzeiInstalled: Boolean,
    onOpenMuzei: () -> Unit,
    isLoggedIn: Boolean = false,
    lcBadge: String? = null,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE) }
    val overrideApiTagEnabled = remember { prefs.getBoolean(LoliApiClient.KEY_DEBUG_OVERRIDE_API_TAG_ENABLED, false) }
    val overrideApiTag = remember { prefs.getString(LoliApiClient.KEY_DEBUG_OVERRIDE_API_TAG, null) ?: "" }
    val hasEsBadge = lcBadge?.contains("ES") == true
    val isNonDefaultTagOverride = overrideApiTagEnabled && overrideApiTag.isNotBlank() && overrideApiTag !in listOf("LC0", "LC YJ")
    val showTagSelector = (isLoggedIn && !lcBadge.isNullOrBlank() && hasEsBadge) || isNonDefaultTagOverride

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
fun selectedTagsLabel(tags: Set<String>): String {
    return when {
        tags == setOf("LC0", "LC YJ") -> "LC0 / LC YJ"
        tags.contains("LC ES") -> "LC ES"
        tags.isEmpty() -> stringResource(R.string.label_all)
        else -> tags.joinToString(" / ")
    }
}
