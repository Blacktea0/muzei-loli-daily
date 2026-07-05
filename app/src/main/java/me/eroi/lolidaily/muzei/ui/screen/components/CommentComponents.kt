package me.eroi.lolidaily.muzei.ui.screen.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.widthIn
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.api.SessionManager
import me.eroi.lolidaily.muzei.model.BangumiReaction
import me.eroi.lolidaily.muzei.model.BangumiSubReply
import me.eroi.lolidaily.muzei.worker.EmojiMap
import me.eroi.lolidaily.muzei.util.CommentBlock
import me.eroi.lolidaily.muzei.util.BBCodeParser
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.FormatQuote

// ── Comment Header ───────────────────────────────────────────────

@Composable
fun CommentHeader(count: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.label_comments),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (count > 0) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        shape = RoundedCornerShape(50),
                        colors =
                            SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        border = null,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// ── Empty Comments ───────────────────────────────────────────────

@Composable
fun EmptyComments() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Comment,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.msg_no_comments_today),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


// ── Reaction Chips (read-only) ──────────────────────────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReactionChips(
    reactions: List<BangumiReaction>,
    onReactionClick: ((Int) -> Unit)? = null
) {
    val valid = remember(reactions) { reactions.mapNotNull { r -> EmojiMap.emojiResId(r.value)?.let { r to it } } }
    if (valid.isEmpty()) return

    val scope = rememberCoroutineScope()

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((reaction, resId) in valid) {
            val tooltipState = rememberTooltipState(isPersistent = true)
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above
                ),
                tooltip = {
                    PlainTooltip {
                        Text(
                            text = formatUserList(reaction.users.map { it.nickname.takeIf { n -> n.isNotBlank() } ?: it.username }),
                            modifier = Modifier.widthIn(max = 280.dp),
                        )
                    }
                },
                state = tooltipState,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .height(22.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    if (onReactionClick != null) {
                                        onReactionClick(reaction.value)
                                    }
                                },
                                onLongPress = {
                                    if (reaction.users.isNotEmpty()) {
                                        scope.launch { tooltipState.show() }
                                    }
                                }
                            )
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        PixelEmoji(resId = resId, modifier = Modifier.size(16.dp))
                        Text(
                            text = "${reaction.users.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}


// ── Floor Comment Entry (sub-reply rendered as top-level) ────────

@Composable
fun FloorCommentEntry(
    reply: BangumiSubReply,
    isLoggedIn: Boolean = false,
    onReplyClick: (() -> Unit)? = null,
    onReactionClick: (() -> Unit)? = null,
    onReactionChipClick: ((Int) -> Unit)? = null
) {
    if (reply.state != 0) return

    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        UserAvatar(
            avatarUrl = reply.creator?.avatar?.medium,
            nickname = reply.creator?.nickname,
            userId = reply.creator?.id,
            size = 36,
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = reply.creator?.nickname ?: reply.creator?.username ?: stringResource(R.string.label_anonymous),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatTimestamp(reply.createdAt, context),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isLoggedIn && onReplyClick != null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = stringResource(R.string.comment_action_reply),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onReplyClick() }
                        )
                    }
                    if (isLoggedIn && onReactionClick != null) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = stringResource(R.string.content_desc_add_reaction),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onReactionClick() }
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            CommentText(
                rawContent = reply.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(8.dp))

            ReactionChips(
                reactions = reply.reactions,
                onReactionClick = onReactionChipClick
            )
        }
    }
}



// ── User Avatar ──────────────────────────────────────────────────

@Composable
fun UserAvatar(
    avatarUrl: String?,
    nickname: String?,
    userId: Int? = null,
    size: Int,
) {
    val initial = remember(nickname) { nickname?.firstOrNull()?.toString() ?: "?" }
    val gradientColors = remember(userId) { avatarGradient(userId) }

    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(avatarUrl).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier.size(size.dp).background(Brush.linearGradient(gradientColors), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                style = if (size >= 36) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────



@Composable
fun CommentText(
    rawContent: String,
    style: TextStyle,
    color: Color,
    lineHeight: TextUnit = 20.sp,
) {
    val context = LocalContext.current
    val bgmDomain = remember { SessionManager.loadDomain(context) }
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val blocks =
        remember(rawContent, bgmDomain, tertiaryColor) {
            BBCodeParser.parse(rawContent, bgmDomain, tertiaryColor)
        }

    RenderBlocks(blocks, style, color, lineHeight)
}

@Composable
private fun RenderBlocks(
    blocks: List<CommentBlock>,
    style: TextStyle,
    color: Color,
    lineHeight: TextUnit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        for (block in blocks) {
            when (block) {
                is CommentBlock.Text -> {
                    if (block.spoilerRanges.isEmpty()) {
                        Text(
                            text = block.annotatedString,
                            inlineContent = block.inlineContent,
                            style = style.copy(lineHeight = lineHeight),
                            color = color,
                        )
                    } else {
                        var revealedRanges by remember { mutableStateOf(emptySet<IntRange>()) }
                        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                        val displayedText = remember(block.annotatedString, revealedRanges) {
                            buildAnnotatedString {
                                append(block.annotatedString)

                                for (range in block.spoilerRanges) {
                                    if (range in revealedRanges) {
                                        addStyle(
                                            SpanStyle(
                                                background = Color.Gray.copy(alpha = 0.2f),
                                                color = Color.Unspecified
                                            ),
                                            range.first,
                                            range.last + 1
                                        )
                                    } else {
                                        addStyle(
                                            SpanStyle(
                                                background = Color.Gray,
                                                color = Color.Gray
                                            ),
                                            range.first,
                                            range.last + 1
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = displayedText,
                            inlineContent = block.inlineContent,
                            style = style.copy(lineHeight = lineHeight),
                            color = color,
                            onTextLayout = { textLayoutResult = it },
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    textLayoutResult?.let { layoutResult ->
                                        val position = layoutResult.getOffsetForPosition(offset)
                                        val clickedSpoiler = block.spoilerRanges.find { position in it }
                                        if (clickedSpoiler != null) {
                                            revealedRanges = revealedRanges + setOf(clickedSpoiler)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
                is CommentBlock.Image -> {
                    NormalBlockImage(url = block.url)
                }
                is CommentBlock.Quote -> {
                    RenderQuoteBlock(block.blocks, style, lineHeight)
                }
            }
        }
    }
}

@Composable
private fun RenderQuoteBlock(
    blocks: List<CommentBlock>,
    style: TextStyle,
    lineHeight: TextUnit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.FormatQuote,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = 180f },
                tint = colorScheme.onSurfaceVariant,
            )
            Box(modifier = Modifier.weight(1f)) {
                RenderBlocks(
                    blocks = blocks,
                    style = style.copy(
                        fontSize = (style.fontSize.value * 0.95f).sp,
                        fontStyle = FontStyle.Italic
                    ),
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = lineHeight * 0.95f
                )
            }
        }
    }
}

@Composable
private fun NormalBlockImage(url: String) {
    var showFullscreenViewer by remember { mutableStateOf(false) }

    AsyncImage(
        model =
            ImageRequest.Builder(LocalContext.current)
                .data(url)
                .build(),
        contentDescription = null,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { showFullscreenViewer = true },
        contentScale = ContentScale.Fit,
        alignment = Alignment.Center
    )

    if (showFullscreenViewer) {
        FullscreenImageViewer(
            model = url,
            filename = url.substringAfterLast('/'),
            onDismiss = { showFullscreenViewer = false },
        )
    }
}



@Composable
internal fun PixelInlineImage(url: String) {
    val context = LocalContext.current
    val imageBitmap = remember(url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        try {
            val request =
                ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val src = result.image.toBitmap()
                // Strip density to get raw pixel dimensions (same as PixelEmoji's inScaled = false)
                val raw =
                    src.copy(android.graphics.Bitmap.Config.ARGB_8888, false).apply {
                        density = android.graphics.Bitmap.DENSITY_NONE
                    }
                imageBitmap.value = raw.asImageBitmap()
            }
        } catch (_: Exception) {
            // Silently ignore failed loads
        }
    }

    Canvas(modifier = Modifier.size(20.dp)) {
        imageBitmap.value?.let { bmp ->
            drawImage(
                image = bmp,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

@Composable
internal fun NormalInlineImage(url: String) {
    var showFullscreenViewer by remember { mutableStateOf(false) }

    AsyncImage(
        model =
            ImageRequest.Builder(LocalContext.current)
                .data(url)
                .build(),
        contentDescription = null,
        modifier =
            Modifier
                .size(60.dp)
                .clickable { showFullscreenViewer = true },
        contentScale = ContentScale.Fit,
    )

    if (showFullscreenViewer) {
        FullscreenImageViewer(
            model = url,
            filename = url.substringAfterLast('/'),
            onDismiss = { showFullscreenViewer = false },
        )
    }
}

private fun formatTimestamp(
    unixSeconds: Int,
    context: android.content.Context,
): String {
    if (unixSeconds <= 0) return ""
    val now = System.currentTimeMillis() / 1000
    val diff = now - unixSeconds

    return when {
        diff < 60 -> context.getString(R.string.time_just_now)
        diff < 3600 -> context.getString(R.string.time_minutes_ago, (diff / 60).toInt())
        diff < 86400 -> context.getString(R.string.time_hours_ago, (diff / 3600).toInt())
        diff < 86400 * 30 -> context.getString(R.string.time_days_ago, (diff / 86400).toInt())
        else -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = unixSeconds * 1000L }
            val month = cal.get(java.util.Calendar.MONTH) + 1
            val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
            "$month/$day"
        }
    }
}

private val avatarGradients =
    listOf(
        listOf(Color(0xFFC471ED), Color(0xFFF64F59)),
        listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
        listOf(Color(0xFFF093FB), Color(0xFFF5576C)),
        listOf(Color(0xFF43E97B), Color(0xFF38F9D7)),
        listOf(Color(0xFFF7971E), Color(0xFFFFD200)),
        listOf(Color(0xFF4FACFE), Color(0xFF00F2FE)),
        listOf(Color(0xFFA8EDEA), Color(0xFFFED6E3)),
        listOf(Color(0xFFFFDD92), Color(0xFFD1FDFF)),
        listOf(Color(0xFF30CFD0), Color(0xFF5B86E5)),
        listOf(Color(0xFFFF9A9E), Color(0xFFFECFEF)),
        listOf(Color(0xFFA1C4FD), Color(0xFFC2E9FB)),
        listOf(Color(0xFFD4FC79), Color(0xFF96E6A1)),
    )

private fun avatarGradient(userId: Int?): List<Color> {
    val index =
        if (userId != null && userId > 0) {
            userId % avatarGradients.size
        } else {
            0
        }
    return avatarGradients[index]
}



private fun formatUserList(users: List<String>): String {
    if (users.isEmpty()) return ""
    val locale = java.util.Locale.getDefault()
    val separator = if (locale.language == "zh" || locale.language == "ja") "、" else ", "
    return users.joinToString(separator)
}
