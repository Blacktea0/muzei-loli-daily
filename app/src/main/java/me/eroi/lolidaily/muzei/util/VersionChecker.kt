package me.eroi.lolidaily.muzei.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.eroi.lolidaily.muzei.BuildConfig
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object VersionChecker {
    private const val TAG = "VersionChecker"
    private const val GITHUB_API_URL = "https://api.github.com/repos/Blacktea0/muzei-loli-daily/releases/latest"
    private const val GITHUB_RELEASES_URL = "https://github.com/Blacktea0/muzei-loli-daily/releases"
    private const val CACHE_KEY_LATEST_VERSION = "cached_latest_version"
    private const val CACHE_KEY_LATEST_URL = "cached_latest_url"
    private const val CACHE_KEY_TIMESTAMP = "version_check_timestamp"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

    @Serializable
    private data class GitHubRelease(
        val tag_name: String? = null,
        val html_url: String? = null,
    )

    data class UpdateCheckResult(
        val hasUpdate: Boolean,
        val latestVersion: String,
        val downloadUrl: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    /**
     * Check for updates by comparing the current version with the latest GitHub release.
     * Uses a 24-hour cache to avoid excessive API calls.
     */
    suspend fun checkForUpdate(context: Context): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val prefs =
                context.getSharedPreferences(
                    LoliDailyArtWorker.PREFS_NAME,
                    Context.MODE_PRIVATE,
                )

            val cachedTimestamp = prefs.getLong(CACHE_KEY_TIMESTAMP, 0)
            val now = System.currentTimeMillis()

            if (now - cachedTimestamp < CACHE_TTL_MS) {
                val cachedVersion = prefs.getString(CACHE_KEY_LATEST_VERSION, null)
                if (cachedVersion != null) {
                    return@withContext UpdateCheckResult(
                        hasUpdate = isNewerVersion(cachedVersion, BuildConfig.VERSION_NAME),
                        latestVersion = cachedVersion,
                        downloadUrl = prefs.getString(CACHE_KEY_LATEST_URL, null) ?: GITHUB_RELEASES_URL,
                    )
                }
            }

            try {
                val request =
                    Request.Builder()
                        .url(GITHUB_API_URL)
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "GitHub API returned ${response.code}")
                        return@withContext UpdateCheckResult(
                            hasUpdate = false,
                            latestVersion = BuildConfig.VERSION_NAME,
                            downloadUrl = GITHUB_RELEASES_URL,
                        )
                    }

                    val body =
                        response.body?.string()
                            ?: return@withContext UpdateCheckResult(
                                hasUpdate = false,
                                latestVersion = BuildConfig.VERSION_NAME,
                                downloadUrl = GITHUB_RELEASES_URL,
                            )

                    val release = json.decodeFromString<GitHubRelease>(body)
                    val tagName =
                        release.tag_name
                            ?: return@withContext UpdateCheckResult(
                                hasUpdate = false,
                                latestVersion = BuildConfig.VERSION_NAME,
                                downloadUrl = GITHUB_RELEASES_URL,
                            )

                    val versionName = tagName.removePrefix("v")
                    val downloadUrl = release.html_url ?: GITHUB_RELEASES_URL

                    prefs.edit()
                        .putString(CACHE_KEY_LATEST_VERSION, versionName)
                        .putString(CACHE_KEY_LATEST_URL, downloadUrl)
                        .putLong(CACHE_KEY_TIMESTAMP, now)
                        .apply()

                    UpdateCheckResult(
                        hasUpdate = isNewerVersion(versionName, BuildConfig.VERSION_NAME),
                        latestVersion = versionName,
                        downloadUrl = downloadUrl,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for updates", e)
                UpdateCheckResult(
                    hasUpdate = false,
                    latestVersion = BuildConfig.VERSION_NAME,
                    downloadUrl = GITHUB_RELEASES_URL,
                )
            }
        }

    /**
     * Compare two semver version strings.
     * @return true if [remote] is newer than [current].
     */
    fun isNewerVersion(
        remote: String,
        current: String,
    ): Boolean {
        val remoteParts = parseVersion(remote)
        val currentParts = parseVersion(current)
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun parseVersion(version: String): List<Int> {
        val cleaned = version.removePrefix("v").removePrefix("V").substringBefore("-")
        return cleaned.split(".").map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }
}
