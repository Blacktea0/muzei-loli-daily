package me.eroi.lolidaily.muzei.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CharacterHistoryDao {
    /** Return all history entries, most-recently selected first. */
    @Query("SELECT * FROM character_history ORDER BY selected_at DESC")
    suspend fun getAll(): List<CharacterHistoryEntity>

    /** Insert or replace a history entry (upserts [selectedAt]). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CharacterHistoryEntity)

    /** Delete a single history entry by character ID. */
    @Query("DELETE FROM character_history WHERE character_id = :characterId")
    suspend fun delete(characterId: Int)
}
