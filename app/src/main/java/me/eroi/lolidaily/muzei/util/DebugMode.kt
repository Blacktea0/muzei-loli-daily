package me.eroi.lolidaily.muzei.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import me.eroi.lolidaily.muzei.LoliDailyArtWorker

object DebugMode {
    private lateinit var prefs: SharedPreferences

    var isEnabled by mutableStateOf(false)
        private set

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean("debug_mode_enabled_crashed", false)) {
            prefs.edit {
                putBoolean("debug_mode_enabled", true)
                remove("debug_mode_enabled_crashed")
            }
        }
        isEnabled = prefs.getBoolean("debug_mode_enabled", false)
    }

    fun setDebugMode(enabled: Boolean) {
        isEnabled = enabled
        prefs.edit { putBoolean("debug_mode_enabled", enabled) }
    }
}
