package me.eroi.lolidaily.muzei.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for persisting artwork metadata beyond the daily API rotation.
 *
 * Complex API types are stored as JSON strings in the entity, avoiding the need for TypeConverters
 * and keeping the schema migration-free.
 */
@Database(entities = [CachedArtworkEntity::class, CharacterHistoryEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedArtworkDao(): CachedArtworkDao
    abstract fun characterHistoryDao(): CharacterHistoryDao
}
