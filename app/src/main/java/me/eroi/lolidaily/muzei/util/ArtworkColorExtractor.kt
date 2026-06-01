package me.eroi.lolidaily.muzei.util

import android.content.Context
import android.graphics.BitmapFactory
import androidx.palette.graphics.Palette
import java.io.File

object ArtworkColorExtractor {
    private val cache = mutableMapOf<String, Int>()

    fun extract(
        context: Context,
        filename: String,
        fallbackColor: Int,
    ): Int {
        cache[filename]?.let { return it }

        val file = File(context.filesDir, "artworks/$filename")
        if (!file.exists()) return fallbackColor

        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return fallbackColor

        val palette = Palette.from(bitmap).generate()
        bitmap.recycle()

        val color =
            palette.vibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: fallbackColor

        cache[filename] = color
        return color
    }
}
