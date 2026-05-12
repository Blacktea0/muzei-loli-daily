package me.eroi.lolidaily.muzei.ui.screen.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import me.eroi.lolidaily.muzei.model.BangumiReply
import me.eroi.lolidaily.muzei.model.BangumiSubReply

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
                    text = "Comments",
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
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No comments yet today",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Comments Loading ─────────────────────────────────────────────

@Composable
fun CommentsLoading() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

// ── Comments Error ───────────────────────────────────────────────

@Composable
fun CommentsError(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

// ── Comment Entry ────────────────────────────────────────────────

@Composable
fun CommentEntry(reply: BangumiReply) {
    if (reply.state != 0) return

    val totalReactions =
        remember(reply) {
            reply.reactions.sumOf { it.users.size }
        }

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
            // Top row: nickname + time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = reply.creator?.nickname ?: reply.creator?.username ?: "Anonymous",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatTimestamp(reply.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))

            // Comment text with @mention highlighting
            Text(
                text = highlightContent(stripBbCode(reply.content)),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(8.dp))

            // Actions row
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (totalReactions > 0) {
                        Text(
                            text = totalReactions.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Reply",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Sub-reply thread
            if (reply.replies.isNotEmpty()) {
                ReplyThread(replies = reply.replies)
            }
        }
    }
}

// ── Floor Comment Entry (sub-reply rendered as top-level) ────────

@Composable
fun FloorCommentEntry(reply: BangumiSubReply) {
    if (reply.state != 0) return

    val totalReactions =
        remember(reply) {
            reply.reactions.sumOf { it.users.size }
        }

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
                    text = reply.creator?.nickname ?: reply.creator?.username ?: "Anonymous",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatTimestamp(reply.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = highlightContent(stripBbCode(reply.content)),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (totalReactions > 0) {
                        Text(
                            text = totalReactions.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Reply",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Reply Thread ─────────────────────────────────────────────────

@Composable
private fun ReplyThread(replies: List<BangumiSubReply>) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val visibleReplies = remember(replies) { replies.filter { it.state == 0 } }

    if (visibleReplies.isEmpty()) return

    Column(modifier = Modifier.padding(start = 46.dp, top = 10.dp)) {
        Row {
            // Left border line
            Canvas(modifier = Modifier.width(2.dp).fillMaxHeight()) {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(0f, this.size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                visibleReplies.forEach { subReply ->
                    SubReplyItem(reply = subReply)
                }
            }
        }
    }
}

// ── Sub-Reply Item ───────────────────────────────────────────────

@Composable
private fun SubReplyItem(reply: BangumiSubReply) {
    val totalReactions =
        remember(reply) {
            reply.reactions.sumOf { it.users.size }
        }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UserAvatar(
            avatarUrl = reply.creator?.avatar?.small,
            nickname = reply.creator?.nickname,
            userId = reply.creator?.id,
            size = 28,
        )

        Column {
            Text(
                text = reply.creator?.nickname ?: reply.creator?.username ?: "Anonymous",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = highlightContent(stripBbCode(reply.content)),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTimestamp(reply.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (totalReactions > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(
                            Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = totalReactions.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
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

private val BBCodeRegex =
    Regex(
        """\[/?(?:url|img|quote|b|i|u|code|color|size|right|center|left)(?:=[^\]]*)?\]""",
        RegexOption.IGNORE_CASE,
    )

private val ImgTagRegex = Regex("""\[img\].*?\[/img\]""", RegexOption.IGNORE_CASE)
private val QuoteRegex = Regex("""\[quote\](.*?)\[/quote\]""", RegexOption.DOT_MATCHES_ALL)
private val MentionRegex = Regex("""@[\w一-鿿]+""")

private fun stripBbCode(input: String): String {
    return input
        .replace(ImgTagRegex, "[image]")
        .replace(QuoteRegex, "「$1」")
        .replace(BBCodeRegex, "")
        .trim()
}

@Composable
private fun highlightContent(text: String): AnnotatedString {
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    return buildAnnotatedString {
        var lastIndex = 0
        for (match in MentionRegex.findAll(text)) {
            append(text.substring(lastIndex, match.range.first))
            withStyle(SpanStyle(color = tertiaryColor, fontWeight = FontWeight.Medium)) {
                append(match.value)
            }
            lastIndex = match.range.last + 1
        }
        append(text.substring(lastIndex))
    }
}

private fun formatTimestamp(unixSeconds: Int): String {
    if (unixSeconds <= 0) return ""
    val now = System.currentTimeMillis() / 1000
    val diff = now - unixSeconds

    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        diff < 86400 * 30 -> "${diff / 86400}d ago"
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
        listOf(Color(0xFFFDD92), Color(0xFFD1FDFF)),
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
