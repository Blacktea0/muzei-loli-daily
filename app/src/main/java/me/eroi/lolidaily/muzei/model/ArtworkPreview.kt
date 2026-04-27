package me.eroi.lolidaily.muzei.model

import android.net.Uri

/**
 * Combines a cached artwork URI with its API metadata
 * for rich display in the settings gallery.
 */
data class ArtworkPreview(
    val uri: Uri,
    val filename: String,
    val artistName: String,
    val comment: String,
    val tags: String,
    val characterNames: List<String>,
    val sourceUrl: String,
    val artistUrl: String,
    val date: String,
)
