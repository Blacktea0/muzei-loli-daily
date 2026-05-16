package me.eroi.lolidaily.muzei.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

object WorkScheduler {
    private const val TAG = "LoliDailyWorker"
    private const val WORK_NAME = "lolidaily_art_load"
    private const val KEY_NEXT_DAILY_REFRESH_TS = "next_daily_refresh_ts"
    private const val KEY_LAST_WORK_COMPLETED = "last_work_completed"
    private const val KEY_FORCE_REFRESH = "force_refresh"
    private const val KEY_INITIAL = "initial"
    private const val KEY_REFILTER_ONLY = "refilter_only"
    private const val WORK_COOLDOWN_MS = 10_000L

    fun enqueueLoad(
        context: Context,
        forceRefresh: Boolean = false,
        initial: Boolean = true,
    ): UUID? {
        val prefs =
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)

        if (!forceRefresh) {
            val lastCompleted = prefs.getLong(KEY_LAST_WORK_COMPLETED, 0L)
            if (
                lastCompleted > 0L && System.currentTimeMillis() - lastCompleted < WORK_COOLDOWN_MS
            ) {
                Log.d(TAG, "enqueueLoad skipped — within ${WORK_COOLDOWN_MS / 1000}s cooldown")
                return null
            }
        }

        var nextRefreshTs = prefs.getLong(KEY_NEXT_DAILY_REFRESH_TS, 0L)
        if (nextRefreshTs == 0L) {
            nextRefreshTs = computeNextRefreshTime(context)
            prefs.edit().putLong(KEY_NEXT_DAILY_REFRESH_TS, nextRefreshTs).apply()
        }

        val shouldDailyRefresh = !forceRefresh && System.currentTimeMillis() > nextRefreshTs
        if (shouldDailyRefresh) {
            Log.d(TAG, "Daily refresh triggered — past scheduled time")
            val newNext = computeNextRefreshTime(context)
            prefs.edit().putLong(KEY_NEXT_DAILY_REFRESH_TS, newNext).apply()
        }

        val work =
            OneTimeWorkRequestBuilder<LoliDailyArtWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setInputData(
                    Data.Builder()
                        .putBoolean(KEY_FORCE_REFRESH, forceRefresh || shouldDailyRefresh)
                        .putBoolean(KEY_INITIAL, initial)
                        .build(),
                )
                .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, work)
        return work.id
    }

    fun enqueueRefilter(context: Context) {
        val work =
            OneTimeWorkRequestBuilder<LoliDailyArtWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build(),
                )
                .setInputData(Data.Builder().putBoolean(KEY_REFILTER_ONLY, true).build())
                .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, work)
    }

    fun computeNextRefreshTime(context: Context): Long {
        val (hour, minute) = getRefreshTimeFromPrefrence(context)
        val gmt8Zone = ZoneId.of("GMT+8")
        val now = ZonedDateTime.now(gmt8Zone)
        val targetTime = LocalTime.of(hour, minute)

        var targetDateTime = now.toLocalDate().atTime(targetTime).atZone(gmt8Zone)
        if (!targetDateTime.isAfter(now)) {
            targetDateTime = targetDateTime.plusDays(1)
        }

        return targetDateTime.toInstant().toEpochMilli()
    }

    fun resetDailyRefreshState(context: Context) {
        val prefs =
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val nextTs = computeNextRefreshTime(context)
        prefs.edit().putLong(KEY_NEXT_DAILY_REFRESH_TS, nextTs).apply()
    }

    fun getRefreshTimeFromPrefrence(context: Context): Pair<Int, Int> {
        val prefs =
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val hour = prefs.getInt(LoliDailyArtWorker.KEY_DEBUG_REFRESH_HOUR, 7)
        val minute = prefs.getInt(LoliDailyArtWorker.KEY_DEBUG_REFRESH_MINUTE, 21)
        return Pair(hour, minute)
    }
}
