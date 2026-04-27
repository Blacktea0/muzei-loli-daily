package me.eroi.lolidaily.muzei.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ──────────────────────────────────────────────
//  Light Color Scheme — warm amber-brown
//  Evokes art gallery, canvas, and creative warmth.
// ──────────────────────────────────────────────

val LightColorScheme = lightColorScheme(
    // Primary: warm terracotta-brown
    primary = Color(0xFF8B5E3C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF2D1600),

    // Secondary: muted olive-sage
    secondary = Color(0xFF6B7B5A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1FFD7),
    onSecondaryContainer = Color(0xFF172200),

    // Tertiary: subdued teal accent
    tertiary = Color(0xFF4A7C7C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCFFFF),
    onTertiaryContainer = Color(0xFF002424),

    // Error
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    // Background / Surface
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFF2DFD1),
    onSurfaceVariant = Color(0xFF51443A),

    // Outlines
    outline = Color(0xFF84746A),
    outlineVariant = Color(0xFFD5C3B5),

    // Inverse
    inverseSurface = Color(0xFF352F2A),
    inverseOnSurface = Color(0xFFFBEEE4),
    inversePrimary = Color(0xFFFFB87A),

    // Scrim / Surface Tint
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF8B5E3C),
)

// ──────────────────────────────────────────────
//  Dark Color Scheme — deeper, richer tones
//  Gallery-at-night feel with warm highlights.
// ──────────────────────────────────────────────

val DarkColorScheme = darkColorScheme(
    // Primary: warm amber glow
    primary = Color(0xFFFFB87A),
    onPrimary = Color(0xFF4E2D00),
    primaryContainer = Color(0xFF6E4420),
    onPrimaryContainer = Color(0xFFFFDCC2),

    // Secondary: pale sage
    secondary = Color(0xFFD5E3BE),
    onSecondary = Color(0xFF383E2A),
    secondaryContainer = Color(0xFF4E5640),
    onSecondaryContainer = Color(0xFFF1FFD7),

    // Tertiary: pale teal
    tertiary = Color(0xFFB0ECEC),
    onTertiary = Color(0xFF003B3B),
    tertiaryContainer = Color(0xFF326464),
    onTertiaryContainer = Color(0xFFCCFFFF),

    // Error
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    // Background / Surface
    background = Color(0xFF1F1B16),
    onBackground = Color(0xFFEAE1D9),
    surface = Color(0xFF1F1B16),
    onSurface = Color(0xFFEAE1D9),
    surfaceVariant = Color(0xFF51443A),
    onSurfaceVariant = Color(0xFFD5C3B5),

    // Outlines
    outline = Color(0xFF9E8E80),
    outlineVariant = Color(0xFF51443A),

    // Inverse
    inverseSurface = Color(0xFFEAE1D9),
    inverseOnSurface = Color(0xFF352F2A),
    inversePrimary = Color(0xFF8B5E3C),

    // Scrim / Surface Tint
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFFFFB87A),
)
