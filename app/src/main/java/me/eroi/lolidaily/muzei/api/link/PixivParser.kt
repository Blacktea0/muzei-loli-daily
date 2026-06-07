package me.eroi.lolidaily.muzei.api.link

import android.content.Context
import android.util.Log
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.model.ArtistResolveResponse
import okhttp3.Request
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "PixivParser"

object PixivParser : SourceLinkParser {
    override val type = "pixiv"

    private val URL_RE = Regex("^https?://(?:www\\.)?pixiv\\.net/(?:en/)?artworks/(\\d+)")

    override fun match(url: String): SourceMatch? {
        val m = URL_RE.find(url) ?: return null
        return SourceMatch(type, m.groupValues[1])
    }

    override suspend fun fetchSourceImage(context: Context, url: String): Pair<ByteArray, String>? {
        val m = URL_RE.find(url) ?: return null
        val illustId = m.groupValues[1]
        val imageUrl = resolveImageUrl(illustId) ?: return null
        return LoliApiClient.downloadImage(imageUrl, referer = "https://www.pixiv.net/")
    }

    override fun resolveArtist(context: Context, resourceId: String): ArtistResolveResponse? {
        return LoliApiClient.resolveArtist(context, type, resourceId)
    }

    private fun resolveImageUrl(illustId: String): String? {
        return try {
            val apiUrl = "https://www.pixiv.net/ajax/illust/$illustId/pages"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", LoliApiClient.USER_AGENT)
                .header("Referer", "https://www.pixiv.net/")
                .build()
            val response = LoliApiClient.httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val pages = LoliApiClient.json.parseToJsonElement(body).jsonObject["body"]?.jsonArray ?: return null
            val urls = pages.firstOrNull()?.jsonObject?.get("urls")?.jsonObject ?: return null
            urls["regular"]?.jsonPrimitive?.content
                ?: urls["original"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve pixiv image URL", e)
            null
        }
    }
}
