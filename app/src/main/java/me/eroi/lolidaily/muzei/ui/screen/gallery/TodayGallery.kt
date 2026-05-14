package me.eroi.lolidaily.muzei.ui.screen.gallery

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.api.ReactionService
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import me.eroi.lolidaily.muzei.model.BangumiReply
import me.eroi.lolidaily.muzei.ui.screen.components.*
import me.eroi.lolidaily.muzei.util.exportArtwork

@OptIn(ExperimentalMaterial3Api::class)
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
) {
    val tags = remember(todayArtwork) { todayArtwork.map { it.tags }.distinct() }
    val showTabs = tags.size > 1
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, (tags.size - 1).coerceAtLeast(0)), pageCount = { tags.size })

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }
    val scope = rememberCoroutineScope()

    val isRefreshing = refreshProgress != null

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.statusBarsPadding()) {
            if (showTabs) {
                PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                    tags.forEachIndexed { index, tag ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(tag) },
                        )
                    }
                }
            }
            RefreshProgressBar(refreshProgress, Modifier.align(Alignment.TopCenter))
        }

        if (tags.isEmpty()) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
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
                    Text(
                        text = stringResource(R.string.msg_no_tag_artwork, ""),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val tag = tags[page]
                val preview = todayArtwork.firstOrNull { it.tags == tag }

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
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
                    } else {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
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
    val context = LocalContext.current
    val token = preview.filename.substringBeforeLast('.')

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

    var showReactionPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // ── Zone 1: Full-bleed image with overlaid info ──
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clickable { onFullscreenImage(preview) }
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(preview.uri).build(),
                contentDescription = preview.artistName.ifBlank { preview.filename },
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            // Gradient scrim at bottom of image
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.45f)
                        .heightIn(max = 200.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                            ),
                        ),
            )

            // Artist info overlay (bottom-start)
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 12.dp, end = 100.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White,
                    )
                    Text(
                        text = preview.artistName.ifBlank { stringResource(R.string.label_unknown_artist) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (preview.tags.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        ) {
                            Text(
                                text = preview.tags,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    if (preview.date.isNotBlank()) {
                        Row(
                            modifier =
                                Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        RoundedCornerShape(50),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.White.copy(alpha = 0.7f),
                    )
                    val suggestedName = preview.suggestedByName
                    Text(
                        text =
                            if (suggestedName != null) {
                                stringResource(
                                    R.string.label_suggested_by,
                                    suggestedName,
                                )
                            } else {
                                stringResource(R.string.label_suggested_by_anonymous)
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }

            // Action buttons overlay (bottom-end)
            val hasReacted = preview.userEmoji != null

            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 12.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                tonalElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { exportArtwork(context, preview) }) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = stringResource(R.string.content_desc_export_artwork),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(
                        onClick = {
                            if (!isLoggedIn) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.msg_login_to_react),
                                    Toast.LENGTH_SHORT,
                                )
                                    .show()
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
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
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
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
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

        // ── Zone 2: Metadata content area ──
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Comment card
            if (preview.comment.isNotBlank()) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.Comment,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            val commentAuthor = preview.suggestedByName
                            Text(
                                text =
                                    if (!commentAuthor.isNullOrBlank()) {
                                        stringResource(R.string.label_comment_by, commentAuthor)
                                    } else {
                                        stringResource(R.string.label_anonymous_comment)
                                    },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = preview.comment,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            softWrap = true,
                        )
                    }
                }
            }

            // Reactions
            if (preview.reactions.isNotEmpty()) {
                ReactionRow(
                    reactions = preview.reactions,
                    userEmoji = preview.userEmoji,
                    token = token,
                    isLoggedIn = isLoggedIn,
                    onReactionClick = onReactionClick,
                    onAddReaction = { showReactionPicker = true },
                )
            }

            // Characters
            if (preview.characterNames.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.label_characters),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    preview.characterNames.forEach { name ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(name, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }

            // Source / Artist links
            if (preview.sourceUrl.isNotBlank() || preview.artistUrl.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (preview.sourceUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(preview.sourceUrl)),
                                )
                            },
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.label_source))
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    if (preview.artistUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(preview.artistUrl)),
                                )
                            },
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.label_artist))
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
