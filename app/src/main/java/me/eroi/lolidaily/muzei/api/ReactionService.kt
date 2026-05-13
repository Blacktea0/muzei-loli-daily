package me.eroi.lolidaily.muzei.api

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.model.BangumiReply
import me.eroi.lolidaily.muzei.model.DailyReactResponse
import me.eroi.lolidaily.muzei.model.DailyResponse
import me.eroi.lolidaily.muzei.model.Discussion
import me.eroi.lolidaily.muzei.model.ReactionCount
import me.eroi.lolidaily.muzei.util.Md5
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

object ReactionService {
    private const val TAG = "ReactionService"
    private const val KEY_REACTIONS = "reactions"
    private const val KEY_USER_REACTIONS = "user_reactions"
    private const val KEY_DISCUSSIONS = "discussions"
    private const val KEY_TOPIC_FLOORS = "topic_floors"
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches reactions for the current daily batch from the API, maps them to image tokens, and
     * caches in SharedPreferences.
     */
    fun fetchAndCacheReactions(context: Context) {
        try {
            val cacheFile = File(context.filesDir, "api_cache.json")
            if (!cacheFile.exists()) return
            val daily = json.decodeFromString<DailyResponse>(cacheFile.readText())
            if (daily.cards.isEmpty()) return

            val request =
                Request.Builder()
                    .url(LoliApiClient.reactApiUrl(context))
                    .header("User-Agent", "LoliDaily/1.0 (Android)")
                    .get()
                    .build()
            val response = LoliApiClient.httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Reactions API returned ${response.code}")
                return
            }
            val body = response.body?.string() ?: return
            val reactData = json.decodeFromString<DailyReactResponse>(body)

            val tokenReactions = mutableMapOf<String, List<ReactionCount>>()
            reactData.reactions.forEachIndexed { idx, reactionMap ->
                if (idx >= daily.cards.size) return@forEachIndexed
                val token = Md5.hash(daily.cards[idx].imgUrl)
                val counts =
                    reactionMap
                        .mapKeys { it.key.toInt() }
                        .mapValues { it.value.size }
                        .filter { it.value > 0 }
                        .map { ReactionCount(it.key, it.value) }
                        .sortedByDescending { it.count }
                if (counts.isNotEmpty()) {
                    tokenReactions[token] = counts
                }
            }

            val prefs =
                context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            val serialized =
                json.encodeToString(serializer<Map<String, List<ReactionCount>>>(), tokenReactions)
            prefs.edit().putString(KEY_REACTIONS, serialized).apply()

            val username = SessionManager.loadUsername(context)
            Log.d(TAG, "loadUsername = $username")
            if (username != null) {
                val userReactions = mutableMapOf<String, Int>()
                reactData.reactions.forEachIndexed { idx, reactionMap ->
                    if (idx >= daily.cards.size) return@forEachIndexed
                    val token = Md5.hash(daily.cards[idx].imgUrl)
                    for ((emojiKey, users) in reactionMap) {
                        val matched = users.any { it.firstOrNull() == username }
                        if (matched) {
                            userReactions[token] = emojiKey.toInt()
                            break
                        }
                    }
                }
                Log.d(TAG, "userReactions map = $userReactions")
                prefs
                    .edit()
                    .putString(
                        KEY_USER_REACTIONS,
                        json.encodeToString(serializer<Map<String, Int>>(), userReactions),
                    )
                    .apply()
            }

            // Cache discussions mapped to tokens
            val tokenDiscussions = mutableMapOf<String, Discussion>()
            reactData.discussions.forEachIndexed { idx, discussion ->
                if (idx >= daily.cards.size) return@forEachIndexed
                val token = Md5.hash(daily.cards[idx].imgUrl)
                tokenDiscussions[token] = discussion
            }
            prefs
                .edit()
                .putString(
                    KEY_DISCUSSIONS,
                    json.encodeToString(serializer<Map<String, Discussion>>(), tokenDiscussions),
                )
                .apply()

            // Fetch and cache topic floors per discussion ID
            val topicFloors = mutableMapOf<String, BangumiReply?>()
            val seenTopicIds = mutableSetOf<Int>()
            reactData.discussions.forEachIndexed { idx, discussion ->
                if (idx >= daily.cards.size) return@forEachIndexed
                val rawId = discussion.id
                if (rawId == "0") return@forEachIndexed

                val (topicId, _) = BangumiApiClient.parseDiscussionId(rawId)
                if (topicId == 0) return@forEachIndexed

                if (seenTopicIds.add(topicId)) {
                    try {
                        val topic = BangumiApiClient.fetchTopic(context, topicId)
                        if (topic != null) {
                            topicFloors[rawId] =
                                BangumiApiClient.findTodayFloor(
                                    topic,
                                    daily.date,
                                    daily.cards[idx].tags,
                                )
                        } else {
                            Log.w(TAG, "Topic $topicId returned null")
                            topicFloors[rawId] = null
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to fetch topic $topicId", e)
                        topicFloors[rawId] = null
                    }
                }
            }
            prefs
                .edit()
                .putString(
                    KEY_TOPIC_FLOORS,
                    json.encodeToString(
                        serializer<Map<String, BangumiReply?>>(),
                        topicFloors,
                    ),
                )
                .apply()

            Log.d(TAG, "Cached reactions for ${tokenReactions.size} cards")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch reactions", e)
        }
    }

    /** Loads cached per-image reaction counts. */
    fun loadReactions(context: Context): Map<String, List<ReactionCount>> {
        val prefs =
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_REACTIONS, null) ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, List<ReactionCount>>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** Loads cached per-image discussion map (token → Discussion). */
    fun loadDiscussions(context: Context): Map<String, Discussion> {
        val prefs =
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DISCUSSIONS, null) ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, Discussion>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** Loads cached per-discussionId topic floor replies. */
    fun loadTopicFloors(context: Context): Map<String, BangumiReply?> {
        val prefs =
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TOPIC_FLOORS, null) ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, BangumiReply?>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** Loads the user's own reaction map (token → emojiValue). */
    fun loadUserReactions(context: Context): Map<String, Int> {
        val prefs =
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_USER_REACTIONS, null) ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, Int>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** Find the index of a card (by image token) in the cached daily response. */
    fun getCardIndex(
        context: Context,
        token: String,
    ): Int? {
        val cacheFile = File(context.filesDir, "api_cache.json")
        if (!cacheFile.exists()) return null
        return try {
            val daily = json.decodeFromString<DailyResponse>(cacheFile.readText())
            daily.cards.indexOfFirst { Md5.hash(it.imgUrl) == token }.takeIf { it >= 0 }
        } catch (_: Exception) {
            null
        }
    }

    /** Submits a reaction to the LC API with the current session. */
    fun patchReaction(
        context: Context,
        cardIndex: Int,
        emojiValue: Int,
    ): Boolean {
        val session = SessionManager.loadSession(context) ?: return false
        val body = "{\"react\":$emojiValue}".toRequestBody("application/json".toMediaType())

        val request =
            Request.Builder()
                .url("${LoliApiClient.getApiBaseUrl(context)}/api/v1/daily/react?cardTypeIdx=$cardIndex")
                .header("Authorization", "Bearer ${session.token}")
                .header("User-Agent", "LoliDaily/1.0 (Android)")
                .method("PATCH", body)
                .build()

        return try {
            val response = LoliApiClient.httpClient.newCall(request).execute()
            val ok = response.isSuccessful
            response.close()

            if (ok) {
                val prefs =
                    context.getSharedPreferences(
                        LoliDailyArtWorker.PREFS_NAME,
                        Context.MODE_PRIVATE,
                    )
                val raw = prefs.getString(KEY_USER_REACTIONS, null)
                val map =
                    if (raw != null) {
                        try {
                            json.decodeFromString<MutableMap<String, Int>>(raw).toMutableMap()
                        } catch (_: Exception) {
                            mutableMapOf()
                        }
                    } else {
                        mutableMapOf()
                    }
                val cacheFile = File(context.filesDir, "api_cache.json")
                val daily = json.decodeFromString<DailyResponse>(cacheFile.readText())
                val token = Md5.hash(daily.cards[cardIndex].imgUrl)
                map[token] = emojiValue
                prefs
                    .edit()
                    .putString(
                        KEY_USER_REACTIONS,
                        json.encodeToString(serializer<Map<String, Int>>(), map),
                    )
                    .apply()
            } else {
                Log.w(TAG, "Reaction PATCH returned ${response.code}")
            }
            ok
        } catch (e: Exception) {
            Log.w(TAG, "Failed to submit reaction", e)
            false
        }
    }
}
