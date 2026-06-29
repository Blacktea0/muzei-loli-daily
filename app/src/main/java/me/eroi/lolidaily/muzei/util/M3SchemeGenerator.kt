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
        style: ColorStyle = ColorStyle.NEUTRAL,
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
                primary = Color(scheme.primary),
                onPrimary = Color(scheme.onPrimary),
                primaryContainer = Color(scheme.primaryContainer),
                onPrimaryContainer = Color(scheme.onPrimaryContainer),
                secondary = Color(scheme.secondary),
                onSecondary = Color(scheme.onSecondary),
                secondaryContainer = Color(scheme.secondaryContainer),
                onSecondaryContainer = Color(scheme.onSecondaryContainer),
                tertiary = Color(scheme.tertiary),
                onTertiary = Color(scheme.onTertiary),
                tertiaryContainer = Color(scheme.tertiaryContainer),
                onTertiaryContainer = Color(scheme.onTertiaryContainer),
                error = Color(scheme.error),
                onError = Color(scheme.onError),
                errorContainer = Color(scheme.errorContainer),
                onErrorContainer = Color(scheme.onErrorContainer),
                background = Color(scheme.background),
                onBackground = Color(scheme.onBackground),
                surface = Color(scheme.surface),
                onSurface = Color(scheme.onSurface),
                surfaceVariant = Color(scheme.surfaceVariant),
                onSurfaceVariant = Color(scheme.onSurfaceVariant),
                surfaceContainerLowest = Color(neutral.tone(6)),
                surfaceContainerLow = Color(neutral.tone(10)),
                surfaceContainer = Color(neutral.tone(12)),
                surfaceContainerHigh = Color(neutral.tone(17)),
                surfaceContainerHighest = Color(neutral.tone(22)),
                outline = Color(scheme.outline),
                outlineVariant = Color(scheme.outlineVariant),
                inverseSurface = Color(scheme.inverseSurface),
                inverseOnSurface = Color(scheme.inverseOnSurface),
                inversePrimary = Color(scheme.inversePrimary),
                scrim = Color(scheme.scrim),
                surfaceTint = Color(scheme.surfaceTint),
            )
        } else {
            lightColorScheme(
                primary = Color(scheme.primary),
                onPrimary = Color(scheme.onPrimary),
                primaryContainer = Color(scheme.primaryContainer),
                onPrimaryContainer = Color(scheme.onPrimaryContainer),
                secondary = Color(scheme.secondary),
                onSecondary = Color(scheme.onSecondary),
                secondaryContainer = Color(scheme.secondaryContainer),
                onSecondaryContainer = Color(scheme.onSecondaryContainer),
                tertiary = Color(scheme.tertiary),
                onTertiary = Color(scheme.onTertiary),
                tertiaryContainer = Color(scheme.tertiaryContainer),
                onTertiaryContainer = Color(scheme.onTertiaryContainer),
                error = Color(scheme.error),
                onError = Color(scheme.onError),
                errorContainer = Color(scheme.errorContainer),
                onErrorContainer = Color(scheme.onErrorContainer),
                background = Color(scheme.background),
                onBackground = Color(scheme.onBackground),
                surface = Color(scheme.surface),
                onSurface = Color(scheme.onSurface),
                surfaceVariant = Color(scheme.surfaceVariant),
                onSurfaceVariant = Color(scheme.onSurfaceVariant),
                surfaceContainerLowest = Color(neutral.tone(100)),
                surfaceContainerLow = Color(neutral.tone(96)),
                surfaceContainer = Color(neutral.tone(94)),
                surfaceContainerHigh = Color(neutral.tone(92)),
                surfaceContainerHighest = Color(neutral.tone(90)),
                outline = Color(scheme.outline),
                outlineVariant = Color(scheme.outlineVariant),
                inverseSurface = Color(scheme.inverseSurface),
                inverseOnSurface = Color(scheme.inverseOnSurface),
                inversePrimary = Color(scheme.inversePrimary),
                scrim = Color(scheme.scrim),
                surfaceTint = Color(scheme.surfaceTint),
            )
        }
    }
}
