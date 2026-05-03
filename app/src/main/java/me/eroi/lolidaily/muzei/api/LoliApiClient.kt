package me.eroi.lolidaily.muzei.api

import android.content.Context
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import me.eroi.lolidaily.muzei.BuildConfig
import me.eroi.lolidaily.muzei.model.Card
import me.eroi.lolidaily.muzei.model.DailyResponse
import okhttp3.OkHttpClient
import okhttp3.Request

object LoliApiClient {
    private const val TAG = "LoliApiClient"
    private const val USER_AGENT = "LoliDaily/1.0 (Android)"

    val API_URL
        get() = "${BuildConfig.API_BASE_URL}/api/v1/daily?badge=LC%20YJ-ES-NC-PG"

    val REACT_API_URL
        get() = "${BuildConfig.API_BASE_URL}/api/v1/daily/react?badge=LC%20YJ-ES-NC-PG"

    val json = Json { ignoreUnknownKeys = true }
    val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    /** Returns parsed cards + the response [DailyResponse.date] field, or null on failure. */
    fun fetchDailyResponse(context: Context): Pair<List<Card>, String>? {
        val prefs =
            context.getSharedPreferences(
                me.eroi.lolidaily.muzei.LoliDailyArtWorker.PREFS_NAME,
                Context.MODE_PRIVATE,
            )
        val useMock = prefs.getBoolean("debug_use_mock_api", false)
        val url =
            if (useMock) {
                prefs.getString("debug_mock_api_url", null) ?: API_URL
            } else {
                API_URL
            }

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
