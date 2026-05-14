package me.eroi.lolidaily.muzei.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ──────────────────────────────────────────────
//  Light Color Scheme — soft purple palette
//  Primary: #9B72CB — matches Muzei / wallpapers
// ──────────────────────────────────────────────

val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFFF09199),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFEBDCFF),
        onPrimaryContainer = Color(0xFF250058),
        secondary = Color(0xFF625B71),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE8DEF8),
        onSecondaryContainer = Color(0xFF1E192B),
        tertiary = Color(0xFF7D5260),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFD8E4),
        onTertiaryContainer = Color(0xFF31101D),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFEF7FF),
        onBackground = Color(0xFF1D1B20),
        surface = Color(0xFFFEF7FF),
        onSurface = Color(0xFF1D1B20),
        surfaceVariant = Color(0xFFE7E0EC),
        onSurfaceVariant = Color(0xFF49454F),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF7F2FA),
        surfaceContainer = Color(0xFFF3EDF7),
        surfaceContainerHigh = Color(0xFFECE6F0),
        surfaceContainerHighest = Color(0xFFE6E0EB),
        outline = Color(0xFF79747E),
        outlineVariant = Color(0xFFCAC4D0),
        inverseSurface = Color(0xFF322F35),
        inverseOnSurface = Color(0xFFF5EFF7),
        inversePrimary = Color(0xFFCFBDFF),
        scrim = Color(0xFF000000),
        surfaceTint = Color(0xFFF09199),
    )

// ──────────────────────────────────────────────
//  Dark Color Scheme — deeper purple tones
//  Gallery-at-night feel with violet highlights.
// ──────────────────────────────────────────────

val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFF09199),
        onPrimary = Color(0xFF3E1570),
        primaryContainer = Color(0xFF54348B),
        onPrimaryContainer = Color(0xFFEBDCFF),
        secondary = Color(0xFFCBC2DC),
        onSecondary = Color(0xFF332D41),
        secondaryContainer = Color(0xFF4A4458),
        onSecondaryContainer = Color(0xFFE8DEF8),
        tertiary = Color(0xFFEFB8C8),
        onTertiary = Color(0xFF4A2532),
        tertiaryContainer = Color(0xFF633B48),
        onTertiaryContainer = Color(0xFFFFD8E4),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF141218),
        onBackground = Color(0xFFE6E1E5),
        surface = Color(0xFF141218),
        onSurface = Color(0xFFE6E1E5),
        surfaceVariant = Color(0xFF49454F),
        onSurfaceVariant = Color(0xFFCAC4D0),
        surfaceContainerLowest = Color(0xFF0F0D13),
        surfaceContainerLow = Color(0xFF1D1B20),
        surfaceContainer = Color(0xFF211F26),
        surfaceContainerHigh = Color(0xFF2B2930),
        surfaceContainerHighest = Color(0xFF36343B),
        outline = Color(0xFF938F99),
        outlineVariant = Color(0xFF49454F),
        inverseSurface = Color(0xFFE6E1E5),
        inverseOnSurface = Color(0xFF322F35),
        inversePrimary = Color(0xFF6B4DA3),
        scrim = Color(0xFF000000),
        surfaceTint = Color(0xFFF09199),
    )
