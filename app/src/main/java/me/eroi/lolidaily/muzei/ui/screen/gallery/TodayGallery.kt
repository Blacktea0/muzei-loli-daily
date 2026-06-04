package me.eroi.lolidaily.muzei.ui.screen.gallery

import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TodayGallery(
    todayArtwork: List<ArtworkPreview>,
    isLoggedIn: Boolean,
    onFullscreenImage: (ArtworkPreview) -> Unit,
    onReactionClick: (String, Int) -> Unit,
    onRefresh: () -> Unit,
    onBookmarkToggle: (token: String, fileName: String, bookmarked: Boolean) -> Unit = { _, _, _ -> },
    refreshProgress: Float? = null,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},
    windowSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    onPageOpened: () -> Unit = {},
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

    // Trigger refresh when page is first displayed
    LaunchedEffect(Unit) {
        onPageOpened()
    }

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }
    val scope = rememberCoroutineScope()
    val isRefreshing = refreshProgress != null
    val state = rememberPullToRefreshState()
    val context = LocalContext.current
    val msgLoginToReact = stringResource(R.string.msg_login_to_react)
    var showReactionPicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // ── Top App Bar + Refresh Progress (overlay at bottom) ──
        val currentPreview = todayArtwork.firstOrNull { it.tags == currentTag }

        Box {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentDate,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    // ── Tag segmented selector (like theme mode in Settings) ──
                    if (showTabs) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        ) {
                            tags.forEachIndexed { index, tag ->
                                ToggleButton(
                                    checked = pagerState.currentPage == index,
                                    onCheckedChange = {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    },
                                    shapes =
                                        when (index) {
                                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                            tags.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                        },
                                    colors = ToggleButtonDefaults.tonalToggleButtonColors(),
                                    modifier = Modifier.height(32.dp),
                                ) {
                                    Text(tag, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
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
            RefreshProgressBar(
                progress = refreshProgress,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // ── Content ──
        if (tags.isEmpty()) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                state = state,
                indicator = {
                    ExpressivePullIndicator(
                        state = state,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
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
                            LoadingIndicator(
                                progress = { refreshProgress },
                                modifier = Modifier.size(48.dp),
                            )
                        } else {
                            LoadingIndicator(
                                progress = { refreshProgress },
                                modifier = Modifier.size(48.dp),
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
                    state = state,
                    indicator = {
                        ExpressivePullIndicator(
                            state = state,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    },
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
                                    LoadingIndicator(
                                        progress = { refreshProgress },
                                        modifier = Modifier.size(48.dp),
                                    )
                                } else {
                                    LoadingIndicator(
                                        progress = { refreshProgress },
                                        modifier = Modifier.size(48.dp),
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
        } // Column

        // ── FAB with action menu ──
        val currentPreviewForFab = todayArtwork.firstOrNull { it.tags == tags.getOrNull(pagerState.currentPage) }
        if (currentPreviewForFab != null) {
            val fabToken = currentPreviewForFab.filename.substringBeforeLast('.')
            val hasReacted = currentPreviewForFab.userEmoji != null
            var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
            val focusRequester = remember { FocusRequester() }

            BackHandler(fabMenuExpanded) { fabMenuExpanded = false }

            val moreActionsDesc = stringResource(R.string.content_desc_more_actions)

            val fabItems =
                listOf(
                    Triple(
                        if (hasReacted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        stringResource(R.string.content_desc_react),
                        if (hasReacted) colorScheme.primary else colorScheme.onSurfaceVariant,
                    ),
                    Triple(
                        if (currentPreviewForFab.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        stringResource(R.string.content_desc_bookmark),
                        if (currentPreviewForFab.isBookmarked) colorScheme.primary else colorScheme.onSurfaceVariant,
                    ),
                    Triple(
                        Icons.Default.Save,
                        stringResource(R.string.content_desc_export_artwork),
                        colorScheme.onSurfaceVariant,
                    ),
                )

            FloatingActionButtonMenu(
                modifier = Modifier.align(Alignment.BottomEnd),
                expanded = fabMenuExpanded,
                button = {
                    TooltipBox(
                        positionProvider =
                            TooltipDefaults.rememberTooltipPositionProvider(
                                if (fabMenuExpanded) {
                                    TooltipAnchorPosition.Start
                                } else {
                                    TooltipAnchorPosition.Above
                                },
                            ),
                        tooltip = {
                            PlainTooltip {
                                Text(stringResource(R.string.content_desc_more_actions))
                            }
                        },
                        state = rememberTooltipState(),
                    ) {
                        ToggleFloatingActionButton(
                            modifier =
                                Modifier
                                    .semantics {
                                        traversalIndex = -1f
                                        stateDescription =
                                            if (fabMenuExpanded) "Expanded" else "Collapsed"
                                        contentDescription = moreActionsDesc
                                    }.animateFloatingActionButton(
                                        visible = true,
                                        alignment = Alignment.BottomEnd,
                                    ).focusRequester(focusRequester),
                            checked = fabMenuExpanded,
                            onCheckedChange = { fabMenuExpanded = !fabMenuExpanded },
                        ) {
                            val imageVector by remember {
                                derivedStateOf {
                                    if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.MoreVert
                                }
                            }
                            Icon(
                                painter = rememberVectorPainter(imageVector),
                                contentDescription = null,
                                modifier = Modifier.animateIcon({ checkedProgress }),
                            )
                        }
                    }
                },
            ) {
                fabItems.forEachIndexed { i, (icon, label, tint) ->
                    FloatingActionButtonMenuItem(
                        onClick = {
                            fabMenuExpanded = false
                            when (i) {
                                0 -> {
                                    // Like / React
                                    val emoji = currentPreviewForFab.userEmoji
                                    if (!isLoggedIn) {
                                        Toast.makeText(context, msgLoginToReact, Toast.LENGTH_SHORT).show()
                                    } else if (emoji != null) {
                                        onReactionClick(fabToken, emoji)
                                    } else {
                                        showReactionPicker = true
                                    }
                                }
                                1 -> {
                                    // Bookmark
                                    val newState = !currentPreviewForFab.isBookmarked
                                    onBookmarkToggle(fabToken, currentPreviewForFab.filename, newState)
                                }
                                2 -> {
                                    // Export
                                    exportArtwork(context, currentPreviewForFab)
                                }
                            }
                        },
                        icon = { Icon(icon, contentDescription = null, tint = tint) },
                        text = { Text(text = label) },
                    )
                }
            }
        }
    } // Box

    // Reaction picker dialog
    val currentPreviewForDialog = todayArtwork.firstOrNull { it.tags == tags.getOrNull(pagerState.currentPage) }
    if (showReactionPicker && isLoggedIn && currentPreviewForDialog != null) {
        val tokenForDialog = currentPreviewForDialog.filename.substringBeforeLast('.')
        ReactionPickerDialog(
            onDismiss = { showReactionPicker = false },
            onEmojiSelected = { value ->
                onReactionClick(tokenForDialog, value)
                showReactionPicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RefreshProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    if (progress == null) return
    LinearWavyProgressIndicator(
        progress = { if (progress <= 0f) 0.01f else progress },
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressivePullIndicator(
    state: androidx.compose.material3.pulltorefresh.PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val distanceFraction = state.distanceFraction.coerceIn(0f, 1f)
    val rawDistanceFraction = state.distanceFraction

    val alpha by animateFloatAsState(
        targetValue = if (isRefreshing) 1f else distanceFraction,
        animationSpec =
            if (isRefreshing) {
                spring(stiffness = Spring.StiffnessMedium)
            } else {
                snap()
            },
    )

    val scale by animateFloatAsState(
        targetValue = if (isRefreshing) 1f else distanceFraction / 2 + 0.5f,
        animationSpec =
            if (isRefreshing) {
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            } else {
                snap()
            },
    )

    val maxDistance = 80.dp
    val slideOffset by animateDpAsState(
        targetValue = if (rawDistanceFraction > 1f) 18.dp + maxDistance * (rawDistanceFraction - 1) else 18.dp,
        animationSpec = spring()
    )

    Box(
        modifier =
            modifier
                .offset { IntOffset(0, slideOffset.roundToPx()) }
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.animation.Crossfade(
            targetState = isRefreshing,
            animationSpec = tween(durationMillis = 200),
        ) { refreshing ->
            if (refreshing) {
                ContainedLoadingIndicator(
                    modifier = Modifier.size(48.dp),
                )
            } else {
                ContainedLoadingIndicator(
                    progress = { rawDistanceFraction / 2 },
                    modifier = Modifier.size(48.dp),
                    polygons = listOf(MaterialShapes.SoftBurst, MaterialShapes.SoftBurst),
                )
            }
        }
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

        // Title: artist name with palette icon
        ArtistTitleRow(preview = preview)

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
                        imageVector = Icons.Filled.FormatQuote,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = 180f },
                        tint = colorScheme.onSurfaceVariant,
                    )
                    Column {
                        Text(
                            text =
                                preview.suggestedByName?.let {
                                    stringResource(R.string.label_uploader_comment, it)
                                } ?: stringResource(R.string.label_anonymous_comment),
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
                            Intent(Intent.ACTION_VIEW, preview.artistUrl.toUri()),
                        )
                    }
                } else {
                    null
                },
        )

        // Source URL
        if (preview.sourceUrl.isNotBlank()) {
            DetailMetaItem(
                icon = Icons.Default.Image,
                label = stringResource(R.string.label_source),
                value = preview.sourceUrl,
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, preview.sourceUrl.toUri()),
                    )
                },
            )
        }

        // Uploader (placed last)
        val suggestedByDisplay =
            buildString {
                append(preview.suggestedByName ?: stringResource(R.string.label_unknown))
                preview.suggestedByUsername?.let { append(" @$it") }
            }
        DetailMetaItem(
            icon = Icons.Default.Person,
            label = stringResource(R.string.label_suggested_by_title),
            value = suggestedByDisplay,
            onClick =
                preview.suggestedByUsername?.let { username ->
                    {
                        val bgmDomain = SessionManager.loadDomain(context)
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, "https://$bgmDomain/user/$username".toUri()),
                        )
                    }
                },
        )

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
                                    Intent(Intent.ACTION_VIEW, url.toUri()),
                                )
                            }
                        },
                        label = { Text(name, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.height(32.dp),
                    )
                }
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
    val msgLoginToReact = stringResource(R.string.msg_login_to_react)
    val colorScheme = MaterialTheme.colorScheme
    var activeTooltipIndex by remember { mutableStateOf<Int?>(null) }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((index, reactionResId) in valid.withIndex()) {
            val (reaction, resId) = reactionResId
            val selected = reaction.emojiValue == userEmoji
            val isActive = activeTooltipIndex == index

            Box {
                Surface(
                    onClick = {
                        if (isLoggedIn) {
                            onReactionClick(token, reaction.emojiValue)
                        } else {
                            Toast
                                .makeText(
                                    context,
                                    msgLoginToReact,
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
                    modifier =
                        Modifier
                            .height(32.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        if (reaction.users.isNotEmpty()) {
                                            activeTooltipIndex = index
                                        }
                                    },
                                )
                            },
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

                if (isActive && reaction.users.isNotEmpty()) {
                    val density = LocalDensity.current
                    Popup(
                        alignment = Alignment.BottomCenter,
                        offset = IntOffset(0, with(density) { -8.dp.roundToPx() }),
                        onDismissRequest = { activeTooltipIndex = null },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colorScheme.inverseSurface,
                            tonalElevation = 6.dp,
                        ) {
                            Text(
                                text = formatUserList(reaction.users),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.inverseOnSurface,
                            )
                        }
                    }
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
                            msgLoginToReact,
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

        // Title: artist name with palette icon
        ArtistTitleRow(preview = preview)

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
                            text =
                                preview.suggestedByName?.let {
                                    stringResource(R.string.label_uploader_comment, it)
                                } ?: stringResource(R.string.label_anonymous_comment),
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
                            Intent(Intent.ACTION_VIEW, preview.artistUrl.toUri()),
                        )
                    }
                } else {
                    null
                },
        )

        // Source URL
        if (preview.sourceUrl.isNotBlank()) {
            DetailMetaItem(
                icon = Icons.Default.Image,
                label = stringResource(R.string.label_source),
                value = preview.sourceUrl,
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, preview.sourceUrl.toUri()),
                    )
                },
            )
        }

        // Uploader (placed last)
        val suggestedByDisplay =
            buildString {
                append(preview.suggestedByName ?: stringResource(R.string.label_unknown))
                preview.suggestedByUsername?.let { append(" @$it") }
            }
        DetailMetaItem(
            icon = Icons.Default.Person,
            label = stringResource(R.string.label_suggested_by_title),
            value = suggestedByDisplay,
            onClick =
                preview.suggestedByUsername?.let { username ->
                    {
                        val bgmDomain = SessionManager.loadDomain(context)
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, "https://$bgmDomain/user/$username".toUri()),
                        )
                    }
                },
        )

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
                                    Intent(Intent.ACTION_VIEW, url.toUri()),
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
            val commentCount = commentsToShow.count { it.state == 0 }
            if (commentCount > 0) {
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
                            text = commentCount.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
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
    val msgLoginToReact = stringResource(R.string.msg_login_to_react)
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
                                msgLoginToReact,
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

// ── Artist Title Row (shared between portrait and tablet) ────────

@Composable
private fun ArtistTitleRow(
    preview: ArtworkPreview,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.Palette,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = preview.artistName.ifBlank { stringResource(R.string.label_unknown_artist) },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatUserList(users: List<String>): String {
    if (users.isEmpty()) return ""
    val locale = java.util.Locale.getDefault()
    val separator = if (locale.language == "zh" || locale.language == "ja") "、" else ", "
    return users.joinToString(separator)
}
