package me.eroi.lolidaily.muzei.api.link

import android.content.Context
import android.util.Log
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.model.ArtistResolveResponse
import okhttp3.Request
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "TwitterParser"

object TwitterParser : SourceLinkParser {
    override val type = "twitter"

    private val URL_RE = Regex("^https?://(?:www\\.)?(?:x|twitter)\\.com/([^/]+/status/\\d+)")

    override fun match(url: String): SourceMatch? {
        val m = URL_RE.find(url) ?: return null
        return SourceMatch(type, m.groupValues[1])
    }

    private val I_STATUS_RE = Regex("^(https?://(?:www\\.)?(?:x|twitter)\\.com/)i(/status/\\d+)")

    override fun canonicalUrl(url: String): String {
        var canonical = url
            .replace("://twitter.com/", "://x.com/")
            .replace("://mobile.twitter.com/", "://x.com/")
        canonical = stripTrackingParams(canonical)
        // Resolve /i/status/ID → /<screenName>/status/ID via vxtwitter API
        val im = I_STATUS_RE.find(canonical)
        if (im != null) {
            resolveScreenName(canonical)?.let { screenName ->
                return im.groupValues[1] + screenName + im.groupValues[2]
            }
        }
        return canonical
    }

    /**
     * Calls the vxtwitter API to get the tweet author's screen name.
     * Returns the screen name (e.g. "AKEndfieldJP"), or null on failure.
     */
    private fun resolveScreenName(url: String): String? {
        return try {
            val apiUrl = "https://api.vxtwitter.com/" + url.substringAfter("x.com/")
            val request = Request.Builder().url(apiUrl).header("User-Agent", LoliApiClient.USER_AGENT).build()
            val response = LoliApiClient.httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val root = LoliApiClient.json.parseToJsonElement(body).jsonObject
            root["user_screen_name"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve screen name", e)
            null
        }
    }

    override suspend fun fetchSourceImage(context: Context, url: String): Pair<ByteArray, String>? {
        val variants = fetchSourceImageUrls(context, url)
        val first = variants?.firstOrNull() ?: return null
        return LoliApiClient.downloadImage(first.fullUrl)
    }

    override suspend fun fetchSourceImageUrls(context: Context, url: String): List<SourceImageVariant>? {
        val m = URL_RE.find(url) ?: return null
        val tweetPath = m.groupValues[1]
        return resolveImageUrls(tweetPath)
    }

    override suspend fun resolveArtist(context: Context, resourceId: String): ArtistResolveResponse? {
        return LoliApiClient.resolveArtist(context, type, resourceId)
    }

    /**
     * Resolves all image URLs from a tweet via the vxtwitter API.
     * Returns a list of [SourceImageVariant] (thumb + full URL pairs), or null.
     */
    private fun resolveImageUrls(tweetPath: String): List<SourceImageVariant>? {
        return try {
            val apiUrl = "https://api.vxtwitter.com/$tweetPath"
            val request = Request.Builder().url(apiUrl).header("User-Agent", LoliApiClient.USER_AGENT).build()
            val response = LoliApiClient.httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val root = LoliApiClient.json.parseToJsonElement(body).jsonObject
            val mediaArr = root["media_extended"]?.jsonArray ?: return null
            mediaArr.mapNotNull { el ->
                val obj = el.jsonObject
                if (obj["type"]?.jsonPrimitive?.content != "image") return@mapNotNull null
                val rawUrl = obj["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                // Upgrade to max quality for full URL
                val fullUrl = rawUrl.replace(Regex("^(https://pbs\\.twimg\\.com/media/[^.]+)\\.(\\w+)$")) {
                    "${it.groupValues[1]}?format=${it.groupValues[2]}&name=4096x4096"
                }
                // Use smaller variant for thumbnail
                val thumbUrl = rawUrl.replace(Regex("^(https://pbs\\.twimg\\.com/media/[^.]+)\\.(\\w+)$")) {
                    "${it.groupValues[1]}?format=${it.groupValues[2]}&name=small"
                }
                SourceImageVariant(thumbUrl, fullUrl)
            }.ifEmpty { null }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve twitter image URLs", e)
            null
        }
    }
}
