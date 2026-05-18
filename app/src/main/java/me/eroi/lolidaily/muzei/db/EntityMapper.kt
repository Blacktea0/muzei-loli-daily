package me.eroi.lolidaily.muzei.db

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import me.eroi.lolidaily.muzei.model.Card
import me.eroi.lolidaily.muzei.model.SuggestedBy

/**
 * Converts between [Card] API models and [CachedArtworkEntity] DB rows.
 *
 * Complex nested types ([List] of characters, [SuggestedBy]) are serialised to JSON strings so
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
        val characters =
            card.characterNames.zip(card.characterIds).map { (name, id) ->
                CharacterEntry(name = name, id = id)
            }

        return CachedArtworkEntity(
            token = token,
            artistName = card.artistName,
            sourceUrl = card.sourceUrl,
            artistUrl = card.artistUrl,
            comment = card.comment,
            tags = card.tags,
            characters = json.encodeToString(ListSerializer(CharacterEntry.serializer()), characters),
            suggestedByNickname = card.suggestedBy?.nickname,
            suggestedByUsername = card.suggestedBy?.username,
            date = date,
            downloadedAt = System.currentTimeMillis(),
            bookmarked = bookmarked,
        )
    }

    /** Reconstruct display-relevant fields from a persisted entity row. */
    fun entityToCardFields(entity: CachedArtworkEntity): CardFields {
        val characters: List<CharacterEntry> =
            try {
                json.decodeFromString<List<CharacterEntry>>(entity.characters)
            } catch (_: Exception) {
                emptyList()
            }

        return CardFields(
            artistName = entity.artistName,
            sourceUrl = entity.sourceUrl,
            artistUrl = entity.artistUrl,
            comment = entity.comment,
            tags = entity.tags,
            characters = characters,
            suggestedByNickname = entity.suggestedByNickname,
            suggestedByUsername = entity.suggestedByUsername,
            date = entity.date,
            bookmarked = entity.bookmarked,
        )
    }

    /** A single character entry with name and ID. */
    @Serializable
    data class CharacterEntry(
        val name: String,
        val id: Long,
    )

    /** Lightweight value object — avoids depending on the full [Card] DTO. */
    data class CardFields(
        val artistName: String,
        val sourceUrl: String,
        val artistUrl: String,
        val comment: String,
        val tags: String,
        val characters: List<CharacterEntry>,
        val suggestedByNickname: String?,
        val suggestedByUsername: String?,
        val date: String,
        val bookmarked: Int = 1,
    ) {
        val characterNames: List<String> get() = characters.map { it.name }
        val characterIds: List<Long> get() = characters.map { it.id }
    }
}
