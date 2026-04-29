package me.eroi.lolidaily.muzei

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
import me.eroi.lolidaily.muzei.db.DatabaseProvider
import me.eroi.lolidaily.muzei.db.EntityMapper
import me.eroi.lolidaily.muzei.model.Card
import me.eroi.lolidaily.muzei.model.DailyReactResponse
import me.eroi.lolidaily.muzei.model.DailyResponse
import me.eroi.lolidaily.muzei.model.ReactionCount
import com.google.android.apps.muzei.api.provider.Artwork
import com.google.android.apps.muzei.api.provider.ProviderContract
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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

    /**
     * Mirrors the JS session data structure: { token: "JWT", expiresAt: 1234567890000 }.
     * Top-level nested so it's accessible as LoliDailyArtWorker.Session.
     */
    @kotlinx.serialization.Serializable
    data class Session(
        val token: String,
        val expiresAt: Long,
    ) {
        val isValid: Boolean get() = token.isNotBlank() && expiresAt > System.currentTimeMillis()
    }

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
                // Read cached date BEFORE writing new response
                val cachedDate = loadCachedResponse()?.date

                // Call the API (normal cycle / force-refresh)
                val fetched = fetchDailyResponse()
                if (fetched != null) {
                    val (fetchedCards, fetchedDate) = fetched
                    cards = fetchedCards
                    apiDate = fetchedDate
                    saveCachedResponse(fetchedCards, fetchedDate)
                    markFetchTime()

                    isNewDay = forceRefresh || cachedDate == null || cachedDate != fetchedDate

                    if (isNewDay && cards.isNotEmpty()) {
                        downloadNewImages(cards, forceDownload = forceRefresh)
                        saveCardsMetadata(cards, fetchedDate)
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
     * Always true for force-refresh or if cached data is from a different date.
     * Otherwise at most once per hour.
     */
    private fun shouldFetchApi(forceRefresh: Boolean): Boolean {
        if (forceRefresh) return true
        val cached = loadCachedResponse()
        // No cache or cached date differs from today — fetch immediately
        if (cached == null || cached.date != currentDateFormatted()) return true
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

    // ── Room metadata persistence ──────────────────────────────────────

    /** Batch-upserts all cards into Room so metadata survives API rotation. */
    private fun saveCardsMetadata(cards: List<Card>, date: String) {
        try {
            val entities = cards.mapNotNull { card ->
                if (card.imgUrl.isBlank()) return@mapNotNull null
                EntityMapper.cardToEntity(card, md5(card.imgUrl), date)
            }
            if (entities.isEmpty()) return
            runBlocking {
                DatabaseProvider.getInstance(applicationContext)
                    .cachedArtworkDao()
                    .upsertAll(entities)
            }
            Log.d(TAG, "Saved ${entities.size} artwork metadata rows to Room")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save artwork metadata to Room", e)
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
        const val KEY_REACTIONS = "reactions"
        private const val KEY_LC_SESSION = "lc_session"
        private const val KEY_USER_REACTIONS = "user_reactions"
        private const val KEY_BGM_USERNAME = "bgm_username"
        private const val KEY_BGM_DOMAIN = "bgm_domain"

        private const val DEFAULT_BGM_DOMAIN = "chii.in"

        const val PROVIDER_AUTHORITY = "me.eroi.lolidaily.muzei.provider"
        private const val FILE_PROVIDER_AUTHORITY =
            "me.eroi.lolidaily.muzei.fileprovider"

        private val API_URL
            get() = "${BuildConfig.API_BASE_URL}/api/v1/daily?badge=LC%20YJ-ES-NC-PG"
        private val REACT_API_URL
            get() = "${BuildConfig.API_BASE_URL}/api/v1/daily/react?badge=LC%20YJ-ES-NC-PG"
        private const val USER_AGENT = "LoliDaily/1.0 (Android)"
        private const val MAX_DOWNLOAD_RETRIES = 3

        private val json = Json { ignoreUnknownKeys = true }
        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        /** Map of reaction emoji value → drawable resource ID. */
        fun emojiResId(value: Int): Int? = when (value) {
            0 -> R.drawable.reaction_44
            104 -> R.drawable.reaction_65
            54 -> R.drawable.reaction_15
            140 -> R.drawable.reaction_101
            122 -> R.drawable.reaction_83
            90 -> R.drawable.reaction_51
            88 -> R.drawable.reaction_49
            80 -> R.drawable.reaction_41
            else -> null
        }

        @Deprecated("Use emojiResId() with local drawable resources instead")
        val EMOJI_URL_MAP = mapOf(
            0 to "https://bgm.tv/img/smiles/tv/44.gif",
            104 to "https://bgm.tv/img/smiles/tv/65.gif",
            54 to "https://bgm.tv/img/smiles/tv/15.gif",
            140 to "https://bgm.tv/img/smiles/tv/101.gif",
            122 to "https://bgm.tv/img/smiles/tv/83.gif",
            90 to "https://bgm.tv/img/smiles/tv/51.gif",
            88 to "https://bgm.tv/img/smiles/tv/49.gif",
            80 to "https://bgm.tv/img/smiles/tv/41.gif",
        )

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

        /**
         * Fetches reactions for the current daily batch from the API,
         * maps them to image tokens, and caches in SharedPreferences.
         *
         * Safe to call on the main thread — blocks briefly for network I/O.
         * Call from a coroutine or background thread in UI contexts.
         */
        fun fetchAndCacheReactions(context: Context) {
            try {
                val cacheFile = File(context.filesDir, "api_cache.json")
                if (!cacheFile.exists()) return
                val daily = json.decodeFromString<DailyResponse>(cacheFile.readText())
                if (daily.cards.isEmpty()) return

                val request = Request.Builder()
                    .url(REACT_API_URL)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Reactions API returned ${response.code}")
                    return
                }
                val body = response.body?.string() ?: return
                val reactData = json.decodeFromString<DailyReactResponse>(body)

                val tokenReactions = mutableMapOf<String, List<ReactionCount>>()
                reactData.reactions.forEachIndexed { idx, reactionMap ->
                    if (idx >= daily.cards.size) return@forEachIndexed
                    val token = md5(daily.cards[idx].imgUrl)
                    val counts = reactionMap
                        .mapKeys { it.key.toInt() }
                        .mapValues { it.value.size }
                        .filter { it.value > 0 }
                        .map { ReactionCount(it.key, it.value) }
                        .sortedByDescending { it.count }
                    if (counts.isNotEmpty()) {
                        tokenReactions[token] = counts
                    }
                }

                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val serialized = json.encodeToString(
                    serializer<Map<String, List<ReactionCount>>>(),
                    tokenReactions,
                )
                prefs.edit().putString(KEY_REACTIONS, serialized).apply()

                // Also record which emoji the current user selected per card
                val username = loadUsername(context)
                Log.d(TAG, "loadUsername = $username")
                if (username != null) {
                    val userReactions = mutableMapOf<String, Int>()
                    reactData.reactions.forEachIndexed { idx, reactionMap ->
                        if (idx >= daily.cards.size) return@forEachIndexed
                        val token = md5(daily.cards[idx].imgUrl)
                        for ((emojiKey, users) in reactionMap) {
                            val matched = users.any { it.firstOrNull() == username }
                            Log.d(TAG, "card=$idx emoji=$emojiKey users=$users matched=$matched username=$username")
                            if (matched) {
                                userReactions[token] = emojiKey.toInt()
                                break
                            }
                        }
                    }
                    Log.d(TAG, "userReactions map = $userReactions")
                    prefs.edit().putString(
                        KEY_USER_REACTIONS,
                        json.encodeToString(serializer<Map<String, Int>>(), userReactions)
                    ).apply()
                }

                Log.d(TAG, "Cached reactions for ${tokenReactions.size} cards")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch reactions", e)
            }
        }

        /**
         * Loads cached per-image reaction counts as a JSON map
         * (token → reaction counts) from SharedPreferences.
         */
        fun loadReactions(context: Context): Map<String, List<ReactionCount>> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_REACTIONS, null) ?: return emptyMap()
            return try {
                json.decodeFromString<Map<String, List<ReactionCount>>>(raw)
            } catch (_: Exception) {
                emptyMap()
            }
        }

        // ── Session Management ──────────────────────────────────

        /** Load the stored LC session from SharedPreferences, or null. */
        fun loadSession(context: Context): Session? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_LC_SESSION, null) ?: return null
            return try {
                val s = json.decodeFromString<Session>(raw)
                if (s.isValid) s else null
            } catch (_: Exception) {
                null
            }
        }

        /** Persist a session to SharedPreferences. */
        fun saveSession(context: Context, session: Session) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = json.encodeToString(Session.serializer(), session)
            prefs.edit().putString(KEY_LC_SESSION, raw).apply()
        }

        /** Remove the stored session (logout). */
        fun clearSession(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LC_SESSION)
                .remove(KEY_BGM_USERNAME)
                .remove(KEY_USER_REACTIONS)
                .apply()
        }

        /** Store the bgm.tv username extracted from the OAuth callback URL. */
        fun saveUsername(context: Context, username: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_BGM_USERNAME, username).apply()
        }

        /** Load the stored bgm.tv username, or null. */
        fun loadUsername(context: Context): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_BGM_USERNAME, null)
        }

        /** Save the chosen Bangumi domain (bgm.tv / bangumi.tv / chii.in). */
        fun saveDomain(context: Context, domain: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_BGM_DOMAIN, domain).apply()
        }

        /** Load the chosen Bangumi domain, defaulting to [DEFAULT_BGM_DOMAIN]. */
        fun loadDomain(context: Context): String {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_BGM_DOMAIN, DEFAULT_BGM_DOMAIN) ?: DEFAULT_BGM_DOMAIN
        }

        /**
         * Extracts the Bangumi username from a JWT session token.
         * The JWT payload is expected to contain a "username" or "sub" claim.
         */
        fun getUsername(session: Session): String? {
            return try {
                val parts = session.token.split('.')
                if (parts.size < 2) return null
                val payload = String(Base64.decode(parts[1], Base64.DEFAULT))
                val json = org.json.JSONObject(payload)
                val name = json.optString("username", "").ifEmpty { null }
                    ?: json.optString("sub", "").ifEmpty { null }
                name
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Loads the user's own reaction map (token → emojiValue)
         * for cards they have reacted to.
         */
        fun loadUserReactions(context: Context): Map<String, Int> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_USER_REACTIONS, null) ?: return emptyMap()
            return try {
                json.decodeFromString<Map<String, Int>>(raw)
            } catch (_: Exception) {
                emptyMap()
            }
        }

        /**
         * Find the index of a card (by image token) in the cached daily response.
         * Needed because the PATCH reactions API uses card index, not token.
         */
        fun getCardIndex(context: Context, token: String): Int? {
            val cacheFile = File(context.filesDir, "api_cache.json")
            if (!cacheFile.exists()) return null
            return try {
                val daily = json.decodeFromString<DailyResponse>(cacheFile.readText())
                daily.cards.indexOfFirst { md5(it.imgUrl) == token }.takeIf { it >= 0 }
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Submits a reaction to the LC API with the current session.
         * Returns true if the request was accepted by the server.
         */
        fun patchReaction(
            context: Context,
            cardIndex: Int,
            emojiValue: Int,
        ): Boolean {
            val session = loadSession(context) ?: return false
            val body = "{\"react\":$emojiValue}".toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${BuildConfig.API_BASE_URL}/api/v1/daily/react?cardTypeIdx=$cardIndex")
                .header("Authorization", "Bearer ${session.token}")
                .header("User-Agent", USER_AGENT)
                .method("PATCH", body)
                .build()

            return try {
                val response = httpClient.newCall(request).execute()
                val ok = response.isSuccessful
                response.close()

                if (ok) {
                    // Track locally so UI can show heart immediately
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val raw = prefs.getString(KEY_USER_REACTIONS, null)
                    val map = if (raw != null) {
                        try { json.decodeFromString<MutableMap<String, Int>>(raw).toMutableMap() }
                        catch (_: Exception) { mutableMapOf() }
                    } else mutableMapOf()
                    val cacheFile = File(context.filesDir, "api_cache.json")
                    val daily = json.decodeFromString<DailyResponse>(cacheFile.readText())
                    val token = md5(daily.cards[cardIndex].imgUrl)
                    map[token] = emojiValue
                    prefs.edit().putString(
                        KEY_USER_REACTIONS,
                        json.encodeToString(serializer<Map<String, Int>>(), map)
                    ).apply()
                } else {
                    Log.w(TAG, "Reaction PATCH returned ${response.code}")
                }
                ok
            } catch (e: Exception) {
                Log.w(TAG, "Failed to submit reaction", e)
                false
            }
        }
    }
}
