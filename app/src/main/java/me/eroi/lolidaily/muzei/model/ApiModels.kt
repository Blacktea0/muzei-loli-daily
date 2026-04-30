package me.eroi.lolidaily.muzei.model

import kotlinx.serialization.Serializable

/**
 * Response from the Loli Daily API. Example: GET
 * https://loliconey.tsuki.ga/api/v1/daily?badge=LC%20YJ-ES-NC-PG
 */
@Serializable data class DailyResponse(val cards: List<Card>, val date: String)

@Serializable
data class Card(
    val characterNames: List<String> = emptyList(),
    val comment: String = "",
    val imgUrl: String,
    val sourceUrl: String = "",
    val tags: String = "",
    val artistName: String = "",
    val artistUrl: String = "",
    val characterIds: List<Long> = emptyList(),
    val suggestedBy: SuggestedBy? = null,
)

@Serializable data class SuggestedBy(val nickname: String, val username: String)

// ── Reactions ─────────────────────────────────────────────

/**
 * Response from the Loli Daily reactions API. Example: GET
 * https://loliconey.tsuki.ga/api/v1/daily/react?badge=LC%20YJ-ES-NC-PG
 */
@Serializable
data class DailyReactResponse(
    /**
     * Per-card reaction maps. Each element is a map of: emoji value (string key) → list of
     * [username, nickname] pairs. Empty maps indicate no reactions for that card.
     */
    val reactions: List<Map<String, List<List<String>>>>,
    val discussions: List<Discussion> = emptyList(),
)

@Serializable data class Discussion(val id: String, val count: Int)
