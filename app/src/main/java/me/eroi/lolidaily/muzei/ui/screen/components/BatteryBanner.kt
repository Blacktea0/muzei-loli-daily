package me.eroi.lolidaily.muzei.ui.screen.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.core.net.toUri
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.R

private const val KEY_BATTERY_BANNER_DISMISSED = "battery_banner_dismissed"

fun isBatteryBannerDismissed(context: Context): Boolean {
    val prefs = context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_BATTERY_BANNER_DISMISSED, false)
}

@Composable
fun BatteryBanner(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs =
        androidx.compose.runtime.remember(context) {
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        }

    val dismissBanner = {
        prefs.edit { putBoolean(KEY_BATTERY_BANNER_DISMISSED, true) }
        onDismiss()
    }

    InfoBanner(
        icon = Icons.Default.BatteryStd,
        title = stringResource(R.string.banner_battery_title),
        description = stringResource(R.string.banner_battery_desc),
        onDismiss = dismissBanner,
        actionButton = {
            FilledTonalButton(
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val intent =
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                        context.startActivity(intent)
                    }
                },
            ) {
                Text(stringResource(R.string.action_battery_settings))
            }
        },
    )
}
