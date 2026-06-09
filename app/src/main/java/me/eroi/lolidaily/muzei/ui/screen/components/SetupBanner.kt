package me.eroi.lolidaily.muzei.ui.screen.components

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.R

private const val KEY_BANNER_DISMISSED = "banner_dismissed_status"

@Composable
fun SetupBanner(
    isMuzeiInstalled: Boolean,
    isSourceActivated: Boolean,
    onOpenMuzei: () -> Unit,
) {
    val context = LocalContext.current
    val currentStatus = "installed=$isMuzeiInstalled,activated=$isSourceActivated"
    val prefs =
        remember(context) {
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        }

    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(isMuzeiInstalled, isSourceActivated) {
        val stored = prefs.getString(KEY_BANNER_DISMISSED, null)
        dismissed = (currentStatus == stored)
    }

    if (dismissed) return

    val dismissBanner = {
        prefs.edit { putString(KEY_BANNER_DISMISSED, currentStatus) }
        dismissed = true
    }

    val title =
        if (!isMuzeiInstalled) {
            stringResource(R.string.banner_muzei_not_installed)
        } else {
            stringResource(R.string.banner_source_not_enabled)
        }

    InfoBanner(
        icon = Icons.Default.Info,
        title = title,
        description = stringResource(R.string.msg_browse_without_muzei),
        onDismiss = dismissBanner,
        actionButton = {
            FilledTonalButton(onClick = onOpenMuzei) {
                if (!isMuzeiInstalled) {
                    Text(stringResource(R.string.action_install_muzei))
                } else {
                    Text(stringResource(R.string.action_open_muzei))
                }
            }
        },
    )
}
