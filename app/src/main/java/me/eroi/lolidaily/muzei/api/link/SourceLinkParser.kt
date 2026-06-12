package me.eroi.lolidaily.muzei.api.link

import android.content.Context
import me.eroi.lolidaily.muzei.model.ArtistResolveResponse

/**
 * Result of matching a URL against a [SourceLinkParser].
 */
data class SourceMatch(
    val type: String,
    val resourceId: String,
)

/**
 * A single image from a multi-image source, with separate thumbnail and full-quality URLs.
 *
 * @param thumbUrl Low-resolution URL suitable for grid preview (may be the same as [fullUrl] if
 *   the platform does not provide a distinct thumbnail).
 * @param fullUrl  Full-quality URL to download when the user selects this image.
 */
data class SourceImageVariant(
    val thumbUrl: String,
    val fullUrl: String,
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
     * Converts a platform URL to its canonical desktop form.
     * Handles mobile subdomains (m.bilibili.com → www.bilibili.com),
     * platform short links (twitter.com → x.com), and strips tracking params.
     * Returns the canonical URL, or the original if no conversion is needed.
     */
    fun canonicalUrl(url: String): String = url

    /**
     * Fetches the first image from the source page.
     * Returns (imageBytes, mimeType) or null.
     */
    suspend fun fetchSourceImage(context: Context, url: String): Pair<ByteArray, String>?

    /**
     * Fetches all image URLs from the source page **without downloading them**.
     * Returns a list of [SourceImageVariant] (thumbnail + full-quality URL pairs),
     * or `null` if the platform does not support multi-image enumeration.
     *
     * The default implementation returns `null`, meaning the caller should fall back
     * to the single-image [fetchSourceImage] path.
     */
    suspend fun fetchSourceImageUrls(context: Context, url: String): List<SourceImageVariant>? = null

    /**
     * Resolves artist name and profile URL from the resource ID.
     * Calls the Loli Commons /v1/daily/resolve API.
     */
    suspend fun resolveArtist(context: Context, resourceId: String): ArtistResolveResponse?
}
