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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import me.eroi.lolidaily.muzei.R
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
    onAddReaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val valid = reactions.mapNotNull { r -> EmojiMap.emojiResId(r.emojiValue)?.let { r to it } }

    val context = LocalContext.current
    var activeTooltipIndex by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        val rows = valid.chunked(4)
        for ((rowIndex, row) in rows.withIndex()) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for ((itemIndex, reactionResId) in row.withIndex()) {
                    val (reaction, resId) = reactionResId
                    val selected = reaction.emojiValue == userEmoji
                    val isActive = activeTooltipIndex == Pair(rowIndex, itemIndex)

                    val bg =
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    val contentColor =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }

                    Box {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = bg,
                            modifier =
                                Modifier
                                    .height(26.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                if (isLoggedIn) {
                                                    onReactionClick(token, reaction.emojiValue)
                                                } else {
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            context.getString(R.string.msg_login_to_react),
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                }
                                            },
                                            onLongPress = {
                                                if (reaction.users.isNotEmpty()) {
                                                    activeTooltipIndex = Pair(rowIndex, itemIndex)
                                                }
                                            },
                                        )
                                    },
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
                                    color = MaterialTheme.colorScheme.inverseSurface,
                                    tonalElevation = 6.dp,
                                ) {
                                    Text(
                                        text = formatUserList(reaction.users),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.inverseOnSurface,
                                    )
                                }
                            }
                        }
                    }
                }

                if (rowIndex == rows.lastIndex) {
                    AddReactionButton(
                        isLoggedIn = isLoggedIn,
                        onAddReaction = onAddReaction,
                    )
                }
            }
        }

        if (valid.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                AddReactionButton(
                    isLoggedIn = isLoggedIn,
                    onAddReaction = onAddReaction,
                )
            }
        }
    }
}

@Composable
private fun AddReactionButton(
    isLoggedIn: Boolean,
    onAddReaction: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        onClick = {
            if (isLoggedIn) {
                onAddReaction()
            } else {
                Toast
                    .makeText(
                        context,
                        context.getString(R.string.msg_login_to_react),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        },
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.height(26.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.content_desc_add_reaction),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatUserList(users: List<String>): String {
    if (users.isEmpty()) return ""
    val locale = java.util.Locale.getDefault()
    val separator = if (locale.language == "zh" || locale.language == "ja") "、" else ", "
    return users.joinToString(separator)
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
                    text = stringResource(R.string.reaction_title),
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
                    text = stringResource(R.string.label_tap_emoji_react),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
