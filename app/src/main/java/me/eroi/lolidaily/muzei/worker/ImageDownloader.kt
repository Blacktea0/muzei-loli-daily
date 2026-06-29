package me.eroi.lolidaily.muzei.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.edit
import me.eroi.lolidaily.muzei.LoliDailyArtWorker
import me.eroi.lolidaily.muzei.api.LoliApiClient
import okhttp3.Request
import okio.Buffer
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import okio.sink
import java.io.File
import java.net.URI

object ImageDownloader {
    private const val TAG = "ImageDownloader"
    private const val FILE_PROVIDER_AUTHORITY = "me.eroi.lolidaily.muzei.fileprovider"
    private const val MAX_DOWNLOAD_RETRIES = 3
    private const val TEMP_SUFFIX = ".tmp"

    private class CountingSink(
        delegate: Sink,
        private val onBytesWritten: (Long) -> Unit,
    ) : ForwardingSink(delegate) {
        private var totalBytesWritten = 0L

        override fun write(
            source: Buffer,
            byteCount: Long,
        ) {
            super.write(source, byteCount)
            totalBytesWritten += byteCount
            onBytesWritten(totalBytesWritten)
        }
    }

    fun ensureArtworksDir(context: Context): File {
        val dir = File(context.filesDir, "artworks")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun downloadImage(
        context: Context,
        url: String,
        token: String,
        dir: File,
        forceDownload: Boolean = false,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
    ): Uri? {
        if (forceDownload) {
            val existing = findExistingFile(token, dir)
            if (existing != null) {
                Log.d(TAG, "Force-refresh — deleting cached $token")
                existing.delete()
            }
        } else {
            val existingFile = findExistingFile(token, dir)
            if (existingFile != null) {
                if (isFileValid(existingFile)) {
                    Log.d(TAG, "Using cached image for $token")
                    val size = existingFile.length()
                    onProgress?.invoke(size, size)
                    return fileToUri(context, existingFile)
                } else {
                    Log.w(TAG, "Cached image corrupted for $token — re-downloading")
                    existingFile.delete()
                }
            }
        }

        var lastError: Exception? = null
        for (attempt in 1..MAX_DOWNLOAD_RETRIES) {
            try {
                val file = downloadImageOnce(url, token, dir, onProgress)
                if (file != null) {
                    if (attempt > 1) Log.d(TAG, "Download succeeded on attempt $attempt for $token")
                    return fileToUri(context, file)
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(
                    TAG,
                    "Download attempt $attempt/$MAX_DOWNLOAD_RETRIES failed for $token: ${e.message}",
                )
            }

            if (attempt < MAX_DOWNLOAD_RETRIES) {
                val delayMs = 1000L * attempt * attempt
                Log.d(TAG, "Retrying download in ${delayMs}ms (attempt ${attempt + 1})")
                Thread.sleep(delayMs)
            }
        }

        Log.e(TAG, "All $MAX_DOWNLOAD_RETRIES download attempts failed for $token", lastError)
        return null
    }

    private fun downloadImageOnce(
        url: String,
        token: String,
        dir: File,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
    ): File? {
        val request = Request.Builder().url(url).header("User-Agent", LoliApiClient.USER_AGENT).get().build()

        val response = LoliApiClient.httpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "HTTP ${resp.code} for $url")
                return null
            }

            val body = resp.body ?: return null
            val ext = detectExtension(body.contentType()?.subtype, url)
            val tempFile = File(dir, "$token.$ext$TEMP_SUFFIX")
            val finalFile = File(dir, "$token.$ext")
            val contentLength = body.contentLength()

            body.source().use { source ->
                val fileSink = tempFile.sink()
                val countingSink =
                    CountingSink(fileSink) { written ->
                        if (contentLength > 0) {
                            onProgress?.invoke(written, contentLength)
                        }
                    }
                countingSink.buffer().use { sink -> sink.writeAll(source) }
            }

            if (tempFile.length() == 0L) {
                Log.w(TAG, "Empty download for $token")
                tempFile.delete()
                return null
            }

            if (!isFileValid(tempFile)) {
                Log.w(
                    TAG,
                    "File integrity check failed for $token (${tempFile.length()} bytes, ext=$ext)",
                )
                tempFile.delete()
                return null
            }

            if (!tempFile.renameTo(finalFile)) {
                Log.e(TAG, "Failed to rename temp file for $token")
                tempFile.delete()
                return null
            }

            if (contentLength > 0) {
                onProgress?.invoke(contentLength, contentLength)
            }

            Log.d(TAG, "Downloaded ${finalFile.length()} bytes → ${finalFile.name}")
            return finalFile
        }
    }

    fun isFileValid(file: File): Boolean {
        if (!file.exists() || file.length() < 8) return false
        return try {
            val header = ByteArray(4)
            file.inputStream().use { it.read(header) }
            val ext = file.extension.lowercase()
            when {
                ext in listOf("jpg", "jpeg") ->
                    header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()
                ext == "png" -> header[0] == 0x89.toByte() && header[1] == 0x50.toByte()
                ext == "webp" -> header[0] == 0x52.toByte() && header[1] == 0x49.toByte()
                ext == "gif" -> header[0] == 0x47.toByte() && header[1] == 0x49.toByte()
                ext == "bmp" -> header[0] == 0x42.toByte() && header[1] == 0x4D.toByte()
                else -> true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot verify file: ${file.name}", e)
            true
        }
    }

    private fun detectExtension(
        subtype: String?,
        url: String,
    ): String {
        val fromMime =
            when (subtype?.lowercase()) {
                "png" -> "png"
                "webp" -> "webp"
                "jpeg" -> "jpg"
                else -> null
            }
        if (fromMime != null) return fromMime

        val path =
            try {
                URI(url).path
            } catch (_: Exception) {
                null
            }
        val fromUrl =
            path?.substringAfterLast('.')?.lowercase()?.takeIf {
                it in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
            }
        if (fromUrl != null) return fromUrl

        return "jpg"
    }

    fun getCachedUri(
        context: Context,
        token: String,
        dir: File,
    ): Uri? {
        val file = findExistingFile(token, dir)
        if (file != null) {
            Log.d(TAG, "Reusing cached image for $token")
            return fileToUri(context, file)
        }
        Log.w(TAG, "No cached file for $token")
        return null
    }

    fun findExistingFile(
        token: String,
        dir: File,
    ): File? {
        val prefix = "$token."
        val files =
            dir.listFiles { f ->
                f.name.startsWith(prefix) && !f.name.endsWith(TEMP_SUFFIX) && f.length() > 0
            }
        return files?.firstOrNull()
    }

    fun fileToUri(
        context: Context,
        file: File,
    ): Uri {
        return FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
    }

    /**
     * Scans all stored image_dates entries for the given date and returns tokens NOT in
     * [keepTokens].
     */
    fun findStaleTokensForDate(
        prefs: android.content.SharedPreferences,
        date: String,
        keepTokens: Set<String>,
        allDates: Map<String, String>,
    ): Set<String> {
        return allDates.filterKeys { it !in keepTokens }.filterValues { it == date }.keys
    }

    /** Removes artwork files, date records, and Room rows for stale tokens. */
    fun cleanupStaleArtworks(
        context: Context,
        staleTokens: List<String>,
        prefs: android.content.SharedPreferences,
        dates: Map<String, String>,
    ) {
        val dao =
            me.eroi.lolidaily.muzei.db.DatabaseProvider.getInstance(context)
                .cachedArtworkDao()

        // Filter out bookmarked tokens - preserve them entirely
        val bookmarkedTokens = mutableSetOf<String>()
        val nonBookmarkedTokens = mutableListOf<String>()
        for (token in staleTokens) {
            val status = kotlinx.coroutines.runBlocking { dao.getBookmarkedStatus(token) }
            if (status != null && status != 0) {
                bookmarkedTokens.add(token)
            } else {
                nonBookmarkedTokens.add(token)
            }
        }

        if (bookmarkedTokens.isNotEmpty()) {
            Log.d(TAG, "Preserving ${bookmarkedTokens.size} bookmarked artworks from cleanup")
        }

        if (nonBookmarkedTokens.isEmpty()) return

        val dir = ensureArtworksDir(context)

        for (token in nonBookmarkedTokens) {
            val prefix = "$token."
            dir.listFiles { f -> f.name.startsWith(prefix) }
                ?.forEach { file ->
                    if (file.delete()) {
                        Log.d(TAG, "Cleaned stale artwork file: ${file.name}")
                    }
                }
        }

        val datesMut = dates.toMutableMap()
        var datesChanged = false
        for (token in nonBookmarkedTokens) {
            if (datesMut.remove(token) != null) {
                datesChanged = true
            }
        }
        if (datesChanged) {
            prefs
                .edit {
                    putString(
                        LoliDailyArtWorker.KEY_IMAGE_DATES,
                        LoliApiClient.json.encodeToString(
                            kotlinx.serialization.builtins.MapSerializer(
                                kotlinx.serialization.serializer<String>(),
                                kotlinx.serialization.serializer<String>(),
                            ),
                            datesMut,
                        ),
                    )
                }
        }

        try {
            kotlinx.coroutines.runBlocking {
                dao.deleteByTokens(nonBookmarkedTokens)
            }
            Log.d(TAG, "Cleaned ${nonBookmarkedTokens.size} stale Room rows (preserved ${bookmarkedTokens.size} bookmarked)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean stale Room rows", e)
        }
    }

    /**
     * Filters [oldTokens] against Room bookmark status and deletes only non-bookmarked entries
     * via [cleanupStaleArtworks]. Called by the worker on day-shift to reclaim space.
     */
    fun cleanupNonBookmarkedFromOldDates(
        context: Context,
        oldTokens: Set<String>,
        prefs: android.content.SharedPreferences,
        allDates: Map<String, String>,
    ) {
        val tokensToDelete = mutableListOf<String>()
        val dao =
            me.eroi.lolidaily.muzei.db.DatabaseProvider.getInstance(context)
                .cachedArtworkDao()

        for (token in oldTokens) {
            val bookmarked =
                kotlinx.coroutines.runBlocking { dao.getBookmarkedStatus(token) }
            if (bookmarked == null || bookmarked == 0) {
                tokensToDelete.add(token)
            }
        }

        if (tokensToDelete.isNotEmpty()) {
            Log.d(TAG, "Cleaning ${tokensToDelete.size} non-bookmarked artworks from old dates")
            cleanupStaleArtworks(context, tokensToDelete, prefs, allDates)
        }
    }
}
