package me.eroi.lolidaily.muzei.api

import android.content.Context
import android.util.Log
import me.eroi.lolidaily.muzei.model.BangumiReply
import me.eroi.lolidaily.muzei.model.BangumiTopic
import okhttp3.Request

object BangumiApiClient {
    private const val TAG = "BangumiApiClient"

    fun fetchTopic(
        context: Context,
        topicId: Int,
    ): BangumiTopic? {
        val url = "${LoliApiClient.getBangumiBaseUrl(context)}/p1/groups/-/topics/$topicId"
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", "LoliDaily/1.0 (Android)")
                .get()
                .build()

        return try {
            val response = LoliApiClient.httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return null
            if (!response.isSuccessful) {
                Log.w(TAG, "Bangumi API returned ${response.code}: $body")
                return null
            }
            LoliApiClient.json.decodeFromString<BangumiTopic>(body)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch topic $topicId", e)
            null
        }
    }

    /** Parses "443086" → (443086, null), "443086#3982" → (443086, 3982). */
    fun parseDiscussionId(raw: String): Pair<Int, Int?> {
        val parts = raw.split("#", limit = 2)
        val topicId = parts[0].toIntOrNull() ?: return 0 to null
        val replyId = parts.getOrNull(1)?.toIntOrNull()
        return topicId to replyId
    }

    /** Traverses topic.replies to find the top-level reply with matching [replyId]. */
    fun findTargetReply(
        topic: BangumiTopic,
        replyId: Int?,
    ): BangumiReply? {
        if (replyId == null) return null
        return topic.replies.firstOrNull { it.id == replyId }
    }

    /**
     * Finds today's "floor" reply by matching content that starts with [date] and contains [tag].
     * Floor format: "2026-05-12清晨至次日凌晨 LC0 / LC YJ"
     */
    fun findTodayFloor(
        topic: BangumiTopic,
        date: String,
        tag: String,
    ): BangumiReply? {
        return topic.replies.lastOrNull { reply ->
            reply.state == 0 &&
                reply.content.startsWith(date) &&
                reply.content.contains(tag)
        }
    }
}
