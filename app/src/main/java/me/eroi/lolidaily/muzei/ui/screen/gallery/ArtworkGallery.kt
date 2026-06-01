package me.eroi.lolidaily.muzei.ui.screen.gallery

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import me.eroi.lolidaily.muzei.util.exportArtwork
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

private const val GALLERY_PAGE_SIZE = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtworkGallery(
    cachedArtwork: List<ArtworkPreview>,
    onFullscreenImage: (ArtworkPreview) -> Unit = {},
    onRemoveBookmark: (ArtworkPreview) -> Unit = {},
    emptyMessage: String = "",
    searchQuery: String = "",
    selectedTag: String? = null,
    onSearchQueryChange: (String) -> Unit = {},
    onTagSelected: (String?) -> Unit = {},
    windowSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
) {
    val filteredArtwork =
        remember(cachedArtwork, searchQuery, selectedTag) {
            cachedArtwork.filter { preview ->
                val matchesSearch =
                    searchQuery.isBlank() ||
                        preview.artistName.contains(searchQuery, ignoreCase = true) ||
                        preview.comment.contains(searchQuery, ignoreCase = true) ||
                        (preview.suggestedByName?.contains(searchQuery, ignoreCase = true) == true)
                val matchesTag = selectedTag == null || preview.tags == selectedTag
                matchesSearch && matchesTag
            }
        }

    var visibleCount by remember { mutableStateOf(GALLERY_PAGE_SIZE) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var searchContentHeightPx by remember { mutableFloatStateOf(0f) }
    var searchBarOffsetY by remember { mutableFloatStateOf(0f) }

    val topPaddingPx by remember {
        derivedStateOf {
            (searchContentHeightPx + searchBarOffsetY).coerceAtLeast(0f)
        }
    }

    val nestedScrollConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val delta = available.y
                    val newOffset = (searchBarOffsetY + delta).coerceIn(-searchContentHeightPx, 0f)
                    val consumed = newOffset - searchBarOffsetY
                    searchBarOffsetY = newOffset
                    return Offset(0f, consumed)
                }
            }
        }

    LaunchedEffect(filteredArtwork.size) {
        visibleCount = minOf(GALLERY_PAGE_SIZE, filteredArtwork.size)
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisible != null &&
                lastVisible.index >= (visibleCount - 2) &&
                visibleCount < filteredArtwork.size
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            visibleCount = minOf(visibleCount + GALLERY_PAGE_SIZE, filteredArtwork.size)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Content area — statusBarsPadding keeps content out of the status bar region
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .clipToBounds(),
        ) {
            if (filteredArtwork.isEmpty() && searchQuery.isBlank() && selectedTag == null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(top = with(density) { searchContentHeightPx.toDp() }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyMessage.ifBlank { stringResource(R.string.msg_no_artwork) },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                val isExpandedScreen = windowSizeClass == WindowWidthSizeClass.Expanded

                if (isExpandedScreen) {
                    // Use BoxWithConstraints to get actual available space
                    // and determine columns based on aspect ratio
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val aspectRatio = maxWidth / maxHeight
                        // Tablet (landscape-like, wider): aspectRatio > 1.3 → 3 columns
                        // Foldable (portrait-like, taller): aspectRatio <= 1.3 → 2 columns
                        val gridColumns = if (aspectRatio > 1.3f) 3 else 2

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .nestedScroll(nestedScrollConnection),
                            contentPadding =
                                PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    bottom = 88.dp,
                                    top = with(density) { topPaddingPx.toDp() },
                                ),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(filteredArtwork.take(visibleCount)) { preview ->
                                ArtworkCard(
                                    preview = preview,
                                    onImageClick = { onFullscreenImage(preview) },
                                    onRemoveBookmark = onRemoveBookmark,
                                )
                            }
                            if (visibleCount < filteredArtwork.size) {
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
                            }
                        }
                    }
                } else {
                    // Phone: Single column layout
                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .nestedScroll(nestedScrollConnection),
                        contentPadding =
                            PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 88.dp,
                                top = with(density) { topPaddingPx.toDp() },
                            ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(filteredArtwork.take(visibleCount)) { preview ->
                            ArtworkCard(
                                preview = preview,
                                onImageClick = { onFullscreenImage(preview) },
                                onRemoveBookmark = onRemoveBookmark,
                            )
                        }
                        if (visibleCount < filteredArtwork.size) {
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
                        } else if (filteredArtwork.size > GALLERY_PAGE_SIZE) {
                            item {
                                Text(
                                    text = stringResource(R.string.label_end_of_gallery),
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
        }

        // Search bar overlay \u2014 hides on scroll down, appears on scroll up
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .clipToBounds(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationY = searchBarOffsetY }
                        .background(MaterialTheme.colorScheme.background)
                        .onGloballyPositioned { coordinates ->
                            searchContentHeightPx = coordinates.size.height.toFloat()
                        }
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = 4.dp),
            ) {
                BookmarkSearchField(query = searchQuery, onQueryChange = onSearchQueryChange)
                Spacer(Modifier.height(8.dp))
                BookmarkFilterRow(selectedTag = selectedTag, onTagSelected = onTagSelected)
            }
        }

        // Fixed status bar background — keeps system bar opaque when search bar slides up
        Spacer(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.background),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArtworkCard(
    preview: ArtworkPreview,
    onImageClick: () -> Unit,
    onRemoveBookmark: (ArtworkPreview) -> Unit = {},
) {
    val context = LocalContext.current
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
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
                    text = preview.artistName.ifBlank { stringResource(R.string.label_unknown_artist) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Suggested by (matching Today page hero overlay style)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val suggestedName = preview.suggestedByName
                Text(
                    text =
                        if (!suggestedName.isNullOrBlank()) {
                            stringResource(R.string.label_suggested_by, suggestedName)
                        } else {
                            stringResource(R.string.label_suggested_by_anonymous)
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Comment
            if (preview.comment.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.FormatQuote,
                        contentDescription = null,
                        modifier =
                            Modifier.size(16.dp)
                                .rotate(180f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = preview.comment,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(onClick = { showBottomSheet = true }) {
                    Icon(Icons.Default.Info, contentDescription = stringResource(R.string.content_desc_artwork_details))
                }

                FilledTonalIconButton(onClick = { exportArtwork(context, preview) }) {
                    Icon(Icons.Default.Save, contentDescription = stringResource(R.string.content_desc_export_artwork))
                }

                var showRemoveDialog by remember { mutableStateOf(false) }

                FilledTonalIconButton(
                    onClick = { showRemoveDialog = true },
                    colors =
                        IconButtonDefaults.filledTonalIconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                ) {
                    Icon(
                        Icons.Default.BookmarkRemove,
                        contentDescription = stringResource(R.string.content_desc_remove_bookmark),
                    )
                }

                if (showRemoveDialog) {
                    AlertDialog(
                        onDismissRequest = { showRemoveDialog = false },
                        title = { Text(stringResource(R.string.title_remove_bookmark)) },
                        text = {
                            Text(stringResource(R.string.msg_remove_bookmark_confirm))
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    onRemoveBookmark(preview)
                                    showRemoveDialog = false
                                },
                            ) {
                                Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRemoveDialog = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
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
                            contentDescription = stringResource(R.string.content_desc_close),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(stringResource(R.string.hint_search_bookmarks), style = MaterialTheme.typography.bodyMedium)
        },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.content_desc_clear),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun BookmarkFilterRow(
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
) {
    val allLabel = stringResource(R.string.label_all)
    val tags = listOf(null to allLabel, "LC0" to "LC0", "LC YJ" to "LC YJ", "LC ES" to "LC ES")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { (tagValue, label) ->
            FilterChip(
                selected = selectedTag == tagValue,
                onClick = { onTagSelected(tagValue) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}
