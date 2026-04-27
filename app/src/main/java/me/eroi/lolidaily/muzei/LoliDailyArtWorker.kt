package me.eroi.lolidaily.muzei

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import me.eroi.lolidaily.muzei.model.Card
import me.eroi.lolidaily.muzei.model.DailyResponse
import com.google.android.apps.muzei.api.provider.Artwork
import com.google.android.apps.muzei.api.provider.ProviderContract
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * WorkManager Worker that fetches artwork from the Loli Daily API
 * and keeps the Muzei queue in sync with the user's tag preferences.
 *
 * Strategy:
 * - API is called at most once per hour (or on force-refresh).
 *   The lightweight JSON is always fetched; images are downloaded
 *   only when the API date changes (new daily batch).
 * - **All** images are downloaded regardless of the user's tag
 *   filter — the full daily batch is cached locally.
 * - Tag filtering happens at push time: every execution re-reads
 *   [KEY_ENABLED_TAGS] and pushes only matching artwork to Muzei.
 * - When the user changes tag preferences in Settings, a
 *   lightweight re-filter is enqueued that uses cached data
 *   without touching the network.
 */
class LoliDailyArtWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    private val prefs: SharedPreferences by lazy {
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun doWork(): Result {
        val forceRefresh = inputData.getBoolean(KEY_FORCE_REFRESH, false)
        val refilterOnly = inputData.getBoolean(KEY_REFILTER_ONLY, false)

        return try {
            var isNewDay = false
            var cards = emptyList<Card>()
            var apiDate = ""

            // ── Step 1: Obtain card data ──────────────────────────
            if (!refilterOnly && shouldFetchApi(forceRefresh)) {
                // Call the API (normal cycle / force-refresh)
                val fetched = fetchDailyResponse()
                if (fetched != null) {
                    val (fetchedCards, fetchedDate) = fetched
                    cards = fetchedCards
                    apiDate = fetchedDate
                    saveCachedResponse(fetchedCards, fetchedDate)
                    markFetchTime()

                    val lastDate = prefs.getString(KEY_LAST_API_DATE, null)
                    isNewDay = forceRefresh || lastDate == null || lastDate != fetchedDate

                    if (isNewDay && cards.isNotEmpty()) {
                        downloadNewImages(cards, forceDownload = forceRefresh)
                        recordImageDates(cards, fetchedDate)
                        prefs.edit().putString(KEY_LAST_API_DATE, fetchedDate).apply()
                        Log.d(TAG, "New day ($fetchedDate) — downloaded ${cards.size} images")
                    }
                }
            }

            // Fallback: use cached data if API didn't provide cards
            if (cards.isEmpty()) {
                val cached = loadCachedResponse()
                if (cached != null) {
                    cards = cached.cards
                    apiDate = cached.date
                } else if (!refilterOnly) {
                    val fetched = fetchDailyResponse()
                    if (fetched != null) {
                        val (fbCards, fbDate) = fetched
                        cards = fbCards
                        apiDate = fbDate
                        saveCachedResponse(fbCards, fbDate)
                        markFetchTime()
                    } else {
                        return Result.retry()
                    }
                }
            }

            if (cards.isEmpty()) {
                Log.d(TAG, "No cards available")
                return Result.success()
            }

            // ── Step 2: Filter by current tag preferences ─────────
            pushFilteredArtworks(cards, apiDate, isNewDay)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load artwork", e)
            Result.retry()
        }
    }

    // ── API fetch gating ──────────────────────────────────────────

    /**
     * Returns true if the API should be called now.
     * Always true for force-refresh; otherwise at most once per hour.
     */
    private fun shouldFetchApi(forceRefresh: Boolean): Boolean {
        if (forceRefresh) return true
        val lastFetch = prefs.getLong(KEY_LAST_FETCH_TIME, 0L)
        val hoursSince = (System.currentTimeMillis() - lastFetch) / 3_600_000L
        return hoursSince >= 1
    }

    private fun markFetchTime() {
        prefs.edit().putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis()).apply()
    }

    // ── Card data cache (filesystem) ──────────────────────────────

    private val cacheFile: File
        get() = File(applicationContext.filesDir, CACHE_FILE)

    private fun saveCachedResponse(cards: List<Card>, date: String) {
        try {
            val response = DailyResponse(cards = cards, date = date)
            cacheFile.writeText(json.encodeToString(DailyResponse.serializer(), response))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache API response", e)
        }
    }

    private fun loadCachedResponse(): DailyResponse? {
        return try {
            if (!cacheFile.exists()) return null
            json.decodeFromString<DailyResponse>(cacheFile.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load cached response", e)
            null
        }
    }

    // ── Per-image download date tracking ───────────────────────────

    /** Records per-token download date using the API response date field. */
    private fun recordImageDates(cards: List<Card>, date: String) {
        val current = loadImageDatesInternal().toMutableMap()
        for (card in cards) {
            if (card.imgUrl.isNotBlank()) {
                current[md5(card.imgUrl)] = date
            }
        }
        prefs.edit()
            .putString(KEY_IMAGE_DATES, json.encodeToString(
                    MapSerializer(serializer<String>(), serializer<String>()), current))
            .apply()
    }

    private fun loadImageDatesInternal(): Map<String, String> {
        val raw = prefs.getString(KEY_IMAGE_DATES, null) ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, String>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    // ── Bulk image download ───────────────────────────────────────

    /** Downloads every card's image regardless of tag filter. */
    private fun downloadNewImages(cards: List<Card>, forceDownload: Boolean) {
        val dir = ensureArtworksDir()
        for (card in cards) {
            if (card.imgUrl.isBlank()) continue
            val token = md5(card.imgUrl)
            downloadImage(card.imgUrl, token, dir, forceDownload)
        }
    }

    // ── Filter + push to Muzei ────────────────────────────────────

    private fun pushFilteredArtworks(cards: List<Card>, apiDate: String, isNewDay: Boolean) {
        val enabledTags = prefs.getStringSet(KEY_ENABLED_TAGS, null)

        val filteredCards = if (enabledTags.isNullOrEmpty()) {
            cards // No filter — show all
        } else {
            cards.filter { card -> enabledTags.contains(card.tags) }
        }

        Log.d(TAG, "Filtered ${cards.size} → ${filteredCards.size} (tags=$enabledTags)")

        val artworksDir = ensureArtworksDir()
        val artworks = filteredCards.mapNotNull { card ->
            buildArtwork(card, artworksDir, apiDate, download = false)
        }

        // Replace Muzei queue (even if empty — clears when filter matches nothing)
        val client = ProviderContract.getProviderClient(
            applicationContext,
            PROVIDER_AUTHORITY
        )
        client.setArtwork(artworks)

        // Force Muzei to rotate if new daily batch arrived
        if (isNewDay) {
            applicationContext.sendBroadcast(
                Intent("com.google.android.apps.muzei.action.NEXT_ARTWORK").apply {
                    setPackage("net.nurik.roman.muzei")
                }
            )
            Log.d(TAG, "New day — advancing Muzei rotation")
        }

        Log.d(TAG, "Set ${artworks.size} artworks (newDay=$isNewDay, tags=$enabledTags)")
    }

    // ── Artwork construction ──────────────────────────────────────────

    private fun buildArtwork(card: Card, dir: File, apiDate: String, download: Boolean): Artwork? {
        if (card.imgUrl.isBlank()) return null

        val token = md5(card.imgUrl)
        val localUri = if (download) {
            downloadImage(card.imgUrl, token, dir)
                ?: getCachedUri(token, dir)
        } else {
            getCachedUri(token, dir)
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

    // ── Filesystem ────────────────────────────────────────────────────

    private fun ensureArtworksDir(): File {
        val dir = File(applicationContext.filesDir, "artworks")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun downloadImage(url: String, token: String, dir: File, forceDownload: Boolean = false): Uri? {
        // On force-refresh, always re-download from network
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
                    return fileToUri(existingFile)
                } else {
                    Log.w(TAG, "Cached image corrupted for $token — re-downloading")
                    existingFile.delete()
                }
            }
        }

        // Retry up to 3 attempts with exponential backoff
        var lastError: Exception? = null
        for (attempt in 1..MAX_DOWNLOAD_RETRIES) {
            try {
                val file = downloadImageOnce(url, token, dir)
                if (file != null) {
                    if (attempt > 1) Log.d(TAG, "Download succeeded on attempt $attempt for $token")
                    return fileToUri(file)
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Download attempt $attempt/$MAX_DOWNLOAD_RETRIES failed for $token: ${e.message}")
            }

            if (attempt < MAX_DOWNLOAD_RETRIES) {
                val delayMs = 1000L * attempt * attempt // 1s, 4s, 9s
                Log.d(TAG, "Retrying download in ${delayMs}ms (attempt ${attempt + 1})")
                Thread.sleep(delayMs)
            }
        }

        Log.e(TAG, "All $MAX_DOWNLOAD_RETRIES download attempts failed for $token", lastError)
        return null
    }

    /** Single download attempt with integrity validation. */
    private fun downloadImageOnce(url: String, token: String, dir: File): File? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "HTTP ${resp.code} for $url")
                return null
            }

            val body = resp.body ?: return null
            val ext = detectExtension(body.contentType()?.subtype, url)
            val file = File(dir, "$token.$ext")

            body.source().use { source ->
                file.sink().buffer().use { sink -> sink.writeAll(source) }
            }

            if (file.length() == 0L) {
                Log.w(TAG, "Empty download for $token")
                file.delete()
                return null
            }

            if (!isFileValid(file)) {
                Log.w(TAG, "File integrity check failed for $token (${file.length()} bytes, ext=$ext)")
                file.delete()
                return null
            }

            Log.d(TAG, "Downloaded ${file.length()} bytes → ${file.name}")
            return file
        }
    }

    /** Verify file starts with expected magic bytes for its extension. */
    private fun isFileValid(file: File): Boolean {
        if (!file.exists() || file.length() < 8) return false
        return try {
            val header = ByteArray(4)
            file.inputStream().use { it.read(header) }
            val ext = file.extension.lowercase()
            when {
                ext in listOf("jpg", "jpeg") -> header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()
                ext == "png" -> header[0] == 0x89.toByte() && header[1] == 0x50.toByte()
                ext == "webp" -> header[0] == 0x52.toByte() && header[1] == 0x49.toByte()
                ext == "gif" -> header[0] == 0x47.toByte() && header[1] == 0x49.toByte()
                ext == "bmp" -> header[0] == 0x42.toByte() && header[1] == 0x4D.toByte()
                else -> true // Unknown format — accept as-is
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot verify file: ${file.name}", e)
            true // Don't reject if we can't read it
        }
    }

    /** Determine file extension from content-type or URL path. */
    private fun detectExtension(subtype: String?, url: String): String {
        // 1. Content-Type
        val fromMime = when (subtype?.lowercase()) {
            "png" -> "png"
            "webp" -> "webp"
            "jpeg" -> "jpg"
            else -> null
        }
        if (fromMime != null) return fromMime

        // 2. URL path extension
        val path = try {
            java.net.URI(url).path
        } catch (_: Exception) { null }
        val fromUrl = path?.substringAfterLast('.')?.lowercase()
            ?.takeIf { it in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp") }
        if (fromUrl != null) return fromUrl

        // 3. Default
        return "jpg"
    }

    /** Returns a FileProvider URI for an already-cached file, or null. */
    private fun getCachedUri(token: String, dir: File): Uri? {
        val file = findExistingFile(token, dir)
        if (file != null) {
            Log.d(TAG, "Reusing cached image for $token")
            return fileToUri(file)
        }
        Log.w(TAG, "No cached file for $token")
        return null
    }

    private fun findExistingFile(token: String, dir: File): File? {
        val prefix = "$token."
        val files = dir.listFiles { f -> f.name.startsWith(prefix) && f.length() > 0 }
        return files?.firstOrNull()
    }

    private fun fileToUri(file: File): Uri {
        return FileProvider.getUriForFile(
            applicationContext,
            FILE_PROVIDER_AUTHORITY,
            file
        )
    }

    // ── API ───────────────────────────────────────────────────────────

    /** Returns parsed cards + the response `date` field, or null on failure. */
    private fun fetchDailyResponse(): Pair<List<Card>, String>? {
        val request = Request.Builder()
            .url(API_URL)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: return null

        if (!response.isSuccessful) {
            Log.w(TAG, "API returned ${response.code}: $body")
            return null
        }

        val daily = json.decodeFromString<DailyResponse>(body)
        return daily.cards to daily.date
    }

    // ── Metadata builders ─────────────────────────────────────────────

    private fun buildTitle(card: Card, apiDate: String): String {
        return card.comment.ifBlank {
            val date = apiDate.ifBlank { currentDateFormatted() }
            if (card.tags.isNotBlank()) "$date [${card.tags}]" else date
        }
    }

    private fun buildByline(card: Card): String {
        return card.artistName.ifBlank { "Unknown Artist" }
    }

    private fun buildAttribution(card: Card, apiDate: String): String {
        val parts = mutableListOf<String>()
        if (card.tags.isNotBlank()) parts.add("[${card.tags}]")
        if (card.characterNames.isNotEmpty()) parts.add(card.characterNames.joinToString(", "))
        card.suggestedBy?.let { parts.add("by ${it.nickname}") }
        val date = apiDate.ifBlank { currentDateFormatted() }
        parts.add(date)
        if (card.sourceUrl.isNotBlank()) parts.add(card.sourceUrl)
        return parts.joinToString("  ·  ")
    }

    private fun buildMetadata(card: Card): String {
        return json.encodeToString(Card.serializer(), card)
    }

    private fun currentDateFormatted(): String {
        val now = java.time.LocalDate.now()
        return now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }

    // ── Companion ─────────────────────────────────────────────────────

    companion object {
        private const val TAG = "LoliDailyWorker"
        private const val WORK_NAME = "lolidaily_art_load"
        private const val CACHE_FILE = "api_cache.json"

        // ── SharedPreferences keys (public for SettingsActivity access) ──
        const val PREFS_NAME = "lolidaily_prefs"
        private const val KEY_LAST_API_DATE = "last_api_date"
        const val KEY_FORCE_REFRESH = "force_refresh"
        const val KEY_ENABLED_TAGS = "enabled_tags"
        private const val KEY_LAST_FETCH_TIME = "last_fetch_time"
        private const val KEY_REFILTER_ONLY = "refilter_only"
        const val KEY_IMAGE_DATES = "image_dates"

        const val PROVIDER_AUTHORITY = "me.eroi.lolidaily.muzei.provider"
        private const val FILE_PROVIDER_AUTHORITY =
            "me.eroi.lolidaily.muzei.fileprovider"

        private const val API_URL =
            "https://loliconey.tsuki.ga/api/v1/daily?badge=LC%20YJ-ES-NC-PG"
        private const val USER_AGENT = "LoliDaily/1.0 (Android)"
        private const val MAX_DOWNLOAD_RETRIES = 3

        private val json = Json { ignoreUnknownKeys = true }
        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        private fun md5(input: String): String {
            val digest = MessageDigest.getInstance("MD5")
            return digest.digest(input.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }

        /**
         * Enqueue a one-shot work request that may call the API
         * and will always re-filter + push to Muzei.
         * Requires network. Deduplicates via REPLACE strategy.
         */
        fun enqueueLoad(context: Context, forceRefresh: Boolean = false) {
            val work = OneTimeWorkRequestBuilder<LoliDailyArtWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(
                    androidx.work.Data.Builder()
                        .putBoolean(KEY_FORCE_REFRESH, forceRefresh)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, work)
        }

        /**
         * Enqueue a lightweight re-filter that uses cached card data
         * without touching the network. Used when the user changes
         * tag preferences in Settings.
         *
         * Network is NOT required — runs immediately if available,
         * or when the device next comes online if offline.
         */
        fun enqueueRefilter(context: Context) {
            val work = OneTimeWorkRequestBuilder<LoliDailyArtWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .setInputData(
                    androidx.work.Data.Builder()
                        .putBoolean(KEY_REFILTER_ONLY, true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, work)
        }

        /**
         * Loads per-image download dates stored as a JSON map
         * (token → API date string) from SharedPreferences.
         */
        fun loadImageDates(context: Context): Map<String, String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_IMAGE_DATES, null) ?: return emptyMap()
            return try {
                json.decodeFromString<Map<String, String>>(raw)
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }
}
