package me.eroi.lolidaily.muzei.ui.screen.pages
import androidx.compose.runtime.Composable

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.model.ArtworkPreview

// ── Bottom Action Bar (fixed at bottom) ────────────────────────

@Composable
fun BottomActionBar(
    preview: ArtworkPreview,
    isLoggedIn: Boolean,
    onReactionClick: (String, Int) -> Unit,
    onBookmarkToggle: (token: String, fileName: String, bookmarked: Boolean) -> Unit,
    onExport: () -> Unit,
    onAddReaction: () -> Unit,
    onSetWallpaper: () -> Unit,
    token: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val msgLoginToReact = stringResource(R.string.msg_login_to_react)
    val hasReacted = preview.userEmoji != null
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        color = colorScheme.surfaceContainerLow,
        modifier =
            modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = colorScheme.outlineVariant,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Primary FAB: Set Wallpaper
            Surface(
                onClick = onSetWallpaper,
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.primaryContainer,
                modifier = Modifier.height(56.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.btn_set_wallpaper),
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Like button
            IconButton(
                onClick = {
                    val emoji = preview.userEmoji
                    if (!isLoggedIn) {
                        Toast
                            .makeText(
                                context,
                                msgLoginToReact,
                                Toast.LENGTH_SHORT,
                            ).show()
                    } else if (emoji != null) {
                        onReactionClick(token, emoji)
                    } else {
                        onAddReaction()
                    }
                },
                modifier = Modifier.size(40.dp),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        contentColor =
                            if (hasReacted) {
                                colorScheme.primary
                            } else {
                                colorScheme.onSurfaceVariant
                            },
                    ),
            ) {
                Icon(
                    if (hasReacted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(if (hasReacted) R.string.content_desc_react_active else R.string.content_desc_react),
                )
            }

            // Bookmark button
            IconButton(
                onClick = {
                    val newState = !preview.isBookmarked
                    onBookmarkToggle(token, preview.filename, newState)
                },
                modifier = Modifier.size(40.dp),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        contentColor =
                            if (preview.isBookmarked) {
                                colorScheme.primary
                            } else {
                                colorScheme.onSurfaceVariant
                            },
                    ),
            ) {
                Icon(
                    if (preview.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = stringResource(if (preview.isBookmarked) R.string.content_desc_bookmark_active else R.string.content_desc_bookmark),
                )
            }

            // Export button
            IconButton(
                onClick = onExport,
                modifier = Modifier.size(40.dp),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        contentColor = colorScheme.onSurfaceVariant,
                    ),
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = stringResource(R.string.content_desc_export_artwork),
                )
            }
        }
    }
}
