package me.eroi.lolidaily.muzei.api

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.serialization.json.Json
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.model.Card
import me.eroi.lolidaily.muzei.model.DailyResponse
import me.eroi.lolidaily.muzei.model.LcUserInfo
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
}
