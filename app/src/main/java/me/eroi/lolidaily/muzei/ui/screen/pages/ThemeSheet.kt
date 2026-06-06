package me.eroi.lolidaily.muzei.ui.screen.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.eroi.lolidaily.muzei.R
import me.eroi.lolidaily.muzei.ui.theme.ColorSource
import me.eroi.lolidaily.muzei.ui.theme.ColorStyle
import me.eroi.lolidaily.muzei.ui.theme.ThemeMode
import me.eroi.lolidaily.muzei.ui.screen.components.ManualColorPickerRow
import me.eroi.lolidaily.muzei.util.M3SchemeGenerator

internal val DEFAULT_SOURCE_COLOR = 0xFFF09199.toInt()

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemeSheet(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    colorSource: ColorSource,
    onColorSourceChanged: (ColorSource) -> Unit,
    colorStyle: ColorStyle,
    onColorStyleChanged: (ColorStyle) -> Unit,
    manualColorArgb: Int,
    onManualColorChanged: (Int) -> Unit,
    sourceColorArgb: Int?,
    artworkAvailable: Boolean,
) {
    val darkPreview =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val previewArgb = sourceColorArgb ?: if (colorSource == ColorSource.MANUAL) manualColorArgb else DEFAULT_SOURCE_COLOR

    SheetTitle(Icons.Filled.Palette, stringResource(R.string.title_theme_colors))

    SettingsSubhead(stringResource(R.string.label_appearance_mode))
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        maxLines = 1
    ) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            ToggleButton(
                checked = themeMode == mode,
                onCheckedChange = { onThemeModeChanged(mode) },
                modifier = Modifier.weight(1f),
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        ThemeMode.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                colors = ToggleButtonDefaults.tonalToggleButtonColors(),
                contentPadding = ButtonDefaults.ExtraSmallContentPadding,
            ) {
                Icon(
                    themeModeIcon(mode),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                Text(
                    themeModeLabel(mode),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    SettingsSubhead(stringResource(R.string.section_color_source))
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        maxLines = 1,
    ) {
        ColorSource.entries.forEachIndexed { index, source ->
            ToggleButton(
                checked = colorSource == source,
                onCheckedChange = { onColorSourceChanged(source) },
                modifier = Modifier.weight(1f),
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        ColorSource.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                colors = ToggleButtonDefaults.tonalToggleButtonColors(),
                contentPadding = ButtonDefaults.ExtraSmallContentPadding,
            ) {
                Icon(
                    colorSourceIcon(source),
                    contentDescription = null,
                    modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                Text(
                    colorSourceLabel(source),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (colorSource == ColorSource.IMAGE && !artworkAvailable) {
        Text(
            text = stringResource(R.string.hint_no_artwork_for_color),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (colorSource == ColorSource.MANUAL) {
        ManualColorPickerRow(
            currentColorArgb = manualColorArgb,
            onColorChanged = onManualColorChanged,
        )
    }

    if (colorSource != ColorSource.DEFAULT) {
        SettingsSubhead(stringResource(R.string.title_color_style_preview))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(colorStyleOptions()) { style ->
                ColorStylePreviewCard(
                    style = style,
                    selected = colorStyle == style,
                    sourceArgb = previewArgb,
                    dark = darkPreview,
                    onClick = { onColorStyleChanged(style) },
                )
            }
        }
    }
}

@Composable
private fun ColorStylePreviewCard(
    style: ColorStyle,
    selected: Boolean,
    sourceArgb: Int,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val scheme = remember(style, sourceArgb, dark) { M3SchemeGenerator.fromSourceColor(sourceArgb, dark, style) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border =
            BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        modifier = Modifier.width(112.dp),
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(68.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(scheme.primary),
                )
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(scheme.secondary),
                )
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(scheme.tertiary),
                )
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(scheme.surface),
                )
            }
            Text(
                text = colorStyleLabel(style),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
internal fun PaletteDots(
    argb: Int,
    style: ColorStyle,
    dark: Boolean,
) {
    val scheme = remember(argb, style, dark) { M3SchemeGenerator.fromSourceColor(argb, dark, style) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEach { color ->
                Box(
                    modifier =
                        Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(color),
                )
            }
        }
    }
}

@Composable
internal fun colorStyleLabel(style: ColorStyle): String {
    return when (style) {
        ColorStyle.TONAL_SPOT -> stringResource(R.string.label_style_tonal_spot)
        ColorStyle.VIBRANT -> stringResource(R.string.label_style_vibrant)
        ColorStyle.CONTENT -> stringResource(R.string.label_style_content)
        ColorStyle.FIDELITY -> stringResource(R.string.label_style_fidelity)
        ColorStyle.EXPRESSIVE -> stringResource(R.string.label_style_expressive)
        ColorStyle.MONOCHROME -> stringResource(R.string.label_style_monochrome)
        ColorStyle.NEUTRAL -> stringResource(R.string.label_style_neutral)
        ColorStyle.RAINBOW -> stringResource(R.string.label_style_rainbow)
        ColorStyle.FRUIT_SALAD -> stringResource(R.string.label_style_fruit_salad)
    }
}

private fun colorStyleOptions(): List<ColorStyle> =
    listOf(
        ColorStyle.TONAL_SPOT,
        ColorStyle.VIBRANT,
        ColorStyle.CONTENT,
        ColorStyle.FIDELITY,
        ColorStyle.EXPRESSIVE,
        ColorStyle.MONOCHROME,
        ColorStyle.RAINBOW,
        ColorStyle.FRUIT_SALAD,
        ColorStyle.NEUTRAL,
    )

@Composable
private fun colorSourceLabel(source: ColorSource): String {
    return when (source) {
        ColorSource.DEFAULT -> stringResource(R.string.label_color_source_default)
        ColorSource.IMAGE -> stringResource(R.string.label_color_source_image)
        ColorSource.MANUAL -> stringResource(R.string.label_color_source_manual)
    }
}

private fun colorSourceIcon(source: ColorSource): ImageVector {
    return when (source) {
        ColorSource.DEFAULT -> Icons.Filled.AutoAwesome
        ColorSource.IMAGE -> Icons.Filled.ImageSearch
        ColorSource.MANUAL -> Icons.Filled.ColorLens
    }
}

@Composable
internal fun themeModeLabel(mode: ThemeMode): String {
    return when (mode) {
        ThemeMode.SYSTEM -> stringResource(R.string.label_theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.label_theme_light)
        ThemeMode.DARK -> stringResource(R.string.label_theme_dark)
    }
}

internal fun themeModeIcon(mode: ThemeMode): ImageVector {
    return when (mode) {
        ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
        ThemeMode.LIGHT -> Icons.Filled.LightMode
        ThemeMode.DARK -> Icons.Filled.DarkMode
    }
}
