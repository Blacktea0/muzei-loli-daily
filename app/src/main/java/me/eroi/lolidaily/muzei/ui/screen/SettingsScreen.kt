package me.eroi.lolidaily.muzei.ui.screen

import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import me.eroi.lolidaily.muzei.model.ReactionCount
import java.io.File
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * MD3 settings screen for the Loli Daily Muzei plugin.
 *
 * Tab 1 — Gallery: Filled cards with frosted-glass tag chips and
 *         a consolidated bottom action bar.
 * Tab 2 — Preference: radio-group tag filter, account card,
 *         source-status card.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    selectedTags: Set<String>,
    onTagsChanged: (Set<String>) -> Unit,
    cachedArtwork: List<ArtworkPreview>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    isLoggedIn: Boolean = false,
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    onReactionClick: (token: String, emojiValue: Int) -> Unit = { _, _ -> },
    bgmDomain: String = "chii.in",
    onDomainChanged: (String) -> Unit = {},
    isSourceActivated: Boolean = false,
    isMuzeiInstalled: Boolean = false,
    onOpenMuzei: () -> Unit = {},
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val tabs = listOf(
        "Gallery" to Icons.Default.Image,
        "Preference" to Icons.Default.Settings,
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Loli Daily Settings") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            if (pagerState.currentPage == 0) {
                FloatingActionButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Now")
                }
            }
        },
        modifier = modifier,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, (title, icon) ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) },
                        icon = { Icon(icon, contentDescription = title) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                when (page) {
                    0 -> GalleryTab(
                        cachedArtwork = cachedArtwork,
                        isLoggedIn = isLoggedIn,
                        onLogin = onLogin,
                        onReactionClick = onReactionClick,
                        onRefresh = onRefresh,
                    )
                    1 -> PreferenceTab(
                        selectedTags = selectedTags,
                        onTagsChanged = onTagsChanged,
                        isLoggedIn = isLoggedIn,
                        onLogin = onLogin,
                        onLogout = onLogout,
                        bgmDomain = bgmDomain,
                        onDomainChanged = onDomainChanged,
                        isSourceActivated = isSourceActivated,
                        isMuzeiInstalled = isMuzeiInstalled,
                        onOpenMuzei = onOpenMuzei,
                    )
                }
            }
        }
    }
}

// ── Tab 2: Preference ──────────────────────────────────────────

@Composable
private fun PreferenceTab(
    selectedTags: Set<String>,
    onTagsChanged: (Set<String>) -> Unit,
    isLoggedIn: Boolean = false,
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    bgmDomain: String = "chii.in",
    onDomainChanged: (String) -> Unit = {},
    isSourceActivated: Boolean = false,
    isMuzeiInstalled: Boolean = false,
    onOpenMuzei: () -> Unit = {},
) {
    val context = LocalContext.current
    val showBanner = (!isMuzeiInstalled || !isSourceActivated)

    // Clear banner dismiss state when installation/activation status changes
    LaunchedEffect(isMuzeiInstalled, isSourceActivated) {
        val prefs = context.getSharedPreferences(
            LoliDailyArtWorker.PREFS_NAME, android.content.Context.MODE_PRIVATE
        )
        val stored = prefs.getString(KEY_BANNER_DISMISSED, null)
        if (stored != null && stored != "installed=$isMuzeiInstalled,activated=$isSourceActivated") {
            prefs.edit().remove(KEY_BANNER_DISMISSED).apply()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Setup banner ─────────────────────────
        if (showBanner) {
            item {
                SetupBanner(
                    isMuzeiInstalled = isMuzeiInstalled,
                    isSourceActivated = isSourceActivated,
                    onOpenMuzei = onOpenMuzei,
                )
            }
        }

        // ── Tag Filters ──────────────────────────
        item {
            SectionTitle("TAG FILTERS")
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    FilterOption(
                        label = "Show all tags (no filter)",
                        selected = selectedTags.isEmpty(),
                        onClick = { onTagsChanged(emptySet()) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    FilterOption(
                        label = "LC0",
                        selected = selectedTags.contains("LC0"),
                        onClick = { onTagsChanged(setOf("LC0")) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    FilterOption(
                        label = "LC ES",
                        selected = selectedTags.contains("LC ES"),
                        onClick = { onTagsChanged(setOf("LC ES")) },
                    )
                }
            }
        }

        // ── Account ─────────────────────────────
        item {
            SectionTitle("ACCOUNT")
        }

        item {
            AccountCard(
                isLoggedIn = isLoggedIn,
                bgmDomain = bgmDomain,
                onLogin = onLogin,
                onLogout = onLogout,
                onDomainChanged = onDomainChanged,
            )
        }

        // ── Source Status ───────────────────────
        item {
            SectionTitle("SOURCE STATUS")
        }

        item {
            SourceStatusCard(
                isSourceActivated = isSourceActivated,
                isMuzeiInstalled = isMuzeiInstalled,
                onClick = onOpenMuzei,
            )
        }
    }
}

// ── Setup Banner ────────────────────────────────────────────────

private const val KEY_BANNER_DISMISSED = "banner_dismissed_status"

@Composable
private fun SetupBanner(
    isMuzeiInstalled: Boolean,
    isSourceActivated: Boolean,
    onOpenMuzei: () -> Unit,
) {
    val context = LocalContext.current
    val currentStatus = "installed=$isMuzeiInstalled,activated=$isSourceActivated"
    val prefs = remember(context) {
        context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }

    var dismissed by remember { mutableStateOf(false) }

    // Sync dismissed state from prefs after status clears
    LaunchedEffect(isMuzeiInstalled, isSourceActivated) {
        val stored = prefs.getString(KEY_BANNER_DISMISSED, null)
        dismissed = (currentStatus == stored)
    }

    if (dismissed) return

    val dismissBanner = {
        prefs.edit().putString(KEY_BANNER_DISMISSED, currentStatus).apply()
        dismissed = true
    }

    val title = if (!isMuzeiInstalled) "Muzei is not installed"
        else "Source is not enabled"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = dismissBanner,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "You can browse artwork and manage tags without Muzei. To set images as your wallpaper, install Muzei and enable this source.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = dismissBanner) {
                    Text("Dismiss")
                }
                FilledTonalButton(onClick = onOpenMuzei) {
                    if (!isMuzeiInstalled) {
                        Text("Install Muzei")
                    } else {
                        Text("Open Muzei")
                    }
                }
            }
        }
    }
}

// ── Section Title ───────────────────────────────────────────────

/**
 * MD3 section heading: labelSmall, uppercase, primary colour.
 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

// ── Filter Option (radio row) ───────────────────────────────────

@Composable
private fun FilterOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

// ── Account Card ────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountCard(
    isLoggedIn: Boolean,
    bgmDomain: String,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onDomainChanged: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Avatar placeholder
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isLoggedIn) "Logged in to Bangumi"
                            else "Not logged in",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = if (isLoggedIn) "via $bgmDomain"
                            else "Login to react to images",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isLoggedIn) {
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Logout")
                }
            } else {
                var showDomainPicker by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { showDomainPicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Login")
                    }
                }

                if (showDomainPicker) {
                    DomainPickerDialog(
                        currentDomain = bgmDomain,
                        onDomainSelected = { domain ->
                            showDomainPicker = false
                            onDomainChanged(domain)
                            onLogin()
                        },
                        onDismiss = { showDomainPicker = false },
                    )
                }
            }
        }
    }
}

// ── Source Status Card ──────────────────────────────────────────

@Composable
private fun SourceStatusCard(
    isSourceActivated: Boolean,
    isMuzeiInstalled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    val icon: ImageVector
    val label: String
    val subLabel: String
    val tint: androidx.compose.ui.graphics.Color

    if (!isMuzeiInstalled) {
        icon = Icons.Default.Info
        label = "Muzei not installed"
        subLabel = "Get it on Play Store"
        tint = colors.onSurfaceVariant
    } else if (isSourceActivated) {
        icon = Icons.Default.Favorite
        label = "Enabled in Muzei"
        subLabel = "Tap to open Muzei"
        tint = colors.primary
    } else {
        icon = Icons.Default.FavoriteBorder
        label = "Not enabled"
        subLabel = "Select this source in Muzei"
        tint = colors.error
    }

    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = tint,
                )
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ── Pixel Emoji Rendering ──────────────────────────────────────

@Composable
private fun rememberPixelBitmap(resId: Int): ImageBitmap {
    val context = LocalContext.current
    return remember(resId) {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = BitmapFactory.decodeResource(context.resources, resId, opts)
        bitmap.asImageBitmap()
    }
}

@Composable
private fun PixelEmoji(resId: Int, modifier: Modifier = Modifier) {
    val imageBitmap = rememberPixelBitmap(resId)
    Canvas(modifier = modifier) {
        drawImage(
            image = imageBitmap,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.None,
        )
    }
}

// ── Reaction Row (Telegram-style pill chips) ──────────────────

@Composable
private fun ReactionRow(
    reactions: List<ReactionCount>,
    userEmoji: Int?,
    token: String,
    isLoggedIn: Boolean,
    onReactionClick: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val valid = reactions.mapNotNull { r ->
        LoliDailyArtWorker.emojiResId(r.emojiValue)?.let { r to it }
    }
    if (valid.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((reaction, resId) in valid) {
            val selected = reaction.emojiValue == userEmoji

            val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
            val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface

            Surface(
                onClick = { if (isLoggedIn) onReactionClick(token, reaction.emojiValue) },
                shape = RoundedCornerShape(50),
                color = bg,
                modifier = Modifier.height(26.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelEmoji(
                        resId = resId,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "${reaction.count}",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                    )
                }
            }
        }
    }
}

// ── Domain Picker Dialog ───────────────────────────────────────

@Composable
private fun DomainPickerDialog(
    currentDomain: String,
    onDomainSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val domains = listOf("chii.in", "bgm.tv", "bangumi.tv")
    var selectedDomain by remember { mutableStateOf(currentDomain) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Choose login site",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                domains.forEach { domain ->
                    val selected = domain == selectedDomain
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDomain = domain }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(domain, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    FilledTonalButton(onClick = { onDomainSelected(selectedDomain) }) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

// ── Tab 1: Gallery ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryTab(
    cachedArtwork: List<ArtworkPreview>,
    isLoggedIn: Boolean = false,
    onLogin: () -> Unit = {},
    onReactionClick: (token: String, emojiValue: Int) -> Unit = { _, _ -> },
    onRefresh: () -> Unit = {},
) {
    var fullscreenPreview by remember { mutableStateOf<ArtworkPreview?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            onRefresh()
            scope.launch {
                kotlinx.coroutines.delay(4000)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        if (cachedArtwork.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No cached images yet.\nActivate this source in Muzei to fetch artwork.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(cachedArtwork.take(4)) { preview ->
                    ArtworkCard(
                        preview = preview,
                        onImageClick = { fullscreenPreview = preview },
                        isLoggedIn = isLoggedIn,
                        onLogin = onLogin,
                        onReactionClick = onReactionClick,
                    )
                }
            }
        }
    }

    fullscreenPreview?.let { preview ->
        FullscreenImageDialog(
            preview = preview,
            onDismiss = { fullscreenPreview = null },
            isLoggedIn = isLoggedIn,
            onReactionClick = onReactionClick,
        )
    }
}

// ── Artwork Card ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ArtworkCard(
    preview: ArtworkPreview,
    onImageClick: () -> Unit,
    isLoggedIn: Boolean = false,
    onLogin: () -> Unit = {},
    onReactionClick: (token: String, emojiValue: Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current

    // Bottom sheet state for artwork detail
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Token for reactions
    val token = preview.filename.substringBeforeLast('.')

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        // ── Hero Image ─────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clickable(onClick = onImageClick)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(preview.uri).build(),
                contentDescription = preview.artistName.ifBlank { preview.filename },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // LC tag badge — frosted glass (top-start)
            if (preview.tags.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                ) {
                    Text(
                        text = preview.tags,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            // Date badge — frosted glass (top-end)
            if (preview.date.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = preview.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Reactions overlay — bottom-start of thumbnail
            if (preview.reactions.isNotEmpty()) {
                ReactionRow(
                    reactions = preview.reactions,
                    userEmoji = preview.userEmoji,
                    token = token,
                    isLoggedIn = isLoggedIn,
                    onReactionClick = onReactionClick,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                )
            }
        }

        // ── Content ────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Artist name
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = preview.artistName.ifBlank { "Unknown Artist" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Comment
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Comment,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 2.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (preview.comment.isNotBlank()) {
                    Text(
                        text = preview.comment,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = "No comment",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                        ),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Action Bar: right-aligned icon buttons ───────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Save / export button
                FilledTonalIconButton(
                    onClick = { exportArtwork(context, preview) },
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Export artwork")
                }

                // Info button → bottom sheet
                FilledTonalIconButton(onClick = { showBottomSheet = true }) {
                    Icon(Icons.Default.Info, contentDescription = "Artwork details")
                }

                // Reaction heart
                var showReactionPicker by remember { mutableStateOf(false) }
                val hasReacted = preview.userEmoji != null

                FilledTonalIconButton(
                    onClick = {
                        if (!isLoggedIn) {
                            Toast.makeText(context, "Login to Bangumi to react", Toast.LENGTH_SHORT).show()
                        } else if (hasReacted) {
                            onReactionClick(token, preview.userEmoji!!)
                        } else {
                            showReactionPicker = true
                        }
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        contentColor = if (hasReacted) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        if (hasReacted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "React",
                    )
                }

                if (showReactionPicker && isLoggedIn) {
                    ReactionPickerDialog(
                        onDismiss = { showReactionPicker = false },
                        onEmojiSelected = { value ->
                            onReactionClick(token, value)
                            showReactionPicker = false
                        },
                    )
                }
            }
        }
    }

    // Bottom sheet for artwork details
    if (showBottomSheet) {
        ArtworkDetailBottomSheet(
            preview = preview,
            sheetState = sheetState,
            onDismiss = { showBottomSheet = false },
        )
    }
}

// ── Reaction Picker Dialog ────────────────────────────────────

@Composable
private fun ReactionPickerDialog(
    onDismiss: () -> Unit,
    onEmojiSelected: (Int) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Reactions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                val emojis = LoliDailyArtWorker.run {
                    listOf(0, 104, 54, 140, 122, 90, 88, 80)
                }

                for (row in emojis.chunked(4)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        row.forEach { value ->
                            val resId = LoliDailyArtWorker.emojiResId(value) ?: return@forEach
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { onEmojiSelected(value) }
                                    .padding(8.dp),
                            ) {
                                PixelEmoji(
                                    resId = resId,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Tap an emoji to react",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Fullscreen Image Viewer ─────────────────────────────────────

@Composable
private fun FullscreenImageDialog(
    preview: ArtworkPreview,
    onDismiss: () -> Unit,
    isLoggedIn: Boolean = false,
    onReactionClick: (token: String, emojiValue: Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(preview.uri).build(),
                contentDescription = preview.filename,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        RoundedCornerShape(50),
                    ),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            val overlayText = if (preview.date.isNotBlank()) {
                "${preview.artistName}  ·  ${preview.date}"
            } else {
                preview.artistName
            }
            Text(
                text = overlayText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )

            // Reactions overlay (bottom-end)
            if (preview.reactions.isNotEmpty()) {
                ReactionRow(
                    reactions = preview.reactions,
                    userEmoji = preview.userEmoji,
                    token = preview.filename.substringBeforeLast('.'),
                    isLoggedIn = isLoggedIn,
                    onReactionClick = onReactionClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
            }
        }
    }
}

// ── Artwork Detail Bottom Sheet ─────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ArtworkDetailBottomSheet(
    preview: ArtworkPreview,
    sheetState: SheetState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            // Title
            Text(
                text = "Artwork Details",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(20.dp))

            // Date
            if (preview.date.isNotBlank()) {
                DetailRow(
                    icon = Icons.Default.CalendarToday,
                    label = "Date",
                    value = preview.date,
                )
                Spacer(Modifier.height(12.dp))
            }

            // Artist
            DetailRow(
                icon = Icons.Default.Palette,
                label = "Artist",
                value = preview.artistName.ifBlank { "Unknown" },
            )

            Spacer(Modifier.height(12.dp))

            // Tag
            if (preview.tags.isNotBlank()) {
                DetailRow(
                    icon = null,
                    label = "Classification",
                    content = {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    preview.tags,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
            }

            // Characters
            if (preview.characterNames.isNotEmpty()) {
                Text(
                    text = "Characters",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    preview.characterNames.forEach { name ->
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(name, style = MaterialTheme.typography.labelMedium)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Comment (full)
            Text(
                text = "Comment",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (preview.comment.isNotBlank()) {
                Text(
                    text = preview.comment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    text = "No comment available",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = FontStyle.Italic,
                    ),
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Filename
            Text(
                text = preview.filename,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (preview.sourceUrl.isNotBlank()) {
                    FilledTonalButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(preview.sourceUrl))
                            )
                        },
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Source")
                    }
                }
                if (preview.artistUrl.isNotBlank()) {
                    FilledTonalButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(preview.artistUrl))
                            )
                        },
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Artist")
                    }
                }
            }
        }
    }
}

// ── Detail Row ──────────────────────────────────────────────────

@Composable
private fun DetailRow(
    icon: ImageVector?,
    label: String,
    value: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        if (content != null) {
            content()
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = value ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ── Export Artwork ──────────────────────────────────────────────

/**
 * Copies the artwork file to the public Pictures/LoliDaily directory
 * via [MediaStore], compatible with API 24+.
 */
private fun exportArtwork(context: android.content.Context, preview: ArtworkPreview) {
    try {
        val resolver = context.contentResolver
        val mimeType = if (preview.filename.endsWith(".png", true)) "image/png"
        else if (preview.filename.endsWith(".gif", true)) "image/gif"
        else if (preview.filename.endsWith(".webp", true)) "image/webp"
        else "image/jpeg"

        val relativePath = Environment.DIRECTORY_PICTURES + "/LoliDaily"

        // Check for existing file on disk
        val destDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "LoliDaily")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "LoliDaily")
        }
        val destFile = File(destDir, preview.filename)
        if (destFile.exists()) {
            Toast.makeText(context, "Already exported", Toast.LENGTH_SHORT).show()
            return
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, preview.filename)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val outputUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: run {
                Toast.makeText(context, "Failed to create export file", Toast.LENGTH_SHORT).show()
                return
            }

        resolver.openInputStream(preview.uri)?.use { input ->
            resolver.openOutputStream(outputUri)?.use { output ->
                input.copyTo(output)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(outputUri, contentValues, null, null)
        }

        Toast.makeText(context, "Saved to Pictures/LoliDaily", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
