package me.eroi.lolidaily.muzei

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import me.eroi.lolidaily.muzei.ui.screen.DebugSettingsScreen
import me.eroi.lolidaily.muzei.ui.theme.LoliDailyTheme

class DebugSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LoliDailyTheme {
                DebugSettingsScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}
