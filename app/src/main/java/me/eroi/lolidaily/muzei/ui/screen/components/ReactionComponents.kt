package me.eroi.lolidaily.muzei.ui.screen.components

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.model.ReactionCount
import me.eroi.lolidaily.muzei.worker.EmojiMap
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun rememberPixelBitmap(resId: Int): ImageBitmap {
    val resources = LocalResources.current
    return remember(resId) {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = BitmapFactory.decodeResource(resources, resId, opts)
        bitmap.asImageBitmap()
    }
}

@Composable
fun PixelEmoji(
    resId: Int,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = rememberPixelBitmap(resId)
    Canvas(modifier = modifier) {
        drawImage(
            image = imageBitmap,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.None,
        )
    }
}

@Composable
fun ReactionPickerDialog(
    onDismiss: () -> Unit,
    onEmojiSelected: (Int) -> Unit,
    emojis: List<Int> = listOf(0, 104, 54, 140, 122, 90, 88, 80),
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.reaction_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp),
                )


                for (row in emojis.chunked(4)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        row.forEach { value ->
                            val resId = EmojiMap.emojiResId(value) ?: return@forEach
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier =
                                    Modifier.clickable { onEmojiSelected(value) }.padding(8.dp),
                            ) {
                                PixelEmoji(resId = resId, modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.label_tap_emoji_react),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
