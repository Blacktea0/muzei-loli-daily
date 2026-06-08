package me.eroi.lolidaily.muzei.api.link

import android.content.Context
import android.util.Log
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.model.ArtistResolveResponse

private const val TAG = "SourceLinkParserRegistry"

/**
 * Registry of all known [SourceLinkParser] implementations.
 * Dispatches URL matching and operations to the correct parser.
 */
object SourceLinkParserRegistry {
    private val parsers = listOf(
        TwitterParser,
        PixivParser,
        BilibiliParser,
    )

    /**
     * Matches [url] against all registered parsers.
     * Returns the first matching [SourceMatch], or null.
     */
    fun match(url: String): SourceMatch? {
        for (parser in parsers) {
            parser.match(url)?.let { return it }
        }
        return null
    }

    /**
     * Returns the canonical desktop URL for [url] by delegating to the matching parser.
     * Returns null if no parser matches.
     */
    fun canonicalUrl(url: String): String? {
        for (parser in parsers) {
            if (parser.match(url) != null) return parser.canonicalUrl(url)
        }
        return null
    }

    /**
     * Fetches the first image from a known source URL.
     * Returns (imageBytes, mimeType) or null.
     */
    suspend fun fetchSourceImage(context: Context, url: String): Pair<ByteArray, String>? {
        for (parser in parsers) {
            if (parser.match(url) != null) {
                return parser.fetchSourceImage(context, url)
            }
        }
        return null
    }

    /**
     * Resolves artist info for a known source type and resource ID.
     * Falls back to the Loli Commons /v1/daily/resolve API.
     */
    suspend fun resolveArtist(
        context: Context,
        type: String,
        resourceId: String,
    ): ArtistResolveResponse? {
        val parser = parsers.find { it.type == type }
        if (parser != null) {
            return parser.resolveArtist(context, resourceId)
        }
        // Fallback: call API directly for unknown types
        return LoliApiClient.resolveArtist(context, type, resourceId)
    }
}
