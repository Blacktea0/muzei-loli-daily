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

    override suspend fun fetchSourceImage(context: Context, url: String): Pair<ByteArray, String>? {
        val m = URL_RE.find(url) ?: return null
        val tweetPath = m.groupValues[1]
        val imageUrl = resolveImageUrl(tweetPath) ?: return null
        return LoliApiClient.downloadImage(imageUrl)
    }

    override fun resolveArtist(context: Context, resourceId: String): ArtistResolveResponse? {
        return LoliApiClient.resolveArtist(context, type, resourceId)
    }

    private fun resolveImageUrl(tweetPath: String): String? {
        return try {
            val apiUrl = "https://api.vxtwitter.com/$tweetPath"
            val request = Request.Builder().url(apiUrl).header("User-Agent", LoliApiClient.USER_AGENT).build()
            val response = LoliApiClient.httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val root = LoliApiClient.json.parseToJsonElement(body).jsonObject
            val mediaArr = root["media_extended"]?.jsonArray ?: return null
            val rawUrl = mediaArr
                .firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "image" }
                ?.jsonObject?.get("url")?.jsonPrimitive?.content
            // Upgrade to max quality
            rawUrl?.replace(Regex("^(https://pbs\\.twimg\\.com/media/[^.]+)\\.(\\w+)$")) {
                "${it.groupValues[1]}?format=${it.groupValues[2]}&name=4096x4096"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve twitter image URL", e)
            null
        }
    }
}
