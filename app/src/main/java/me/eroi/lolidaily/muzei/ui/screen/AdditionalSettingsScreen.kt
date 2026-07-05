package me.eroi.lolidaily.muzei.ui.screen
import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.PixivLoginActivity
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.worker.WorkScheduler
import me.eroi.lolidaily.muzei.api.SessionManager
import me.eroi.lolidaily.muzei.util.DebugMode

const val KEY_HIDE_RECENTS_CONTENT = "hide_recents_content"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdditionalSettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_debug_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_desc_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SettingsSectionLabel(stringResource(R.string.section_language)) }
            item {
                SettingsGroup {
                    LanguageCard()
                    PreferChineseRoleCard()
                }
            }
            item { SettingsSectionLabel(stringResource(R.string.section_debug_refresh)) }
            item {
                SettingsGroup {
                    RefreshTimeCard()
                }
            }
            item { SettingsSectionLabel(stringResource(R.string.section_third_party_accounts)) }
            item {
                SettingsGroup {
                    PixivAccountCard()
                }
            }
            item { SettingsSectionLabel(stringResource(R.string.section_privacy)) }
            item {
                SettingsGroup {
                    HideRecentsCard()
                }
            }
            if (DebugMode.isEnabled) {
                item { SettingsSectionLabel(stringResource(R.string.section_debug_api)) }
                item { ApiCombinedCard() }
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    endIcon: ImageVector = Icons.Filled.ChevronRight,
) {
    val content: @Composable () -> Unit = {
        ListItem(
            modifier = Modifier.defaultMinSize(minHeight = 76.dp),
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = subtitle?.takeIf { it.isNotEmpty() }?.let { text ->
                {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            leadingContent =
                if (leadingContent != null) {
                    { leadingContent() }
                } else {
                    {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                },
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    trailing?.invoke()
                    if (onClick != null) {
                        Icon(
                            imageVector = endIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            content()
        }
    } else {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRowWithSwitchAndArrow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = onClick != null) { onClick?.invoke() },
    ) {
        ListItem(
            modifier = Modifier.defaultMinSize(minHeight = 76.dp),
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = subtitle?.takeIf { it.isNotEmpty() }?.let { text ->
                {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            leadingContent = {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onClick != null) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        VerticalDivider(
                            modifier =
                                Modifier
                                    .height(32.dp)
                                    .padding(horizontal = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    Switch(
                        checked = checked,
                        onCheckedChange = onCheckedChange,
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

// ── API Combined Card ────────────────────────────────────────────

@Composable
private fun ApiCombinedCard() {
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

    val apiUrl = remember(refreshKey) { LoliApiClient.getApiBaseUrl(context) }
    val bangumiUrl = remember(refreshKey) { LoliApiClient.getBangumiBaseUrl(context) }
    var skipCache by remember { mutableStateOf(prefs.getBoolean("debug_skip_cache", false)) }

    var overrideApiTagEnabled by remember {
        mutableStateOf(prefs.getBoolean(LoliApiClient.KEY_DEBUG_OVERRIDE_API_TAG_ENABLED, false))
    }
    var overrideApiTag by remember {
        mutableStateOf(
            prefs.getString(LoliApiClient.KEY_DEBUG_OVERRIDE_API_TAG, null)
                ?: LoliApiClient.DEFAULT_BADGE,
        )
    }

    var overrideTopicIdEnabled by remember {
        mutableStateOf(prefs.getBoolean(LoliApiClient.KEY_DEBUG_OVERRIDE_TOPIC_ID_ENABLED, false))
    }
    var overrideTopicId by remember {
        mutableStateOf(
            prefs.getString(LoliApiClient.KEY_DEBUG_OVERRIDE_TOPIC_ID, "465120") ?: "465120"
        )
    }

    var showApiDialog by remember { mutableStateOf(false) }
    var showBangumiDialog by remember { mutableStateOf(false) }
    var showApiTagDialog by remember { mutableStateOf(false) }
    var showApiTopicIdDialog by remember { mutableStateOf(false) }

    SettingsGroup {
        SettingsRow(
            icon = Icons.Default.Dns,
            title = stringResource(R.string.title_api_server),
            subtitle = apiUrl,
            onClick = { showApiDialog = true },
        )
        SettingsRow(
            icon = Icons.Default.Forum,
            title = stringResource(R.string.title_bangumi_api_server),
            subtitle = bangumiUrl,
            onClick = { showBangumiDialog = true },
        )
        SettingsRow(
            icon = Icons.Default.CloudOff,
            title = stringResource(R.string.title_skip_cache),
            subtitle = stringResource(R.string.desc_skip_cache),
            trailing = {
                Switch(
                    checked = skipCache,
                    onCheckedChange = { checked ->
                        skipCache = checked
                        prefs.edit { putBoolean("debug_skip_cache", checked) }
                    },
                )
            },
        )
        SettingsRowWithSwitchAndArrow(
            icon = Icons.Default.Tag,
            title = stringResource(R.string.title_override_api_tag),
            subtitle = overrideApiTag,
            checked = overrideApiTagEnabled,
            onCheckedChange = { checked ->
                overrideApiTagEnabled = checked
                prefs.edit { putBoolean(LoliApiClient.KEY_DEBUG_OVERRIDE_API_TAG_ENABLED, checked) }
            },
            onClick = { showApiTagDialog = true },
        )
        SettingsRowWithSwitchAndArrow(
            icon = Icons.Default.Forum,
            title = stringResource(R.string.title_override_api_topic_id),
            subtitle = overrideTopicId,
            checked = overrideTopicIdEnabled,
            onCheckedChange = { checked ->
                overrideTopicIdEnabled = checked
                prefs.edit { putBoolean(LoliApiClient.KEY_DEBUG_OVERRIDE_TOPIC_ID_ENABLED, checked) }
            },
            onClick = { showApiTopicIdDialog = true },
        )
        SettingsRow(
            icon = Icons.Default.VisibilityOff,
            title = stringResource(R.string.title_disable_debug_mode),
            subtitle = stringResource(R.string.desc_disable_debug_mode),
            onClick = {
                DebugMode.isEnabled = false
            },
        )
    }

    if (showApiDialog) {
        ApiServerPickerDialog(onDismiss = {
            showApiDialog = false
            refreshKey++
        })
    }
    if (showBangumiDialog) {
        BangumiApiServerPickerDialog(onDismiss = {
            showBangumiDialog = false
            refreshKey++
        })
    }
    if (showApiTagDialog) {
        ApiTagPickerDialog(
            currentTag = overrideApiTag,
            onDismiss = { showApiTagDialog = false },
            onTagSelected = { tag ->
                overrideApiTag = tag
                prefs.edit { putString(LoliApiClient.KEY_DEBUG_OVERRIDE_API_TAG, tag) }
                showApiTagDialog = false
                refreshKey++
            },
        )
    }
    if (showApiTopicIdDialog) {
        ApiTopicIdPickerDialog(
            currentTopicId = overrideTopicId,
            onDismiss = { showApiTopicIdDialog = false },
            onTopicIdSelected = { topicId ->
                overrideTopicId = topicId
                prefs.edit { putString(LoliApiClient.KEY_DEBUG_OVERRIDE_TOPIC_ID, topicId) }
                showApiTopicIdDialog = false
                refreshKey++
            },
        )
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
        mutableIntStateOf(prefs.getInt(LoliDailyArtWorker.KEY_DEBUG_REFRESH_HOUR, 7))
    }
    var minute by remember {
        mutableIntStateOf(prefs.getInt(LoliDailyArtWorker.KEY_DEBUG_REFRESH_MINUTE, 21))
    }
    var showDialog by remember { mutableStateOf(false) }

    SettingsRow(
        icon = Icons.Default.Schedule,
        title = stringResource(R.string.title_refresh_time),
        subtitle = "%02d:%02d GMT+8".format(hour, minute),
        onClick = { showDialog = true },
    )

    if (showDialog) {
        RefreshTimeDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showDialog = false },
            onConfirm = { h, m ->
                val oldDayChangeDate =
                    LoliDailyArtWorker.computeDayChangeDate(context, System.currentTimeMillis())
                hour = h
                minute = m
                prefs
                    .edit {
                        putInt(LoliDailyArtWorker.KEY_DEBUG_REFRESH_HOUR, h)
                        putInt(LoliDailyArtWorker.KEY_DEBUG_REFRESH_MINUTE, m)
                    }
                val newDayChangeDate =
                    LoliDailyArtWorker.computeDayChangeDate(context, System.currentTimeMillis())
                if (oldDayChangeDate != newDayChangeDate) {
                    Log.d(
                        "DebugSettings",
                        "Refresh time changed: day-change date $oldDayChangeDate -> $newDayChangeDate, triggering force refresh",
                    )
                    WorkScheduler.enqueueLoad(context, forceRefresh = true)
                } else {
                    Log.d(
                        "DebugSettings",
                        "Refresh time changed: day-change date unchanged ($newDayChangeDate), no force refresh needed"
                    )
                }
                WorkScheduler.resetDailyRefreshState(context)
                showDialog = false
            },
        )
    }
}

// ── Hide Recents Card ─────────────────────────────────────────

@Composable
private fun HideRecentsCard() {
    val context = LocalContext.current
    val prefs =
        remember {
            context.getSharedPreferences(
                LoliDailyArtWorker.PREFS_NAME,
                android.content.Context.MODE_PRIVATE,
            )
        }

    var hideRecents by remember {
        mutableStateOf(prefs.getBoolean(KEY_HIDE_RECENTS_CONTENT, false))
    }

    SettingsRow(
        icon = Icons.Default.VisibilityOff,
        title = stringResource(R.string.title_hide_recents),
        subtitle = null,
        trailing = {
            Switch(
                checked = hideRecents,
                onCheckedChange = { checked ->
                    hideRecents = checked
                    prefs.edit { putBoolean(KEY_HIDE_RECENTS_CONTENT, checked) }
                },
            )
        },
    )
}

// ── Language Card ────────────────────────────────────────────────

@Composable
private fun LanguageCard() {
    var showDialog by remember { mutableStateOf(false) }

    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val currentTag = if (currentLocales.isEmpty) "" else currentLocales[0]?.toLanguageTag() ?: ""
    val currentLabel =
        when {
            currentTag.startsWith("zh-Hans") ->
                stringResource(R.string.label_language_zh_hans)
            currentTag.startsWith("zh-Hant") ->
                stringResource(R.string.label_language_zh_hant)
            currentTag == "ja" -> stringResource(R.string.label_language_ja)
            currentTag == "en" -> stringResource(R.string.label_language_en)
            else -> stringResource(R.string.label_language_system)
        }

    SettingsRow(
        icon = Icons.Default.Language,
        title = stringResource(R.string.title_language),
        subtitle = currentLabel,
        onClick = { showDialog = true },
    )

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
        )
    }
}

@Composable
private fun PreferChineseRoleCard() {
    val context = LocalContext.current
    var preferChinese by remember {
        mutableStateOf(SessionManager.loadPreferChineseRole(context))
    }

    SettingsRow(
        icon = Icons.Default.Translate,
        title = stringResource(R.string.title_prefer_chinese_role),
        subtitle = stringResource(R.string.desc_prefer_chinese_role),
        trailing = {
            Switch(
                checked = preferChinese,
                onCheckedChange = { checked ->
                    preferChinese = checked
                    SessionManager.savePreferChineseRole(context, checked)
                },
            )
        },
    )
}
// ── Pixiv Account Card ────────────────────────────────────────
@Composable
private fun PixivAccountCard() {
    val context = LocalContext.current
    var pixivSessionId by remember {
        mutableStateOf(SessionManager.loadPixivSessionId(context))
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pixivSessionId = SessionManager.loadPixivSessionId(context)
        }
    }
    val isLoggedIn = pixivSessionId != null
    val subtitle = if (isLoggedIn) {
        stringResource(R.string.status_pixiv_logged_in)
    } else {
        stringResource(R.string.label_pixiv_login_hint)
    }
    SettingsRow(
        icon = Icons.Default.Language,
        title = stringResource(R.string.title_pixiv_account),
        subtitle = subtitle,
        trailing = {
            if (isLoggedIn) {
                Text(
                    text = stringResource(R.string.action_logout),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            SessionManager.clearPixivSession(context)
                            PixivLoginActivity.clearPixivCookies()
                            pixivSessionId = null
                            @SuppressLint("LocalContextGetResourceValueCall")
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.msg_pixiv_logged_out),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        },
        onClick = if (isLoggedIn) null else {
            {
                launcher.launch(Intent(context, PixivLoginActivity::class.java))
            }
        },
    )
}

