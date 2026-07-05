package me.eroi.lolidaily.muzei.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "character_names_cache")
data class CharacterNameCacheEntity(
    @PrimaryKey @ColumnInfo(name = "character_id") val characterId: Int,
    @ColumnInfo(name = "name_cn") val nameCN: String,
    @ColumnInfo(name = "name") val name: String,
)
