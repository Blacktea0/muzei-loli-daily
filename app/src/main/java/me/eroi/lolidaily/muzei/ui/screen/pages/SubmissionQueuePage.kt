package me.eroi.lolidaily.muzei.ui.screen.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.db.SUBMISSION_QUEUE_ES
import me.eroi.lolidaily.muzei.db.SUBMISSION_QUEUE_GENERAL
import me.eroi.lolidaily.muzei.db.SubmissionQueueEntity
import me.eroi.lolidaily.muzei.db.SubmissionQueueStore
import me.eroi.lolidaily.muzei.model.DailySubmitStatusResponse
import me.eroi.lolidaily.muzei.ui.screen.components.FullscreenImageViewer

@Composable
fun SubmissionQueuePage(
    ownerUsername: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    windowSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val entries by
        remember(context, ownerUsername) {
            SubmissionQueueStore.observe(context, ownerUsername)
        }.collectAsStateWithLifecycle(initialValue = emptyList())
    val generalEntries = remember(entries) { entries.filter { it.queueGroup == SUBMISSION_QUEUE_GENERAL } }
    val esEntries = remember(entries) { entries.filter { it.queueGroup == SUBMISSION_QUEUE_ES } }
    val generalQueueTitle = stringResource(R.string.queue_section_general)
    val esQueueTitle = stringResource(R.string.queue_section_es)
    val removeErrorMessage = stringResource(R.string.msg_remove_submission_failed)

    var status by remember { mutableStateOf<DailySubmitStatusResponse?>(null) }
    var statusError by remember { mutableStateOf(false) }
    var isStatusLoading by remember { mutableStateOf(true) }
    var statusRefreshKey by remember { mutableIntStateOf(0) }
    var fullscreenEntry by remember { mutableStateOf<SubmissionQueueEntity?>(null) }
    var entryToRemove by remember(ownerUsername) { mutableStateOf<SubmissionQueueEntity?>(null) }
    var isRemoving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(ownerUsername, statusRefreshKey) {
        isStatusLoading = true
        statusError = false
        val result = withContext(Dispatchers.IO) { LoliApiClient.fetchDailyStatus(context) }
        result.fold(
            onSuccess = {
                status = it
                isStatusLoading = false
            },
            onFailure = {
                status = null
                statusError = true
                isStatusLoading = false
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.title_submission_queue)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_desc_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        modifier = modifier,
    ) { padding ->
        LazyVerticalGrid(
            columns =
                GridCells.Fixed(
                    if (windowSizeClass == WindowWidthSizeClass.Expanded) {
                        3
                    } else {
                        1
                    },
                ),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                QueueStatusCard(
                    localCount = entries.size,
                    status = status,
                    isLoading = isStatusLoading,
                    isError = statusError,
                    onRetry = { statusRefreshKey++ },
                )
            }

            queueSection(
                title = generalQueueTitle,
                entries = generalEntries,
                onImageClick = { fullscreenEntry = it },
                onRemove = { entryToRemove = it },
            )
            queueSection(
                title = esQueueTitle,
                entries = esEntries,
                onImageClick = { fullscreenEntry = it },
                onRemove = { entryToRemove = it },
            )
        }
    }

    entryToRemove?.let { entry ->
        AlertDialog(
            onDismissRequest = { if (!isRemoving) entryToRemove = null },
            title = { Text(stringResource(R.string.title_remove_submission)) },
            text = { Text(stringResource(R.string.msg_remove_submission_confirm)) },
            confirmButton = {
                TextButton(
                    enabled = !isRemoving,
                    onClick = {
                        isRemoving = true
                        scope.launch {
                            val result =
                                try {
                                    SubmissionQueueStore.remove(context, entry)
                                } finally {
                                    isRemoving = false
                                }
                            entryToRemove = null
                            if (result.isFailure) {
                                snackbarHostState.showSnackbar(removeErrorMessage)
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(enabled = !isRemoving, onClick = { entryToRemove = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    fullscreenEntry?.let { entry ->
        FullscreenImageViewer(
            model = SubmissionQueueStore.imageFile(context, entry),
            filename = entry.imageFileName,
            onDismiss = { fullscreenEntry = null },
        )
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.queueSection(
    title: String,
    entries: List<SubmissionQueueEntity>,
    onImageClick: (SubmissionQueueEntity) -> Unit,
    onRemove: (SubmissionQueueEntity) -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = entries.size.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (entries.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(R.string.queue_section_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    } else {
        items(entries, key = { it.id }) { entry ->
            SubmissionQueueCard(
                entry = entry,
                onClick = { onImageClick(entry) },
                onRemove = { onRemove(entry) },
            )
        }
    }
}

@Composable
private fun QueueStatusCard(
    localCount: Int,
    status: DailySubmitStatusResponse?,
    isLoading: Boolean,
    isError: Boolean,
    onRetry: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.queue_status_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.queue_local_count, localCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                isLoading -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.queue_status_loading),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                status != null -> {
                    Text(
                        text = stringResource(R.string.queue_server_count, status.queued),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    status.position?.takeIf { it.isNotBlank() }?.let { position ->
                        Text(
                            text = stringResource(R.string.queue_server_position, position),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (status.queued != localCount) {
                        Text(
                            text = stringResource(R.string.queue_local_only_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                isError -> {
                    Text(
                        text = stringResource(R.string.queue_status_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmissionQueueCard(
    entry: SubmissionQueueEntity,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val submittedAt =
        remember(entry.submittedAt) {
            DateFormat
                .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(entry.submittedAt))
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .clickable(onClick = onClick)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(context)
                        .data(SubmissionQueueStore.imageFile(context, entry))
                        .build(),
                contentDescription = stringResource(R.string.queue_image_description, entry.tag),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            if (entry.tag.isNotBlank()) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                ) {
                    Text(
                        text = entry.tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

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
                        text = submittedAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
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
                    text = entry.artistName.ifBlank { stringResource(R.string.label_unknown_artist) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (entry.comment.isNotBlank()) {
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
                        text = entry.comment,
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
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                ToggleButton(
                    checked = false,
                    onCheckedChange = { onRemove() },
                    modifier = Modifier.size(40.dp),
                    shapes = ToggleButtonDefaults.shapes(),
                    colors =
                        ToggleButtonDefaults.toggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.title_remove_submission),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
