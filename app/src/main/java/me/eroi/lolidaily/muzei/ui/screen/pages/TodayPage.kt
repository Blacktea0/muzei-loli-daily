package me.eroi.lolidaily.muzei.ui.screen.pages

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
fun TodayPage(
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
    val tags = remember(todayArtwork) { todayArtwork.map { it.tags }.filter { it.isNotBlank() }.distinct() }
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
                        if (currentTag != null) {
                            Text(
                                text = currentTag,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    // ── Tag segmented selector (like theme mode in Settings) ──
                    if (showTabs) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                            modifier = Modifier.padding(8.dp)
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
                        Icons.Default.Save,
                        stringResource(R.string.content_desc_export_artwork),
                        colorScheme.onSurfaceVariant,
                    ),
                    Triple(
                        if (currentPreviewForFab.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        stringResource(if (currentPreviewForFab.isBookmarked) R.string.content_desc_bookmark_active else R.string.content_desc_bookmark),
                        if (currentPreviewForFab.isBookmarked) colorScheme.primary else colorScheme.onSurfaceVariant,
                    ),
                    Triple(
                        if (hasReacted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        stringResource(if (hasReacted) R.string.content_desc_react_active else R.string.content_desc_react),
                        if (hasReacted) colorScheme.primary else colorScheme.onSurfaceVariant,
                    ),
                )


            // Scrim overlay when FAB menu is expanded
            val scrimAlpha by animateFloatAsState(
                targetValue = if (fabMenuExpanded) 0.32f else 0f,
                animationSpec = tween(durationMillis = 200),
                label = "fabMenuScrim",
            )
            if (scrimAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = scrimAlpha }
                        .background(Color.Black)
                        .clickable { fabMenuExpanded = false },
                )
            }
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
                            when (i) {
                                0 -> {
                                    // Export / Save
                                    exportArtwork(context, currentPreviewForFab)
                                }
                                1 -> {
                                    // Bookmark
                                    val newState = !currentPreviewForFab.isBookmarked
                                    onBookmarkToggle(fabToken, currentPreviewForFab.filename, newState)
                                }
                                2 -> {
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
