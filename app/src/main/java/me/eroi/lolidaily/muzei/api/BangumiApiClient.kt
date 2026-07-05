package me.eroi.lolidaily.muzei.api

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
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
import okhttp3.FormBody
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
                .header("User-Agent", LoliApiClient.USER_AGENT)
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
                .header("User-Agent", LoliApiClient.USER_AGENT)
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

    /**
     * Posts a reply to a Bangumi group topic using the WebView's login cookies.
     * Steps: GET the topic page HTML → extract formhash → POST the reply form.
     * Returns true on success, false on failure.
     */
    fun postTopicReply(context: Context, topicId: Int, content: String): Boolean {
        if (content.isBlank()) {
            Log.w(TAG, "postTopicReply: blank content")
            return false
        }

        val topicPage = openTopicPostSession(context, topicId) ?: return false

        val postUrl = resolveReplyPostUrl(topicPage.topicUrl, topicPage.pageHtml)
        val postResult = submitTopicReply(
            postUrl = postUrl,
            topicUrl = topicPage.topicUrl,
            topicId = topicId,
            cookies = topicPage.cookies,
            formhash = topicPage.formhash,
            content = content,
        )
        if (!postResult.accepted) return false
        if (!postResult.needsVerification) return true

        val verified = fetchTopicHtml(topicPage.topicUrl, topicPage.cookies, noCache = true)
            ?.let { containsPostedContent(it, content) } == true
        if (!verified) {
            Log.w(TAG, "postTopicReply: reply accepted but content was not visible after POST")
        }
        return verified
    }

    fun postTopicSubReply(
        context: Context,
        topicId: Int,
        parentPostId: Int,
        content: String,
    ): Boolean {
        if (content.isBlank()) {
            Log.w(TAG, "postTopicSubReply: blank content")
            return false
        }

        val topicPage = openTopicPostSession(context, topicId) ?: return false
        val subReplyTarget = extractSubReplyTarget(topicPage.pageHtml, topicId, parentPostId)
            ?: run {
                Log.w(TAG, "postTopicSubReply: missing subReply target for post $parentPostId")
                return false
            }
        val lastview = extractInputValue(topicPage.pageHtml, "lastview") ?: "0"
        val postUrl = resolveReplyPostUrl(topicPage.topicUrl, topicPage.pageHtml)
        val postResult = submitTopicSubReply(
            postUrl = postUrl,
            topicUrl = topicPage.topicUrl,
            topicId = topicId,
            cookies = topicPage.cookies,
            formhash = topicPage.formhash,
            lastview = lastview,
            target = subReplyTarget,
            content = content,
        )
        if (!postResult.accepted) return false
        if (!postResult.needsVerification) return true

        val verified = fetchTopicHtml(topicPage.topicUrl, topicPage.cookies, noCache = true)
            ?.let { containsPostedContent(it, content) && it.contains("topic_reply_$parentPostId") } == true
        if (!verified) {
            Log.w(TAG, "postTopicSubReply: reply accepted but subreply content was not visible after POST")
        }
        return verified
    }

    /**
     * Posts a comment for a daily card on a topic.
     * If the floor reply for that card does not exist yet, creates it, fetches the topic again to get its floor ID, and then posts the sub-reply.
     * Returns true if successfully posted.
     */
    fun postDailyComment(
        context: Context,
        topicId: Int,
        dailyDate: String,
        tags: String,
        content: String
    ): Boolean {
        // Step 1: Fetch the topic to see if the floor already exists
        var topic = fetchTopic(context, topicId) ?: return false
        var floor = findTodayFloor(topic, dailyDate, tags)

        if (floor == null) {
            // Step 2: Create a top-level topic reply first
            val tagSuffix = when {
                tags.contains("LC0") || tags.contains("LC YJ") -> "LC0 / LC YJ"
                tags.contains("LC ES") || tags.contains("ES") -> "LC ES"
                else -> tags
            }
            val floorHeader = "${dailyDate}清晨至次日凌晨 $tagSuffix"
            val ok = postTopicReply(context, topicId, floorHeader)
            if (!ok) {
                Log.w(TAG, "postDailyComment: failed to post top-level floor reply")
                return false
            }

            // Step 3: Refetch and locate the floor
            var retries = 3
            while (retries > 0) {
                topic = fetchTopic(context, topicId) ?: return false
                floor = findTodayFloor(topic, dailyDate, tags)
                if (floor != null) break
                retries--
                Thread.sleep(1000)
            }

            if (floor == null) {
                Log.w(TAG, "postDailyComment: failed to find newly created floor reply after retries")
                return false
            }
        }

        // Step 4: Post the subreply under the matched/created floor
        return postTopicSubReply(context, topicId, floor.id, content)
    }

    fun postLike(
        context: Context,
        topicId: Int,
        replyId: Int,
        value: Int,
    ): Boolean {
        val topicPage = openTopicPostSession(context, topicId) ?: return false
        val uri = topicPage.topicUrl.toUri()
        val domain = uri.host ?: "chii.in"
        val likeUrl = "https://$domain/like?type=8&main_id=$topicId&id=$replyId&value=$value&gh=${topicPage.formhash}&ajax=1"

        val request = Request.Builder()
            .url(likeUrl)
            .header("User-Agent", LoliApiClient.MOBILE_UA)
            .header("Cookie", topicPage.cookies)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Referer", topicPage.topicUrl)
            .header("X-Requested-With", "XMLHttpRequest")
            .get()
            .build()

        return try {
            LoliApiClient.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "postLike failed with status ${response.code}")
                    false
                } else {
                    val body = response.body?.string()
                    Log.d(TAG, "postLike response: $body")
                    response.code == 200
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "postLike failed", e)
            false
        }
    }

    private fun openTopicPostSession(
        context: Context,
        topicId: Int,
    ): TopicPostSession? {
        val preferredDomain = SessionManager.loadDomain(context)
        val domains = bangumiWebDomains(preferredDomain)
        return domains.firstNotNullOfOrNull { domain ->
            val topicUrl = "https://$domain/group/topic/$topicId"
            val cookies = buildBangumiCookieHeader(listOf(domain) + domains.filterNot { it == domain })
            if (cookies.isNullOrBlank()) {
                Log.w(TAG, "openTopicPostSession: no cookies for $domain")
                return@firstNotNullOfOrNull null
            }

            val pageHtml = fetchTopicHtml(topicUrl, cookies) ?: return@firstNotNullOfOrNull null
            val formhash = extractFormhash(pageHtml)
            if (formhash.isNullOrBlank()) {
                Log.w(TAG, "openTopicPostSession: no formhash on $domain; ${summarizeTopicPage(pageHtml)}")
                null
            } else {
                TopicPostSession(
                    topicUrl = topicUrl,
                    cookies = cookies,
                    pageHtml = pageHtml,
                    formhash = formhash,
                )
            }
        }
    }

    private data class SubReplyTarget(
        val parentPostId: Int,
        val subReplyUid: Int,
        val postUid: Int,
    )

    private data class TopicPostSession(
        val topicUrl: String,
        val cookies: String,
        val pageHtml: String,
        val formhash: String,
    )

    private data class ReplyPostResult(
        val accepted: Boolean,
        val needsVerification: Boolean,
    )

    private fun bangumiWebDomains(preferredDomain: String): List<String> {
        val domains = mutableListOf(preferredDomain)
        domains += listOf("chii.in", "bgm.tv", "bangumi.tv")
        return domains.distinct()
    }

    private fun buildBangumiCookieHeader(domains: List<String>): String? {
        val cookieByName = LinkedHashMap<String, String>()
        val cookieManager = android.webkit.CookieManager.getInstance()
        domains.forEach { domain ->
            cookieManager.getCookie("https://$domain")
                ?.split(";")
                ?.asSequence()
                ?.map { it.trim() }
                ?.filter { it.contains("=") }
                ?.forEach { cookie ->
                    val name = cookie.substringBefore("=").trim()
                    if (name.isNotBlank() && !cookieByName.containsKey(name)) {
                        cookieByName[name] = cookie
                    }
                }
        }
        return cookieByName.values.joinToString("; ").takeIf { it.isNotBlank() }
    }

    private fun fetchTopicHtml(
        topicUrl: String,
        cookies: String,
        noCache: Boolean = false,
    ): String? {
        val requestBuilder = Request.Builder()
            .url(topicUrl)
            .header("User-Agent", LoliApiClient.MOBILE_UA)
            .header("Cookie", cookies)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .get()
        if (noCache) {
            requestBuilder
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
        }

        return try {
            LoliApiClient.httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    Log.w(TAG, "postTopicReply: GET topic returned ${response.code}")
                    return null
                }
                body
            }
        } catch (e: Exception) {
            Log.w(TAG, "postTopicReply: GET topic failed", e)
            null
        }
    }

    private fun submitTopicReply(
        postUrl: String,
        topicUrl: String,
        topicId: Int,
        cookies: String,
        formhash: String,
        content: String,
    ): ReplyPostResult {
        val formBody = FormBody.Builder()
            .add("formhash", formhash)
            .add("content", content)
            .add("submit", "submit")
            .build()

        val origin = topicUrl.toUri().let { "${it.scheme}://${it.encodedAuthority}" }
        val postRequest = Request.Builder()
            .url(postUrl)
            .header("User-Agent", LoliApiClient.MOBILE_UA)
            .header("Cookie", cookies)
            .header("Origin", origin)
            .header("Referer", topicUrl)
            .post(formBody)
            .build()

        return try {
            LoliApiClient.httpClient
                .newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
                .newCall(postRequest)
                .execute()
                .use { response ->
                    val location = response.header("Location").orEmpty()
                    when {
                        response.code in 300..399 &&
                            location.contains("/group/topic/$topicId") ->
                            ReplyPostResult(accepted = true, needsVerification = false)
                        response.code in 200..299 ->
                            ReplyPostResult(accepted = true, needsVerification = true)
                        else -> {
                            Log.w(
                                TAG,
                                "postTopicReply: POST returned ${response.code}, location=$location",
                            )
                            ReplyPostResult(accepted = false, needsVerification = false)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "postTopicReply: POST failed", e)
            ReplyPostResult(accepted = false, needsVerification = false)
        }
    }

    private fun submitTopicSubReply(
        postUrl: String,
        topicUrl: String,
        topicId: Int,
        cookies: String,
        formhash: String,
        lastview: String,
        target: SubReplyTarget,
        content: String,
    ): ReplyPostResult {
        val formBody = FormBody.Builder()
            .add("topic_id", topicId.toString())
            .add("related", target.parentPostId.toString())
            .add("sub_reply_uid", target.subReplyUid.toString())
            .add("post_uid", target.postUid.toString())
            .add("content", content)
            .add("related_photo", "0")
            .add("lastview", lastview)
            .add("formhash", formhash)
            .add("submit", "submit")
            .build()

        val origin = topicUrl.toUri().let { "${it.scheme}://${it.encodedAuthority}" }
        val postRequest = Request.Builder()
            .url("$postUrl?ajax=1")
            .header("User-Agent", LoliApiClient.MOBILE_UA)
            .header("Cookie", cookies)
            .header("Origin", origin)
            .header("Referer", topicUrl)
            .post(formBody)
            .build()

        return try {
            LoliApiClient.httpClient
                .newCall(postRequest)
                .execute()
                .use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (
                        response.isSuccessful &&
                        responseBody.contains("\"posts\"") &&
                        responseBody.contains("\"sub\"") &&
                        responseBody.contains("\"${target.parentPostId}\"")
                    ) {
                        ReplyPostResult(accepted = true, needsVerification = false)
                    } else {
                        Log.w(
                            TAG,
                            "postTopicSubReply: POST returned ${response.code}, body=${responseBody.take(500)}",
                        )
                        ReplyPostResult(accepted = false, needsVerification = false)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "postTopicSubReply: POST failed", e)
            ReplyPostResult(accepted = false, needsVerification = false)
        }
    }

    private fun extractFormhash(pageHtml: String): String? {
        Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(pageHtml)
            .forEach { match ->
                val inputTag = match.value
                if (extractHtmlAttribute(inputTag, "name").equals("formhash", ignoreCase = true)) {
                    return extractHtmlAttribute(inputTag, "value")?.takeIf { it.isNotBlank() }
                }
            }

        return Regex("""formhash['"]?\s*[:=]\s*['"]([^'"]+)""", RegexOption.IGNORE_CASE)
            .find(pageHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractInputValue(pageHtml: String, name: String): String? {
        Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(pageHtml)
            .forEach { match ->
                val inputTag = match.value
                if (extractHtmlAttribute(inputTag, "name").equals(name, ignoreCase = true)) {
                    return extractHtmlAttribute(inputTag, "value")?.takeIf { it.isNotBlank() }
                }
            }
        return null
    }

    private fun resolveReplyPostUrl(topicUrl: String, pageHtml: String): String {
        val action = extractReplyFormAction(pageHtml) ?: return "$topicUrl/new_reply"
        if (action.startsWith("http://") || action.startsWith("https://")) return action
        if (action.startsWith("//")) return "https:$action"

        val topicUri = topicUrl.toUri()
        return if (action.startsWith("/")) {
            topicUri.buildUpon()
                .encodedPath(action)
                .encodedQuery(null)
                .fragment(null)
                .build()
                .toString()
        } else {
            "$topicUrl/${action.trimStart('/')}"
        }
    }

    private fun extractReplyFormAction(pageHtml: String): String? {
        Regex("""<form\b[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(pageHtml)
            .forEach { match ->
                val formTag = match.value
                val action = extractHtmlAttribute(formTag, "action")?.takeIf { it.isNotBlank() }
                    ?: return@forEach
                if (
                    formTag.contains("ReplyForm", ignoreCase = true) ||
                    action.contains("new_reply", ignoreCase = true) ||
                    action.contains("add_reply", ignoreCase = true)
                ) {
                    return action
                }
            }
        return null
    }

    private fun extractHtmlAttribute(tag: String, name: String): String? {
        val attrRegex = Regex(
            """\b${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""",
            RegexOption.IGNORE_CASE,
        )
        val match = attrRegex.find(tag) ?: return null
        return (1..3)
            .firstNotNullOfOrNull { index -> match.groupValues.getOrNull(index)?.takeIf { it.isNotEmpty() } }
    }

    private fun extractSubReplyTarget(
        pageHtml: String,
        topicId: Int,
        parentPostId: Int,
    ): SubReplyTarget? {
        Log.d(TAG, "extractSubReplyTarget: searching for topicId=$topicId, parentPostId=$parentPostId")
        var matchCount = 0
        Regex("""subReply\(([^)]*)\)""")
            .findAll(pageHtml)
            .forEach { match ->
                matchCount++
                val args = parseJsArgs(match.groupValues[1])
                Log.d(TAG, "extractSubReplyTarget: match $matchCount: args=$args")
                if (
                    args.getOrNull(1)?.toIntOrNull() == topicId &&
                    args.getOrNull(2)?.toIntOrNull() == parentPostId &&
                    args.getOrNull(6)?.toIntOrNull() == 0
                ) {
                    val subReplyUid = args.getOrNull(4)?.toIntOrNull() ?: return@forEach
                    val postUid = args.getOrNull(5)?.toIntOrNull() ?: return@forEach
                    Log.d(TAG, "extractSubReplyTarget: matched target: subReplyUid=$subReplyUid, postUid=$postUid")
                    return SubReplyTarget(
                        parentPostId = parentPostId,
                        subReplyUid = subReplyUid,
                        postUid = postUid,
                    )
                }
            }
        Log.w(TAG, "extractSubReplyTarget: no match found out of $matchCount subReply matches in HTML")
        return null
    }

    private fun parseJsArgs(rawArgs: String): List<String> {
        return Regex("""'([^']*)'|"([^"]*)"|([^,\s]+)""")
            .findAll(rawArgs)
            .map { match ->
                match.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }
                    ?: match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
                    ?: match.groupValues.getOrNull(3).orEmpty()
            }
            .toList()
    }

    private fun summarizeTopicPage(pageHtml: String): String {
        val uid = Regex("""CHOBITS_UID\s*=\s*(\d+)""")
            .find(pageHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?: "unknown"
        val hasLoginLink = pageHtml.contains("/login", ignoreCase = true)
        val hasReplyEndpoint = pageHtml.contains("new_reply", ignoreCase = true) ||
            pageHtml.contains("add_reply", ignoreCase = true)
        val hasFormhashText = pageHtml.contains("formhash", ignoreCase = true)
        return "uid=$uid, hasLoginLink=$hasLoginLink, hasReplyEndpoint=$hasReplyEndpoint, " +
            "hasFormhashText=$hasFormhashText, length=${pageHtml.length}"
    }

    private fun containsPostedContent(pageHtml: String, content: String): Boolean {
        return pageHtml.contains(content) || pageHtml.contains(escapeHtml(content))
    }

    private fun escapeHtml(text: String): String = buildString(text.length) {
        text.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
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
            .header("User-Agent", LoliApiClient.USER_AGENT)
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
        val candidates = mapNotNull { celeb ->
            val charId = celeb.id.substringAfter("character_").toIntOrNull() ?: return@mapNotNull null
            SlimCharacter(
                id = charId,
                name = celeb.name,
                nameCN = celeb.alias.firstOrNull().orEmpty(),
                images = null,
            )
        }
        if (candidates.isEmpty()) return emptyList()

        val imagesMap = fetchCharactersImages(candidates.map { it.id })
        return candidates.map { char ->
            char.copy(images = imagesMap[char.id])
        }
    }

    private fun fetchCharactersImages(ids: List<Int>): Map<Int, SlimCharacterImages> {
        if (ids.isEmpty()) return emptyMap()

        val queryBuilder = StringBuilder()
        queryBuilder.append("query GetBangumiCharacters {")
        for (id in ids) {
            queryBuilder.append(" c_$id: queryBangumiCharacter(id: $id) { id images { large medium small } }")
        }
        queryBuilder.append(" }")

        val escapedQuery = LoliApiClient.escapeJson(queryBuilder.toString())
        val bodyStr = """{"query":"$escapedQuery"}"""
        val body = bodyStr.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(CHII_AI_GRAPHQL_URL)
            .header("User-Agent", LoliApiClient.USER_AGENT)
            .post(body)
            .build()

        return try {
            val response = LoliApiClient.httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return emptyMap()
            if (!response.isSuccessful) return emptyMap()
            val json = LoliApiClient.json.parseToJsonElement(responseBody).jsonObject
            val data = json["data"]?.jsonObject ?: return emptyMap()
            val result = mutableMapOf<Int, SlimCharacterImages>()
            for (id in ids) {
                val key = "c_$id"
                val charObj = data[key] as? JsonObject ?: continue
                val imagesObj = charObj["images"] as? JsonObject
                if (imagesObj != null) {
                    result[id] = SlimCharacterImages(
                        large = imagesObj["large"]?.jsonPrimitive?.contentOrNull ?: "",
                        medium = imagesObj["medium"]?.jsonPrimitive?.contentOrNull ?: "",
                        small = imagesObj["small"]?.jsonPrimitive?.contentOrNull ?: "",
                        grid = "",
                    )
                }
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch character images in batch", e)
            emptyMap()
        }
    }

    private const val GET_BANGUMI_CHARACTER_QUERY =
        """query GetBangumiCharacter(${'$'}id: Int!, ${'$'}token: String) { queryBangumiCharacter(id: ${'$'}id, token: ${'$'}token) { ...BangumiCharacter __typename } } fragment BangumiCharacter on BangumiCharacter { id name images { ...Images __typename } infobox { ...Info __typename } __typename } fragment Images on Images { large medium small __typename } fragment Info on Info { key value { ...InfoValue __typename } __typename } fragment InfoValue on InfoValue { property list { ...KV __typename } __typename } fragment KV on KV { k v __typename }"""

    private fun fetchCharacterDetail(characterId: Int): CharacterDetail? {
        val bodyStr = """{"operationName":"GetBangumiCharacter","variables":{"id":$characterId,"token":""},"query":"$GET_BANGUMI_CHARACTER_QUERY"}"""
        val body = bodyStr.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(CHII_AI_GRAPHQL_URL)
            .header("User-Agent", LoliApiClient.USER_AGENT)
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
            .header("User-Agent", LoliApiClient.USER_AGENT)
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
