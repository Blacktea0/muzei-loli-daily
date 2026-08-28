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

    val MIGRATION_3_4 =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `submission_queue` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `owner_username` TEXT NOT NULL,
                        `queue_group` TEXT NOT NULL,
                        `tag` TEXT NOT NULL,
                        `source_url` TEXT NOT NULL,
                        `source_key` TEXT NOT NULL,
                        `artist_name` TEXT NOT NULL,
                        `artist_url` TEXT NOT NULL,
                        `characters` TEXT NOT NULL,
                        `comment` TEXT NOT NULL,
                        `anonymous` INTEGER NOT NULL,
                        `image_file_name` TEXT NOT NULL,
                        `submitted_at` INTEGER NOT NULL
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
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                        .build()
                        .also { instance = it }
            }
    }
}
