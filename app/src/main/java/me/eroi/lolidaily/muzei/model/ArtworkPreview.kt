package me.eroi.lolidaily.muzei.model

import android.net.Uri
import kotlinx.serialization.Serializable

/** Combines a cached artwork URI with its API metadata for rich display in the settings gallery. */
data class ArtworkPreview(
    val uri: Uri,
    val filename: String,
    val artistName: String,
    val comment: String,
    val tags: String,
    val characterNames: List<String>,
    val characterIds: List<Long> = emptyList(),
    val sourceUrl: String,
    val artistUrl: String,
    val date: String,
    val reactions: List<ReactionCount> = emptyList(),
    val userEmoji: Int? = null,
    val isBookmarked: Boolean = true,
    val suggestedByName: String? = null,
    val discussionId: String? = null,
    val discussionCount: Int = 0,
)

/**
 * A single reaction emoji and its count for a card. Emojis are Bangumi smileys keyed by numeric ID.
 */
@Serializable data class ReactionCount(
    val emojiValue: Int,
    val count: Int,
    val users: List<String> = emptyList(),
)
