package me.eroi.lolidaily.muzei

import android.app.Application
import me.eroi.lolidaily.muzei.util.DebugMode

class LoliDailyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugMode.initialize(this)
        me.eroi.lolidaily.muzei.util.Log.initialize(this)
    }
}
