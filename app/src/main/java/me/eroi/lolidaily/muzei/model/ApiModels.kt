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

// ── Bangumi Topic / Reply ─────────────────────────────────

@Serializable
data class BangumiTopic(
    val id: Int,
    val title: String = "",
    val creator: BangumiUser? = null,
    val createdAt: Int = 0,
    val replyCount: Int = 0,
    val replies: List<BangumiReply> = emptyList(),
)

@Serializable
data class BangumiReply(
    val id: Int,
    val creatorID: Int = 0,
    val content: String = "",
    val createdAt: Int = 0,
    val state: Int = 0,
    val creator: BangumiUser? = null,
    val replies: List<BangumiSubReply> = emptyList(),
    val reactions: List<BangumiReaction> = emptyList(),
)

@Serializable
data class BangumiSubReply(
    val id: Int,
    val creatorID: Int = 0,
    val content: String = "",
    val createdAt: Int = 0,
    val state: Int = 0,
    val creator: BangumiUser? = null,
    val reactions: List<BangumiReaction> = emptyList(),
)

@Serializable
data class BangumiUser(
    val id: Int = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: BangumiAvatar? = null,
)

@Serializable
data class BangumiAvatar(
    val small: String = "",
    val medium: String = "",
    val large: String = "",
)

@Serializable
data class BangumiReaction(
    val value: Int = 0,
    val users: List<BangumiReactionUser> = emptyList(),
)

@Serializable
data class BangumiReactionUser(
    val id: Int = 0,
    val username: String = "",
    val nickname: String = "",
)

// ── Loli Commons User Info ─────────────────────────────────

@Serializable
data class LcUserInfo(
    val badge: String = "未授权",
    val privacy: String = "private",
    val sd: String = "",
    val subPrivacy: String = "private",
)

// ── Daily Submit ─────────────────────────────────────────

@Serializable
data class DailySubmitResponse(val otc: String)

@Serializable
data class PresignResponse(val signedUrl: String)

@Serializable
data class ArtistResolveResponse(
    val name: String? = null,
    val link: String? = null,
)
