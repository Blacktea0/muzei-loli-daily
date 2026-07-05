package me.eroi.lolidaily.muzei.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Thread-safe lazy singleton for the Room database.
 *
 * Uses [Context.applicationContext] so it's safe to call from any component (Activity, Worker,
 * BroadcastReceiver) without leaking short-lived contexts.
 */
object DatabaseProvider {
    @Volatile private var instance: AppDatabase? = null

    private val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `character_history` (
                        `character_id` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `name_cn` TEXT NOT NULL,
                        `image_url` TEXT NOT NULL,
                        `selected_at` INTEGER NOT NULL,
                        PRIMARY KEY(`character_id`)
                    )
                    """.trimIndent(),
                )
            }
        }

    private val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `character_names_cache` (
                        `character_id` INTEGER NOT NULL,
                        `name_cn` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        PRIMARY KEY(`character_id`)
                    )
                    """.trimIndent(),
                )
            }
        }

    fun getInstance(context: Context): AppDatabase {
        return instance
            ?: synchronized(this) {
                instance
                    ?: Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "lolidaily_artwork_cache.db",
                    )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                        .build()
                        .also { instance = it }
            }
    }
}
