package me.eroi.lolidaily.muzei

import android.content.Context
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
        buildPreviews()
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
            ?.sortedByDescending { it.lastModified() }
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
