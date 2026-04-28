package me.eroi.lolidaily.muzei.ui.screen

import android.content.Intent
import android.content.ContentValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import me.eroi.lolidaily.muzei.model.ReactionCount
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Material 3 Compose settings screen for the Loli Daily Muzei plugin.
 *
 * Tab 1 — Gallery: M3 cards with artwork previews, metadata,
 *         expandable detail, and fullscreen viewer.
 * Tab 2 — Preference: tag selection with radio buttons.
 *
 * All state is owned externally — this composable receives values
 * and callbacks only.
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
    val tabs = listOf("Gallery", "Preference")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Loli Daily Settings") },
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
            TabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) },
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                text = "Tag Filters",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        item {
            FilterOption(
                label = "Show all tags (no filter)",
                selected = selectedTags.isEmpty(),
                onClick = { onTagsChanged(emptySet()) },
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            Text(
                text = "Specific Tags",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        item {
            FilterOption(
                label = "LC0",
                selected = selectedTags.contains("LC0"),
                onClick = { onTagsChanged(setOf("LC0")) },
            )
        }

        item {
            FilterOption(
                label = "LC ES",
                selected = selectedTags.contains("LC ES"),
                onClick = { onTagsChanged(setOf("LC ES")) },
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            Text(
                text = "Account",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        item {
            Text(
                text = "Login via: $bgmDomain",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        item {
            if (isLoggedIn) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Logged in to Bangumi",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = onLogout) {
                        Text("Logout")
                    }
                }
            } else {
                var showDomainPicker by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Login to react to images",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = { showDomainPicker = true }) {
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

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            Text(
                text = "Source Status",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        item {
            SourceStatusRow(
                isSourceActivated = isSourceActivated,
                isMuzeiInstalled = isMuzeiInstalled,
            ) {
                onOpenMuzei()
            }
        }
    }
}

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
            .padding(vertical = 12.dp, horizontal = 4.dp),
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

// ── Source Status Row ──────────────────────────────────────────

/**
 * Displays whether this plugin is active in Muzei.
 * - Activated: green check
 * - Not activated: warning icon, tap to open Muzei
 * - Muzei not installed: info + tap to open Play Store
 */
@Composable
private fun SourceStatusRow(
    isSourceActivated: Boolean,
    isMuzeiInstalled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    val icon: androidx.compose.ui.graphics.vector.ImageVector
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
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

// ── Pixel Emoji Rendering ──────────────────────────────────────

/**
 * Decodes a drawable resource at native resolution with density
 * scaling disabled, returning a crisp [ImageBitmap] suitable for
 * pixel-art nearest-neighbour upscaling.
 */
@Composable
private fun rememberPixelBitmap(resId: Int): ImageBitmap {
    val context = LocalContext.current
    return remember(resId) {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = BitmapFactory.decodeResource(context.resources, resId, opts)
        bitmap.asImageBitmap()
    }
}

/**
 * Renders a pixel-art emoji drawable with nearest-neighbour
 * filtering via [Canvas], preserving blocky edges regardless
 * of display size.
 */
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

/**
 * Renders reactions as a row of custom pill chips, mirroring
 * Telegram's reaction bar: fully rounded, borderless, compact,
 * with full control over internal spacing.
 */
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
                     else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant

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
                    val selected = domain == currentDomain
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDomainSelected(domain) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(domain, style = MaterialTheme.typography.bodyLarge)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtworkCard(
    preview: ArtworkPreview,
    onImageClick: () -> Unit,
    isLoggedIn: Boolean = false,
    onLogin: () -> Unit = {},
    onReactionClick: (token: String, emojiValue: Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var showDetailDialog by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
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

            // Date badge overlay (top-end)
            if (preview.date.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = preview.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // LC tag badge overlay (top-start)
            if (preview.tags.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = preview.tags,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            // Reactions overlay (bottom-start)
            if (preview.reactions.isNotEmpty()) {
                ReactionRow(
                    reactions = preview.reactions,
                    userEmoji = preview.userEmoji,
                    token = preview.filename.substringBeforeLast('.'),
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
                .padding(16.dp),
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

            Spacer(Modifier.height(8.dp))

            // Comment
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = "No comment",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = FontStyle.Italic,
                        ),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Info button → opens detail dialog
                FilledTonalIconButton(onClick = { showDetailDialog = true }) {
                    Icon(Icons.Default.Info, contentDescription = "Artwork details")
                }

                // Reaction heart button → opens reaction picker or prompts login
                val token = preview.filename.substringBeforeLast('.')
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

    // Detail dialog
    if (showDetailDialog) {
        ArtworkDetailDialog(
            preview = preview,
            onDismiss = { showDetailDialog = false },
        )
    }
}

// ── Reaction Picker Dialog ────────────────────────────────────

/**
 * Grid overlay of 8 Bangumi reaction emojis.
 * Selecting one dismisses the dialog and fires [onEmojiSelected].
 */
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

// ── Artwork Detail Dialog ──────────────────────────────────────

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

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, preview.filename)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LoliDaily")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtworkDetailDialog(
    preview: ArtworkPreview,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                // Title
                Text(
                    text = "Artwork Details",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(16.dp))

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
                    FilledTonalIconButton(onClick = { exportArtwork(context, preview) }) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "Export artwork",
                        )
                    }
                    if (preview.sourceUrl.isNotBlank()) {
                        FilledTonalIconButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(preview.sourceUrl))
                                )
                            },
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = "View source",
                            )
                        }
                    }
                    if (preview.artistUrl.isNotBlank()) {
                        FilledTonalIconButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(preview.artistUrl))
                                )
                            },
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "View artist",
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FilledTonalButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
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
