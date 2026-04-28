package me.eroi.lolidaily.muzei.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedArtworkDao {

    /** Insert or replace a single artwork metadata row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedArtworkEntity)

    /** Batch upsert — all rows in a single transaction. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CachedArtworkEntity>)

    /** Look up a single artwork by its image token (MD5 of imgUrl). */
    @Query("SELECT * FROM cached_artworks WHERE token = :token")
    suspend fun getByToken(token: String): CachedArtworkEntity?

    /** Return all persisted artworks, newest first. */
    @Query("SELECT * FROM cached_artworks ORDER BY downloaded_at DESC")
    suspend fun getAll(): List<CachedArtworkEntity>

    /** Delete all rows (e.g. for cache cleanup). */
    @Query("DELETE FROM cached_artworks")
    suspend fun deleteAll()
}
