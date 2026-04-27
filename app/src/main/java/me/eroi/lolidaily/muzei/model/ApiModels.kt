package me.eroi.lolidaily.muzei.model

import kotlinx.serialization.Serializable

/**
 * Response from the Loli Daily API.
 * Example: GET https://loliconey.tsuki.ga/api/v1/daily?badge=LC%20YJ-ES-NC-PG
 */
@Serializable
data class DailyResponse(
    val cards: List<Card>,
    val date: String,
)

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

@Serializable
data class SuggestedBy(
    val nickname: String,
    val username: String,
)
