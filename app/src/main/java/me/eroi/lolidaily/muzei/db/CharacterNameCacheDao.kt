package me.eroi.lolidaily.muzei.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CharacterNameCacheDao {
    @Query("SELECT * FROM character_names_cache WHERE character_id = :characterId")
    suspend fun get(characterId: Int): CharacterNameCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CharacterNameCacheEntity)
}
