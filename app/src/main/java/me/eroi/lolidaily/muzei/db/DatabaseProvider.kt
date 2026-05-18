package me.eroi.lolidaily.muzei.db

import android.content.Context
import androidx.room.Room

/**
 * Thread-safe lazy singleton for the Room database.
 *
 * Uses [Context.applicationContext] so it's safe to call from any component (Activity, Worker,
 * BroadcastReceiver) without leaking short-lived contexts.
 */
object DatabaseProvider {
    @Volatile private var instance: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
        return instance
            ?: synchronized(this) {
                instance
                    ?: Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "lolidaily_artwork_cache.db",
                    )
                        .fallbackToDestructiveMigration(dropAllTables = true)
                        .build()
                        .also { instance = it }
            }
    }
}
