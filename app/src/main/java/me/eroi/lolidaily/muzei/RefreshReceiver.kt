package me.eroi.lolidaily.muzei

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives force-refresh requests from Muzei's command action or other in-app triggers. Enqueues
 * the Worker with bypass.
 */
class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        Log.d(TAG, "Force refresh triggered via broadcast")
        LoliDailyArtWorker.enqueueLoad(context, forceRefresh = true)
    }

    companion object {
        private const val TAG = "RefreshReceiver"
        const val ACTION_FORCE_REFRESH = "me.eroi.lolidaily.muzei.ACTION_FORCE_REFRESH"
    }
}
