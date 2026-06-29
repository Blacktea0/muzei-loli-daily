package me.eroi.lolidaily.muzei.api.link

import me.eroi.lolidaily.muzei.api.LoliApiClient
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "LinkUtils"

private val STRIPPED_PARAMS = setOf(
    "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
    "ref", "spm_id_from", "trackid", "from_source", "msource",
    "source", "medium", "campaign", "vd_source", "unique_k",
    "share_from", "share_medium", "share_plat", "share_session_id",
    "share_source", "share_tag", "share_type", "share_id",
    "plat_id", "spmid", "timestamp",
)

private val URL_PATTERN = Regex("https?://\\S+")

/**
 * Resolves a short URL (e.g. b23.tv) to its redirect destination.
 * Returns null on failure or if the URL is not a short link.
 */
private val noRedirectClient = OkHttpClient.Builder()
    .followRedirects(false)
    .build()

fun resolveShortLink(url: String): String? {
    if (!isShortLink(url)) return null
    return try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", LoliApiClient.USER_AGENT)
            .head()
            .build()
        val response = noRedirectClient.newCall(request).execute()
        val location = response.header("Location")
        response.close()
        location
    } catch (e: Exception) {
        Log.w(TAG, "Failed to resolve short link: $url", e)
        null
    }
}

fun isShortLink(url: String): Boolean {
    val host = try {
        java.net.URI(url).host?.lowercase() ?: return false
    } catch (_: Exception) {
        return false
    }
    return host == "b23.tv" || host == "t.co" || host == "bit.ly"
}

/**
 * Strips common tracking query parameters from a URL.
 * Preserves structural/query params (e.g. bilibili's ?opus_id=).
 */
fun stripTrackingParams(url: String): String {
    val uri = try {
        java.net.URI(url)
    } catch (_: Exception) {
        return url
    }
    val query = uri.query ?: return url
    val kept = query.split("&")
        .filter { param ->
            val key = param.substringBefore("=", "")
            key !in STRIPPED_PARAMS
        }
    val newQuery = if (kept.isEmpty()) null else kept.joinToString("&")
    return java.net.URI(
        uri.scheme, uri.authority, uri.path, newQuery, uri.fragment
    ).toString()
}

/**
 * Extracts the first URL from mixed text (e.g. shared content that includes
 * a title or description alongside the URL).  Returns null if no URL found.
 */
fun extractUrl(text: String): String? {
    val m = URL_PATTERN.find(text) ?: return null
    val url = m.value

    // Handle Twitter/X share links: replace "/i/status/" with the actual username if found in text
    val twitterIStatusRegex = Regex("^https?://(?:www\\.|mobile\\.)?(?:x|twitter)\\.com/i/status/\\d+")
    if (twitterIStatusRegex.containsMatchIn(url)) {
        val usernameRegex = Regex("\\(@([a-zA-Z0-9_]{1,15})\\)")
        val usernameMatch = usernameRegex.find(text)
        if (usernameMatch != null) {
            val username = usernameMatch.groupValues[1]
            return url.replace("/i/status/", "/$username/status/")
        }
    }
    return url
}

