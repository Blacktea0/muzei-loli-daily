package me.eroi.lolidaily.muzei.ui.screen.components

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.api.SessionManager
import me.eroi.lolidaily.muzei.model.ArtworkPreview

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArtworkDetailBottomSheet(
    preview: ArtworkPreview,
    sheetState: SheetState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.title_artwork_details),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(16.dp))

            if (preview.date.isNotBlank()) {
                DetailRow(icon = Icons.Default.CalendarToday, label = stringResource(R.string.label_date), value = preview.date)
                Spacer(Modifier.height(12.dp))
            }

            DetailRow(
                icon = Icons.Default.Palette,
                label = stringResource(R.string.label_artist),
                value = preview.artistName.ifBlank { stringResource(R.string.label_unknown) },
            )

            Spacer(Modifier.height(12.dp))

            val suggestedName = preview.suggestedByName
            if (!suggestedName.isNullOrBlank()) {
                DetailRow(
                    icon = Icons.Default.Person,
                    label = stringResource(R.string.label_suggested_by_title),
                    value = suggestedName,
                )
            }

            Spacer(Modifier.height(12.dp))

            if (preview.tags.isNotBlank()) {
                DetailRow(
                    icon = null,
                    label = stringResource(R.string.label_classification),
                    content = {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(preview.tags, style = MaterialTheme.typography.labelMedium)
                            },
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
            }

            if (preview.characterNames.isNotEmpty()) {
                val bgmDomain = remember { SessionManager.loadDomain(context) }
                Text(
                    text = stringResource(R.string.label_characters),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
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
                            }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Text(
                text = stringResource(R.string.label_comment),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (preview.comment.isNotBlank()) {
                Text(
                    text = preview.comment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    text = stringResource(R.string.msg_no_comment_available),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = preview.filename,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (preview.sourceUrl.isNotBlank()) {
                    FilledTonalButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, preview.sourceUrl.toUri()),
                            )
                        },
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.label_source))
                    }
                }
                if (preview.artistUrl.isNotBlank()) {
                    FilledTonalButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, preview.artistUrl.toUri()),
                            )
                        },
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.label_artist))
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(
    icon: ImageVector?,
    label: String,
    value: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        if (content != null) {
            content()
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = value ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
