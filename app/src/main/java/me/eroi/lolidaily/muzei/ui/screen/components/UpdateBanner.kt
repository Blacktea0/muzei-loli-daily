package me.eroi.lolidaily.muzei.ui.screen.components

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.util.VersionChecker

private const val KEY_UPDATE_DISMISSED_VERSION = "update_dismissed_version"

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
)

suspend fun checkForUpdate(context: Context): UpdateInfo? {
    val prefs = context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
    val result = VersionChecker.checkForUpdate(context)
    if (result.hasUpdate) {
        val dismissedVersion = prefs.getString(KEY_UPDATE_DISMISSED_VERSION, null)
        if (dismissedVersion != result.latestVersion) {
            return UpdateInfo(result.latestVersion, result.downloadUrl)
        }
    }
    return null
}

@Composable
fun UpdateBanner(
    latestVersion: String,
    downloadUrl: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs =
        androidx.compose.runtime.remember(context) {
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        }

    val dismissBanner: () -> Unit = {
        scope.launch {
            prefs.edit { putString(KEY_UPDATE_DISMISSED_VERSION, latestVersion) }
            onDismiss()
        }
        Unit
    }

    InfoBanner(
        icon = Icons.Default.SystemUpdate,
        title = stringResource(R.string.banner_update_available),
        description = stringResource(R.string.banner_update_description, latestVersion),
        onDismiss = dismissBanner,
        actionButton = {
            FilledTonalButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, downloadUrl.toUri())
                    context.startActivity(intent)
                },
            ) {
                Text(stringResource(R.string.action_download_update))
            }
        },
    )
}
