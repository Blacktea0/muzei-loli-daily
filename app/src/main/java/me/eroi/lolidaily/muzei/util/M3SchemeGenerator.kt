package me.eroi.lolidaily.muzei.util

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import me.eroi.lolidaily.muzei.ui.theme.ColorStyle
import me.tatarka.google.material.dynamiccolor.DynamicScheme
import me.tatarka.google.material.hct.Hct
import me.tatarka.google.material.scheme.SchemeContent
import me.tatarka.google.material.scheme.SchemeExpressive
import me.tatarka.google.material.scheme.SchemeFidelity
import me.tatarka.google.material.scheme.SchemeFruitSalad
import me.tatarka.google.material.scheme.SchemeMonochrome
import me.tatarka.google.material.scheme.SchemeNeutral
import me.tatarka.google.material.scheme.SchemeRainbow
import me.tatarka.google.material.scheme.SchemeTonalSpot
import me.tatarka.google.material.scheme.SchemeVibrant

object M3SchemeGenerator {
    fun fromSourceColor(
        sourceArgb: Int,
        darkMode: Boolean,
        style: ColorStyle = ColorStyle.TONAL_SPOT,
    ): androidx.compose.material3.ColorScheme {
        val hct = Hct.fromInt(sourceArgb)
        val scheme = createScheme(hct, darkMode, style)
        return mapToComposeScheme(scheme)
    }

    private fun createScheme(
        hct: Hct,
        darkMode: Boolean,
        style: ColorStyle,
    ): DynamicScheme =
        when (style) {
            ColorStyle.TONAL_SPOT -> SchemeTonalSpot(hct, darkMode, 0.0)
            ColorStyle.VIBRANT -> SchemeVibrant(hct, darkMode, 0.0)
            ColorStyle.CONTENT -> SchemeContent(hct, darkMode, 0.0)
            ColorStyle.FIDELITY -> SchemeFidelity(hct, darkMode, 0.0)
            ColorStyle.EXPRESSIVE -> SchemeExpressive(hct, darkMode, 0.0)
            ColorStyle.MONOCHROME -> SchemeMonochrome(hct, darkMode, 0.0)
            ColorStyle.NEUTRAL -> SchemeNeutral(hct, darkMode, 0.0)
            ColorStyle.RAINBOW -> SchemeRainbow(hct, darkMode, 0.0)
            ColorStyle.FRUIT_SALAD -> SchemeFruitSalad(hct, darkMode, 0.0)
        }

    private fun mapToComposeScheme(scheme: DynamicScheme): androidx.compose.material3.ColorScheme {
        val neutral = scheme.neutralPalette

        return if (scheme.isDark) {
            darkColorScheme(
                primary = Color(scheme.getPrimary()),
                onPrimary = Color(scheme.getOnPrimary()),
                primaryContainer = Color(scheme.getPrimaryContainer()),
                onPrimaryContainer = Color(scheme.getOnPrimaryContainer()),
                secondary = Color(scheme.getSecondary()),
                onSecondary = Color(scheme.getOnSecondary()),
                secondaryContainer = Color(scheme.getSecondaryContainer()),
                onSecondaryContainer = Color(scheme.getOnSecondaryContainer()),
                tertiary = Color(scheme.getTertiary()),
                onTertiary = Color(scheme.getOnTertiary()),
                tertiaryContainer = Color(scheme.getTertiaryContainer()),
                onTertiaryContainer = Color(scheme.getOnTertiaryContainer()),
                error = Color(scheme.getError()),
                onError = Color(scheme.getOnError()),
                errorContainer = Color(scheme.getErrorContainer()),
                onErrorContainer = Color(scheme.getOnErrorContainer()),
                background = Color(scheme.getBackground()),
                onBackground = Color(scheme.getOnBackground()),
                surface = Color(scheme.getSurface()),
                onSurface = Color(scheme.getOnSurface()),
                surfaceVariant = Color(scheme.getSurfaceVariant()),
                onSurfaceVariant = Color(scheme.getOnSurfaceVariant()),
                surfaceContainerLowest = Color(neutral.tone(6)),
                surfaceContainerLow = Color(neutral.tone(10)),
                surfaceContainer = Color(neutral.tone(12)),
                surfaceContainerHigh = Color(neutral.tone(17)),
                surfaceContainerHighest = Color(neutral.tone(22)),
                outline = Color(scheme.getOutline()),
                outlineVariant = Color(scheme.getOutlineVariant()),
                inverseSurface = Color(scheme.getInverseSurface()),
                inverseOnSurface = Color(scheme.getInverseOnSurface()),
                inversePrimary = Color(scheme.getInversePrimary()),
                scrim = Color(scheme.getScrim()),
                surfaceTint = Color(scheme.getSurfaceTint()),
            )
        } else {
            lightColorScheme(
                primary = Color(scheme.getPrimary()),
                onPrimary = Color(scheme.getOnPrimary()),
                primaryContainer = Color(scheme.getPrimaryContainer()),
                onPrimaryContainer = Color(scheme.getOnPrimaryContainer()),
                secondary = Color(scheme.getSecondary()),
                onSecondary = Color(scheme.getOnSecondary()),
                secondaryContainer = Color(scheme.getSecondaryContainer()),
                onSecondaryContainer = Color(scheme.getOnSecondaryContainer()),
                tertiary = Color(scheme.getTertiary()),
                onTertiary = Color(scheme.getOnTertiary()),
                tertiaryContainer = Color(scheme.getTertiaryContainer()),
                onTertiaryContainer = Color(scheme.getOnTertiaryContainer()),
                error = Color(scheme.getError()),
                onError = Color(scheme.getOnError()),
                errorContainer = Color(scheme.getErrorContainer()),
                onErrorContainer = Color(scheme.getOnErrorContainer()),
                background = Color(scheme.getBackground()),
                onBackground = Color(scheme.getOnBackground()),
                surface = Color(scheme.getSurface()),
                onSurface = Color(scheme.getOnSurface()),
                surfaceVariant = Color(scheme.getSurfaceVariant()),
                onSurfaceVariant = Color(scheme.getOnSurfaceVariant()),
                surfaceContainerLowest = Color(neutral.tone(100)),
                surfaceContainerLow = Color(neutral.tone(96)),
                surfaceContainer = Color(neutral.tone(94)),
                surfaceContainerHigh = Color(neutral.tone(92)),
                surfaceContainerHighest = Color(neutral.tone(90)),
                outline = Color(scheme.getOutline()),
                outlineVariant = Color(scheme.getOutlineVariant()),
                inverseSurface = Color(scheme.getInverseSurface()),
                inverseOnSurface = Color(scheme.getInverseOnSurface()),
                inversePrimary = Color(scheme.getInversePrimary()),
                scrim = Color(scheme.getScrim()),
                surfaceTint = Color(scheme.getSurfaceTint()),
            )
        }
    }
}
