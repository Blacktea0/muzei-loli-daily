package me.eroi.lolidaily.muzei

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import me.eroi.lolidaily.muzei.ui.screen.DebugSettingsScreen
import me.eroi.lolidaily.muzei.ui.screen.KEY_HIDE_RECENTS_CONTENT
import me.eroi.lolidaily.muzei.ui.theme.ColorSource
import me.eroi.lolidaily.muzei.ui.theme.ColorStyle
import me.eroi.lolidaily.muzei.ui.theme.LoliDailyTheme
import me.eroi.lolidaily.muzei.ui.theme.ThemeMode
import me.eroi.lolidaily.muzei.util.applyRecentsPrivacy

class DebugSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, MODE_PRIVATE)
        applyRecentsPrivacy(this, prefs.getBoolean(KEY_HIDE_RECENTS_CONTENT, false))

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
                DebugSettingsScreen(onBack = { finish() })
            }
        }
    }
}
