package me.eroi.lolidaily.muzei.ui.screen.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
            context.getSharedPreferences(
                LoliDailyArtWorker.PREFS_NAME,
                android.content.Context.MODE_PRIVATE,
            )
        }

    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(isMuzeiInstalled, isSourceActivated) {
        val stored = prefs.getString(KEY_BANNER_DISMISSED, null)
        dismissed = (currentStatus == stored)
    }

    if (dismissed) return

    val dismissBanner = {
        prefs.edit().putString(KEY_BANNER_DISMISSED, currentStatus).apply()
        dismissed = true
    }

    val title =
        if (!isMuzeiInstalled) {
            stringResource(
                R.string.banner_muzei_not_installed,
            )
        } else {
            stringResource(R.string.banner_source_not_enabled)
        }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = dismissBanner, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.content_desc_dismiss),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.msg_browse_without_muzei),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = dismissBanner) { Text(stringResource(R.string.action_dismiss)) }
                FilledTonalButton(onClick = onOpenMuzei) {
                    if (!isMuzeiInstalled) {
                        Text(stringResource(R.string.action_install_muzei))
                    } else {
                        Text(stringResource(R.string.action_open_muzei))
                    }
                }
            }
        }
    }
}
