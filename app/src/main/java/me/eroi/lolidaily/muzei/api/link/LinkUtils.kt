package me.eroi.lolidaily.muzei.api.link

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "LinkUtils"

private val STRIPPED_PARAMS = setOf(
    "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
    "ref", "spm_id_from", "trackid", "from_source", "msource",
    "source", "medium", "campaign", "vd_source", "unique_k",
)

/**
 * Resolves a short URL (e.g. b23.tv) to its redirect destination.
 * Returns null on failure or if the URL is not a short link.
 */
fun resolveShortLink(client: OkHttpClient, url: String): String? {
    if (!isShortLink(url)) return null
    return try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "LoliDaily/1.0 (Android)")
            .head()
            .build()
        val response = client.newCall(request).execute()
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
 * Full pipeline: resolve short link → strip tracking params.
 * Returns the cleaned URL, or the original if no changes needed.
 */
fun cleanUrl(client: OkHttpClient, url: String): String {
    val resolved = resolveShortLink(client, url) ?: url
    return stripTrackingParams(resolved)
}
