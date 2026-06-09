package me.eroi.lolidaily.muzei.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists recently selected characters for the submit page search bar.
 *
 * Keyed by [characterId] so re-selecting the same character updates [selectedAt]
 * rather than creating a duplicate row.
 */
@Entity(tableName = "character_history")
data class CharacterHistoryEntity(
    @PrimaryKey @ColumnInfo(name = "character_id") val characterId: Int,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "name_cn") val nameCN: String,
    /** URL of the small grid image, or empty string if unavailable. */
    @ColumnInfo(name = "image_url") val imageUrl: String,
    /** Unix-epoch millis of the last time the user selected this character. */
    @ColumnInfo(name = "selected_at") val selectedAt: Long,
)
