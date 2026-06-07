package me.eroi.lolidaily.muzei.api.link

import android.content.Context
import me.eroi.lolidaily.muzei.model.ArtistResolveResponse
import okhttp3.OkHttpClient

/**
 * Result of matching a URL against a [SourceLinkParser].
 */
data class SourceMatch(
    val type: String,
    val resourceId: String,
)

/**
 * Parses source URLs from specific platforms.
 *
 * Each implementation handles one domain: URL matching, image resolution,
 * and artist resolution. The [SourceLinkParserRegistry] dispatches to
 * the correct parser based on URL patterns.
 */
interface SourceLinkParser {
    /**
     * The source type identifier (e.g. "twitter", "pixiv", "bilibili").
     * Used as the `type` parameter in API calls.
     */
    val type: String

    /**
     * Tries to match [url] against this parser's known URL patterns.
     * Returns a [SourceMatch] with the extracted resource ID, or null.
     */
    fun match(url: String): SourceMatch?

    /**
     * Fetches the first image from the source page.
     * Returns (imageBytes, mimeType) or null.
     */
    suspend fun fetchSourceImage(context: Context, url: String): Pair<ByteArray, String>?

    /**
     * Resolves artist name and profile URL from the resource ID.
     * Calls the Loli Commons /v1/daily/resolve API.
     */
    fun resolveArtist(context: Context, resourceId: String): ArtistResolveResponse?
}
