package me.eroi.lolidaily.muzei.ui.screen.components

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import me.eroi.lolidaily.muzei.model.ReactionCount
import me.eroi.lolidaily.muzei.worker.EmojiMap
import kotlin.math.roundToInt

@Composable
fun rememberPixelBitmap(resId: Int): ImageBitmap {
    val context = LocalContext.current
    return remember(resId) {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = BitmapFactory.decodeResource(context.resources, resId, opts)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionRow(
    reactions: List<ReactionCount>,
    userEmoji: Int?,
    token: String,
    isLoggedIn: Boolean,
    onReactionClick: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val valid = reactions.mapNotNull { r -> EmojiMap.emojiResId(r.emojiValue)?.let { r to it } }
    if (valid.isEmpty()) return

    val context = LocalContext.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (row in valid.chunked(4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for ((reaction, resId) in row) {
                    val selected = reaction.emojiValue == userEmoji

                    val bg =
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                        }
                    val contentColor =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }

                    Surface(
                        onClick = {
                            if (isLoggedIn) {
                                onReactionClick(token, reaction.emojiValue)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Login to Bangumi to react",
                                    Toast.LENGTH_SHORT,
                                )
                                    .show()
                            }
                        },
                        shape = RoundedCornerShape(50),
                        color = bg,
                        modifier = Modifier.height(26.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PixelEmoji(resId = resId, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "${reaction.count}",
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReactionPickerDialog(
    onDismiss: () -> Unit,
    onEmojiSelected: (Int) -> Unit,
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
                    text = "Reactions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                val emojis = listOf(0, 104, 54, 140, 122, 90, 88, 80)

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
                    text = "Tap an emoji to react",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
