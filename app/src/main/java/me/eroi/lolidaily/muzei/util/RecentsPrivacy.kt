package me.eroi.lolidaily.muzei.util

import android.app.Activity
import android.os.Build
import android.view.WindowManager

fun applyRecentsPrivacy(
    activity: Activity,
    enabled: Boolean,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        activity.setRecentsScreenshotEnabled(!enabled)
    } else {
        if (enabled) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
