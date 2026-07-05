package me.eroi.lolidaily.muzei.ui.screen

import android.content.Intent
import androidx.activity.compose.BackHandler
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.content.edit
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import me.eroi.lolidaily.muzei.AcknowledgmentsActivity
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.BuildConfig
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.ui.screen.components.*
import me.eroi.lolidaily.muzei.util.VersionChecker
import android.widget.Toast
import androidx.compose.material.icons.filled.Info
import me.eroi.lolidaily.muzei.util.DebugMode
import kotlin.time.Duration.Companion.milliseconds

private const val GITHUB_URL = "https://github.com/Blacktea0/muzei-loli-daily"
private const val OFFICIAL_SITE_URL = "https://lolicommons.tsuki.ga/"

private val LoliSCFont = FontFamily(Font(R.font.lolisc_light))

private enum class UpdateCheckState {
    IDLE,
    CHECKING,
    RESULT,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toastHelper = remember(context) {
        object {
            var lastToast: Toast? = null
            fun show(text: String, duration: Int = Toast.LENGTH_SHORT) {
                lastToast?.cancel()
                lastToast = Toast.makeText(context, text, duration).apply { show() }
            }
        }
    }

    var updateState by remember { mutableStateOf(UpdateCheckState.IDLE) }
    var updateResult by remember { mutableStateOf<VersionChecker.UpdateCheckResult?>(null) }
    var isBlocked by remember { mutableStateOf(false) }

    BackHandler(enabled = isBlocked) {
        // Intercepts and blocks system back navigation
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_about)) },
                navigationIcon = {
                    IconButton(onClick = { if (!isBlocked) onBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_desc_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (updateState == UpdateCheckState.IDLE && !isBlocked) {
                                updateState = UpdateCheckState.CHECKING
                                scope.launch {
                                    val result = VersionChecker.checkForUpdate(context, forceRefresh = true)
                                    updateResult = result
                                    updateState = UpdateCheckState.RESULT
                                }
                            }
                        },
                        enabled = updateState == UpdateCheckState.IDLE && !isBlocked,
                    ) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = stringResource(R.string.content_desc_check_update),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Hero section ──
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(96.dp)
                                .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_background),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.requiredSize(144.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.about_hero_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = LoliSCFont,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StrikethroughText(
                        text = buildStyledHeroSubtitle(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = LoliSCFont,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        strikethroughColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.about_hero_tagline),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = LoliSCFont,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // ── App info ──
            item {
                SegmentedSettingsGroup {
                    GroupedSettingsRow(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.about_official_site),
                        subtitle = OFFICIAL_SITE_URL,
                        onClick = {
                            if (!isBlocked) {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, OFFICIAL_SITE_URL.toUri()),
                                )
                            }
                        },
                    )
                    GroupedSettingsRow(
                        icon = Icons.Default.Code,
                        title = stringResource(R.string.about_source_code),
                        subtitle = GITHUB_URL,
                        onClick = {
                            if (!isBlocked) {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, GITHUB_URL.toUri()),
                                )
                            }
                        },
                    )
                    GroupedSettingsRow(
                        icon = Icons.Default.Favorite,
                        title = stringResource(R.string.about_acknowledgments),
                        subtitle = stringResource(R.string.about_acknowledgments_subtitle),
                        onClick = {
                            if (!isBlocked) {
                                context.startActivity(
                                    Intent(context, AcknowledgmentsActivity::class.java),
                                )
                            }
                        },
                    )
                    var clickCount by remember { mutableIntStateOf(0) }
                    var activeClickCount by remember { mutableIntStateOf(0) }
                    var lastClickTime by remember { mutableLongStateOf(0L) }
                    var vibrationStartTime by remember { mutableLongStateOf(0L) }
                    var vibrationDuration by remember { mutableLongStateOf(0L) }
                    val vibrate = remember(context) {
                        val vibrator = context.getSystemService("vibrator") as Vibrator?
                        { duration: Long ->
                            vibrator?.vibrate(
                                VibrationEffect.createOneShot(
                                    duration,
                                    VibrationEffect.DEFAULT_AMPLITUDE,
                                ),
                            )
                        }
                    }
                    val activeDebugClickToast1 = stringResource(R.string.toast_debug_active_click_1)
                    val activeDebugClickToast2 = stringResource(R.string.toast_debug_active_click_2)
                    val activeDebugClickToast3 = stringResource(R.string.toast_debug_active_click_3)
                    val activeDebugClickToast4 = stringResource(R.string.toast_debug_active_click_4)
                    val activeDebugClickToast5 = stringResource(R.string.toast_debug_active_click_5)
                    val activeDebugClickToast6 = stringResource(R.string.toast_debug_active_click_6)
                    val debugClickToast1 = stringResource(R.string.toast_debug_click_1)
                    val debugClickToast2 = stringResource(R.string.toast_debug_click_2)
                    val debugClickToast3 = stringResource(R.string.toast_debug_click_3)
                    val debugClickToast4 = stringResource(R.string.toast_debug_click_4)
                    val debugClickToast5 = stringResource(R.string.toast_debug_click_5)
                    val debugClickToast6 = stringResource(R.string.toast_debug_click_6)
                    val debugClickToast7 = stringResource(R.string.toast_debug_click_7)
                    GroupedSettingsRow(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.title_version),
                        subtitle = BuildConfig.VERSION_NAME,
                        onClick = {
                            if (isBlocked) return@GroupedSettingsRow
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - vibrationStartTime < vibrationDuration) {
                                return@GroupedSettingsRow
                            }
                            if (currentTime - lastClickTime >= 721L) {
                                lastClickTime = currentTime
                                if (DebugMode.isEnabled) {
                                    activeClickCount++
                                    val duration = when (activeClickCount) {
                                        1 -> 100L
                                        2 -> 300L
                                        3 -> 600L
                                        4 -> 1000L
                                        5 -> 2000L
                                        6 -> 4000L
                                        else -> 50L
                                    }
                                    vibrationStartTime = currentTime
                                    vibrationDuration = duration
                                    vibrate(duration)
                                    when (activeClickCount) {
                                        1 -> toastHelper.show(activeDebugClickToast1)
                                        2 -> toastHelper.show(activeDebugClickToast2)
                                        3 -> toastHelper.show(activeDebugClickToast3)
                                        4 -> toastHelper.show(activeDebugClickToast4)
                                        5 -> toastHelper.show(activeDebugClickToast5, Toast.LENGTH_LONG)
                                        6 -> {
                                            toastHelper.show(activeDebugClickToast6, Toast.LENGTH_LONG)
                                            scope.launch {
                                                isBlocked = true
                                                delay(2000L.milliseconds)
                                                toastHelper.show(activeDebugClickToast6, Toast.LENGTH_LONG)
                                                delay(2000L.milliseconds)
                                                val prefs = context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
                                                prefs.edit { putBoolean("debug_mode_enabled_crashed", true) }
                                                (context as? android.app.Activity)?.finishAffinity()
                                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                    // Trigger a genuine, unexpected NullPointerException in the foreground
                                                    val obj: Any? = null
                                                    @Suppress("KotlinConstantConditions")
                                                    obj!!.hashCode()
                                                }, 100L)
                                            }
                                        }
                                    }
                                } else {
                                    clickCount++
                                    when (clickCount) {
                                        4 -> toastHelper.show(debugClickToast1)
                                        5 -> toastHelper.show(debugClickToast2)
                                        6 -> toastHelper.show(debugClickToast3)
                                        7 -> toastHelper.show(debugClickToast4)
                                        8 -> toastHelper.show(debugClickToast5)
                                        9 -> toastHelper.show(debugClickToast6)
                                        10 -> {
                                            DebugMode.isEnabled = true
                                            clickCount = 0
                                            activeClickCount = 0
                                            vibrationStartTime = currentTime
                                            vibrationDuration = 3000L
                                            vibrate(3000L)
                                            toastHelper.show(debugClickToast7, Toast.LENGTH_LONG)
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    // ── Update check dialog ──
    when (updateState) {
        UpdateCheckState.CHECKING -> {
            AlertDialog(
                onDismissRequest = {},
                icon = {
                    LoadingIndicator()
                },
                title = { Text(stringResource(R.string.msg_checking_update)) },
                text = null,
                confirmButton = {},
            )
        }
        UpdateCheckState.RESULT -> {
            val result = updateResult
            if (result != null) {
                if (result.hasUpdate) {
                    UpdateReleaseNotesDialog(
                        latestVersion = result.latestVersion,
                        downloadUrl = result.downloadUrl,
                        releaseNotes = result.releaseNotes,
                        onDismiss = { updateState = UpdateCheckState.IDLE },
                    )
                } else {
                    AlertDialog(
                        onDismissRequest = { updateState = UpdateCheckState.IDLE },
                        icon = {
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        title = { Text(stringResource(R.string.msg_up_to_date)) },
                        text = {
                            Text(stringResource(R.string.msg_up_to_date_desc, BuildConfig.VERSION_NAME))
                        },
                        confirmButton = {
                            TextButton(onClick = { updateState = UpdateCheckState.IDLE }) {
                                Text(stringResource(R.string.action_ok))
                            }
                        },
                    )
                }
            }
        }
        UpdateCheckState.IDLE -> {}
    }
}

@Composable
private fun buildStyledHeroSubtitle(): androidx.compose.ui.text.AnnotatedString {
    val fullText = stringResource(R.string.about_hero_subtitle)
    val nonprofit = stringResource(R.string.about_highlight_nonprofit)
    val appName = stringResource(R.string.about_hero_title)
    val primaryColor = MaterialTheme.colorScheme.primary

    return buildAnnotatedString {
        append(fullText)

        // Style "nonprofit" / "非盈利" with strikethrough (drawn by StrikethroughText)
        val nonprofitIndex = fullText.indexOf(nonprofit, ignoreCase = true)
        if (nonprofitIndex >= 0) {
            addStyle(
                style =
                    SpanStyle(
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    ),
                start = nonprofitIndex,
                end = nonprofitIndex + nonprofit.length,
            )
            // Add a tag to mark strikethrough range for custom drawing
            addStringAnnotation(
                tag = "STRIKETHROUGH",
                annotation = "",
                start = nonprofitIndex,
                end = nonprofitIndex + nonprofit.length,
            )
        }

        // Style "Loli Commons" / "萝莉共享" with bold + primary color
        val appNameIndex = fullText.indexOf(appName, ignoreCase = true)
        if (appNameIndex >= 0) {
            addStyle(
                style =
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                    ),
                start = appNameIndex,
                end = appNameIndex + appName.length,
            )
        }
    }
}


@Composable
private fun StrikethroughText(
    text: androidx.compose.ui.text.AnnotatedString,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified,
    strikethroughColor: Color = Color.Gray,
    strikethroughWidth: Float = 10f,
) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = text,
        modifier =
            modifier.drawBehind {
                textLayoutResult?.let { layoutResult ->
                    // Find strikethrough annotations
                    val annotations = text.getStringAnnotations("STRIKETHROUGH", 0, text.length)
                    annotations.forEach { annotation ->
                        val start = annotation.start
                        val end = annotation.end
                        val startLine = layoutResult.getLineForOffset(start)
                        val endLine = layoutResult.getLineForOffset(end)

                        for (line in startLine..endLine) {
                            val lineStart = if (line == startLine) start else layoutResult.getLineStart(line)
                            val lineEnd = if (line == endLine) end else layoutResult.getLineEnd(line, visibleEnd = true)

                            val startOffset = layoutResult.getBoundingBox(lineStart).left
                            val endOffset = layoutResult.getBoundingBox(lineEnd - 1).right
                            val lineTop = layoutResult.getLineTop(line)
                            val lineBottom = layoutResult.getLineBottom(line)
                            val y = (lineTop + lineBottom) / 2

                            drawLine(
                                color = strikethroughColor,
                                start = Offset(startOffset, y),
                                end = Offset(endOffset, y),
                                strokeWidth = strikethroughWidth,
                            )
                        }
                    }
                }
            },
        style = style,
        fontFamily = fontFamily,
        textAlign = textAlign,
        color = color,
        onTextLayout = { textLayoutResult = it },
    )
}
