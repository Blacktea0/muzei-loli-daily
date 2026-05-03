package me.eroi.lolidaily.muzei.ui.screen.gallery

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import me.eroi.lolidaily.muzei.ui.screen.components.ReactionPickerDialog
import me.eroi.lolidaily.muzei.ui.screen.components.ReactionRow
import me.eroi.lolidaily.muzei.util.exportArtwork
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

private const val GALLERY_PAGE_SIZE = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtworkGallery(
    cachedArtwork: List<ArtworkPreview>,
    onFullscreenImage: (ArtworkPreview) -> Unit = {},
    isLoggedIn: Boolean = false,
    onLogin: () -> Unit = {},
    onReactionClick: (token: String, emojiValue: Int) -> Unit = { _, _ -> },
    emptyMessage: String = "No artwork yet.",
    isToday: Boolean = true,
) {
    var visibleCount by remember { mutableStateOf(GALLERY_PAGE_SIZE) }
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisible != null &&
                lastVisible.index >= (visibleCount - 2) &&
                visibleCount < cachedArtwork.size
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            visibleCount = minOf(visibleCount + GALLERY_PAGE_SIZE, cachedArtwork.size)
        }
    }

    LaunchedEffect(cachedArtwork.size) {
        visibleCount = minOf(GALLERY_PAGE_SIZE, cachedArtwork.size)
    }

    if (cachedArtwork.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = emptyMessage,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(cachedArtwork.take(visibleCount)) { preview ->
                ArtworkCard(
                    preview = preview,
                    onImageClick = { onFullscreenImage(preview) },
                    isLoggedIn = isLoggedIn,
                    onLogin = onLogin,
                    onReactionClick = onReactionClick,
                )
            }

            if (visibleCount < cachedArtwork.size) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp,
                        )
                    }
                }
            } else if (cachedArtwork.size > GALLERY_PAGE_SIZE) {
                item {
                    Text(
                        text = "\u2014 End of gallery \u2014",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArtworkCard(
    preview: ArtworkPreview,
    onImageClick: () -> Unit,
    isLoggedIn: Boolean = false,
    onLogin: () -> Unit = {},
    onReactionClick: (token: String, emojiValue: Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val token = preview.filename.substringBeforeLast('.')

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
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

            if (preview.tags.isNotBlank()) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
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
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
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

            if (preview.reactions.isNotEmpty()) {
                ReactionRow(
                    reactions = preview.reactions,
                    userEmoji = preview.userEmoji,
                    token = token,
                    isLoggedIn = isLoggedIn,
                    onReactionClick = onReactionClick,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
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

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Comment,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp),
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(onClick = { exportArtwork(context, preview) }) {
                    Icon(Icons.Default.Save, contentDescription = "Export artwork")
                }

                FilledTonalIconButton(onClick = { showBottomSheet = true }) {
                    Icon(Icons.Default.Info, contentDescription = "Artwork details")
                }

                var showReactionPicker by remember { mutableStateOf(false) }
                val hasReacted = preview.userEmoji != null

                FilledTonalIconButton(
                    onClick = {
                        if (!isLoggedIn) {
                            Toast.makeText(context, "Login to Bangumi to react", Toast.LENGTH_SHORT)
                                .show()
                        } else if (hasReacted) {
                            onReactionClick(token, preview.userEmoji)
                        } else {
                            showReactionPicker = true
                        }
                    },
                    colors =
                        IconButtonDefaults.filledTonalIconButtonColors(
                            contentColor =
                                if (hasReacted) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
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

    if (showBottomSheet) {
        ArtworkDetailBottomSheet(
            preview = preview,
            sheetState = sheetState,
            onDismiss = { showBottomSheet = false },
        )
    }
}

@Composable
fun FullscreenImageOverlay(
    preview: ArtworkPreview,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val zoomState = rememberZoomState()

    var showAppBar by remember { mutableStateOf(false) }
    val currentShowAppBar by rememberUpdatedState(showAppBar)

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(preview.uri).build(),
                contentDescription = preview.filename,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier.fillMaxSize()
                        .zoomable(
                            zoomState,
                            enableOneFingerZoom = false,
                            onTap = {
                                if (currentShowAppBar) showAppBar = false else showAppBar = true
                            },
                        ),
            )

            AnimatedVisibility(
                visible = showAppBar,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .statusBarsPadding()
                            .padding(4.dp),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}
