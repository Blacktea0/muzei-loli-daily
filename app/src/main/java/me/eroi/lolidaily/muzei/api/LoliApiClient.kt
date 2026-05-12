package me.eroi.lolidaily.muzei.api

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.model.Card
import me.eroi.lolidaily.muzei.model.DailyResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object LoliApiClient {
    private const val TAG = "LoliApiClient"
    private const val USER_AGENT = "LoliDaily/1.0 (Android)"

    const val DEFAULT_API_BASE_URL = "https://loliconey.tsuki.ga"
    const val KEY_DEBUG_API_BASE_URL = "debug_api_base_url"

    val KNOWN_SERVERS =
        listOf(
            "https://loliconey.tsuki.ga",
            "https://lc-coney.deno.dev",
            "https://next.bgm.tv",
        )

    fun getApiBaseUrl(context: Context): String {
        val prefs =
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEBUG_API_BASE_URL, null)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_API_BASE_URL
    }

    fun apiUrl(context: Context) = "${getApiBaseUrl(context)}/api/v1/daily?badge=LC%20YJ-ES-NC-PG"

    fun reactApiUrl(context: Context) = "${getApiBaseUrl(context)}/api/v1/daily/react?badge=LC%20YJ-ES-NC-PG"

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
}
