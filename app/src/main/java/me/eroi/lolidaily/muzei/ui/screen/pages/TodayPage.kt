package me.eroi.lolidaily.muzei.ui.screen.pages

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.eroi.lolidaily.muzei.api.BangumiApiClient
import me.eroi.lolidaily.muzei.api.SessionManager
import me.eroi.lolidaily.muzei.util.Log
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.api.ReactionService
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import me.eroi.lolidaily.muzei.model.BangumiReply
import me.eroi.lolidaily.muzei.model.BangumiReaction
import me.eroi.lolidaily.muzei.model.BangumiReactionUser
import me.eroi.lolidaily.muzei.ui.screen.components.*
import me.eroi.lolidaily.muzei.util.exportArtwork
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
    val state = rememberExpressivePullToRefreshState()
    val context = LocalContext.current
    val msgLoginToReact = stringResource(R.string.msg_login_to_react)
    var showReactionPicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // ── Top App Bar + Refresh Progress (overlay at bottom) ──

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
                    PullToRefreshDefaults.LoadingIndicator(
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
                        PullToRefreshDefaults.LoadingIndicator(
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
                        var serverFloor by remember { mutableStateOf<BangumiReply?>(null) }
                        var optimisticCommentReactions by remember { mutableStateOf<Map<Int, Int?>>(emptyMap()) }
                        var showCommentSheet by remember { mutableStateOf(false) }
                        var commentSheetInitialText by remember { mutableStateOf("") }
                        var commentDraftText by rememberSaveable { mutableStateOf("") }
                        val coroutineScope = rememberCoroutineScope()
                        var isRefreshingComments by remember { mutableStateOf(false) }
                        var activeReactionReplyId by remember { mutableStateOf<Int?>(null) }
                        var pendingCommentReactions by remember { mutableStateOf<Set<Int>>(emptySet()) }

                        fun updateUiFloor(
                            floor: BangumiReply?,
                            username: String?,
                            nickname: String?,
                            optimisticMap: Map<Int, Int?>
                        ) {
                            if (floor == null) {
                                todayFloor = null
                                return
                            }
                            if (username == null) {
                                todayFloor = floor
                                return
                            }
                            val (reconciled, remainingOptimistic) = reconcileFloor(
                                floor = floor,
                                username = username,
                                nickname = nickname.orEmpty(),
                                optimisticMap = optimisticMap
                            )
                            todayFloor = reconciled
                            if (remainingOptimistic != optimisticCommentReactions) {
                                optimisticCommentReactions = remainingOptimistic
                            }
                        }

                        fun refreshComments() {
                            if (discussionId == null) return
                            val overrideTopicId = LoliApiClient.getDebugOverrideTopicId(context)
                            val targetTopicId = overrideTopicId ?: BangumiApiClient.parseDiscussionId(discussionId).first
                            if (targetTopicId == 0) return

                            isRefreshingComments = true
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val topic = BangumiApiClient.fetchTopic(context, targetTopicId)
                                    val floor = topic?.let {
                                        BangumiApiClient.findTodayFloor(it, preview.date, preview.tags)
                                    }
                                    withContext(Dispatchers.Main) {
                                        serverFloor = floor
                                        val username = SessionManager.loadUsername(context)
                                        val nickname = SessionManager.loadNickname(context)
                                        updateUiFloor(floor, username, nickname, optimisticCommentReactions)
                                        isRefreshingComments = false
                                    }
                                } catch (e: Exception) {
                                    Log.w("TodayPage", "Failed to refresh comments", e)
                                    withContext(Dispatchers.Main) {
                                        isRefreshingComments = false
                                    }
                                }
                            }
                        }

                        LaunchedEffect(discussionId, todayArtwork) {
                            if (discussionId == null) {
                                serverFloor = null
                                todayFloor = null
                                return@LaunchedEffect
                            }
                            val overrideTopicId = LoliApiClient.getDebugOverrideTopicId(context)
                            if (overrideTopicId == null) {
                                val floor = ReactionService.loadTopicFloors(context)[discussionId]
                                serverFloor = floor
                                val username = SessionManager.loadUsername(context)
                                val nickname = SessionManager.loadNickname(context)
                                updateUiFloor(floor, username, nickname, optimisticCommentReactions)
                            }
                            refreshComments()
                        }

                        val onPostReaction: (Int, Int) -> Unit = { replyId, emojiValue ->
                            val overrideTopicId = LoliApiClient.getDebugOverrideTopicId(context)
                            val targetTopicId = overrideTopicId ?: (discussionId?.let {
                                BangumiApiClient.parseDiscussionId(it).first
                            } ?: 0)

                            if (targetTopicId > 0 && replyId !in pendingCommentReactions) {
                                pendingCommentReactions = pendingCommentReactions + replyId
                                val username = SessionManager.loadUsername(context)
                                val nickname = SessionManager.loadNickname(context)

                                val floor = todayFloor ?: serverFloor
                                if (username != null && floor != null) {
                                    val previousEmoji = floor.replies.firstOrNull { it.id == replyId }?.reactions?.firstOrNull { r ->
                                        r.users.any { it.username == username }
                                    }?.value
                                    val nextEmoji = if (previousEmoji == emojiValue) null else emojiValue
                                    
                                    val updatedMap = optimisticCommentReactions + (replyId to nextEmoji)
                                    optimisticCommentReactions = updatedMap
                                    updateUiFloor(serverFloor, username, nickname, updatedMap)
                                }

                                coroutineScope.launch(Dispatchers.IO) {
                                    val ok = BangumiApiClient.postLike(
                                        context = context,
                                        topicId = targetTopicId,
                                        replyId = replyId,
                                        value = emojiValue
                                    )
                                    withContext(Dispatchers.Main) {
                                        pendingCommentReactions = pendingCommentReactions - replyId
                                        if (ok) {
                                            refreshComments()
                                        } else {
                                            val updatedMap = optimisticCommentReactions - replyId
                                            optimisticCommentReactions = updatedMap
                                            updateUiFloor(serverFloor, username, nickname, updatedMap)
                                        }
                                    }
                                }
                            }
                        }

                        val onPostReply: (String, (Boolean) -> Unit) -> Unit = { commentText, callback ->
                            val overrideTopicId = LoliApiClient.getDebugOverrideTopicId(context)
                            val targetTopicId = overrideTopicId ?: (discussionId?.let {
                                BangumiApiClient.parseDiscussionId(it).first
                            } ?: 0)
                            if (targetTopicId == 0) {
                                Toast.makeText(context, "无法确定讨论帖子", Toast.LENGTH_SHORT).show()
                                callback(false)
                            } else {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val ok = BangumiApiClient.postDailyComment(
                                        context = context,
                                        topicId = targetTopicId,
                                        dailyDate = preview.date,
                                        tags = preview.tags,
                                        content = commentText
                                    )
                                    withContext(Dispatchers.Main) {
                                        if (ok) {
                                            Toast.makeText(context, R.string.comment_post_success, Toast.LENGTH_SHORT).show()
                                            refreshComments()
                                            commentDraftText = ""
                                            callback(true)
                                        } else {
                                            Toast.makeText(context, R.string.comment_post_failure, Toast.LENGTH_SHORT).show()
                                            callback(false)
                                        }
                                    }
                                }
                            }
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
                                                    onCommentPlaceholderClick = { text ->
                                                        commentSheetInitialText = text
                                                        showCommentSheet = true
                                                    },
                                                    onCommentReactionClick = { replyId ->
                                                        activeReactionReplyId = replyId
                                                    },
                                                    onCommentReactionChipClick = { replyId, emojiValue ->
                                                        onPostReaction(replyId, emojiValue)
                                                    }
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

                                        item(key = "comment_input") {
                                            CommentInputPlaceholder(
                                                isLoggedIn = isLoggedIn,
                                                onClick = {
                                                    commentSheetInitialText = ""
                                                    showCommentSheet = true
                                                }
                                            )
                                        }

                                        if (commentsToShow.isEmpty()) {
                                            item(key = "comments_empty") { EmptyComments() }
                                        } else {
                                            items(
                                                count = commentsToShow.size,
                                                key = { commentsToShow[it].id },
                                            ) { index ->
                                                val reply = commentsToShow[index]
                                                FloorCommentEntry(
                                                    reply = reply,
                                                    isLoggedIn = isLoggedIn,
                                                    onReplyClick = {
                                                        val nickname = reply.creator?.nickname ?: reply.creator?.username ?: "Loli"
                                                        val quoteContent = cleanCommentForQuote(reply.content)
                                                        val initialText = "[quote][b]$nickname[/b] 说: $quoteContent[/quote]\n"
                                                        commentSheetInitialText = initialText
                                                        showCommentSheet = true
                                                    },
                                                    onReactionClick = {
                                                        activeReactionReplyId = reply.id
                                                    },
                                                    onReactionChipClick = { emojiValue ->
                                                        onPostReaction(reply.id, emojiValue)
                                                    }
                                                )
                                            }
                                            item(key = "bottom_spacer") {
                                                Spacer(modifier = Modifier.height(80.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                                if (showCommentSheet) {
                                    CommentBottomSheet(
                                        onDismiss = { showCommentSheet = false },
                                        onPostReply = onPostReply,
                                        initialText = commentSheetInitialText,
                                        draftText = commentDraftText,
                                        onDraftTextChange = { commentDraftText = it }
                                    )
                                }

                                activeReactionReplyId?.let { replyId ->
                                    ReactionPickerDialog(
                                        onDismiss = { activeReactionReplyId = null },
                                        onEmojiSelected = { value ->
                                            activeReactionReplyId = null
                                            onPostReaction(replyId, value)
                                        },
                                        emojis = listOf(0, 79, 54, 140, 62, 122, 104, 80, 141, 88, 85, 90)
                                    )
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


/**
 * A [PullToRefreshState] whose settle animations use the M3 Expressive spatial
 * motion spec, so releasing the pull (and hiding after refresh) has the
 * characteristic springy "bounce back" feel instead of a plain ease-out.
 */
private class ExpressivePullToRefreshState(
    private val animationSpec: AnimationSpec<Float>,
) : PullToRefreshState {
    private val anim = Animatable(0f)

    override val distanceFraction: Float
        get() = anim.value

    override val isAnimating: Boolean
        get() = anim.isRunning

    override suspend fun animateToThreshold() {
        anim.animateTo(1f, animationSpec)
    }

    override suspend fun animateToHidden() {
        anim.animateTo(0f, animationSpec)
    }

    override suspend fun snapTo(targetValue: Float) {
        anim.snapTo(targetValue)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rememberExpressivePullToRefreshState(): PullToRefreshState {
    // fastSpatialSpec of the expressive MotionScheme is a bouncy spring
    // (damping < 1), matching the M3 pull-to-refresh release motion.
    val bounceSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    return remember(bounceSpec) { ExpressivePullToRefreshState(bounceSpec) }
}

private fun List<BangumiReaction>.withOptimisticReactionCount(
    username: String,
    nickname: String,
    previousEmoji: Int?,
    nextEmoji: Int?,
): List<BangumiReaction> {
    val reactionsMap = associateBy { it.value }.toMutableMap()

    // 1. Remove user from previous emoji reaction
    if (previousEmoji != null) {
        val prevReaction = reactionsMap[previousEmoji]
        if (prevReaction != null) {
            val updatedUsers = prevReaction.users.filterNot { it.username == username }
            if (updatedUsers.isNotEmpty()) {
                reactionsMap[previousEmoji] = prevReaction.copy(users = updatedUsers)
            } else {
                reactionsMap.remove(previousEmoji)
            }
        }
    }

    // 2. Add user to next emoji reaction
    if (nextEmoji != null) {
        val nextReaction = reactionsMap[nextEmoji]
        if (nextReaction != null) {
            val alreadyExists = nextReaction.users.any { it.username == username }
            val updatedUsers = if (alreadyExists) {
                nextReaction.users
            } else {
                nextReaction.users + BangumiReactionUser(username = username, nickname = nickname)
            }
            reactionsMap[nextEmoji] = nextReaction.copy(users = updatedUsers)
        } else {
            reactionsMap[nextEmoji] = BangumiReaction(
                value = nextEmoji,
                users = listOf(BangumiReactionUser(username = username, nickname = nickname))
            )
        }
    }

    return reactionsMap.values.toList()
}

private fun reconcileFloor(
    floor: BangumiReply,
    username: String,
    nickname: String,
    optimisticMap: Map<Int, Int?>
): Pair<BangumiReply, Map<Int, Int?>> {
    val newOptimisticMap = optimisticMap.toMutableMap()
    val updatedReplies = floor.replies.map { subReply ->
        if (subReply.id !in newOptimisticMap) {
            subReply
        } else {
            val targetEmoji = newOptimisticMap[subReply.id]
            val serverReflects = if (targetEmoji == null) {
                subReply.reactions.none { r -> r.users.any { it.username == username } }
            } else {
                subReply.reactions.any { r -> r.value == targetEmoji && r.users.any { it.username == username } }
            }

            if (serverReflects) {
                newOptimisticMap.remove(subReply.id)
                subReply
            } else {
                val previousEmoji = subReply.reactions.firstOrNull { r ->
                    r.users.any { it.username == username }
                }?.value
                val updatedReactions = subReply.reactions.withOptimisticReactionCount(
                    username = username,
                    nickname = nickname,
                    previousEmoji = previousEmoji,
                    nextEmoji = targetEmoji
                )
                subReply.copy(reactions = updatedReactions)
            }
        }
    }
    return floor.copy(replies = updatedReplies) to newOptimisticMap
}
