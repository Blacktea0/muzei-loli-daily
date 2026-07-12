package me.eroi.lolidaily.muzei

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.eroi.lolidaily.muzei.util.Log
import me.eroi.lolidaily.muzei.worker.DailyRefreshScheduler
import me.eroi.lolidaily.muzei.worker.WorkScheduler

class DailyRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        if (intent?.action == ACTION_DAILY_REFRESH) {
            handleDailyRefresh(context)
        }
    }

    private fun handleDailyRefresh(context: Context) {
        val now = System.currentTimeMillis()
        val (targetTs, deadlineTs) = DailyRefreshScheduler.computeWindowForTodayOrPrevious(context)

        if (now <= deadlineTs) {
            Log.d(TAG, "Daily refresh alarm fired in window")
            WorkScheduler.enqueueScheduledDailyRefresh(
                context = context,
                scheduledTargetTs = targetTs,
                scheduledDeadlineTs = deadlineTs,
            )
        } else {
            Log.w(TAG, "Daily refresh alarm fired after deadline; skipping fetch")
        }

        DailyRefreshScheduler.scheduleNext(context)
    }

    companion object {
        private const val TAG = "DailyRefreshReceiver"
        const val ACTION_DAILY_REFRESH = "me.eroi.lolidaily.muzei.action.DAILY_REFRESH"
    }
}
