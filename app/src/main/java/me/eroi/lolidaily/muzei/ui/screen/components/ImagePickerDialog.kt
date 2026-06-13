package me.eroi.lolidaily.muzei.ui.screen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.api.link.SourceImageVariant

/**
 * Bottom sheet that displays a grid of image thumbnails from a multi-image source,
 * allowing the user to pick one image to submit.
 *
 * Thumbnails are downloaded via [LoliApiClient.downloadImage] which handles
 * platform-specific headers (e.g. pixiv's Referer requirement).
 *
 * @param variants The list of image variants (thumbnail + full-quality URL pairs).
 * @param onImageSelected Called with the selected [SourceImageVariant] when the user taps one.
 * @param onDismiss Called when the user dismisses the sheet without selecting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePickerDialog(
    variants: List<SourceImageVariant>,
    onImageSelected: (SourceImageVariant) -> Unit,
    onDismiss: () -> Unit,
) {
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    // Download thumbnails in background; index-aligned with [variants]
    val thumbBytes = remember { mutableStateListOf<Pair<ByteArray, String>?>() }
    LaunchedEffect(variants) {
        thumbBytes.clear()
        thumbBytes.addAll(List(variants.size) { null })
        variants.forEachIndexed { index, variant ->
            val result = withContext(Dispatchers.IO) {
                try { LoliApiClient.downloadSourceImage(context, variant.thumbUrl) } catch (_: Exception) { null }
            }
            if (index < thumbBytes.size) thumbBytes[index] = result
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.submit_image_picker_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = pluralStringResource(R.plurals.submit_image_picker_subtitle, variants.size, variants.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            val columns = if (variants.size <= 2) variants.size else 3
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                itemsIndexed(variants) { index, variant ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable { onImageSelected(variant) },
                        contentAlignment = Alignment.Center,
                    ) {
                        val bytes = thumbBytes.getOrNull(index)
                        if (bytes != null) {
                            AsyncImage(
                                model = bytes.first,
                                contentDescription = stringResource(
                                    R.string.submit_image_picker_item_desc,
                                    index + 1,
                                ),
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}
