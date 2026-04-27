package me.eroi.lolidaily.muzei

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import me.eroi.lolidaily.muzei.model.Card
import me.eroi.lolidaily.muzei.model.DailyResponse
import me.eroi.lolidaily.muzei.ui.screen.SettingsScreen
import me.eroi.lolidaily.muzei.ui.theme.LoliDailyTheme
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Settings activity launched from Muzei's source configuration screen
 * and the app drawer.
 *
 * Displays tag filter and cached artwork previews with metadata.
 */
class SettingsActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var selectedTags by mutableStateOf(emptySet<String>())
    private var cachedPreviews by mutableStateOf(emptyList<ArtworkPreview>())
    private var isLoggedIn by mutableStateOf(false)

    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadState()

        setContent {
            LoliDailyTheme {
                SettingsScreen(
                    selectedTags = selectedTags,
                    onTagsChanged = { newTags ->
                        selectedTags = newTags
                        saveState()
                    },
                    cachedArtwork = cachedPreviews,
                    isLoggedIn = isLoggedIn,
                    onLogin = {
                        startActivity(Intent(this, LoginActivity::class.java))
                    },
                    onLogout = {
                        LoliDailyArtWorker.clearSession(this)
                        LoginActivity.clearBgmCookies()
                        isLoggedIn = false
                        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
                    },
                    onReactionClick = { token, emojiValue ->
                        handleReactionClick(token, emojiValue)
                    },
                    onRefresh = {
                        LoliDailyArtWorker.enqueueLoad(this, forceRefresh = true)
                        Toast.makeText(
                            this,
                            "Refresh enqueued — respecting current tag filter",
                            Toast.LENGTH_SHORT
                        ).show()
                        window.decorView.postDelayed({ loadPreview() }, 5000)
                    },
                )
            }
        }

        loadPreview()
    }

    override fun onResume() {
        super.onResume()
        isLoggedIn = LoliDailyArtWorker.loadSession(this) != null
        buildPreviews()
    }

    private fun handleReactionClick(token: String, emojiValue: Int) {
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
                        "Reaction failed — session may have expired",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    private fun loadState() {
        val enabledTags = prefs.getStringSet(
            LoliDailyArtWorker.KEY_ENABLED_TAGS, null
        )
        selectedTags = enabledTags ?: emptySet()
    }

    private fun saveState() {
        prefs.edit()
            .putStringSet(LoliDailyArtWorker.KEY_ENABLED_TAGS, selectedTags)
            .apply()
        LoliDailyArtWorker.enqueueRefilter(this)
    }

    // ── Image preview with metadata ────────────────────────────────

    /**
     * Full refresh cycle: builds previews from current cache,
     * then fetches fresh reactions in the background.
     * Called on every onCreate / onResume.
     */
    private fun loadPreview() {
        buildPreviews()

        // Background fetch — mirrors the JS lazy-load behaviour:
        // reactions are fetched each time the gallery is opened
        Thread {
            LoliDailyArtWorker.fetchAndCacheReactions(this@SettingsActivity)
            runOnUiThread { buildPreviews() }
        }.start()
    }

    /** Builds previews from on-disk cache without triggering network. */
    private fun buildPreviews() {
        val artworksDir = File(filesDir, "artworks")
        val files = artworksDir.listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?: emptyList()

        // Load cached API response to get Card metadata
        val (cards, apiDate) = loadCachedDaily()
        val cardByToken = cards.associateBy { md5(it.imgUrl) }
        val dateMap = LoliDailyArtWorker.loadImageDates(this)
        val reactionsMap = LoliDailyArtWorker.loadReactions(this)

        cachedPreviews = files.take(4).map { file ->
            val uri = FileProvider.getUriForFile(
                this,
                "me.eroi.lolidaily.muzei.fileprovider",
                file
            )
            val token = file.nameWithoutExtension
            val card = cardByToken[token]

            ArtworkPreview(
                uri = uri,
                filename = file.name,
                artistName = card?.artistName ?: "",
                comment = card?.comment ?: "",
                tags = card?.tags ?: "",
                characterNames = card?.characterNames ?: emptyList(),
                sourceUrl = card?.sourceUrl ?: "",
                artistUrl = card?.artistUrl ?: "",
                date = dateMap[token] ?: apiDate,
                reactions = reactionsMap[token] ?: emptyList(),
            )
        }.sortedBy { preview ->
            // LC0 → LC YJ → LC ES → others
            when (preview.tags) {
                "LC0" -> 0
                "LC YJ" -> 1
                "LC ES" -> 2
                else -> 3
            }
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

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
