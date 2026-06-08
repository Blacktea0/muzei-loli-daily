package me.eroi.lolidaily.muzei.api.decoder

import android.graphics.ImageDecoder
import android.os.Build
import android.util.Log
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import java.nio.ByteBuffer

private const val TAG = "AvifDecoder"

/**
 * Coil decoder for AVIF images using Android's ImageDecoder (API 28+).
 * AVIF decode support requires API 31+.
 */
class AvifDecoder(private val source: ImageSource) : Decoder {

    override suspend fun decode(): DecodeResult? {
        val bytes = source.source().use { it.readByteArray() }
        Log.d(TAG, "Decoding AVIF: ${bytes.size} bytes")
        return try {
            val buffer = ByteBuffer.wrap(bytes)
            val src = ImageDecoder.createSource(buffer)
            val bitmap = ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            Log.d(TAG, "AVIF decoded: ${bitmap.width}x${bitmap.height}")
            DecodeResult(image = bitmap.asImage(), isSampled = false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode AVIF", e)
            null
        }
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
            if (!isAvif(result.source.source())) return null
            return AvifDecoder(result.source)
        }

        private fun isAvif(source: okio.BufferedSource): Boolean {
            return try {
                source.request(12)
                val buf = source.peek().readByteArray(12)
                val header = String(buf, Charsets.US_ASCII)
                header.startsWith("ftyp", startIndex = 4) &&
                    (header.contains("avif") || header.contains("avis") || header.contains("mif1"))
            } catch (_: Exception) {
                false
            }
        }
    }
}
