package me.eroi.lolidaily.muzei.ui.screen.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    val releaseNotes: String?,
)

suspend fun checkForUpdate(context: Context): UpdateInfo? {
    val prefs = context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
    val result = VersionChecker.checkForUpdate(context)
    if (result.hasUpdate) {
        val dismissedVersion = prefs.getString(KEY_UPDATE_DISMISSED_VERSION, null)
        if (dismissedVersion != result.latestVersion) {
            return UpdateInfo(result.latestVersion, result.downloadUrl, result.releaseNotes)
        }
    }
    return null
}

@Composable
fun UpdateBanner(
    latestVersion: String,
    downloadUrl: String,
    releaseNotes: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showReleaseNotes by rememberSaveable { mutableStateOf(false) }
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
                onClick = { showReleaseNotes = true },
            ) {
                Text(stringResource(R.string.action_view_update_details))
            }
        },
    )

    if (showReleaseNotes) {
        UpdateReleaseNotesDialog(
            latestVersion = latestVersion,
            downloadUrl = downloadUrl,
            releaseNotes = releaseNotes,
            onDismiss = { showReleaseNotes = false },
        )
    }
}

@Composable
fun UpdateReleaseNotesDialog(
    latestVersion: String,
    downloadUrl: String,
    releaseNotes: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val notes = releaseNotes?.trim()?.takeIf { it.isNotEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(R.string.msg_update_available_title)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.msg_update_available_desc, latestVersion))
                Text(
                    text = "\n" + stringResource(R.string.title_release_notes) + "\n\n" +
                        (notes ?: stringResource(R.string.msg_release_notes_empty)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, downloadUrl.toUri()))
                onDismiss()
            }) {
                Text(stringResource(R.string.action_download_update))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
