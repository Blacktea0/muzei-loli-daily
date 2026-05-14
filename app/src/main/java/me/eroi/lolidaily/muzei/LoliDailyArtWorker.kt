package me.eroi.lolidaily.muzei

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.work.*
import com.google.android.apps.muzei.api.provider.ProviderContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.serializer
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.api.ReactionService
import me.eroi.lolidaily.muzei.api.Session
import me.eroi.lolidaily.muzei.api.SessionManager
import me.eroi.lolidaily.muzei.db.DatabaseProvider
import me.eroi.lolidaily.muzei.db.EntityMapper
import me.eroi.lolidaily.muzei.model.Card
import me.eroi.lolidaily.muzei.model.DailyResponse
import me.eroi.lolidaily.muzei.util.Md5
import me.eroi.lolidaily.muzei.worker.ArtworkBuilder
import me.eroi.lolidaily.muzei.worker.EmojiMap
import me.eroi.lolidaily.muzei.worker.ImageDownloader
import me.eroi.lolidaily.muzei.worker.WorkScheduler
import java.io.File

/**
 * WorkManager Worker that fetches artwork from the Loli Daily API and keeps the Muzei queue in sync
 * with the user's tag preferences.
 *
 * Strategy:
 * - API is called when the date changes, on force-refresh, or when debug_skip_cache is enabled.
 *   Images are downloaded whenever the API returns new cards; stale files from earlier same-day
 *   fetches are automatically cleaned up.
 * - **All** images are downloaded regardless of the user's tag filter — the full daily batch is
 *   cached locally.
 * - Tag filtering happens at push time: every execution re-reads [KEY_ENABLED_TAGS] and pushes only
 *   matching artwork to Muzei.
 * - When the user changes tag preferences in Settings, a lightweight re-filter is enqueued that
 *   uses cached data without touching the network.
 */
class LoliDailyArtWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val prefs: SharedPreferences by lazy {
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun doWork(): Result {
        val forceRefresh = inputData.getBoolean(KEY_FORCE_REFRESH, false)
        val refilterOnly = inputData.getBoolean(KEY_REFILTER_ONLY, false)
        val initial = inputData.getBoolean(KEY_INITIAL, true)

        refreshProgress.value = 0f

        return try {
            var isNewDay = false
            var cards = emptyList<Card>()
            var apiDate = ""
            var didFetchApi = false

            // ── Step 1: Obtain card data ──────────────────────────
            if (!refilterOnly && shouldFetchApi(forceRefresh)) {
                val cachedDate = loadCachedResponse()?.date

                val fetched = LoliApiClient.fetchDailyResponse(applicationContext)
                if (fetched != null) {
                    didFetchApi = true
                    val (fetchedCards, fetchedDate) = fetched
                    cards = fetchedCards
                    apiDate = fetchedDate
                    saveCachedResponse(fetchedCards, fetchedDate)
                    markFetchTime()

                    isNewDay = forceRefresh || cachedDate == null || cachedDate != fetchedDate

                    if (cards.isNotEmpty()) {
                        downloadNewImages(cards, forceDownload = forceRefresh) { fraction ->
                            refreshProgress.value = fraction
                        }
                        saveCardsMetadata(cards, fetchedDate)
                        recordImageDates(cards, fetchedDate)
                        prefs.edit().putString(KEY_LAST_API_DATE, fetchedDate).apply()

                        val newTokens =
                            cards
                                .mapNotNull {
                                    if (it.imgUrl.isNotBlank()) Md5.hash(it.imgUrl) else null
                                }
                                .toSet()
                        val allDates = loadImageDatesInternal()
                        val staleTokens =
                            ImageDownloader.findStaleTokensForDate(
                                prefs,
                                fetchedDate,
                                newTokens,
                                allDates,
                            )
                        if (staleTokens.isNotEmpty()) {
                            Log.d(
                                TAG,
                                "Date $fetchedDate — cleaning ${staleTokens.size} stale artworks",
                            )
                            ImageDownloader.cleanupStaleArtworks(
                                applicationContext,
                                staleTokens.toList(),
                                prefs,
                                allDates,
                            )
                        }

                        if (isNewDay) {
                            val oldDateTokens =
                                allDates.filterValues { it != fetchedDate }.keys
                            if (oldDateTokens.isNotEmpty()) {
                                Log.d(
                                    TAG,
                                    "New day — scanning ${oldDateTokens.size} old-date tokens",
                                )
                                ImageDownloader.cleanupNonBookmarkedFromOldDates(
                                    applicationContext,
                                    oldDateTokens,
                                    prefs,
                                    allDates,
                                )
                            }
                        }

                        Log.d(
                            TAG,
                            "Synced ${cards.size} artworks for $fetchedDate (newDay=$isNewDay)",
                        )
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
                    val fetched = LoliApiClient.fetchDailyResponse(applicationContext)
                    if (fetched != null) {
                        val (fbCards, fbDate) = fetched
                        cards = fbCards
                        apiDate = fbDate
                        saveCachedResponse(fbCards, fbDate)
                        markFetchTime()

                        if (cards.isNotEmpty()) {
                            downloadNewImages(cards, forceDownload = false) { fraction ->
                                refreshProgress.value = fraction
                            }
                            saveCardsMetadata(cards, fbDate)
                            recordImageDates(cards, fbDate)
                            val newTokens =
                                cards
                                    .mapNotNull {
                                        if (it.imgUrl.isNotBlank()) Md5.hash(it.imgUrl) else null
                                    }
                                    .toSet()
                            val allDates = loadImageDatesInternal()
                            val staleTokens =
                                ImageDownloader.findStaleTokensForDate(
                                    prefs,
                                    fbDate,
                                    newTokens,
                                    allDates,
                                )
                            if (staleTokens.isNotEmpty()) {
                                ImageDownloader.cleanupStaleArtworks(
                                    applicationContext,
                                    staleTokens.toList(),
                                    prefs,
                                    allDates,
                                )
                            }

                            val oldDateTokens =
                                loadImageDatesInternal().filterValues { it != fbDate }.keys
                            if (oldDateTokens.isNotEmpty()) {
                                ImageDownloader.cleanupNonBookmarkedFromOldDates(
                                    applicationContext,
                                    oldDateTokens,
                                    prefs,
                                    loadImageDatesInternal(),
                                )
                            }
                        }
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
            if (initial || didFetchApi || refilterOnly) {
                pushFilteredArtworks(cards, apiDate, isNewDay)
            } else {
                Log.d(TAG, "Skipping Muzei push — non-initial load with cached data only")
            }
            markWorkCompleted()
            refreshProgress.value = null

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load artwork", e)
            refreshProgress.value = null
            Result.retry()
        }
    }

    // ── API fetch gating ──────────────────────────────────────────

    private fun shouldFetchApi(forceRefresh: Boolean): Boolean {
        if (forceRefresh) return true
        val skipCache = prefs.getBoolean("debug_skip_cache", false)
        if (skipCache) return true
        val cached = loadCachedResponse()
        return cached == null
    }

    private fun markFetchTime() {
        prefs.edit().putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis()).apply()
    }

    private fun markWorkCompleted() {
        prefs.edit().putLong(KEY_LAST_WORK_COMPLETED, System.currentTimeMillis()).apply()
    }

    // ── Card data cache (filesystem) ──────────────────────────────

    private val cacheFile: File
        get() = File(applicationContext.filesDir, CACHE_FILE)

    private fun saveCachedResponse(
        cards: List<Card>,
        date: String,
    ) {
        try {
            val response = DailyResponse(cards = cards, date = date)
            cacheFile.writeText(
                LoliApiClient.json.encodeToString(DailyResponse.serializer(), response),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache API response", e)
        }
    }

    private fun loadCachedResponse(): DailyResponse? {
        return try {
            if (!cacheFile.exists()) return null
            LoliApiClient.json.decodeFromString<DailyResponse>(cacheFile.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load cached response", e)
            null
        }
    }

    // ── Per-image download date tracking ───────────────────────────

    private fun recordImageDates(
        cards: List<Card>,
        date: String,
    ) {
        val current = loadImageDatesInternal().toMutableMap()
        for (card in cards) {
            if (card.imgUrl.isNotBlank()) {
                current[Md5.hash(card.imgUrl)] = date
            }
        }
        prefs
            .edit()
            .putString(
                KEY_IMAGE_DATES,
                LoliApiClient.json.encodeToString(
                    MapSerializer(serializer<String>(), serializer<String>()),
                    current,
                ),
            )
            .apply()
    }

    private fun loadImageDatesInternal(): Map<String, String> {
        val raw = prefs.getString(KEY_IMAGE_DATES, null) ?: return emptyMap()
        return try {
            LoliApiClient.json.decodeFromString<Map<String, String>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    // ── Room metadata persistence ──────────────────────────────────────

    private fun saveCardsMetadata(
        cards: List<Card>,
        date: String,
    ) {
        try {
            val entities =
                cards.mapNotNull { card ->
                    if (card.imgUrl.isBlank()) return@mapNotNull null
                    EntityMapper.cardToEntity(card, Md5.hash(card.imgUrl), date)
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

    private fun downloadNewImages(
        cards: List<Card>,
        forceDownload: Boolean,
        onProgress: ((Float) -> Unit)? = null,
    ) {
        val dir = ImageDownloader.ensureArtworksDir(applicationContext)
        val downloadableCards = cards.filter { it.imgUrl.isNotBlank() }
        val totalFiles = downloadableCards.size
        if (totalFiles == 0) return

        var completedFiles = 0
        for (card in downloadableCards) {
            val token = Md5.hash(card.imgUrl)
            ImageDownloader.downloadImage(
                applicationContext,
                card.imgUrl,
                token,
                dir,
                forceDownload,
                onProgress = { bytesWritten, totalBytes ->
                    if (totalBytes > 0) {
                        val fileFraction = bytesWritten.toFloat() / totalBytes
                        val overall = (completedFiles + fileFraction) / totalFiles
                        onProgress?.invoke(overall.coerceIn(0f, 1f))
                    }
                },
            )
            completedFiles++
            onProgress?.invoke(completedFiles.toFloat() / totalFiles)
        }
    }

    // ── Filter + push to Muzei ────────────────────────────────────

    private fun pushFilteredArtworks(
        cards: List<Card>,
        apiDate: String,
        isNewDay: Boolean,
    ) {
        val enabledTags = prefs.getStringSet(KEY_ENABLED_TAGS, null) ?: setOf("LC0", "LC YJ")

        val filteredCards =
            if (enabledTags.isEmpty()) {
                cards
            } else {
                cards.filter { card -> enabledTags.contains(card.tags) }
            }

        Log.d(TAG, "Filtered ${cards.size} → ${filteredCards.size} (tags=$enabledTags)")

        val artworksDir = ImageDownloader.ensureArtworksDir(applicationContext)
        val artworks =
            filteredCards.mapNotNull { card ->
                ArtworkBuilder.buildArtworkFromCache(applicationContext, card, artworksDir, apiDate)
            }

        val client =
            ProviderContract.getProviderClient(applicationContext, LoliDailyArtProvider::class.java)
        client.setArtwork(artworks)

        if (isNewDay) {
            applicationContext.sendBroadcast(
                Intent("com.google.android.apps.muzei.action.NEXT_ARTWORK").apply {
                    setPackage("net.nurik.roman.muzei")
                },
            )
            Log.d(TAG, "New day — advancing Muzei rotation")
        }

        Log.d(TAG, "Set ${artworks.size} artworks (newDay=$isNewDay, tags=$enabledTags)")
    }

    // ── Companion ─────────────────────────────────────────────────────

    companion object {
        private const val TAG = "LoliDailyWorker"
        private const val CACHE_FILE = "api_cache.json"

        const val PREFS_NAME = "lolidaily_prefs"
        private const val KEY_LAST_API_DATE = "last_api_date"
        const val KEY_FORCE_REFRESH = "force_refresh"
        const val KEY_ENABLED_TAGS = "enabled_tags"
        private const val KEY_LAST_FETCH_TIME = "last_fetch_time"
        private const val KEY_LAST_WORK_COMPLETED = "last_work_completed"
        const val KEY_LAST_REACTION_FETCH = "last_reaction_fetch"
        private const val KEY_REFILTER_ONLY = "refilter_only"
        const val KEY_IMAGE_DATES = "image_dates"
        private const val KEY_INITIAL = "initial"

        const val KEY_DEBUG_REFRESH_HOUR = "debug_refresh_hour"
        const val KEY_DEBUG_REFRESH_MINUTE = "debug_refresh_minute"

        const val DEFAULT_BGM_DOMAIN = "chii.in"

        const val PROVIDER_AUTHORITY = "me.eroi.lolidaily.muzei.provider"

        val refreshProgress = MutableStateFlow<Float?>(null)

        // ── Delegation wrappers (implementation moved to api/ + worker/) ──

        fun md5(input: String): String = Md5.hash(input)

        fun emojiResId(value: Int): Int? = EmojiMap.emojiResId(value)

        @Deprecated("Use emojiResId() with local drawable resources instead")
        val EMOJI_URL_MAP
            get() = EmojiMap.EMOJI_URL_MAP

        fun enqueueLoad(
            context: Context,
            forceRefresh: Boolean = false,
            initial: Boolean = true,
        ) = WorkScheduler.enqueueLoad(context, forceRefresh, initial)

        fun enqueueRefilter(context: Context) = WorkScheduler.enqueueRefilter(context)

        fun resetDailyRefreshState(context: Context) = WorkScheduler.resetDailyRefreshState(context)

        fun getRefreshTimeFromPrefrence(context: Context): Pair<Int, Int> = WorkScheduler.getRefreshTimeFromPrefrence(context)

        fun loadImageDates(context: Context): Map<String, String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_IMAGE_DATES, null) ?: return emptyMap()
            return try {
                LoliApiClient.json.decodeFromString<Map<String, String>>(raw)
            } catch (_: Exception) {
                emptyMap()
            }
        }

        fun fetchAndCacheReactions(context: Context) = ReactionService.fetchAndCacheReactions(context)

        fun loadReactions(context: Context) = ReactionService.loadReactions(context)

        fun loadUserReactions(context: Context) = ReactionService.loadUserReactions(context)

        fun loadDiscussions(context: Context) = ReactionService.loadDiscussions(context)

        fun loadTopicFloors(context: Context) = ReactionService.loadTopicFloors(context)

        fun getCardIndex(
            context: Context,
            token: String,
        ) = ReactionService.getCardIndex(context, token)

        fun patchReaction(
            context: Context,
            cardIndex: Int,
            emojiValue: Int,
        ) = ReactionService.patchReaction(context, cardIndex, emojiValue)

        fun loadSession(context: Context): Session? = SessionManager.loadSession(context)

        fun saveSession(
            context: Context,
            session: Session,
        ) = SessionManager.saveSession(context, session)

        fun clearSession(context: Context) = SessionManager.clearSession(context)

        fun saveUsername(
            context: Context,
            username: String,
        ) = SessionManager.saveUsername(context, username)

        fun loadUsername(context: Context): String? = SessionManager.loadUsername(context)

        fun saveDomain(
            context: Context,
            domain: String,
        ) = SessionManager.saveDomain(context, domain)

        fun loadDomain(context: Context): String = SessionManager.loadDomain(context)

        fun getUsername(session: Session): String? = SessionManager.getUsername(session)
    }
}
