package me.eroi.lolidaily.muzei.ui.screen.gallery

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import me.eroi.lolidaily.muzei.ui.screen.components.ReactionPickerDialog
import me.eroi.lolidaily.muzei.ui.screen.components.ReactionRow
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
) {
    val tags = listOf("LC0", "LC ES")
    val pagerState = rememberPagerState(pageCount = { tags.size })
    val selectedTag by remember { derivedStateOf { tags[pagerState.currentPage] } }
    val scope = rememberCoroutineScope()

    var isRefreshing by remember { mutableStateOf(false) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val insetsController = WindowCompat.getInsetsController(window, view)
        val previous = insetsController.isAppearanceLightStatusBars
        insetsController.isAppearanceLightStatusBars = false
        onDispose { insetsController.isAppearanceLightStatusBars = previous }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val tag = tags[page]
            val preview = todayArtwork.firstOrNull { it.tags == tag }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    onRefresh()
                    scope.launch {
                        delay(4000)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val viewportHeight = maxHeight
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (preview != null) {
                            HeroArtwork(
                                preview = preview,
                                isLoggedIn = isLoggedIn,
                                onLogin = onLogin,
                                onFullscreenImage = onFullscreenImage,
                                onReactionClick = onReactionClick,
                                modifier = Modifier.fillMaxWidth().height(viewportHeight),
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(viewportHeight),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No $tag artwork for today.\nPull down to refresh.",
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

        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0.0f to Color.Black.copy(alpha = 0.75f),
                                    0.7f to Color.Black.copy(alpha = 0.3f),
                                    1.0f to Color.Transparent,
                                )
                        )
                    )
        )

        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            divider = {},
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier =
                        Modifier.tabIndicatorOffset(selectedTabIndex = pagerState.currentPage),
                    color = Color.White,
                )
            },
            modifier = Modifier.statusBarsPadding(),
        ) {
            tags.forEachIndexed { index, tag ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            tag,
                            color =
                                if (pagerState.currentPage == index) Color.White
                                else Color.White.copy(alpha = 0.6f),
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HeroArtwork(
    preview: ArtworkPreview,
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    onFullscreenImage: (ArtworkPreview) -> Unit,
    onReactionClick: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val token = preview.filename.substringBeforeLast('.')

    Box(modifier = modifier.clickable { onFullscreenImage(preview) }) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(preview.uri).build(),
            contentDescription = preview.artistName.ifBlank { preview.filename },
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0.0f to Color.Transparent,
                                    0.6f to Color.Black.copy(alpha = 0.5f),
                                    1.0f to Color.Black.copy(alpha = 0.8f),
                                )
                        )
                    )
        )

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                if (preview.tags.isNotBlank()) {
                    Surface(
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
                if (preview.date.isNotBlank()) {
                    Surface(
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
            }
            Text(
                text = preview.artistName.ifBlank { "Unknown Artist" },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
            if (preview.comment.isNotBlank()) {
                Text(
                    text = preview.comment,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    softWrap = true,
                    modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 56.dp),
                )
            }
            if (preview.reactions.isNotEmpty()) {
                ReactionRow(
                    reactions = preview.reactions,
                    userEmoji = preview.userEmoji,
                    token = token,
                    isLoggedIn = isLoggedIn,
                    onReactionClick = onReactionClick,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(onClick = { exportArtwork(context, preview) }) {
                Icon(Icons.Default.Save, contentDescription = "Export artwork", tint = Color.White)
            }
            IconButton(onClick = { showBottomSheet = true }) {
                Icon(Icons.Default.Info, contentDescription = "Artwork details", tint = Color.White)
            }

            var showReactionPicker by remember { mutableStateOf(false) }
            val hasReacted = preview.userEmoji != null

            IconButton(
                onClick = {
                    if (!isLoggedIn) {
                        Toast.makeText(context, "Login to Bangumi to react", Toast.LENGTH_SHORT)
                            .show()
                    } else if (hasReacted) {
                        onReactionClick(token, preview.userEmoji)
                    } else {
                        showReactionPicker = true
                    }
                }
            ) {
                Icon(
                    if (hasReacted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "React",
                    tint = if (hasReacted) MaterialTheme.colorScheme.error else Color.White,
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

    if (showBottomSheet) {
        ArtworkDetailBottomSheet(
            preview = preview,
            sheetState = sheetState,
            onDismiss = { showBottomSheet = false },
        )
    }
}
