package me.eroi.lolidaily.muzei.api

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.serialization.json.Json
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.api.link.SourceLinkParserRegistry
import me.eroi.lolidaily.muzei.model.ArtistResolveResponse
import me.eroi.lolidaily.muzei.model.Card
import me.eroi.lolidaily.muzei.model.DailyResponse
import me.eroi.lolidaily.muzei.model.DailySubmitResponse
import me.eroi.lolidaily.muzei.model.LcUserInfo
import me.eroi.lolidaily.muzei.model.PresignResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object LoliApiClient {
    private const val TAG = "LoliApiClient"
    internal const val USER_AGENT = "LoliDaily/1.0 (Android)"

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
    suspend fun resolveArtist(
        context: Context,
        type: String,
        rid: String,
    ): ArtistResolveResponse? {
        val url =
            "${getApiBaseUrl(context)}/api/v1/daily/resolve?type=${Uri.encode(type)}&rid=${Uri.encode(rid)}"
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
        return try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "resolveArtist returned ${response.code}")
                return null
            }
            val body = response.body?.string() ?: return null
            json.decodeFromString<ArtistResolveResponse>(body)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve artist", e)
            null
        }
    }

    /**
     * Fetches the first image from a known source URL.
     * Delegates to [SourceLinkParserRegistry] for per-platform resolution.
     * Returns (imageBytes, mimeType) or null on failure.
     */
    suspend fun fetchSourceImage(context: Context, url: String): Pair<ByteArray, String>? {
        return SourceLinkParserRegistry.fetchSourceImage(context, url)
    }

    /**
     * Fetches all image URLs (thumbnail + full-quality pairs) from a known source URL.
     * Returns a list of [me.eroi.lolidaily.muzei.api.link.SourceImageVariant], or null.
     */
    suspend fun fetchSourceImageUrls(context: Context, url: String): List<me.eroi.lolidaily.muzei.api.link.SourceImageVariant>? {
        return SourceLinkParserRegistry.fetchSourceImageUrls(context, url)
    }

    /**
     * Downloads an image from [imageUrl].
     * Optionally sets a Referer header (needed for pixiv) and Cookie header (for authenticated pixiv).
     */
    internal fun downloadImage(imageUrl: String, referer: String? = null, cookie: String? = null): Pair<ByteArray, String>? {
        return try {
            Log.d(TAG, "downloadImage: url=${imageUrl.take(80)}, hasCookie=${cookie != null}")
            val builder = Request.Builder().url(imageUrl).header("User-Agent", USER_AGENT)
            if (referer != null) {
                builder.header("Referer", referer)
            } else if (imageUrl.contains("pximg.net")) {
                builder.header("Referer", "https://www.pixiv.net/")
            }
            if (cookie != null) {
                builder.header("Cookie", cookie)
            }
            val response = httpClient.newCall(builder.build()).execute()
            Log.d(TAG, "downloadImage response: ${response.code}, contentLength=${response.header("Content-Length")}")
            if (!response.isSuccessful) {
                Log.w(TAG, "Image download failed: ${response.code}")
                return null
            }
            val bytes = response.body?.bytes() ?: return null
            if (bytes.isEmpty()) return null
            val mime =
                response.header("Content-Type", "image/jpeg")
                    ?.split(";")?.first()?.trim() ?: "image/jpeg"
            Log.d(TAG, "downloadImage success: ${bytes.size} bytes, $mime")
            bytes to mime
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download image", e)
            null
        }
    }
    internal fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        .replace("\t", "\\t")
}