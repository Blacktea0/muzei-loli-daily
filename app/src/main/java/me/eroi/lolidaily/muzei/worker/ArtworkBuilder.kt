package me.eroi.lolidaily.muzei.worker

import android.content.Context
import android.net.Uri
import com.google.android.apps.muzei.api.provider.Artwork
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.model.Card
import me.eroi.lolidaily.muzei.util.Md5
import java.io.File

object ArtworkBuilder {
    fun buildArtwork(
        context: Context,
        card: Card,
        dir: File,
        apiDate: String,
        download: Boolean,
    ): Artwork? {
        if (card.imgUrl.isBlank()) return null

        val token = Md5.hash(card.imgUrl)
        val localUri =
            if (download) {
                ImageDownloader.downloadImage(context, card.imgUrl, token, dir)
                    ?: ImageDownloader.getCachedUri(context, token, dir)
            } else {
                ImageDownloader.getCachedUri(context, token, dir)
            } ?: return null

        return Artwork.Builder()
            .token(token)
            .title(buildTitle(card, apiDate))
            .byline(buildByline(card))
            .attribution(buildAttribution(card, apiDate))
            .persistentUri(localUri)
            .webUri(card.sourceUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
            .metadata(buildMetadata(card))
            .build()
    }

    fun buildArtworkFromCache(
        context: Context,
        card: Card,
        dir: File,
        apiDate: String,
    ): Artwork? {
        return buildArtwork(context, card, dir, apiDate, download = false)
    }

    fun buildTitle(
        card: Card,
        apiDate: String,
    ): String {
        return card.comment.ifBlank {
            if (card.tags.isNotBlank()) "$apiDate [${card.tags}]" else apiDate
        }
    }

    fun buildByline(card: Card): String {
        return card.artistName.ifBlank { "Unknown Artist" }
    }

    fun buildAttribution(
        card: Card,
        apiDate: String,
    ): String {
        val parts = mutableListOf<String>()
        if (card.tags.isNotBlank()) parts.add("[${card.tags}]")
        if (card.characterNames.isNotEmpty()) parts.add(card.characterNames.joinToString(", "))
        card.suggestedBy?.let { parts.add("by ${it.nickname}") }
        parts.add(apiDate)
        if (card.sourceUrl.isNotBlank()) parts.add(card.sourceUrl)
        return parts.joinToString("  ·  ")
    }

    fun buildMetadata(card: Card): String {
        return LoliApiClient.json.encodeToString(Card.serializer(), card)
    }
}
