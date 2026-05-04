package me.eroi.lolidaily.muzei.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for persisting artwork metadata beyond the daily API rotation.
 *
 * Complex API types are stored as JSON strings in the entity, avoiding the need for TypeConverters
 * and keeping the schema migration-free.
 */
@Database(entities = [CachedArtworkEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedArtworkDao(): CachedArtworkDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE cached_artworks ADD COLUMN bookmarked INTEGER NOT NULL DEFAULT 1",
                    )
                }
            }
    }
}
