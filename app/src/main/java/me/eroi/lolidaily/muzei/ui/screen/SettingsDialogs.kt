package me.eroi.lolidaily.muzei.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.ui.screen.components.ThemeOption

internal const val CUSTOM_OPTION = "Custom"

// ── Refresh Time Dialog ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshTimeDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val state =
        rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true,
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_refresh_time_gmt)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    state.hour,
                    state.minute
                )
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// ── Language Picker Dialog ───────────────────────────────────────

@Composable
fun LanguagePickerDialog(
    currentTag: String,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit,
) {
    val languages =
        listOf(
            "" to stringResource(R.string.label_language_system),
            "en" to stringResource(R.string.label_language_en),
            "zh" to stringResource(R.string.label_language_zh),
            "ja" to stringResource(R.string.label_language_ja),
        )

    var selectedTag by remember { mutableStateOf(currentTag) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_language)) },
        text = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                languages.forEachIndexed { _, (tag, label) ->
                    ThemeOption(
                        label = label,
                        selected = selectedTag == tag,
                        onClick = { selectedTag = tag },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onLanguageSelected(selectedTag) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// ── API Server Picker Dialog ─────────────────────────────────────

@Composable
fun ApiServerPickerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs =
        remember {
            context.getSharedPreferences(
                LoliDailyArtWorker.PREFS_NAME,
                android.content.Context.MODE_PRIVATE,
            )
        }

    val savedUrl = remember { prefs.getString(LoliApiClient.KEY_DEBUG_API_BASE_URL, null) }

    val initialSelection =
        remember {
            if (savedUrl == null) {
                LoliApiClient.KNOWN_SERVERS.first()
            } else {
                val matched = LoliApiClient.KNOWN_SERVERS.indexOfFirst { it == savedUrl }
                if (matched >= 0) LoliApiClient.KNOWN_SERVERS[matched] else CUSTOM_OPTION
            }
        }

    var selected by remember { mutableStateOf(initialSelection) }
    val savedCustom =
        remember {
            prefs.getString(LoliApiClient.KEY_DEBUG_API_BASE_URL_CUSTOM, null) ?: ""
        }
    var customUrl by remember {
        mutableStateOf(
            if (initialSelection == CUSTOM_OPTION) savedUrl ?: "" else savedCustom
        )
    }

    fun persist(url: String) {
        prefs.edit { putString(LoliApiClient.KEY_DEBUG_API_BASE_URL, url) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_api_server)) },
        text = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LoliApiClient.KNOWN_SERVERS.forEach { server ->
                    ThemeOption(
                        label = server,
                        selected = selected == server,
                        onClick = {
                            selected = server
                            persist(server)
                        },
                    )
                }

                ThemeOption(
                    label = stringResource(R.string.label_custom),
                    selected = selected == CUSTOM_OPTION,
                    onClick = { selected = CUSTOM_OPTION },
                )

                Column {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = {
                            customUrl = it
                            prefs.edit {
                                putString(
                                    LoliApiClient.KEY_DEBUG_API_BASE_URL_CUSTOM,
                                    it
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("https://example.com") },
                        singleLine = true,
                        label = { Text(stringResource(R.string.label_server_url)) },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selected == CUSTOM_OPTION) persist(customUrl)
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// ── Bangumi API Server Picker Dialog ──────────────────────────────

@Composable
fun BangumiApiServerPickerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs =
        remember {
            context.getSharedPreferences(
                LoliDailyArtWorker.PREFS_NAME,
                android.content.Context.MODE_PRIVATE,
            )
        }

    val savedUrl = remember { prefs.getString(LoliApiClient.KEY_DEBUG_BANGUMI_BASE_URL, null) }

    val initialSelection =
        remember {
            if (savedUrl == null) {
                LoliApiClient.KNOWN_BANGUMI_SERVERS.first()
            } else {
                val matched = LoliApiClient.KNOWN_BANGUMI_SERVERS.indexOfFirst { it == savedUrl }
                if (matched >= 0) LoliApiClient.KNOWN_BANGUMI_SERVERS[matched] else CUSTOM_OPTION
            }
        }

    var selected by remember { mutableStateOf(initialSelection) }
    val savedCustom =
        remember {
            prefs.getString(LoliApiClient.KEY_DEBUG_BANGUMI_BASE_URL_CUSTOM, null) ?: ""
        }
    var customUrl by remember {
        mutableStateOf(
            if (initialSelection == CUSTOM_OPTION) savedUrl ?: "" else savedCustom
        )
    }

    fun persist(url: String) {
        prefs.edit { putString(LoliApiClient.KEY_DEBUG_BANGUMI_BASE_URL, url) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_bangumi_api_server)) },
        text = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LoliApiClient.KNOWN_BANGUMI_SERVERS.forEach { server ->
                    ThemeOption(
                        label = server,
                        selected = selected == server,
                        onClick = {
                            selected = server
                            persist(server)
                        },
                    )
                }

                ThemeOption(
                    label = stringResource(R.string.label_custom),
                    selected = selected == CUSTOM_OPTION,
                    onClick = { selected = CUSTOM_OPTION },
                )

                Column {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = {
                            customUrl = it
                            prefs.edit {
                                putString(
                                    LoliApiClient.KEY_DEBUG_BANGUMI_BASE_URL_CUSTOM,
                                    it
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("https://bgm.tv") },
                        singleLine = true,
                        label = { Text(stringResource(R.string.label_bangumi_server_url)) },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selected == CUSTOM_OPTION) persist(customUrl)
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// ── API Tag Picker Dialog ──────────────────────────────────────────

@Composable
fun ApiTagPickerDialog(
    currentTag: String,
    onDismiss: () -> Unit,
    onTagSelected: (String) -> Unit,
) {
    var selected by remember { mutableStateOf(currentTag) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_select_api_tag)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LoliApiClient.ALL_LC_TAGS.forEach { tag ->
                    ThemeOption(
                        label = tag,
                        selected = selected == tag,
                        onClick = { selected = tag },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onTagSelected(selected) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
