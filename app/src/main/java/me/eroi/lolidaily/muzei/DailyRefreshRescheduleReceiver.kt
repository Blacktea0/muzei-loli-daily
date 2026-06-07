package me.eroi.lolidaily.muzei

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import me.eroi.lolidaily.muzei.worker.WorkScheduler

class DailyRefreshRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                Log.d(TAG, "Rescheduling daily refresh after ${intent.action}")
                WorkScheduler.ensureDailyRefreshScheduled(context)
            }
            else -> Unit
        }
    }

    companion object {
        private const val TAG = "DailyRefreshReschedule"
    }
}
