package me.eroi.lolidaily.muzei.api

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.model.ArtistResolveResponse
import me.eroi.lolidaily.muzei.model.Card
import me.eroi.lolidaily.muzei.model.DailyResponse
import me.eroi.lolidaily.muzei.model.DailySubmitResponse
import me.eroi.lolidaily.muzei.model.LcUserInfo
import me.eroi.lolidaily.muzei.model.PresignResponse
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object LoliApiClient {
    private const val TAG = "LoliApiClient"
    private const val USER_AGENT = "LoliDaily/1.0 (Android)"
    const val DEFAULT_BADGE = "LC0"

    const val DEFAULT_API_BASE_URL = "https://loliconey.tsuki.ga"
    const val KEY_DEBUG_API_BASE_URL = "debug_api_base_url"
    const val KEY_DEBUG_API_BASE_URL_CUSTOM = "debug_api_base_url_custom"

    const val DEFAULT_BANGUMI_BASE_URL = "https://next.bgm.tv"
    const val KEY_DEBUG_BANGUMI_BASE_URL = "debug_bangumi_base_url"
    const val KEY_DEBUG_BANGUMI_BASE_URL_CUSTOM = "debug_bangumi_base_url_custom"

    const val KEY_DEBUG_OVERRIDE_API_TAG_ENABLED = "debug_override_api_tag_enabled"
    const val KEY_DEBUG_OVERRIDE_API_TAG = "debug_override_api_tag"

    val ALL_LC_TAGS =
        listOf(
            "LC0",
            "LC ES",
            "LC ES-PG",
            "LC ES-NC",
            "LC ES-NC-PG",
            "LC ES-NC-GR",
            "LC ES-NC-PG-GR",
            "LC YJ",
            "LC YJ-ES",
            "LC YJ-ES-PG",
            "LC YJ-ES-NC",
            "LC YJ-ES-NC-PG",
            "LC YJ-ES-NC-GR",
            "LC YJ-ES-NC-PG-GR",
        )

    val KNOWN_SERVERS =
        listOf(
            "https://loliconey.tsuki.ga",
            "https://lc-coney.deno.dev",
        )

    val KNOWN_BANGUMI_SERVERS =
        listOf(
            "https://next.bgm.tv",
        )

    fun getApiBaseUrl(context: Context): String {
        val prefs =
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEBUG_API_BASE_URL, null)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_API_BASE_URL
    }

    fun getBangumiBaseUrl(context: Context): String {
        val prefs =
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEBUG_BANGUMI_BASE_URL, null)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BANGUMI_BASE_URL
    }

    private fun getBadge(context: Context): String {
        val prefs =
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DEBUG_OVERRIDE_API_TAG_ENABLED, false)) {
            return prefs.getString(KEY_DEBUG_OVERRIDE_API_TAG, null)?.takeIf { it.isNotBlank() }
                ?: DEFAULT_BADGE
        }
        return SessionManager.loadBadge(context)
    }

    fun apiUrl(context: Context): String {
        val badge = getBadge(context)
        return "${getApiBaseUrl(context)}/api/v1/daily?badge=${Uri.encode(badge)}"
    }

    fun reactApiUrl(context: Context): String {
        val badge = getBadge(context)
        return "${getApiBaseUrl(context)}/api/v1/daily/react?badge=${Uri.encode(badge)}"
    }

    val json = Json { ignoreUnknownKeys = true }
    val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    /** Returns parsed cards + the response [DailyResponse.date] field, or null on failure. */
    fun fetchDailyResponse(context: Context): Pair<List<Card>, String>? {
        val url = apiUrl(context)

        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: return null

        if (!response.isSuccessful) {
            Log.w(TAG, "API returned ${response.code}: $body")
            return null
        }

        val daily = json.decodeFromString<DailyResponse>(body)
        return daily.cards to daily.date
    }

    /** Fetches LC user info (including badge) from the Loli Commons API. */
    fun fetchUserInfo(
        context: Context,
        username: String,
        token: String,
    ): LcUserInfo? {
        val encodedUsername = Uri.encode(username)
        val url = "${getApiBaseUrl(context)}/api/v1/user/$encodedUsername"
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

        return try {
            Log.d(TAG, "Fetching LC user info for $username from $url")
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            if (!response.isSuccessful) {
                Log.w(TAG, "LC user info API returned ${response.code}: $responseBody")
                return null
            }
            Log.d(TAG, "LC user info response: $responseBody")
            json.decodeFromString<LcUserInfo>(responseBody)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch LC user info for $username", e)
            null
        }
    }

    fun updateBadge(
        context: Context,
        badge: String,
        token: String,
    ): Boolean {
        val url = "${getApiBaseUrl(context)}/api/v1/settings"
        val body = """{"badge":"$badge"}""".toRequestBody("application/json".toMediaType())
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()

        return try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "updateBadge returned ${response.code}: ${response.body?.string()}")
            }
            response.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update badge", e)
            false
        }
    }

    /**
     * Submits daily artwork metadata. Returns the OTC (one-time code) on success, or an error message.
     * Mirrors JS: lcClient.postDaily → POST /v1/daily/submit
     */
    fun submitDaily(
        context: Context,
        sourceUrl: String,
        artistName: String,
        artistUrl: String,
        characters: List<Long>,
        tags: String,
        comment: String,
        anonymous: Boolean,
        token: String,
    ): Result<String> {
        val url = "${getApiBaseUrl(context)}/api/v1/daily/submit"
        val charactersJson = characters.joinToString(",", "[", "]")
        val bodyStr =
            """{"sourceUrl":"${escapeJson(sourceUrl)}","artistName":"${escapeJson(artistName)}",""" +
                """"artistUrl":"${escapeJson(artistUrl)}","characters":$charactersJson,""" +
                """"tags":"${escapeJson(tags)}","comment":"${escapeJson(comment)}","anonymous":$anonymous}"""
        val body = bodyStr.toRequestBody("application/json".toMediaType())
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
        return try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (response.code == 429) {
                return Result.failure(Exception("已达到提交队列上限，过一段时间再来吧"))
            }
            if (!response.isSuccessful) {
                Log.w(TAG, "submitDaily returned ${response.code}: $responseBody")
                return Result.failure(Exception("嘶……好像网卡了或者服务器炸了……"))
            }
            val otc = json.decodeFromString<DailySubmitResponse>(responseBody).otc
            Result.success(otc)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to submit daily", e)
            Result.failure(e)
        }
    }

    /**
     * Uploads an image for a submitted daily artwork using a presigned URL.
     * Mirrors JS: lcClient.uploadDailyImage → POST /v1/daily/img-upload-presign + PUT
     */
    fun uploadDailyImage(
        context: Context,
        fileName: String,
        contentType: String,
        contentLength: Long,
        otc: String,
        imageBytes: ByteArray,
        token: String,
    ): Result<Unit> {
        // Step 1: Get presigned URL
        val presignUrl = "${getApiBaseUrl(context)}/api/v1/daily/img-upload-presign"
        val presignBody =
            """{"filename":"${escapeJson(fileName)}","contentType":"${escapeJson(contentType)}",""" +
                """"contentLength":$contentLength,"otc":"${escapeJson(otc)}"}"""
        val presignRequest =
            Request.Builder()
                .url(presignUrl)
                .header("User-Agent", USER_AGENT)
                .header("Authorization", "Bearer $token")
                .post(presignBody.toRequestBody("application/json".toMediaType()))
                .build()
        val presignResponse = httpClient.newCall(presignRequest).execute()
        val presignBodyStr = presignResponse.body?.string() ?: ""
        if (!presignResponse.isSuccessful) {
            Log.w(TAG, "presign returned ${presignResponse.code}: $presignBodyStr")
            return Result.failure(Exception("上传请求被拒绝"))
        }
        val signedUrl = json.decodeFromString<PresignResponse>(presignBodyStr).signedUrl

        // Step 2: PUT image to signed URL
        val putBody = imageBytes.toRequestBody(contentType.toMediaType())
        val putRequest = Request.Builder().url(signedUrl).put(putBody).build()
        val putResponse = httpClient.newCall(putRequest).execute()
        if (!putResponse.isSuccessful) {
            Log.w(TAG, "image PUT returned ${putResponse.code}")
            return Result.failure(Exception("上传失败"))
        }
        return Result.success(Unit)
    }

    /**
     * Resolves artist info from a known source URL (twitter/pixiv/bilibili).
     * Mirrors JS: lcClient.fetchDailyResolve → GET /v1/daily/resolve
     */
    fun resolveArtist(
        context: Context,
        type: String,
        rid: String,
    ): ArtistResolveResponse? {
        val url =
            "${getApiBaseUrl(context)}/api/v1/daily/resolve?type=${Uri.encode(type)}&rid=${Uri.encode(rid)}"
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
        return try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            json.decodeFromString<ArtistResolveResponse>(body)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve artist", e)
            null
        }
    }

    private val TWITTER_URL_RE = Regex("^https?://(?:www\\.)?(?:x|twitter)\\.com/([^/]+/status/\\d+)")

    private val PIXIV_URL_RE = Regex("^https?://(?:www\\.)?pixiv\\.net/(?:en/)?artworks/(\\d+)")

    private val BILIBILI_URL_RE = Regex("^https?://(?:www\\.)?bilibili\\.com/opus/(\\d+)")

    /**
     * Fetches the first image from a known source URL (twitter, pixiv, bilibili).
     * For bilibili, uses WebView to bypass captcha (context required).
     * Returns (imageBytes, mimeType) or null on failure / no image found.
     */
    suspend fun fetchSourceImage(context: Context? = null, url: String): Pair<ByteArray, String>? {
        // Bilibili → resolve via WebView (needs Context)
        val biliMatch = BILIBILI_URL_RE.find(url)
        if (biliMatch != null) {
            val opusId = biliMatch.groupValues[1]
            val imageUrl = if (context != null) resolveBilibiliImage(context, opusId) else null
            return imageUrl?.let { downloadImage(it) }
        }
        // Twitter / Pixiv → resolve via API
        val imageUrl = resolveSourceImageUrl(url) ?: return null
        return downloadImage(imageUrl)
    }

    private fun downloadImage(imageUrl: String): Pair<ByteArray, String>? {
        return try {
            val builder = Request.Builder().url(imageUrl).header("User-Agent", USER_AGENT)
            if (imageUrl.contains("pximg.net")) {
                builder.header("Referer", "https://www.pixiv.net/")
            }
            val response = httpClient.newCall(builder.build()).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Image download failed: ${response.code}")
                return null
            }
            val bytes = response.body?.bytes() ?: return null
            if (bytes.isEmpty()) return null
            val mime =
                response.header("Content-Type", "image/jpeg")
                    ?.split(";")?.first()?.trim() ?: "image/jpeg"
            bytes to mime
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download source image", e)
            null
        }
    }


    /**
     * Resolves bilibili opus image URL using a WebView to bypass captcha.
     * Loads the page with real WebView UA, extracts image from __INITIAL_STATE__.
     */
    suspend fun resolveBilibiliImage(context: Context, opusId: String): String? {
        val url = "https://www.bilibili.com/opus/$opusId"
        val result = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                handler.post {
                    if (!cont.isActive) return@post
                    val webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.blockNetworkImage = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, pageUrl: String?) {
                                if (!cont.isActive) return
                                view.evaluateJavascript(
                                    "(function(){var s=window.__INITIAL_STATE__;" +
                                        "if(!s)return null;" +
                                        "var j=JSON.stringify(s);" +
                                        "var m=j.match(/https?:\\/\\/i\\d+\\.hdslb\\.com\\/bfs\\/new_dyn\\/[^\"'\\\\\\s@]+/);" +
                                        "return m?m[0].replace('http://','https://'):null})()"
                                ) { value ->
                                    if (!cont.isActive) return@evaluateJavascript
                                    val cleaned = value?.removeSurrounding("\"")?.removeSurrounding("null")
                                    cont.resume(if (cleaned.isNullOrEmpty() || cleaned == "null") null else cleaned, onCancellation = { _, _, _ -> })
                                }
                            }
                        }
                    }
                    cont.invokeOnCancellation { webView.destroy() }
                    webView.loadUrl(url)
                }
            }
        }
        return result
    }
    private fun resolveSourceImageUrl(url: String): String? {
        // Twitter → vxtwitter API
        val twMatch = TWITTER_URL_RE.find(url)
        if (twMatch != null) {
            val tweetPath = twMatch.groupValues[1]
            return try {
                val apiUrl = "https://api.vxtwitter.com/$tweetPath"
                val request = Request.Builder().url(apiUrl).header("User-Agent", USER_AGENT).build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val root = json.parseToJsonElement(body).jsonObject
                val mediaArr = root["media_extended"]?.jsonArray ?: return null
                val rawUrl = mediaArr
                    .firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "image" }
                    ?.jsonObject?.get("url")?.jsonPrimitive?.content
                // Upgrade to max quality: pbs.twimg.com/media/{id}.jpg → ...?format=jpg&name=4096x4096
                rawUrl?.replace(Regex("^(https://pbs\\.twimg\\.com/media/[^.]+)\\.(\\w+)$")) {
                    "${it.groupValues[1]}?format=${it.groupValues[2]}&name=4096x4096"
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve source image URL via vxtwitter", e)
                null
            }
        }
        // Pixiv → /ajax/illust/{id}/pages API (no auth needed)
        val pixivMatch = PIXIV_URL_RE.find(url)
        if (pixivMatch != null) {
            val illustId = pixivMatch.groupValues[1]
            return try {
                val apiUrl = "https://www.pixiv.net/ajax/illust/$illustId/pages"
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://www.pixiv.net/")
                    .build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val pages = json.parseToJsonElement(body).jsonObject["body"]?.jsonArray ?: return null
                // Prefer "regular" (1200px) to stay under 3 MB limit; fall back to "original"
                val urls = pages.firstOrNull()?.jsonObject?.get("urls")?.jsonObject ?: return null
                urls["regular"]?.jsonPrimitive?.content
                    ?: urls["original"]?.jsonPrimitive?.content
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve source image URL via pixiv", e)
                null
            }
        }
        return null
    }

    internal fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        .replace("\t", "\\t")
}
