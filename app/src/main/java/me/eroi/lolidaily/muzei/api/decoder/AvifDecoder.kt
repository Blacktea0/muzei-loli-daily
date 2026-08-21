package me.eroi.lolidaily.muzei.api.decoder

import com.github.penfeizhou.animation.avif.AVIFDrawable
import com.github.penfeizhou.animation.loader.ByteBufferLoader
import me.eroi.lolidaily.muzei.util.Log
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
 * Coil decoder for static and animated AVIF images.
 *
 * Android's platform decoder only guarantees AVIF support on API 34+ and renders AVIF
 * inconsistently on some devices. AVIFDrawable decodes frames with its bundled decoder and
 * retains the source bytes for the drawable's asynchronous playback.
 */
class AvifDecoder(private val source: ImageSource) : Decoder {

    override suspend fun decode(): DecodeResult? {
        val bytes = source.source().use { it.readByteArray() }
        Log.d(TAG, "Decoding AVIF: ${bytes.size} bytes")
        return try {
            val drawable =
                AVIFDrawable(
                    object : ByteBufferLoader() {
                        override fun getByteBuffer(): ByteBuffer = ByteBuffer.wrap(bytes)
                    },
                )
            drawable.start()
            Log.d(TAG, "AVIF decoded: ${drawable.intrinsicWidth}x${drawable.intrinsicHeight}")
            DecodeResult(image = drawable.asImage(), isSampled = false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode AVIF", e)
            null
        }
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
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
