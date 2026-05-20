package me.eroi.lolidaily.muzei.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

@Composable
fun rememberAnimatedColorScheme(target: ColorScheme): ColorScheme {
    val animationSpec = tween<Color>(durationMillis = 400)
    val primary by animateColorAsState(target.primary, animationSpec)
    val onPrimary by animateColorAsState(target.onPrimary, animationSpec)
    val primaryContainer by animateColorAsState(target.primaryContainer, animationSpec)
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, animationSpec)
    val secondary by animateColorAsState(target.secondary, animationSpec)
    val onSecondary by animateColorAsState(target.onSecondary, animationSpec)
    val secondaryContainer by animateColorAsState(target.secondaryContainer, animationSpec)
    val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, animationSpec)
    val tertiary by animateColorAsState(target.tertiary, animationSpec)
    val onTertiary by animateColorAsState(target.onTertiary, animationSpec)
    val tertiaryContainer by animateColorAsState(target.tertiaryContainer, animationSpec)
    val onTertiaryContainer by animateColorAsState(target.onTertiaryContainer, animationSpec)
    val error by animateColorAsState(target.error, animationSpec)
    val onError by animateColorAsState(target.onError, animationSpec)
    val errorContainer by animateColorAsState(target.errorContainer, animationSpec)
    val onErrorContainer by animateColorAsState(target.onErrorContainer, animationSpec)
    val background by animateColorAsState(target.background, animationSpec)
    val onBackground by animateColorAsState(target.onBackground, animationSpec)
    val surface by animateColorAsState(target.surface, animationSpec)
    val onSurface by animateColorAsState(target.onSurface, animationSpec)
    val surfaceVariant by animateColorAsState(target.surfaceVariant, animationSpec)
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, animationSpec)
    val surfaceBright by animateColorAsState(target.surfaceBright, animationSpec)
    val surfaceDim by animateColorAsState(target.surfaceDim, animationSpec)
    val surfaceContainerLowest by animateColorAsState(target.surfaceContainerLowest, animationSpec)
    val surfaceContainerLow by animateColorAsState(target.surfaceContainerLow, animationSpec)
    val surfaceContainer by animateColorAsState(target.surfaceContainer, animationSpec)
    val surfaceContainerHigh by animateColorAsState(target.surfaceContainerHigh, animationSpec)
    val surfaceContainerHighest by animateColorAsState(target.surfaceContainerHighest, animationSpec)
    val outline by animateColorAsState(target.outline, animationSpec)
    val outlineVariant by animateColorAsState(target.outlineVariant, animationSpec)
    val inverseSurface by animateColorAsState(target.inverseSurface, animationSpec)
    val inverseOnSurface by animateColorAsState(target.inverseOnSurface, animationSpec)
    val inversePrimary by animateColorAsState(target.inversePrimary, animationSpec)
    val scrim by animateColorAsState(target.scrim, animationSpec)
    val surfaceTint by animateColorAsState(target.surfaceTint, animationSpec)

    return ColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceBright = surfaceBright,
        surfaceDim = surfaceDim,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        outline = outline,
        outlineVariant = outlineVariant,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        inversePrimary = inversePrimary,
        scrim = scrim,
        surfaceTint = surfaceTint,
    )
}
