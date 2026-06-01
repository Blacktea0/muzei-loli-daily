package me.eroi.lolidaily.muzei.ui.screen.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

@Composable
fun UpdateBanner() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs =
        remember(context) {
            context.getSharedPreferences(
                LoliDailyArtWorker.PREFS_NAME,
                Context.MODE_PRIVATE,
            )
        }

    var showBanner by remember { mutableStateOf(false) }
    var latestVersion by remember { mutableStateOf("") }
    var downloadUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val result = VersionChecker.checkForUpdate(context)
        if (result.hasUpdate) {
            val dismissedVersion = prefs.getString(KEY_UPDATE_DISMISSED_VERSION, null)
            if (dismissedVersion != result.latestVersion) {
                latestVersion = result.latestVersion
                downloadUrl = result.downloadUrl
                showBanner = true
            }
        }
    }

    if (!showBanner) return

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
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.banner_update_available),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            prefs.edit { putString(KEY_UPDATE_DISMISSED_VERSION, latestVersion) }
                            showBanner = false
                        }
                    },
                    modifier = Modifier.size(24.dp),
                ) {
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
                text = stringResource(R.string.banner_update_description, latestVersion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(
                    onClick = {
                        prefs.edit { putString(KEY_UPDATE_DISMISSED_VERSION, latestVersion) }
                        showBanner = false
                    },
                ) {
                    Text(stringResource(R.string.action_dismiss))
                }
                FilledTonalButton(
                    onClick = {
                        val intent =
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                downloadUrl.toUri(),
                            )
                        context.startActivity(intent)
                    },
                ) {
                    Text(stringResource(R.string.action_download_update))
                }
            }
        }
    }
}
