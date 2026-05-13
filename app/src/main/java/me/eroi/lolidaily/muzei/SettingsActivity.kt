package me.eroi.lolidaily.muzei

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.google.android.apps.muzei.api.MuzeiContract
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.eroi.lolidaily.muzei.db.DatabaseProvider
import me.eroi.lolidaily.muzei.db.EntityMapper
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import me.eroi.lolidaily.muzei.model.Card
import me.eroi.lolidaily.muzei.model.DailyResponse
import me.eroi.lolidaily.muzei.ui.screen.SettingsScreen
import me.eroi.lolidaily.muzei.ui.theme.LoliDailyTheme
import me.eroi.lolidaily.muzei.ui.theme.ThemeMode
import me.eroi.lolidaily.muzei.util.Md5
import java.io.File

/**
 * Settings activity launched from Muzei's source configuration screen and the app drawer.
 *
 * Displays tag filter and cached artwork previews with metadata.
 */
class SettingsActivity : AppCompatActivity() {
    private val prefs by lazy {
        getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var selectedTags by mutableStateOf(emptySet<String>())
    private var todayPreviews by mutableStateOf(emptyList<ArtworkPreview>())
    private var bookmarkPreviews by mutableStateOf(emptyList<ArtworkPreview>())
    private var cachedCardsByToken: Map<String, Card> = emptyMap()
    private var isLoggedIn by mutableStateOf(false)
    private var bgmDomain by mutableStateOf("chii.in")
    private var isSourceActivated by mutableStateOf(false)
    private var isMuzeiInstalled by mutableStateOf(false)
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)

    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        loadState()
        loadSourceStatus()

        // Muzei launches with an explicit Intent (no action); launcher uses ACTION_MAIN
        val fromMuzei = intent?.action != Intent.ACTION_MAIN

        setContent {
            LoliDailyTheme(themeMode = themeMode) {
                SettingsScreen(
                    selectedTags = selectedTags,
                    onTagsChanged = { newTags ->
                        selectedTags = newTags
                        saveState()
                    },
                    todayArtwork = todayPreviews,
                    bookmarkArtwork = bookmarkPreviews,
                    isLoggedIn = isLoggedIn,
                    onLogin = { startActivity(Intent(this, LoginActivity::class.java)) },
                    onLogout = {
                        LoliDailyArtWorker.clearSession(this)
                        LoginActivity.clearBgmCookies()
                        isLoggedIn = false
                        buildPreviews()
                        Toast.makeText(this, getString(R.string.msg_logged_out), Toast.LENGTH_SHORT).show()
                    },
                    bgmDomain = bgmDomain,
                    onDomainChanged = { domain ->
                        bgmDomain = domain
                        LoliDailyArtWorker.saveDomain(this, domain)
                    },
                    onReactionClick = { token, emojiValue ->
                        handleReactionClick(token, emojiValue)
                    },
                    onRefresh = {
                        LoliDailyArtWorker.enqueueLoad(this, forceRefresh = true)
                        Toast.makeText(this, getString(R.string.msg_refresh_enqueued), Toast.LENGTH_SHORT).show()
                        Thread {
                            val cacheFile = File(filesDir, "api_cache.json")
                            val initialModTime =
                                if (cacheFile.exists()) cacheFile.lastModified() else 0L
                            var attempts = 0
                            while (attempts < 20) {
                                Thread.sleep(500)
                                attempts++
                                if (cacheFile.exists() &&
                                    cacheFile.lastModified() != initialModTime
                                ) {
                                    break
                                }
                            }
                            runOnUiThread { loadPreview(forceRefresh = true) }
                        }
                            .start()
                    },
                    isSourceActivated = isSourceActivated,
                    isMuzeiInstalled = isMuzeiInstalled,
                    onOpenMuzei = { openMuzei() },
                    themeMode = themeMode,
                    onThemeModeChanged = { mode ->
                        themeMode = mode
                        saveThemeMode(mode)
                    },
                    onOpenDebug = {
                        startActivity(
                            Intent(this@SettingsActivity, DebugSettingsActivity::class.java),
                        )
                    },
                    onBookmarkToggle = { token, fileName, bookmarked ->
                        toggleBookmark(token, fileName, bookmarked)
                    },
                    onRemoveBookmark = { preview ->
                        removeBookmark(preview)
                    },
                    initialTab = if (fromMuzei) 2 else null,
                )
            }
        }

        loadPreview()
    }

    override fun onResume() {
        super.onResume()
        val wasLoggedIn = isLoggedIn
        isLoggedIn = LoliDailyArtWorker.loadSession(this) != null
        loadSourceStatus()

        if (!wasLoggedIn && isLoggedIn) {
            // Just logged in — full refresh to fetch user reactions
            loadPreview()
        } else {
            buildPreviews()
        }
    }

    private fun handleReactionClick(
        token: String,
        emojiValue: Int,
    ) {
        val cardIndex = LoliDailyArtWorker.getCardIndex(this, token) ?: return
        Thread {
            val ok = LoliDailyArtWorker.patchReaction(this, cardIndex, emojiValue)
            if (ok) {
                LoliDailyArtWorker.fetchAndCacheReactions(this)
                runOnUiThread { buildPreviews() }
            } else {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.msg_reaction_failed),
                        Toast.LENGTH_SHORT,
                    )
                        .show()
                }
            }
        }
            .start()
    }

    private fun loadState() {
        val enabledTags = prefs.getStringSet(LoliDailyArtWorker.KEY_ENABLED_TAGS, null)
        selectedTags = enabledTags ?: emptySet()
        bgmDomain = LoliDailyArtWorker.loadDomain(this)
        themeMode = loadThemeMode()
    }

    private fun saveState() {
        prefs.edit().putStringSet(LoliDailyArtWorker.KEY_ENABLED_TAGS, selectedTags).apply()
        LoliDailyArtWorker.enqueueRefilter(this)
    }

    private fun loadThemeMode(): ThemeMode {
        val stored = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return try {
            ThemeMode.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    private fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    // ── Source activation ───────────────────────────────────────────

    private fun loadSourceStatus() {
        isSourceActivated =
            MuzeiContract.Sources.isProviderSelected(this, LoliDailyArtWorker.PROVIDER_AUTHORITY)
        isMuzeiInstalled = packageManager.getLaunchIntentForPackage(MUZEI_PACKAGE) != null
    }

    private fun openMuzei() {
        try {
            startActivity(
                MuzeiContract.Sources.createChooseProviderIntent(
                    LoliDailyArtWorker.PROVIDER_AUTHORITY,
                ),
            )
        } catch (_: ActivityNotFoundException) {
            val launchIntent = packageManager.getLaunchIntentForPackage(MUZEI_PACKAGE)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, getString(R.string.msg_muzei_not_installed_opening), Toast.LENGTH_SHORT)
                    .show()
                try {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                "https://play.google.com/store/apps/details?id=$MUZEI_PACKAGE",
                            ),
                        ),
                    )
                } catch (_: Exception) {
                    try {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=$MUZEI_PACKAGE"),
                            ),
                        )
                    } catch (_: Exception) {
                        Toast.makeText(this, getString(R.string.msg_no_app_store), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    companion object {
        private const val MUZEI_PACKAGE = "net.nurik.roman.muzei"
        private const val KEY_THEME_MODE = "theme_mode"
    }

    // ── Image preview with metadata ────────────────────────────────

    /**
     * Full refresh cycle: builds previews from current cache, then fetches fresh reactions in the
     * background. Called on every onCreate / onResume.
     */
    private fun loadPreview(forceRefresh: Boolean = false) {
        buildPreviews()

        // Background fetch — mirrors the JS lazy-load behaviour:
        // reactions are fetched each time the gallery is opened.
        // Gated by a 5-minute cooldown to avoid spamming the API.
        Thread {
            if (!forceRefresh) {
                val lastFetch = prefs.getLong(LoliDailyArtWorker.KEY_LAST_REACTION_FETCH, 0)
                val cooldownMs = 5 * 60 * 1000L
                if (System.currentTimeMillis() - lastFetch < cooldownMs) {
                    return@Thread
                }
            }
            LoliDailyArtWorker.fetchAndCacheReactions(this@SettingsActivity)
            prefs.edit()
                .putLong(LoliDailyArtWorker.KEY_LAST_REACTION_FETCH, System.currentTimeMillis())
                .apply()
            runOnUiThread { buildPreviews() }
        }
            .start()
    }

    /** Builds previews from on-disk cache without triggering network. */
    private fun buildPreviews() {
        val artworksDir = File(filesDir, "artworks")
        val files = artworksDir.listFiles()?.filter { it.isFile && it.length() > 0 } ?: emptyList()

        // Load cached API response for current-day metadata
        val (cards, apiDate) = loadCachedDaily()
        val cardByToken = cards.associateBy { Md5.hash(it.imgUrl) }
        cachedCardsByToken = cardByToken
        val dateMap = LoliDailyArtWorker.loadImageDates(this)
        val reactionsMap = LoliDailyArtWorker.loadReactions(this)
        val userReactionsMap = LoliDailyArtWorker.loadUserReactions(this)
        val discussionsMap = LoliDailyArtWorker.loadDiscussions(this)

        // Persisted metadata from Room (covers all historical batches)
        val roomFieldsByToken = loadRoomArtworkFields()

        val previews =
            files
                .map { file ->
                    val uri =
                        FileProvider.getUriForFile(
                            this,
                            "me.eroi.lolidaily.muzei.fileprovider",
                            file,
                        )
                    val token = file.nameWithoutExtension
                    val card = cardByToken[token]
                    val roomFields = roomFieldsByToken[token]

                    ArtworkPreview(
                        uri = uri,
                        filename = file.name,
                        artistName = card?.artistName ?: roomFields?.artistName ?: "",
                        comment = card?.comment ?: roomFields?.comment ?: "",
                        tags = card?.tags ?: roomFields?.tags ?: "",
                        characterNames =
                            card?.characterNames ?: roomFields?.characterNames ?: emptyList(),
                        sourceUrl = card?.sourceUrl ?: roomFields?.sourceUrl ?: "",
                        artistUrl = card?.artistUrl ?: roomFields?.artistUrl ?: "",
                        date = dateMap[token] ?: roomFields?.date ?: apiDate,
                        reactions = reactionsMap[token] ?: emptyList(),
                        userEmoji = userReactionsMap[token],
                        isBookmarked = roomFields?.bookmarked?.let { it != 0 } ?: false,
                        suggestedByName = card?.suggestedBy?.nickname ?: roomFields?.suggestedByNickname,
                        discussionId = discussionsMap[token]?.id,
                        discussionCount = discussionsMap[token]?.count ?: 0,
                    )
                }
                .sortedWith(
                    compareByDescending<ArtworkPreview> { it.date }
                        .thenBy { preview ->
                            when (preview.tags) {
                                "LC0" -> 0
                                "LC YJ" -> 1
                                "LC ES" -> 2
                                else -> 3
                            }
                        },
                )

        todayPreviews = previews.filter { it.date == apiDate }
        bookmarkPreviews = previews.filter { it.isBookmarked }
    }

    /** Loads all Room-persisted artwork fields, keyed by token. */
    private fun loadRoomArtworkFields(): Map<String, EntityMapper.CardFields> {
        return try {
            val entities =
                runBlocking {
                    DatabaseProvider.getInstance(this@SettingsActivity).cachedArtworkDao().getAll()
                }
            entities.associate { entity -> entity.token to EntityMapper.entityToCardFields(entity) }
        } catch (e: Exception) {
            Log.w("SettingsActivity", "Failed to load artwork metadata from Room", e)
            emptyMap()
        }
    }

    private fun loadCachedDaily(): Pair<List<Card>, String> {
        val cacheFile = File(filesDir, "api_cache.json")
        if (!cacheFile.exists()) return Pair(emptyList(), "")
        return try {
            val daily = json.decodeFromString<DailyResponse>(cacheFile.readText())
            Pair(daily.cards, daily.date)
        } catch (_: Exception) {
            Pair(emptyList(), "")
        }
    }

    private fun toggleBookmark(
        token: String,
        fileName: String,
        bookmarked: Boolean,
    ) {
        Thread {
            try {
                val dao = DatabaseProvider.getInstance(this).cachedArtworkDao()
                val existing = runBlocking { dao.getByToken(token) }
                if (existing != null) {
                    runBlocking { dao.setBookmarked(token, if (bookmarked) 1 else 0) }
                } else {
                    val card = cachedCardsByToken[token] ?: return@Thread
                    val (_, apiDate) = loadCachedDaily()
                    val entity = EntityMapper.cardToEntity(card, token, apiDate, if (bookmarked) 1 else 0)
                    runBlocking { dao.upsert(entity) }
                }
                runOnUiThread { buildPreviews() }
            } catch (e: Exception) {
                Log.w("SettingsActivity", "Failed to toggle bookmark", e)
            }
        }.start()
    }

    private fun removeBookmark(preview: ArtworkPreview) {
        Thread {
            try {
                val dao = DatabaseProvider.getInstance(this).cachedArtworkDao()
                val token = preview.filename.substringBeforeLast('.')
                runBlocking { dao.setBookmarked(token, 0) }

                val (_, apiDate) = loadCachedDaily()
                if (preview.date != apiDate) {
                    val imageFile = File(filesDir, "artworks/${preview.filename}")
                    if (imageFile.exists()) imageFile.delete()
                }

                runOnUiThread { buildPreviews() }
            } catch (e: Exception) {
                Log.w("SettingsActivity", "Failed to remove bookmark", e)
            }
        }.start()
    }
}
