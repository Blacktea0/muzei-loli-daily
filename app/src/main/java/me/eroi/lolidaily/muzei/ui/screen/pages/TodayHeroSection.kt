package me.eroi.lolidaily.muzei.ui.screen.pages

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.api.SessionManager
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import me.eroi.lolidaily.muzei.ui.screen.components.*

@Composable
private fun HeroArtworkImage(
    preview: ArtworkPreview,
    onFullscreenImage: (ArtworkPreview) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val aspectRatio =
        remember(preview.filename) {
            try {
                val imageFile = java.io.File(context.filesDir, "artworks/${preview.filename}")
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(imageFile.absolutePath, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    opts.outWidth.toFloat() / opts.outHeight.toFloat()
                } else {
                    1f
                }
            } catch (_: Exception) {
                1f
            }
        }

    Card(
        onClick = { onFullscreenImage(preview) },
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio),
        shape = RoundedCornerShape(0.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(preview.uri).build(),
            contentDescription = preview.artistName.ifBlank { preview.filename },
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroDetailContent(
    preview: ArtworkPreview,
    isLoggedIn: Boolean,
    onReactionClick: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val token = preview.filename.substringBeforeLast('.')
    val colorScheme = MaterialTheme.colorScheme
    var showReactionPicker by remember { mutableStateOf(false) }

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
                        imageVector = Icons.Filled.FormatQuote,
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
        if (preview.reactions.isNotEmpty() || isLoggedIn) {
            SectionLabel(text = stringResource(R.string.section_reactions))
            TabletReactionRow(
                reactions = preview.reactions,
                userEmoji = preview.userEmoji,
                token = token,
                isLoggedIn = isLoggedIn,
                onReactionClick = onReactionClick,
                onAddReaction = { showReactionPicker = true },
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
        val suggestedByColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        val suggestedByDisplay =
            buildAnnotatedString {
                append(preview.suggestedByName ?: stringResource(R.string.label_unknown))
                preview.suggestedByUsername?.let { username ->
                    withStyle(
                        style = SpanStyle(
                            color = suggestedByColor
                        )
                    ) {
                        append(" @$username")
                    }
                }
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
            trailing =
                if (preview.suggestedByUsername == null) {
                    {}
                } else {
                    null
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
    }

    // ── Reaction picker dialog (managed internally) ──
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HeroArtwork(
    preview: ArtworkPreview,
    isLoggedIn: Boolean,
    onFullscreenImage: (ArtworkPreview) -> Unit,
    onReactionClick: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HeroArtworkImage(
            preview = preview,
            onFullscreenImage = onFullscreenImage,
        )
        HeroDetailContent(
            preview = preview,
            isLoggedIn = isLoggedIn,
            onReactionClick = onReactionClick,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}

// ── Section Label (uppercase with letter spacing) ──────────────

@Composable
internal fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

// ── Detail Meta Item (icon + label/value + trailing) ───────────

@Composable
internal fun DetailMetaItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    maxLines: Int = 2,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    DetailMetaItem(
        icon = icon,
        label = label,
        value = AnnotatedString(value),
        maxLines = maxLines,
        onClick = onClick,
        trailing = trailing,
    )
}

@Composable
internal fun DetailMetaItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: AnnotatedString,
    maxLines: Int = 2,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val rowModifier =
        if (onClick != null) {
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 4.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                if (onClick != null) Icons.AutoMirrored.Filled.OpenInNew else Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Artist Title Row (shared between portrait and tablet) ────────

@Composable
internal fun ArtistTitleRow(
    preview: ArtworkPreview,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.Palette,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = preview.artistName.ifBlank { stringResource(R.string.label_unknown_artist) },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
