package me.eroi.lolidaily.muzei.db

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import me.eroi.lolidaily.muzei.model.Card
import me.eroi.lolidaily.muzei.model.SuggestedBy

/**
 * Converts between [Card] API models and [CachedArtworkEntity] DB rows.
 *
 * Complex nested types ([List] of character names, [SuggestedBy]) are serialised to JSON strings so
 * the Room schema stays flat and migration-free.
 */
object EntityMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun cardToEntity(
        card: Card,
        token: String,
        date: String,
        bookmarked: Int = 0,
    ): CachedArtworkEntity {
        return CachedArtworkEntity(
            token = token,
            artistName = card.artistName,
            sourceUrl = card.sourceUrl,
            artistUrl = card.artistUrl,
            comment = card.comment,
            tags = card.tags,
            characterNames =
                json.encodeToString(
                    ListSerializer(kotlinx.serialization.serializer<String>()),
                    card.characterNames,
                ),
            suggestedBy =
                card.suggestedBy?.let { json.encodeToString(SuggestedBy.serializer(), it) },
            date = date,
            downloadedAt = System.currentTimeMillis(),
            bookmarked = bookmarked,
        )
    }

    /** Reconstruct display-relevant fields from a persisted entity row. */
    fun entityToCardFields(entity: CachedArtworkEntity): CardFields {
        val characters: List<String> =
            try {
                json.decodeFromString<List<String>>(entity.characterNames)
            } catch (_: Exception) {
                emptyList()
            }

        val suggested: SuggestedBy? =
            entity.suggestedBy?.let {
                try {
                    json.decodeFromString<SuggestedBy>(it)
                } catch (_: Exception) {
                    null
                }
            }

        return CardFields(
            artistName = entity.artistName,
            sourceUrl = entity.sourceUrl,
            artistUrl = entity.artistUrl,
            comment = entity.comment,
            tags = entity.tags,
            characterNames = characters,
            suggestedByNickname = suggested?.nickname,
            suggestedByUsername = suggested?.username,
            date = entity.date,
            bookmarked = entity.bookmarked,
        )
    }

    /** Lightweight value object — avoids depending on the full [Card] DTO. */
    data class CardFields(
        val artistName: String,
        val sourceUrl: String,
        val artistUrl: String,
        val comment: String,
        val tags: String,
        val characterNames: List<String>,
        val suggestedByNickname: String?,
        val suggestedByUsername: String?,
        val date: String,
        val bookmarked: Int = 1,
    )
}
