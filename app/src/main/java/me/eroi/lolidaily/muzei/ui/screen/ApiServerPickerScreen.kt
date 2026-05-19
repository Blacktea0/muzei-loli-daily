package me.eroi.lolidaily.muzei.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Text
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
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.ui.screen.components.ThemeOption

private const val CUSTOM_OPTION = "Custom"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoliApiServerPickerScreen(onBack: () -> Unit) {
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
    var customUrl by remember { mutableStateOf(if (initialSelection == CUSTOM_OPTION) savedUrl ?: "" else savedCustom) }
    var activeUrl by remember { mutableStateOf(LoliApiClient.getApiBaseUrl(context)) }

    fun persist(url: String) {
        prefs.edit().putString(LoliApiClient.KEY_DEBUG_API_BASE_URL, url).apply()
        activeUrl = url
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.title_api_server)) },
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
            item {
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
                                text = stringResource(R.string.label_active_url, activeUrl),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BangumiApiServerPickerScreen(onBack: () -> Unit) {
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
    var customUrl by remember { mutableStateOf(if (initialSelection == CUSTOM_OPTION) savedUrl ?: "" else savedCustom) }
    var activeUrl by remember { mutableStateOf(LoliApiClient.getBangumiBaseUrl(context)) }

    fun persist(url: String) {
        prefs.edit().putString(LoliApiClient.KEY_DEBUG_BANGUMI_BASE_URL, url).apply()
        activeUrl = url
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.title_bangumi_api_server)) },
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
            item {
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
                                text = stringResource(R.string.label_active_url, activeUrl),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
