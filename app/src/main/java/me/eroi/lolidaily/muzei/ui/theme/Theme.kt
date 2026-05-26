package me.eroi.lolidaily.muzei.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import me.eroi.lolidaily.muzei.util.M3SchemeGenerator

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

@Composable
fun LoliDailyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    colorSource: ColorSource = ColorSource.DEFAULT,
    sourceArgb: Int? = null,
    colorStyle: ColorStyle = ColorStyle.NEUTRAL,
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

    val defaultSourceArgb = 0xFFF09199.toInt()

    val colorScheme =
        when (colorSource) {
            ColorSource.IMAGE, ColorSource.MANUAL -> {
                val argb = sourceArgb
                if (argb != null) {
                    M3SchemeGenerator.fromSourceColor(argb, darkTheme, colorStyle)
                } else {
                    M3SchemeGenerator.fromSourceColor(defaultSourceArgb, darkTheme, ColorStyle.NEUTRAL)
                }
            }
            ColorSource.DEFAULT -> {
                M3SchemeGenerator.fromSourceColor(defaultSourceArgb, darkTheme, ColorStyle.NEUTRAL)
            }
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    val animatedScheme = rememberAnimatedColorScheme(colorScheme)
    MaterialTheme(colorScheme = animatedScheme, typography = Typography, content = content)
}
