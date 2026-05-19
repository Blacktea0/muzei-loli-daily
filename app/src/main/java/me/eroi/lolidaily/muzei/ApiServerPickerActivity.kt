package me.eroi.lolidaily.muzei

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import me.eroi.lolidaily.muzei.ui.screen.BangumiApiServerPickerScreen
import me.eroi.lolidaily.muzei.ui.screen.LoliApiServerPickerScreen
import me.eroi.lolidaily.muzei.ui.theme.ColorSource
import me.eroi.lolidaily.muzei.ui.theme.ColorStyle
import me.eroi.lolidaily.muzei.ui.theme.LoliDailyTheme
import me.eroi.lolidaily.muzei.ui.theme.ThemeMode

class ApiServerPickerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isBangumi = intent.getBooleanExtra(EXTRA_IS_BANGUMI, false)

        val prefs = getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, MODE_PRIVATE)

        val themeMode =
            try {
                ThemeMode.valueOf(prefs.getString("theme_mode", null) ?: ThemeMode.SYSTEM.name)
            } catch (_: IllegalArgumentException) {
                ThemeMode.SYSTEM
            }

        val colorSource =
            try {
                ColorSource.valueOf(prefs.getString("color_source", null) ?: ColorSource.DEFAULT.name)
            } catch (_: IllegalArgumentException) {
                ColorSource.DEFAULT
            }

        val colorStyle =
            try {
                ColorStyle.valueOf(prefs.getString("color_style", null) ?: ColorStyle.TONAL_SPOT.name)
            } catch (_: IllegalArgumentException) {
                ColorStyle.TONAL_SPOT
            }

        val manualColorArgb = prefs.getInt("manual_color", 0xFFF09199.toInt())

        val extractedArgb = prefs.getInt("extracted_color", 0)

        val sourceArgb =
            when (colorSource) {
                ColorSource.IMAGE -> if (extractedArgb != 0) extractedArgb else null
                ColorSource.MANUAL -> manualColorArgb
                ColorSource.DEFAULT -> null
            }

        setContent {
            LoliDailyTheme(
                themeMode = themeMode,
                colorSource = colorSource,
                sourceArgb = sourceArgb,
                colorStyle = colorStyle,
            ) {
                if (isBangumi) {
                    BangumiApiServerPickerScreen(onBack = { finish() })
                } else {
                    LoliApiServerPickerScreen(onBack = { finish() })
                }
            }
        }
    }

    companion object {
        const val EXTRA_IS_BANGUMI = "is_bangumi"
    }
}
