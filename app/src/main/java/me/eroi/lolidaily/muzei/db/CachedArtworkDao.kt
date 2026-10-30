package me.eroi.lolidaily.muzei.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CachedArtworkDao {
    /** Insert or replace a single artwork metadata row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedArtworkEntity)

    /** Batch upsert — all rows in a single transaction. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CachedArtworkEntity>)

    /** Refresh metadata without overwriting a bookmark added while the worker was downloading. */
    @Transaction
    suspend fun refreshMetadata(entities: List<CachedArtworkEntity>) {
        upsertAll(
            entities.map { incoming ->
                val existing = getByToken(incoming.token)
                if (existing == null) {
                    incoming
                } else {
                    incoming.copy(
                        bookmarked = existing.bookmarked,
                        bookmarkDate = existing.bookmarkDate,
                    )
                }
            },
        )
    }

    /** Look up a single artwork by its image token (MD5 of imgUrl). */
    @Query("SELECT * FROM cached_artworks WHERE token = :token")
    suspend fun getByToken(token: String): CachedArtworkEntity?

    /** Return all persisted artworks, newest first. */
    @Query("SELECT * FROM cached_artworks ORDER BY downloaded_at DESC")
    suspend fun getAll(): List<CachedArtworkEntity>

    /** Delete all rows (e.g. for cache cleanup). */
    @Query("DELETE FROM cached_artworks")
    suspend fun deleteAll()

    /** Delete rows by token list. */
    @Query("DELETE FROM cached_artworks WHERE token IN (:tokens)")
    suspend fun deleteByTokens(tokens: List<String>)

    /** Update bookmark status for a single artwork. */
    @Query(
        "UPDATE cached_artworks SET bookmarked = :bookmarked, bookmark_date = " +
            "CASE WHEN :bookmarked = 0 THEN '' WHEN bookmarked != 0 THEN bookmark_date ELSE date END " +
            "WHERE token = :token",
    )
    suspend fun setBookmarked(
        token: String,
        bookmarked: Int,
    )

    /** Get bookmark status for a single artwork, or null if not in DB. */
    @Query("SELECT bookmarked FROM cached_artworks WHERE token = :token")
    suspend fun getBookmarkedStatus(token: String): Int?

    /** Get bookmark statuses for multiple artworks at once. Returns map of token -> bookmarked. */
    @Query("SELECT token, bookmarked FROM cached_artworks WHERE token IN (:tokens)")
    suspend fun getBookmarkedStatuses(tokens: List<String>): List<BookmarkStatus>

    data class BookmarkStatus(
        val token: String,
        val bookmarked: Int,
    )
}
