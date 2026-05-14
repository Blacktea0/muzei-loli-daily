package me.eroi.lolidaily.muzei.ui.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.ui.screen.components.ThemeOption
import me.eroi.lolidaily.muzei.util.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.title_debug_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_desc_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Language ──────────────────────────
            item { SectionTitle(stringResource(R.string.section_language)) }
            item { LanguageCard() }

            // ── API ───────────────────────────────
            item { SectionTitle(stringResource(R.string.section_debug_api)) }
            item { ApiServerCard() }
            item { BangumiApiServerCard() }
            item { CacheSwitchCard() }

            // ── Refresh Schedule ─────────────────
            item { SectionTitle(stringResource(R.string.section_debug_refresh)) }
            item { RefreshTimeCard() }
        }
    }
}

// ── API Server Card ──────────────────────────────────────────────

private const val CUSTOM_OPTION = "Custom"

@Composable
private fun ApiServerCard() {
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
            val matched = LoliApiClient.KNOWN_SERVERS.indexOfFirst { it == savedUrl }
            if (matched >= 0) LoliApiClient.KNOWN_SERVERS[matched] else CUSTOM_OPTION
        }

    var selected by remember { mutableStateOf(initialSelection) }
    val savedCustom =
        remember {
            prefs.getString(LoliApiClient.KEY_DEBUG_API_BASE_URL_CUSTOM, null) ?: ""
        }
    var customUrl by remember { mutableStateOf(if (initialSelection == CUSTOM_OPTION) savedUrl ?: "" else savedCustom) }

    fun persist(url: String) {
        prefs.edit().putString(LoliApiClient.KEY_DEBUG_API_BASE_URL, url).apply()
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            LoliApiClient.KNOWN_SERVERS.forEach { server ->
                ThemeOption(
                    label = server,
                    selected = selected == server,
                    onClick = {
                        selected = server
                        persist(server)
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }

            ThemeOption(
                label = stringResource(R.string.label_custom),
                selected = selected == CUSTOM_OPTION,
                onClick = {
                    selected = CUSTOM_OPTION
                    if (customUrl.isNotBlank()) persist(customUrl)
                },
            )

            AnimatedVisibility(visible = selected == CUSTOM_OPTION) {
                Column {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = {
                            customUrl = it
                            prefs.edit().putString(LoliApiClient.KEY_DEBUG_API_BASE_URL_CUSTOM, it).apply()
                            if (selected == CUSTOM_OPTION) persist(it)
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("https://example.com") },
                        singleLine = true,
                        label = { Text(stringResource(R.string.label_server_url)) },
                    )

                    if (customUrl.isNotBlank()) {
                        FilledTonalButton(
                            onClick = { persist(customUrl) },
                            modifier =
                                Modifier.align(Alignment.End).padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Text(stringResource(R.string.action_apply))
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 12.dp)) {
                Text(
                    text = stringResource(R.string.label_active_url, LoliApiClient.getApiBaseUrl(context)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Bangumi API Server Card ──────────────────────────────────────

@Composable
private fun BangumiApiServerCard() {
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
            val matched = LoliApiClient.KNOWN_BANGUMI_SERVERS.indexOfFirst { it == savedUrl }
            if (matched >= 0) LoliApiClient.KNOWN_BANGUMI_SERVERS[matched] else CUSTOM_OPTION
        }

    var selected by remember { mutableStateOf(initialSelection) }
    val savedCustom =
        remember {
            prefs.getString(LoliApiClient.KEY_DEBUG_BANGUMI_BASE_URL_CUSTOM, null) ?: ""
        }
    var customUrl by remember { mutableStateOf(if (initialSelection == CUSTOM_OPTION) savedUrl ?: "" else savedCustom) }

    fun persist(url: String) {
        prefs.edit().putString(LoliApiClient.KEY_DEBUG_BANGUMI_BASE_URL, url).apply()
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            LoliApiClient.KNOWN_BANGUMI_SERVERS.forEach { server ->
                ThemeOption(
                    label = server,
                    selected = selected == server,
                    onClick = {
                        selected = server
                        persist(server)
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }

            ThemeOption(
                label = stringResource(R.string.label_custom),
                selected = selected == CUSTOM_OPTION,
                onClick = {
                    selected = CUSTOM_OPTION
                    if (customUrl.isNotBlank()) persist(customUrl)
                },
            )

            AnimatedVisibility(visible = selected == CUSTOM_OPTION) {
                Column {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = {
                            customUrl = it
                            prefs.edit().putString(LoliApiClient.KEY_DEBUG_BANGUMI_BASE_URL_CUSTOM, it).apply()
                            if (selected == CUSTOM_OPTION) persist(it)
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("https://bgm.tv") },
                        singleLine = true,
                        label = { Text(stringResource(R.string.label_bangumi_server_url)) },
                    )

                    if (customUrl.isNotBlank()) {
                        FilledTonalButton(
                            onClick = { persist(customUrl) },
                            modifier =
                                Modifier.align(Alignment.End).padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Text(stringResource(R.string.action_apply))
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 12.dp)) {
                Text(
                    text = stringResource(R.string.label_active_url, LoliApiClient.getBangumiBaseUrl(context)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Skip API Cache Card ─────────────────────────────────────────

@Composable
private fun CacheSwitchCard() {
    val context = LocalContext.current
    val prefs =
        remember {
            context.getSharedPreferences(
                LoliDailyArtWorker.PREFS_NAME,
                android.content.Context.MODE_PRIVATE,
            )
        }

    var skipCache by remember { mutableStateOf(prefs.getBoolean("debug_skip_cache", false)) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.title_skip_cache), style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.desc_skip_cache),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = skipCache,
                    onCheckedChange = { checked ->
                        skipCache = checked
                        prefs.edit().putBoolean("debug_skip_cache", checked).apply()
                    },
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 12.dp)) {
                Text(
                    text = if (skipCache) stringResource(R.string.status_cache_skipped) else stringResource(R.string.status_cache_used),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (skipCache) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Refresh Time Card ────────────────────────────────────────────

@Composable
private fun RefreshTimeCard() {
    val context = LocalContext.current
    val prefs =
        remember {
            context.getSharedPreferences(
                LoliDailyArtWorker.PREFS_NAME,
                android.content.Context.MODE_PRIVATE,
            )
        }

    var hour by remember {
        mutableStateOf(prefs.getInt(LoliDailyArtWorker.KEY_DEBUG_REFRESH_HOUR, 7))
    }
    var minute by remember {
        mutableStateOf(prefs.getInt(LoliDailyArtWorker.KEY_DEBUG_REFRESH_MINUTE, 30))
    }
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.title_refresh_time), style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.desc_refresh_time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                FilledTonalButton(onClick = { showDialog = true }) { Text(stringResource(R.string.action_edit)) }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 12.dp)) {
                Text(
                    text = "%02d:%02d GMT+8".format(hour, minute),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showDialog) {
        RefreshTimeDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showDialog = false },
            onConfirm = { h, m ->
                hour = h
                minute = m
                prefs
                    .edit()
                    .putInt(LoliDailyArtWorker.KEY_DEBUG_REFRESH_HOUR, h)
                    .putInt(LoliDailyArtWorker.KEY_DEBUG_REFRESH_MINUTE, m)
                    .apply()
                LoliDailyArtWorker.resetDailyRefreshState(context)
                showDialog = false
            },
        )
    }
}

// ── Refresh Time Dialog ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshTimeDialog(
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
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// ── Language Card ────────────────────────────────────────────────

@Composable
private fun LanguageCard() {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val currentTag = if (currentLocales.isEmpty) "" else currentLocales[0]?.language ?: ""
    val currentLabel =
        when (currentTag) {
            "zh" -> stringResource(R.string.label_language_zh)
            "ja" -> stringResource(R.string.label_language_ja)
            "en" -> stringResource(R.string.label_language_en)
            else -> stringResource(R.string.label_language_system)
        }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.title_language), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.desc_language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            FilledTonalButton(onClick = { showDialog = true }) {
                Text(currentLabel)
            }
        }
    }

    if (showDialog) {
        LanguagePickerDialog(
            currentTag = currentTag,
            onDismiss = { showDialog = false },
            onLanguageSelected = { tag ->
                val locales =
                    if (tag.isEmpty()) {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(tag)
                    }
                AppCompatDelegate.setApplicationLocales(locales)
                showDialog = false
            },
            onOpenSystemSettings = {
                showDialog = false
                val intent =
                    Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                }
            },
        )
    }
}

@Composable
private fun LanguagePickerDialog(
    currentTag: String,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    val languages =
        listOf(
            "" to stringResource(R.string.label_language_system),
            "en" to stringResource(R.string.label_language_en),
            "zh" to stringResource(R.string.label_language_zh),
            "ja" to stringResource(R.string.label_language_ja),
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_language)) },
        text = {
            Column {
                languages.forEach { (tag, label) ->
                    ThemeOption(
                        label = label,
                        selected = currentTag == tag,
                        onClick = { onLanguageSelected(tag) },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onOpenSystemSettings) {
                    Text("System language settings")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
