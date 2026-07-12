package me.eroi.lolidaily.muzei.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import me.eroi.lolidaily.muzei.util.Log
import androidx.core.content.edit
import me.eroi.lolidaily.muzei.DailyRefreshReceiver
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object DailyRefreshScheduler {
    private const val TAG = "DailyRefreshScheduler"
    private const val REQUEST_CODE_DAILY_REFRESH = 2101
    const val KEY_SCHEDULED_TARGET_TS = "scheduled_target_ts"
    const val KEY_SCHEDULED_DEADLINE_TS = "scheduled_deadline_ts"
    const val REFRESH_WINDOW_MS = 30 * 60 * 1000L

    private val refreshZone: ZoneId = ZoneId.of("GMT+8")

    fun ensureScheduled(context: Context) {
        val now = System.currentTimeMillis()
        val prefs =
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val targetTs = prefs.getLong(WorkScheduler.KEY_NEXT_DAILY_REFRESH_TS, 0L)
        if (targetTs > now) {
            schedule(context, targetTs)
        } else {
            scheduleNext(context)
        }
    }

    fun scheduleNext(context: Context): Long {
        val targetTs = computeNextRefreshTime(context)
        context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putLong(WorkScheduler.KEY_NEXT_DAILY_REFRESH_TS, targetTs)
            }
        schedule(context, targetTs)
        return targetTs
    }

    fun resetAndSchedule(context: Context): Long = scheduleNext(context)

    fun computeNextRefreshTime(context: Context): Long {
        val (hour, minute) = WorkScheduler.getRefreshTimeFromPreference(context)
        val now = ZonedDateTime.now(refreshZone)
        val targetTime = LocalTime.of(hour, minute)

        var targetDateTime = now.toLocalDate().atTime(targetTime).atZone(refreshZone)
        if (!targetDateTime.isAfter(now)) {
            targetDateTime = targetDateTime.plusDays(1)
        }

        return targetDateTime.toInstant().toEpochMilli()
    }

    fun computeWindowForTodayOrPrevious(context: Context): Pair<Long, Long> {
        val (hour, minute) = WorkScheduler.getRefreshTimeFromPreference(context)
        val now = ZonedDateTime.now(refreshZone)
        val targetTime = LocalTime.of(hour, minute)
        var targetDateTime = now.toLocalDate().atTime(targetTime).atZone(refreshZone)
        if (targetDateTime.isAfter(now)) {
            targetDateTime = targetDateTime.minusDays(1)
        }
        val targetTs = targetDateTime.toInstant().toEpochMilli()
        return targetTs to targetTs + REFRESH_WINDOW_MS
    }

    private fun schedule(
        context: Context,
        targetTs: Long,
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = dailyRefreshPendingIntent(context)

        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP,
            targetTs,
            REFRESH_WINDOW_MS,
            pendingIntent,
        )
        Log.d(TAG, "Scheduled daily refresh alarm at $targetTs")
    }

    private fun dailyRefreshPendingIntent(context: Context): PendingIntent {
        val intent =
            Intent(context, DailyRefreshReceiver::class.java).apply {
                action = DailyRefreshReceiver.ACTION_DAILY_REFRESH
            }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DAILY_REFRESH,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
