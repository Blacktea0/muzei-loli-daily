package me.eroi.lolidaily.muzei.api

import android.content.Context
import android.net.Uri
import android.util.Log
import me.eroi.lolidaily.muzei.model.BangumiReply
import me.eroi.lolidaily.muzei.model.BangumiTopic
import me.eroi.lolidaily.muzei.model.BangumiUser
import me.eroi.lolidaily.muzei.model.SlimCharacter
import me.eroi.lolidaily.muzei.model.SlimCharacterImages
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request

object BangumiApiClient {
    private const val TAG = "BangumiApiClient"

    fun fetchUser(
        context: Context,
        username: String,
    ): BangumiUser? {
        val encodedUsername = Uri.encode(username)
        val url = "${LoliApiClient.getBangumiBaseUrl(context)}/p1/users/$encodedUsername"
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
                Log.w(TAG, "Bangumi user API returned ${response.code}: $body")
                return null
            }
            LoliApiClient.json.decodeFromString<BangumiUser>(body)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch user $username", e)
            null
        }
    }

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
    private const val CHII_AI_GRAPHQL_URL = "https://chii.ai/graphql"

    private const val AUTOCOMPLETE_QUERY =
        """query GetAutoComplete(${'$'}q: String!, ${'$'}type: String!, ${'$'}fields: String) { queryAutoComplete(q: ${'$'}q, type: ${'$'}type, fields: ${'$'}fields) }"""

    private const val CELEBRITY_SEARCH_QUERY =
        """query CelebritySearch(${'$'}q: String, ${'$'}type: String) { queryCelebritySearch(q: ${'$'}q, type: ${'$'}type) { total scroll_id result { ... on Celebrity { id name alias type __typename } __typename } __typename } }"""

    private const val CELEBRITY_SCROLL_QUERY =
        """query CelebrityScroll(${'$'}scroll_id: String!) { queryScroll(scroll_id: ${'$'}scroll_id) { total scroll_id result { ... on Celebrity { id name alias type __typename } __typename } __typename } }"""

    data class CharacterSearchPage(
        val characters: List<SlimCharacter>,
        val scrollId: String?,
        val total: Int,
    )

    /**
     * Searches chii.ai for virtual characters matching [keyword].
 * Returns the first page of results with a [scrollId] for fetching more.
     */
    fun searchCharacters(keyword: String): CharacterSearchPage {
        val raw = searchChiiAi(keyword) ?: return CharacterSearchPage(emptyList(), null, 0)
        val characters = raw.celebrities.toSlimCharacters()
        return CharacterSearchPage(characters, raw.scrollId, raw.total)
    }

    /**
     * Fetches the next page of character results using the Elasticsearch scroll cursor.
     */
    fun searchCharactersNextPage(scrollId: String): CharacterSearchPage {
        val raw = scrollChiiAi(scrollId) ?: return CharacterSearchPage(emptyList(), null, 0)
        val characters = raw.celebrities.toSlimCharacters()
        return CharacterSearchPage(characters, raw.scrollId, raw.total)
    }

    private data class ChiiCelebrity(val id: String, val name: String, val alias: List<String>)
    private data class ChiiSearchResult(val celebrities: List<ChiiCelebrity>, val scrollId: String?, val total: Int)

    private fun parseCelebrityResults(json: JsonObject, key: String): ChiiSearchResult? {
        val node = json["data"]?.jsonObject?.get(key)?.jsonObject ?: return null
        val scrollId = node["scroll_id"]?.jsonPrimitive?.contentOrNull
        val total = node["total"]?.jsonPrimitive?.intOrNull ?: 0
        val results = node["result"]?.jsonArray ?: return ChiiSearchResult(emptyList(), scrollId, total)
        val celebrities = results.mapNotNull { elem ->
            val obj = elem.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val alias = obj["alias"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            ChiiCelebrity(id, name, alias)
        }
        return ChiiSearchResult(celebrities, scrollId, total)
    }

    private fun searchChiiAi(keyword: String): ChiiSearchResult? {
        val escapedKeyword = LoliApiClient.escapeJson(keyword)
        val bodyStr = """{"operationName":"CelebritySearch","variables":{"q":"$escapedKeyword","type":"character"},"query":"$CELEBRITY_SEARCH_QUERY"}"""
        return executeChiiQuery(bodyStr)
    }

    private fun scrollChiiAi(scrollId: String): ChiiSearchResult? {
        val escapedScrollId = LoliApiClient.escapeJson(scrollId)
        val bodyStr = """{"operationName":"CelebrityScroll","variables":{"scroll_id":"$escapedScrollId"},"query":"$CELEBRITY_SCROLL_QUERY"}"""
        return executeChiiQuery(bodyStr, "queryScroll")
    }

    private fun executeChiiQuery(bodyStr: String, key: String = "queryCelebritySearch"): ChiiSearchResult? {
        val body = bodyStr.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(CHII_AI_GRAPHQL_URL)
            .header("User-Agent", "LoliDaily/1.0 (Android)")
            .post(body)
            .build()
        return try {
            val response = LoliApiClient.httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            if (!response.isSuccessful) {
                Log.w(TAG, "chii.ai query returned ${response.code}: $responseBody")
                return null
            }
            parseCelebrityResults(LoliApiClient.json.parseToJsonElement(responseBody).jsonObject, key)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query chii.ai", e)
            null
        }
    }

    private data class CharacterDetail(val name: String, val nameCN: String, val images: SlimCharacterImages?)

    private fun List<ChiiCelebrity>.toSlimCharacters(): List<SlimCharacter> {
        return map { celeb ->
            val charId = celeb.id.substringAfter("character_").toIntOrNull() ?: return@map null
            SlimCharacter(
                id = charId,
                name = celeb.name,
                nameCN = celeb.alias.firstOrNull().orEmpty(),
                images = null,
            )
        }.filterNotNull()
    }

    private const val GET_BANGUMI_CHARACTER_QUERY =
        """query GetBangumiCharacter(${'$'}id: Int!, ${'$'}token: String) { queryBangumiCharacter(id: ${'$'}id, token: ${'$'}token) { ...BangumiCharacter __typename } } fragment BangumiCharacter on BangumiCharacter { id name images { ...Images __typename } infobox { ...Info __typename } __typename } fragment Images on Images { large medium small __typename } fragment Info on Info { key value { ...InfoValue __typename } __typename } fragment InfoValue on InfoValue { property list { ...KV __typename } __typename } fragment KV on KV { k v __typename }"""

    private fun fetchCharacterDetail(characterId: Int): CharacterDetail? {
        val bodyStr = """{"operationName":"GetBangumiCharacter","variables":{"id":$characterId,"token":""},"query":"$GET_BANGUMI_CHARACTER_QUERY"}"""
        val body = bodyStr.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(CHII_AI_GRAPHQL_URL)
            .header("User-Agent", "LoliDaily/1.0 (Android)")
            .post(body)
            .build()

        return try {
            val response = LoliApiClient.httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            if (!response.isSuccessful) return null
            val json = LoliApiClient.json.parseToJsonElement(responseBody).jsonObject
            val char = json["data"]?.jsonObject?.get("queryBangumiCharacter")?.jsonObject ?: return null
            val name = char["name"]?.jsonPrimitive?.contentOrNull ?: ""
            val infobox = char["infobox"]?.jsonArray
            var nameCN = ""
            infobox?.forEach { item ->
                try {
                    val obj = item.jsonObject
                    if (obj["key"]?.jsonPrimitive?.contentOrNull == "简体中文名") {
                        val list = (obj["value"] as? JsonObject)?.get("list") as? JsonArray
                        nameCN = list?.firstOrNull()?.jsonObject?.get("v")?.jsonPrimitive?.contentOrNull ?: ""
                    }
                } catch (_: Exception) {}
            }
            val imagesObj = char["images"]?.jsonObject
            val images = imagesObj?.let {
                SlimCharacterImages(
                    large = it["large"]?.jsonPrimitive?.contentOrNull ?: "",
                    medium = it["medium"]?.jsonPrimitive?.contentOrNull ?: "",
                    small = it["small"]?.jsonPrimitive?.contentOrNull ?: "",
                    grid = "",
                )
            }
            CharacterDetail(name, nameCN, images)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch character detail for $characterId", e)
            null
        }
    }

    /**
     * Returns autocomplete name suggestions from chii.ai for virtual characters.
     * Lightweight — returns plain strings, no images or IDs.
     */
    fun autocompleteCharacters(keyword: String, limit: Int = 10): List<String> {
        val escapedKeyword = LoliApiClient.escapeJson(keyword)
        val bodyStr = """{"operationName":"GetAutoComplete","variables":{"q":"$escapedKeyword","type":"character","fields":"name"},"query":"$AUTOCOMPLETE_QUERY"}"""
        val body = bodyStr.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(CHII_AI_GRAPHQL_URL)
            .header("User-Agent", "LoliDaily/1.0 (Android)")
            .post(body)
            .build()

        return try {
            val response = LoliApiClient.httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return emptyList()
            if (!response.isSuccessful) return emptyList()
            val json = LoliApiClient.json.parseToJsonElement(responseBody).jsonObject
            val suggestions = json["data"]?.jsonObject
                ?.get("queryAutoComplete")?.jsonArray ?: return emptyList()
            suggestions.mapNotNull { it.jsonPrimitive.contentOrNull }.take(limit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to autocomplete characters for '$keyword'", e)
            emptyList()
        }
    }
}
