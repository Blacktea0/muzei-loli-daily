package me.eroi.lolidaily.muzei.ui.screen.pages

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.api.SessionManager
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import me.eroi.lolidaily.muzei.model.BangumiSubReply
import me.eroi.lolidaily.muzei.model.ReactionCount
import me.eroi.lolidaily.muzei.ui.screen.components.*
import me.eroi.lolidaily.muzei.worker.EmojiMap
import kotlinx.coroutines.launch

// ── Tablet Reaction Row (horizontal capsule chips) ──────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun TabletReactionRow(
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
    val msgLoginToReact = stringResource(R.string.msg_login_to_react)
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((index, reactionResId) in valid.withIndex()) {
            val (reaction, resId) = reactionResId
            val selected = reaction.emojiValue == userEmoji

            val tooltipState = rememberTooltipState(isPersistent = true)

            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above
                ),
                tooltip = {
                    PlainTooltip {
                        Text(
                            text = formatUserList(reaction.users),
                            modifier = Modifier.widthIn(max = 280.dp),
                        )
                    }
                },
                state = tooltipState,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color =
                        if (selected) {
                            colorScheme.primaryContainer
                        } else {
                            colorScheme.surfaceContainerHigh
                        },
                    modifier =
                        Modifier
                            .height(32.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        if (isLoggedIn) {
                                            onReactionClick(token, reaction.emojiValue)
                                        } else {
                                            Toast
                                                .makeText(
                                                    context,
                                                    msgLoginToReact,
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                        }
                                    },
                                    onLongPress = {
                                        if (reaction.users.isNotEmpty()) {
                                            scope.launch { tooltipState.show() }
                                        }
                                    },
                                )
                            },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PixelEmoji(resId = resId, modifier = Modifier.size(20.dp))
                        Text(
                            text = "${reaction.count}",
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (selected) {
                                    colorScheme.onPrimaryContainer
                                } else {
                                    colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        }
        // Add reaction button
        Surface(
            onClick = {
                if (isLoggedIn) {
                    onAddReaction()
                } else {
                    Toast
                        .makeText(
                            context,
                            msgLoginToReact,
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            },
            shape = RoundedCornerShape(50),
            color = colorScheme.surfaceContainerHigh,
            modifier = Modifier.height(32.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.content_desc_add_reaction),
                    modifier = Modifier.size(18.dp),
                    tint = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Tablet Detail Content ──────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TabletDetailContent(
    preview: ArtworkPreview,
    isLoggedIn: Boolean,
    onReactionClick: (String, Int) -> Unit,
    onAddReaction: () -> Unit,
    discussionId: String?,
    commentsToShow: List<BangumiSubReply>,
    onCommentPlaceholderClick: (String) -> Unit,
    onCommentReactionClick: ((Int) -> Unit)? = null,
    onCommentReactionChipClick: ((Int, Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val token = preview.filename.substringBeforeLast('.')
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Header Section ──
        // Badge chip (tag name)
        if (preview.tags.isNotBlank()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = colorScheme.primaryContainer,
                ) {
                    Text(
                        text = preview.tags,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // Title: artist name with palette icon
        ArtistTitleRow(preview = preview)

        // Comment (moved above reactions)
        if (preview.comment.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = 180f },
                        tint = colorScheme.onSurfaceVariant,
                    )
                    Column {
                        Text(
                            text =
                                preview.suggestedByName?.let {
                                    stringResource(R.string.label_uploader_comment, it)
                                } ?: stringResource(R.string.label_anonymous_comment),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = preview.comment,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // ── Reactions Section ──
        if (preview.reactions.isNotEmpty()) {
            SectionLabel(text = stringResource(R.string.section_reactions))
            TabletReactionRow(
                reactions = preview.reactions,
                userEmoji = preview.userEmoji,
                token = token,
                isLoggedIn = isLoggedIn,
                onReactionClick = onReactionClick,
                onAddReaction = onAddReaction,
            )
        }

        HorizontalDivider(color = colorScheme.outlineVariant)

        // ── Details Section ──
        SectionLabel(text = stringResource(R.string.section_details))

        // Artist (clickable to open artist URL)
        DetailMetaItem(
            icon = Icons.Default.Palette,
            label = stringResource(R.string.label_artist),
            value = preview.artistName.ifBlank { stringResource(R.string.label_unknown) },
            onClick =
                if (preview.artistUrl.isNotBlank()) {
                    {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, preview.artistUrl.toUri()),
                        )
                    }
                } else {
                    null
                },
        )

        // Source URL
        if (preview.sourceUrl.isNotBlank()) {
            DetailMetaItem(
                icon = Icons.Default.Image,
                label = stringResource(R.string.label_source),
                value = preview.sourceUrl,
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, preview.sourceUrl.toUri()),
                    )
                },
            )
        }

        // Uploader (placed last)
        val suggestedByDisplay =
            buildString {
                append(preview.suggestedByName ?: stringResource(R.string.label_anonymous))
                preview.suggestedByUsername?.let { append(" @$it") }
            }
        DetailMetaItem(
            icon = Icons.Default.Person,
            label = stringResource(R.string.label_suggested_by_title),
            value = suggestedByDisplay,
            onClick =
                preview.suggestedByUsername?.let { username ->
                    {
                        val bgmDomain = SessionManager.loadDomain(context)
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, "https://$bgmDomain/user/$username".toUri()),
                        )
                    }
                },
        )

        // ── Characters Section ──
        if (preview.characterNames.isNotEmpty()) {
            HorizontalDivider(color = colorScheme.outlineVariant)
            val bgmDomain = remember { SessionManager.loadDomain(context) }
            SectionLabel(text = stringResource(R.string.label_characters))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                preview.characterNames.forEachIndexed { index, name ->
                    val characterId = preview.characterIds.getOrNull(index)
                    CharacterChip(
                        defaultName = name,
                        characterId = characterId,
                        onClick = {
                            if (characterId != null) {
                                val url = "https://$bgmDomain/character/$characterId"
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, url.toUri()),
                                )
                            }
                        },
                        modifier = Modifier.height(32.dp),
                    )
                }
            }
        }


        // ── Comments Section ──
        if (discussionId != null) {
            val commentCount = commentsToShow.count { it.state == 0 }
            CommentHeader(count = commentCount)

            CommentInputPlaceholder(
                isLoggedIn = isLoggedIn,
                onClick = { onCommentPlaceholderClick("") }
            )

            if (commentsToShow.isEmpty()) {
                // Empty state with icon
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Comment,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.msg_no_comments_today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                commentsToShow.forEach { reply ->
                    FloorCommentEntry(
                        reply = reply,
                        isLoggedIn = isLoggedIn,
                        onReplyClick = {
                            val nickname = reply.creator?.nickname ?: reply.creator?.username ?: "Loli"
                            val quoteContent = cleanCommentForQuote(reply.content)
                            val initialText = "[quote][b]$nickname[/b] 说: $quoteContent[/quote]\n"
                            onCommentPlaceholderClick(initialText)
                        },
                        onReactionClick = {
                            onCommentReactionClick?.invoke(reply.id)
                        },
                        onReactionChipClick = { emojiValue ->
                            onCommentReactionChipClick?.invoke(reply.id, emojiValue)
                        }
                    )
                }
            }
            if (commentsToShow.isNotEmpty()) {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

private fun formatUserList(users: List<String>): String {
    if (users.isEmpty()) return ""
    val locale = java.util.Locale.getDefault()
    val separator = if (locale.language == "zh" || locale.language == "ja") "、" else ", "
    return users.joinToString(separator)
}
