package me.eroi.lolidaily.muzei.ui.screen.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SourceStatusCard(
    isSourceActivated: Boolean,
    isMuzeiInstalled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    val icon: ImageVector
    val label: String
    val subLabel: String
    val tint: androidx.compose.ui.graphics.Color

    if (!isMuzeiInstalled) {
        icon = Icons.Default.Info
        label = "Muzei not installed"
        subLabel = "Get it on Play Store"
        tint = colors.onSurfaceVariant
    } else if (isSourceActivated) {
        icon = Icons.Default.Favorite
        label = "Enabled in Muzei"
        subLabel = "Tap to open Muzei"
        tint = colors.primary
    } else {
        icon = Icons.Default.FavoriteBorder
        label = "Not enabled"
        subLabel = "Select this source in Muzei"
        tint = colors.error
    }

    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
