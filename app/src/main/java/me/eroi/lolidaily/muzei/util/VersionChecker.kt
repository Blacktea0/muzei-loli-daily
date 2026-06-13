package me.eroi.lolidaily.muzei.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit
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
    private const val GITHUB_RELEASES_LATEST_URL = "https://github.com/Blacktea0/muzei-loli-daily/releases/latest"
    private const val GITHUB_RELEASES_URL = "https://github.com/Blacktea0/muzei-loli-daily/releases"
    private const val CACHE_KEY_LATEST_VERSION = "cached_latest_version"
    private const val CACHE_KEY_LATEST_URL = "cached_latest_url"
    private const val CACHE_KEY_RELEASE_NOTES = "cached_release_notes"
    private const val CACHE_KEY_TIMESTAMP = "version_check_timestamp"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

    @Serializable
    private data class GitHubRelease(
        val tag_name: String? = null,
        val html_url: String? = null,
        val body: String? = null,
    )

    data class UpdateCheckResult(
        val hasUpdate: Boolean,
        val latestVersion: String,
        val downloadUrl: String,
        val releaseNotes: String? = null,
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
     * @param forceRefresh bypass the cache and always fetch from the API.
     */
    suspend fun checkForUpdate(context: Context, forceRefresh: Boolean = false): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val prefs =
                context.getSharedPreferences(
                    LoliDailyArtWorker.PREFS_NAME,
                    Context.MODE_PRIVATE,
                )

            val cachedTimestamp = prefs.getLong(CACHE_KEY_TIMESTAMP, 0)
            val now = System.currentTimeMillis()

            if (!forceRefresh && now - cachedTimestamp < CACHE_TTL_MS) {
                val cachedVersion = prefs.getString(CACHE_KEY_LATEST_VERSION, null)
                if (cachedVersion != null) {
                    return@withContext UpdateCheckResult(
                        hasUpdate = isNewerVersion(cachedVersion, BuildConfig.VERSION_NAME),
                        latestVersion = cachedVersion,
                        downloadUrl = prefs.getString(CACHE_KEY_LATEST_URL, null) ?: GITHUB_RELEASES_URL,
                        releaseNotes = prefs.getString(CACHE_KEY_RELEASE_NOTES, null),
                    )
                }
            }

            // Try GitHub API first; fall back to HTML releases page on failure (e.g. rate limit)
            val result = tryApiCheck() ?: tryHtmlFallback()

            prefs.edit {
                putString(CACHE_KEY_LATEST_VERSION, result.latestVersion)
                putString(CACHE_KEY_LATEST_URL, result.downloadUrl)
                putString(CACHE_KEY_RELEASE_NOTES, result.releaseNotes)
                putLong(CACHE_KEY_TIMESTAMP, now)
            }

            result
        }

    /**
     * Try the GitHub REST API. Returns null if the request fails (rate limit, network error, etc.)
     * so the caller can fall back to [tryHtmlFallback].
     */
    private fun tryApiCheck(): UpdateCheckResult? {
        return try {
            val request =
                Request.Builder()
                    .url(GITHUB_API_URL)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GitHub API returned ${response.code}, falling back to HTML")
                    return null
                }

                val body = response.body?.string() ?: return null
                val release = json.decodeFromString<GitHubRelease>(body)
                val tagName = release.tag_name ?: return null
                val versionName = tagName.removePrefix("v")
                val downloadUrl = release.html_url ?: GITHUB_RELEASES_URL

                UpdateCheckResult(
                    hasUpdate = isNewerVersion(versionName, BuildConfig.VERSION_NAME),
                    latestVersion = versionName,
                    downloadUrl = downloadUrl,
                    releaseNotes = release.body?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "GitHub API check failed, falling back to HTML", e)
            null
        }
    }

    /**
     * Fallback: request /releases/latest which 302-redirects to /releases/tag/vX.Y.Z.
     * OkHttp follows redirects by default, so we can read the final URL to extract the version.
     * This endpoint is not subject to the same aggressive rate limiting as the REST API.
     */
    private fun tryHtmlFallback(): UpdateCheckResult {
        return try {
            val request =
                Request.Builder()
                    .url(GITHUB_RELEASES_LATEST_URL)
                    .build()

            client.newCall(request).execute().use { response ->
                // Even if the page returns 200, the effective URL after redirect holds the tag
                val effectiveUrl = response.request.url.toString()
                val tagPrefix = "/releases/tag/"
                val tagIndex = effectiveUrl.indexOf(tagPrefix)

                if (tagIndex < 0) {
                    Log.w(TAG, "Could not extract version from redirect URL: $effectiveUrl")
                    return UpdateCheckResult(
                        hasUpdate = false,
                        latestVersion = BuildConfig.VERSION_NAME,
                        downloadUrl = GITHUB_RELEASES_URL,
                        releaseNotes = null,
                    )
                }

                val tagName = effectiveUrl.substring(tagIndex + tagPrefix.length)
                val versionName = tagName.removePrefix("v")
                val downloadUrl = effectiveUrl

                UpdateCheckResult(
                    hasUpdate = isNewerVersion(versionName, BuildConfig.VERSION_NAME),
                    latestVersion = versionName,
                    downloadUrl = downloadUrl,
                    releaseNotes = null,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTML fallback check failed", e)
            UpdateCheckResult(
                hasUpdate = false,
                latestVersion = BuildConfig.VERSION_NAME,
                downloadUrl = GITHUB_RELEASES_URL,
                releaseNotes = null,
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
