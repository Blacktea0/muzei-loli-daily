package me.eroi.lolidaily.muzei.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Loli Daily Material Design 3 theme.
 *
 * Supports [dynamicColor] (Material You) on Android 12+ (API 31).
 * Falls back to hand-crafted warm amber-brown palettes on older devices or
 * when dynamic color is disabled.
 *
 * @param darkTheme  Whether to use the dark variant. Defaults to the
 *                   system-wide dark mode setting.
 * @param dynamicColor  When `true` and running on API 31+, use the
 *                      wallpaper-based dynamic color scheme. Falls back
 *                      to custom palettes otherwise.
 * @param content  The composable content tree to theme.
 */
@Composable
fun LoliDailyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
