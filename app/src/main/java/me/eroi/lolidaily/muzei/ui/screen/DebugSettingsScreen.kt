package me.eroi.lolidaily.muzei.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.eroi.lolidaily.muzei.LoliDailyArtWorker

/**
 * Debug settings screen for development testing. Each option lives in its own M3-style section
 * card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Debug Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── API ───────────────────────────────
            item { SectionTitle("API") }

            item { CacheSwitchCard() }

            // ── Refresh Schedule ─────────────────
            item { SectionTitle("Refresh Schedule") }

            item { RefreshTimeCard() }
        }
    }
}

// ── Section Title ───────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

// ── Skip API Cache Card ─────────────────────────────────────────

@Composable
private fun CacheSwitchCard() {
    val context = LocalContext.current
    val prefs = remember {
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
                    Text(text = "Skip API Cache", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Always fetch from API instead of using cached response",
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
                    text = "Status: ${if (skipCache) "Cache SKIPPED" else "Cache USED"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (skipCache) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
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
    val prefs = remember {
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
                    Text(text = "Daily Refresh Time", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Scheduled daily API fetch (GMT+8). Default is 07:30.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                FilledTonalButton(onClick = { showDialog = true }) { Text("Edit") }
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
        title = { Text("Refresh Time (GMT+8)") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
