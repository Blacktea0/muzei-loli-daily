package me.eroi.lolidaily.muzei.ui.screen.gallery

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.api.ReactionService
import me.eroi.lolidaily.muzei.api.SessionManager
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import me.eroi.lolidaily.muzei.model.BangumiReply
import me.eroi.lolidaily.muzei.model.BangumiSubReply
import me.eroi.lolidaily.muzei.model.ReactionCount
import me.eroi.lolidaily.muzei.ui.screen.components.*
import me.eroi.lolidaily.muzei.util.exportArtwork
import me.eroi.lolidaily.muzei.worker.EmojiMap
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun TodayGallery(
    todayArtwork: List<ArtworkPreview>,
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    onFullscreenImage: (ArtworkPreview) -> Unit,
    onReactionClick: (String, Int) -> Unit,
    onRefresh: () -> Unit,
    onBookmarkToggle: (token: String, fileName: String, bookmarked: Boolean) -> Unit = { _, _, _ -> },
    refreshProgress: Float? = null,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},
    windowSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
) {
    val colorScheme = MaterialTheme.colorScheme
    val currentDate =
        remember {
            LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
        }
    val tags = remember(todayArtwork) { todayArtwork.map { it.tags }.distinct() }
    val showTabs = tags.size > 1
    val pagerState =
        rememberPagerState(
            initialPage = initialPage.coerceIn(0, (tags.size - 1).coerceAtLeast(0)),
            pageCount = { tags.size },
        )
    val currentTag = tags.getOrNull(pagerState.currentPage)

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }
    val scope = rememberCoroutineScope()
    val isRefreshing = refreshProgress != null
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Top App Bar ──
        val currentPreview = todayArtwork.firstOrNull { it.tags == currentTag }

        TopAppBar(
            title = {
                Column {
                    Text(
                        text = currentDate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    currentTag?.let { tag ->
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            actions = {
                if (currentPreview != null) {
                    val token = currentPreview.filename.substringBeforeLast('.')
                    val hasReacted = currentPreview.userEmoji != null

                    // Like button
                    IconButton(
                        onClick = {
                            val emoji = currentPreview.userEmoji
                            if (!isLoggedIn) {
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.msg_login_to_react),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            } else if (emoji != null) {
                                onReactionClick(token, emoji)
                            } else {
                                // Show reaction picker
                            }
                        },
                    ) {
                        Icon(
                            if (hasReacted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(R.string.content_desc_react),
                            tint =
                                if (hasReacted) {
                                    colorScheme.primary
                                } else {
                                    colorScheme.onSurfaceVariant
                                },
                        )
                    }

                    // Bookmark button
                    IconButton(
                        onClick = {
                            val newState = !currentPreview.isBookmarked
                            onBookmarkToggle(token, currentPreview.filename, newState)
                        },
                    ) {
                        Icon(
                            if (currentPreview.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = stringResource(R.string.content_desc_bookmark),
                            tint =
                                if (currentPreview.isBookmarked) {
                                    colorScheme.primary
                                } else {
                                    colorScheme.onSurfaceVariant
                                },
                        )
                    }

                    // Export button
                    IconButton(
                        onClick = { exportArtwork(context, currentPreview) },
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = stringResource(R.string.content_desc_export_artwork),
                        )
                    }
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface,
                ),
            modifier =
                Modifier.drawBehind {
                    drawLine(
                        color = colorScheme.outlineVariant,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
        )

        // ── Tag Tabs ──
        if (showTabs) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier =
                    Modifier.drawBehind {
                        drawLine(
                            color = colorScheme.outlineVariant,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                    },
            ) {
                tags.forEachIndexed { index, tag ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(tag) },
                    )
                }
            }
        }

        // ── Refresh Progress ──
        if (tags.isNotEmpty()) {
            RefreshProgressBar(refreshProgress)
        }

        // ── Content ──
        if (tags.isEmpty()) {
            PullToRefreshBox(
                isRefreshing = false,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f).fillMaxSize(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (isRefreshing) {
                        if (refreshProgress > 0f) {
                            CircularProgressIndicator(
                                progress = { refreshProgress },
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp,
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.msg_loading_artwork),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.msg_no_tag_artwork, ""),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val tag = tags[page]
                val preview = todayArtwork.firstOrNull { it.tags == tag }

                val showLoading = preview == null && isRefreshing
                PullToRefreshBox(
                    isRefreshing = if (showLoading) false else isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (preview != null) {
                        val context = LocalContext.current
                        val discussionId = preview.discussionId

                        var todayFloor by remember { mutableStateOf<BangumiReply?>(null) }

                        LaunchedEffect(discussionId) {
                            if (discussionId == null) return@LaunchedEffect
                            todayFloor = ReactionService.loadTopicFloors(context)[discussionId]
                        }

                        val commentsToShow =
                            remember(todayFloor) {
                                todayFloor?.replies.orEmpty()
                            }

                        when (windowSizeClass) {
                            WindowWidthSizeClass.Expanded -> {
                                // Tablet/foldable landscape: two-pane layout
                                var showReactionPicker by remember { mutableStateOf(false) }
                                val token = preview.filename.substringBeforeLast('.')
                                val hasReacted = preview.userEmoji != null

                                Row(modifier = Modifier.fillMaxSize()) {
                                    // Left: Image pane — scrollable for pull-to-refresh
                                    Column(
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .verticalScroll(rememberScrollState())
                                                .background(colorScheme.surfaceContainerLowest),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context).data(preview.uri).build(),
                                            contentDescription = preview.artistName.ifBlank { preview.filename },
                                            contentScale = ContentScale.Fit,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onFullscreenImage(preview) },
                                        )
                                    }
                                    // Right: Detail pane — scrollable content + fixed bottom bar
                                    Surface(
                                        modifier =
                                            Modifier
                                                .width(400.dp)
                                                .fillMaxHeight()
                                                .drawBehind {
                                                    // Left border
                                                    drawLine(
                                                        color = colorScheme.outlineVariant,
                                                        start = Offset(0f, 0f),
                                                        end = Offset(0f, size.height),
                                                        strokeWidth = 1.dp.toPx(),
                                                    )
                                                },
                                        color = colorScheme.surfaceContainerLow,
                                    ) {
                                        Column(modifier = Modifier.fillMaxHeight()) {
                                            // Scrollable detail content
                                            Column(
                                                modifier =
                                                    Modifier
                                                        .weight(1f)
                                                        .verticalScroll(rememberScrollState())
                                                        .padding(16.dp),
                                            ) {
                                                TabletDetailContent(
                                                    preview = preview,
                                                    isLoggedIn = isLoggedIn,
                                                    onReactionClick = onReactionClick,
                                                    onAddReaction = { showReactionPicker = true },
                                                    discussionId = discussionId,
                                                    commentsToShow = commentsToShow,
                                                )
                                            }
                                        }
                                    }
                                }

                                // Reaction picker dialog
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

                            else -> {
                                // Phone portrait: LazyColumn with image + comments
                                LazyColumn(overscrollEffect = null) {
                                    item(key = "hero") {
                                        HeroArtwork(
                                            preview = preview,
                                            isLoggedIn = isLoggedIn,
                                            onFullscreenImage = onFullscreenImage,
                                            onReactionClick = onReactionClick,
                                            onBookmarkToggle = onBookmarkToggle,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }

                                    if (discussionId != null) {
                                        item(key = "comment_header") {
                                            CommentHeader(count = commentsToShow.count { it.state == 0 })
                                        }

                                        if (commentsToShow.isEmpty()) {
                                            item(key = "comments_empty") { EmptyComments() }
                                        } else {
                                            items(
                                                count = commentsToShow.size,
                                                key = { commentsToShow[it].id },
                                            ) { index ->
                                                FloorCommentEntry(reply = commentsToShow[index])
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (isRefreshing) {
                                if (refreshProgress > 0f) {
                                    CircularProgressIndicator(
                                        progress = { refreshProgress },
                                        modifier = Modifier.size(48.dp),
                                        strokeWidth = 4.dp,
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(48.dp),
                                        strokeWidth = 4.dp,
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.msg_loading_artwork),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.msg_no_tag_artwork, tag),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(32.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RefreshProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    if (progress == null) return
    if (progress <= 0f) {
        LinearProgressIndicator(modifier = modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HeroArtworkImage(
    preview: ArtworkPreview,
    onFullscreenImage: (ArtworkPreview) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val aspectRatio =
        remember(preview.filename) {
            try {
                val imageFile = java.io.File(context.filesDir, "artworks/${preview.filename}")
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(imageFile.absolutePath, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    opts.outWidth.toFloat() / opts.outHeight.toFloat()
                } else {
                    1f
                }
            } catch (_: Exception) {
                1f
            }
        }

    Card(
        onClick = { onFullscreenImage(preview) },
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio),
        shape = RoundedCornerShape(0.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(preview.uri).build(),
            contentDescription = preview.artistName.ifBlank { preview.filename },
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroDetailContent(
    preview: ArtworkPreview,
    isLoggedIn: Boolean,
    onReactionClick: (String, Int) -> Unit,
    onBookmarkToggle: (token: String, fileName: String, bookmarked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val token = preview.filename.substringBeforeLast('.')
    val colorScheme = MaterialTheme.colorScheme
    var showReactionPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Header Section ──
        // Badge chip (tag name)
        if (preview.tags.isNotBlank()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = colorScheme.primaryContainer,
                ) {
                    Text(
                        text = preview.tags,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // Title: artist name
        Text(
            text = preview.artistName.ifBlank { stringResource(R.string.label_unknown_artist) },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Comment (moved above reactions)
        if (preview.comment.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = 180f },
                        tint = colorScheme.onSurfaceVariant,
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.label_uploader_comment),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = preview.comment,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // ── Reactions Section ──
        if (preview.reactions.isNotEmpty()) {
            SectionLabel(text = stringResource(R.string.section_reactions))
            TabletReactionRow(
                reactions = preview.reactions,
                userEmoji = preview.userEmoji,
                token = token,
                isLoggedIn = isLoggedIn,
                onReactionClick = onReactionClick,
                onAddReaction = { showReactionPicker = true },
            )
        }

        HorizontalDivider(color = colorScheme.outlineVariant)

        // ── Details Section ──
        SectionLabel(text = stringResource(R.string.section_details))

        // Artist (clickable to open artist URL)
        DetailMetaItem(
            icon = Icons.Default.Palette,
            label = stringResource(R.string.label_artist),
            value = preview.artistName.ifBlank { stringResource(R.string.label_unknown) },
            onClick =
                if (preview.artistUrl.isNotBlank()) {
                    {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(preview.artistUrl)),
                        )
                    }
                } else {
                    null
                },
        )

        // Uploader
        DetailMetaItem(
            icon = Icons.Default.Person,
            label = stringResource(R.string.label_suggested_by_title),
            value = preview.suggestedByName ?: stringResource(R.string.label_unknown),
        )

        // Source URL
        if (preview.sourceUrl.isNotBlank()) {
            DetailMetaItem(
                icon = Icons.Default.Image,
                label = stringResource(R.string.label_source),
                value = preview.sourceUrl,
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(preview.sourceUrl)),
                    )
                },
            )
        }

        // ── Characters Section ──
        if (preview.characterNames.isNotEmpty()) {
            HorizontalDivider(color = colorScheme.outlineVariant)
            val bgmDomain = remember { SessionManager.loadDomain(context) }
            SectionLabel(text = stringResource(R.string.label_characters))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                preview.characterNames.forEachIndexed { index, name ->
                    val characterId = preview.characterIds.getOrNull(index)
                    SuggestionChip(
                        onClick = {
                            if (characterId != null) {
                                val url = "https://$bgmDomain/character/$characterId"
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                )
                            }
                        },
                        label = { Text(name, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.height(32.dp),
                    )
                }
            }
        }

        HorizontalDivider(color = colorScheme.outlineVariant)

        // ── Action Buttons ──
        val hasReacted = preview.userEmoji != null
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = { exportArtwork(context, preview) }) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = stringResource(R.string.content_desc_export_artwork),
                    tint = colorScheme.onSurfaceVariant,
                )
            }

            IconButton(
                onClick = {
                    if (!isLoggedIn) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.msg_login_to_react),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else if (hasReacted) {
                        onReactionClick(token, preview.userEmoji)
                    } else {
                        showReactionPicker = true
                    }
                },
                colors =
                    IconButtonDefaults.iconButtonColors(
                        contentColor =
                            if (hasReacted) {
                                colorScheme.error
                            } else {
                                colorScheme.onSurfaceVariant
                            },
                    ),
            ) {
                Icon(
                    if (hasReacted) {
                        Icons.Default.Favorite
                    } else {
                        Icons.Default.FavoriteBorder
                    },
                    contentDescription = stringResource(R.string.content_desc_react),
                )
            }

            IconButton(
                onClick = {
                    val newState = !preview.isBookmarked
                    onBookmarkToggle(token, preview.filename, newState)
                },
                colors =
                    IconButtonDefaults.iconButtonColors(
                        contentColor =
                            if (preview.isBookmarked) {
                                colorScheme.primary
                            } else {
                                colorScheme.onSurfaceVariant
                            },
                    ),
            ) {
                Icon(
                    if (preview.isBookmarked) {
                        Icons.Default.Bookmark
                    } else {
                        Icons.Default.BookmarkBorder
                    },
                    contentDescription = stringResource(R.string.content_desc_bookmark),
                )
            }
        }
    }

    // ── Reaction picker dialog (managed internally) ──
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HeroArtwork(
    preview: ArtworkPreview,
    isLoggedIn: Boolean,
    onFullscreenImage: (ArtworkPreview) -> Unit,
    onReactionClick: (String, Int) -> Unit,
    onBookmarkToggle: (token: String, fileName: String, bookmarked: Boolean) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HeroArtworkImage(
            preview = preview,
            onFullscreenImage = onFullscreenImage,
        )
        HeroDetailContent(
            preview = preview,
            isLoggedIn = isLoggedIn,
            onReactionClick = onReactionClick,
            onBookmarkToggle = onBookmarkToggle,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}

// ── Section Label (uppercase with letter spacing) ──────────────

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

// ── Detail Meta Item (icon + label/value + trailing) ───────────

@Composable
private fun DetailMetaItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    maxLines: Int = 2,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier =
        if (onClick != null) {
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 4.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            if (onClick != null) Icons.AutoMirrored.Filled.OpenInNew else Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Tablet Reaction Row (horizontal capsule chips) ──────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TabletReactionRow(
    reactions: List<ReactionCount>,
    userEmoji: Int?,
    token: String,
    isLoggedIn: Boolean,
    onReactionClick: (String, Int) -> Unit,
    onAddReaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val valid = reactions.mapNotNull { r -> EmojiMap.emojiResId(r.emojiValue)?.let { r to it } }
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((reaction, resId) in valid) {
            val selected = reaction.emojiValue == userEmoji
            Surface(
                onClick = {
                    if (isLoggedIn) {
                        onReactionClick(token, reaction.emojiValue)
                    } else {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.msg_login_to_react),
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                },
                shape = RoundedCornerShape(50),
                color =
                    if (selected) {
                        colorScheme.primaryContainer
                    } else {
                        colorScheme.surfaceContainerHigh
                    },
                modifier = Modifier.height(32.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PixelEmoji(resId = resId, modifier = Modifier.size(20.dp))
                    Text(
                        text = "${reaction.count}",
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            if (selected) {
                                colorScheme.onPrimaryContainer
                            } else {
                                colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
        // Add reaction button
        Surface(
            onClick = {
                if (isLoggedIn) {
                    onAddReaction()
                } else {
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.msg_login_to_react),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            },
            shape = RoundedCornerShape(50),
            color = colorScheme.surfaceContainerHigh,
            modifier = Modifier.height(32.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.content_desc_add_reaction),
                    modifier = Modifier.size(18.dp),
                    tint = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Tablet Detail Content ──────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TabletDetailContent(
    preview: ArtworkPreview,
    isLoggedIn: Boolean,
    onReactionClick: (String, Int) -> Unit,
    onAddReaction: () -> Unit,
    discussionId: String?,
    commentsToShow: List<BangumiSubReply>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val token = preview.filename.substringBeforeLast('.')
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Header Section ──
        // Badge chip (tag name)
        if (preview.tags.isNotBlank()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = colorScheme.primaryContainer,
                ) {
                    Text(
                        text = preview.tags,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // Title: artist name
        Text(
            text = preview.artistName.ifBlank { stringResource(R.string.label_unknown_artist) },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Comment (moved above reactions)
        if (preview.comment.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = 180f },
                        tint = colorScheme.onSurfaceVariant,
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.label_uploader_comment),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = preview.comment,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // ── Reactions Section ──
        if (preview.reactions.isNotEmpty()) {
            SectionLabel(text = stringResource(R.string.section_reactions))
            TabletReactionRow(
                reactions = preview.reactions,
                userEmoji = preview.userEmoji,
                token = token,
                isLoggedIn = isLoggedIn,
                onReactionClick = onReactionClick,
                onAddReaction = onAddReaction,
            )
        }

        HorizontalDivider(color = colorScheme.outlineVariant)

        // ── Details Section ──
        SectionLabel(text = stringResource(R.string.section_details))

        // Artist (clickable to open artist URL)
        DetailMetaItem(
            icon = Icons.Default.Palette,
            label = stringResource(R.string.label_artist),
            value = preview.artistName.ifBlank { stringResource(R.string.label_unknown) },
            onClick =
                if (preview.artistUrl.isNotBlank()) {
                    {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(preview.artistUrl)),
                        )
                    }
                } else {
                    null
                },
        )

        // Uploader
        DetailMetaItem(
            icon = Icons.Default.Person,
            label = stringResource(R.string.label_suggested_by_title),
            value = preview.suggestedByName ?: stringResource(R.string.label_unknown),
        )

        // Source URL
        if (preview.sourceUrl.isNotBlank()) {
            DetailMetaItem(
                icon = Icons.Default.Image,
                label = stringResource(R.string.label_source),
                value = preview.sourceUrl,
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(preview.sourceUrl)),
                    )
                },
            )
        }

        // ── Characters Section ──
        if (preview.characterNames.isNotEmpty()) {
            HorizontalDivider(color = colorScheme.outlineVariant)
            val bgmDomain = remember { SessionManager.loadDomain(context) }
            SectionLabel(text = stringResource(R.string.label_characters))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                preview.characterNames.forEachIndexed { index, name ->
                    val characterId = preview.characterIds.getOrNull(index)
                    SuggestionChip(
                        onClick = {
                            if (characterId != null) {
                                val url = "https://$bgmDomain/character/$characterId"
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                )
                            }
                        },
                        label = { Text(name, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.height(32.dp),
                    )
                }
            }
        }

        HorizontalDivider(color = colorScheme.outlineVariant)

        // ── Comments Section ──
        if (discussionId != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.label_comments),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.tab_today),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
                val commentCount = commentsToShow.count { it.state == 0 }
                if (commentCount > 0) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = commentCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        shape = RoundedCornerShape(50),
                        colors =
                            SuggestionChipDefaults.suggestionChipColors(
                                containerColor = colorScheme.surfaceContainerHighest,
                            ),
                        border = null,
                    )
                }
            }

            if (commentsToShow.isEmpty()) {
                // Empty state with icon
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Comment,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.msg_no_comments_today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                commentsToShow.forEach { reply ->
                    FloorCommentEntry(reply = reply)
                }
            }
        }
    }
}

// ── Bottom Action Bar (fixed at bottom) ────────────────────────

@Composable
private fun BottomActionBar(
    preview: ArtworkPreview,
    isLoggedIn: Boolean,
    onReactionClick: (String, Int) -> Unit,
    onBookmarkToggle: (token: String, fileName: String, bookmarked: Boolean) -> Unit,
    onExport: () -> Unit,
    onAddReaction: () -> Unit,
    onSetWallpaper: () -> Unit,
    token: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasReacted = preview.userEmoji != null
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        color = colorScheme.surfaceContainerLow,
        modifier =
            modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = colorScheme.outlineVariant,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Primary FAB: Set Wallpaper
            Surface(
                onClick = onSetWallpaper,
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.primaryContainer,
                modifier = Modifier.height(56.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.btn_set_wallpaper),
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Like button
            IconButton(
                onClick = {
                    val emoji = preview.userEmoji
                    if (!isLoggedIn) {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.msg_login_to_react),
                                Toast.LENGTH_SHORT,
                            ).show()
                    } else if (emoji != null) {
                        onReactionClick(token, emoji)
                    } else {
                        onAddReaction()
                    }
                },
                modifier = Modifier.size(40.dp),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        contentColor =
                            if (hasReacted) {
                                colorScheme.primary
                            } else {
                                colorScheme.onSurfaceVariant
                            },
                    ),
            ) {
                Icon(
                    if (hasReacted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(R.string.content_desc_react),
                )
            }

            // Bookmark button
            IconButton(
                onClick = {
                    val newState = !preview.isBookmarked
                    onBookmarkToggle(token, preview.filename, newState)
                },
                modifier = Modifier.size(40.dp),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        contentColor =
                            if (preview.isBookmarked) {
                                colorScheme.primary
                            } else {
                                colorScheme.onSurfaceVariant
                            },
                    ),
            ) {
                Icon(
                    if (preview.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = stringResource(R.string.content_desc_bookmark),
                )
            }

            // Export button
            IconButton(
                onClick = onExport,
                modifier = Modifier.size(40.dp),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        contentColor = colorScheme.onSurfaceVariant,
                    ),
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = stringResource(R.string.content_desc_export_artwork),
                )
            }
        }
    }
}
