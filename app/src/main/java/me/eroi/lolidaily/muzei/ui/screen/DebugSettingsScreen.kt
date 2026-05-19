package me.eroi.lolidaily.muzei.ui.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.eroi.lolidaily.muzei.ApiServerPickerActivity
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
            item { ApiServerSummaryCard() }
            item { BangumiApiServerSummaryCard() }
            item { CacheSwitchCard() }

            // ── Refresh Schedule ─────────────────
            item { SectionTitle(stringResource(R.string.section_debug_refresh)) }
            item { RefreshTimeCard() }
        }
    }
}

// ── API Server Summary Cards ────────────────────────────────────

@Composable
private fun ApiServerSummaryCard() {
    val context = LocalContext.current
    val prefs =
        remember {
            context.getSharedPreferences(
                LoliDailyArtWorker.PREFS_NAME,
                android.content.Context.MODE_PRIVATE,
            )
        }

    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) refreshKey++
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activeUrl = remember(refreshKey) { LoliApiClient.getApiBaseUrl(context) }

    ServerSummaryCard(
        title = stringResource(R.string.title_api_server),
        subtitle = stringResource(R.string.desc_api_server, LoliApiClient.DEFAULT_API_BASE_URL),
        activeUrl = activeUrl,
        icon = Icons.Default.Public,
        onClick = {
            context.startActivity(Intent(context, ApiServerPickerActivity::class.java))
        },
    )
}

@Composable
private fun BangumiApiServerSummaryCard() {
    val context = LocalContext.current
    val prefs =
        remember {
            context.getSharedPreferences(
                LoliDailyArtWorker.PREFS_NAME,
                android.content.Context.MODE_PRIVATE,
            )
        }

    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) refreshKey++
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activeUrl = remember(refreshKey) { LoliApiClient.getBangumiBaseUrl(context) }

    ServerSummaryCard(
        title = stringResource(R.string.title_bangumi_api_server),
        subtitle = stringResource(R.string.desc_bangumi_api_server, LoliApiClient.DEFAULT_BANGUMI_BASE_URL),
        activeUrl = activeUrl,
        icon = Icons.Default.Public,
        onClick = {
            val intent =
                Intent(context, ApiServerPickerActivity::class.java).apply {
                    putExtra(ApiServerPickerActivity.EXTRA_IS_BANGUMI, true)
                }
            context.startActivity(intent)
        },
    )
}

@Composable
private fun ServerSummaryCard(
    title: String,
    subtitle: String,
    activeUrl: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.label_active_url, activeUrl),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
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
        mutableStateOf(prefs.getInt(LoliDailyArtWorker.KEY_DEBUG_REFRESH_MINUTE, 21))
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
