package me.eroi.lolidaily.muzei.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object DebugMode {
    var isEnabled by mutableStateOf(false)
}
