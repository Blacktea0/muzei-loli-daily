package me.eroi.lolidaily.muzei.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted artwork metadata keyed by the MD5 token of [me.eroi.lolidaily.muzei.model.Card.imgUrl].
 *
 * Complex fields are stored as JSON strings via [Converters] to keep the schema migration-free —
 * adding a field to the upstream Card/SuggestedBy model does not require a Room migration.
 */
@Entity(tableName = "cached_artworks")
data class CachedArtworkEntity(
    @PrimaryKey @ColumnInfo(name = "token") val token: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    @ColumnInfo(name = "artist_url") val artistUrl: String,
    @ColumnInfo(name = "comment") val comment: String,
    @ColumnInfo(name = "tags") val tags: String,

    /** JSON-serialised [List] of character names. */
    @ColumnInfo(name = "character_names") val characterNames: String,

    /** JSON-serialised [me.eroi.lolidaily.muzei.model.SuggestedBy], or null. */
    @ColumnInfo(name = "suggested_by") val suggestedBy: String?,

    /** The API `date` field of the batch this artwork came from. */
    @ColumnInfo(name = "date") val date: String,

    /** Unix-epoch millis when this record was inserted (not the API date). */
    @ColumnInfo(name = "downloaded_at") val downloadedAt: Long,
)
