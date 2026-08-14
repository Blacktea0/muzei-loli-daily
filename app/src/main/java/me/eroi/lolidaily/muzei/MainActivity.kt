package me.eroi.lolidaily.muzei

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import me.eroi.lolidaily.muzei.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import com.google.android.apps.muzei.api.MuzeiContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.eroi.lolidaily.muzei.api.BangumiApiClient
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.api.link.extractUrl
import me.eroi.lolidaily.muzei.db.DatabaseProvider
import me.eroi.lolidaily.muzei.db.EntityMapper
import me.eroi.lolidaily.muzei.model.ArtworkPreview
import me.eroi.lolidaily.muzei.model.Card
import me.eroi.lolidaily.muzei.model.DailyResponse
import me.eroi.lolidaily.muzei.model.ReactionCount
import me.eroi.lolidaily.muzei.ui.screen.KEY_HIDE_RECENTS_CONTENT
import me.eroi.lolidaily.muzei.ui.screen.MainScreen
import me.eroi.lolidaily.muzei.ui.theme.ColorSource
import me.eroi.lolidaily.muzei.ui.theme.ColorStyle
import me.eroi.lolidaily.muzei.ui.theme.LoliDailyTheme
import me.eroi.lolidaily.muzei.ui.theme.ThemeMode
import me.eroi.lolidaily.muzei.util.ArtworkColorExtractor
import me.eroi.lolidaily.muzei.util.DebugMode
import me.eroi.lolidaily.muzei.util.Md5
import me.eroi.lolidaily.muzei.util.applyRecentsPrivacy
import me.eroi.lolidaily.muzei.worker.WorkScheduler
import java.io.File

/**
 * Main activity launched from Muzei's source configuration screen and the app drawer.
 *
 * Displays tag filter and cached artwork previews with metadata.
 */
class MainActivity : AppCompatActivity() {
    private val prefs by lazy {
        getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, MODE_PRIVATE)
    }

    private var selectedTags by mutableStateOf(emptySet<String>())
    private var todayPreviews by mutableStateOf(emptyList<ArtworkPreview>())
    private var bookmarkPreviews by mutableStateOf(emptyList<ArtworkPreview>())
    private var cachedCardsByToken: Map<String, Card> = emptyMap()
    private var isLoggedIn by mutableStateOf(false)
    private var optimisticArtworkReactions by mutableStateOf<Map<String, Int?>>(emptyMap())
    private var bgmUsername by mutableStateOf<String?>(null)
    private var bgmNickname by mutableStateOf<String?>(null)
    private var bgmAvatarUrl by mutableStateOf<String?>(null)
    private var bgmDomain by mutableStateOf("chii.in")
    private var lcBadge by mutableStateOf<String?>(null)
    private var isSourceActivated by mutableStateOf(false)
    private var isMuzeiInstalled by mutableStateOf(false)
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var colorSource by mutableStateOf(ColorSource.DEFAULT)
    private var colorStyle by mutableStateOf(ColorStyle.NEUTRAL)
    private var manualColorArgb by mutableIntStateOf(0xFFF09199.toInt())
    private var extractedArgb by mutableStateOf<Int?>(null)
    private var refreshProgress by mutableStateOf<Float?>(null)
    private var pendingReactionTokens = emptySet<String>()

    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure Coil with SVG and AVIF decoders
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(SvgDecoder.Factory())
                    add(me.eroi.lolidaily.muzei.api.decoder.AvifDecoder.Factory())
                }
                .build()
        }

        loadState()
        applyRecentsPrivacy(this, prefs.getBoolean(KEY_HIDE_RECENTS_CONTENT, false))
        loadSourceStatus()
        WorkScheduler.ensureDailyRefreshScheduled(this)

        // Muzei launches with an explicit Intent (no action); launcher uses ACTION_MAIN
        val fromMuzei = intent?.action.let { it != Intent.ACTION_MAIN && it != Intent.ACTION_SEND }

        // Handle share intent: extract URL from shared text
        // Short link resolution and canonicalization happen in SubmitPage
        val sharedUrl: String? = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val raw = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!raw.isNullOrBlank()) extractUrl(raw.trim()) else null
        } else null

        setContent {
            LoliDailyTheme(
                themeMode = themeMode,
                colorSource = colorSource,
                sourceArgb = extractedArgb,
                colorStyle = colorStyle,
            ) {
                MainScreen(
                    selectedTags = selectedTags,
                    onTagsChanged = { newTags ->
                        selectedTags = newTags
                        saveState()
                        if (colorSource == ColorSource.IMAGE) {
                            resolveSourceColor()
                        }
                    },
                    todayArtwork = todayPreviews,
                    bookmarkArtwork = bookmarkPreviews,
                    isLoggedIn = isLoggedIn,
                    bgmUsername = bgmUsername,
                    bgmNickname = bgmNickname,
                    bgmAvatarUrl = bgmAvatarUrl,
                    onLogin = { startActivity(Intent(this, LoginActivity::class.java)) },
                    onLogout = {
                        LoliDailyArtWorker.clearSession(this)
                        LoginActivity.clearBgmCookies()
                        isLoggedIn = false
                        bgmUsername = null
                        bgmNickname = null
                        bgmAvatarUrl = null
                        lcBadge = null
                        startRefresh()
                        Toast.makeText(this, getString(R.string.msg_logged_out), Toast.LENGTH_SHORT).show()
                    },
                    bgmDomain = bgmDomain,
                    onDomainChanged = { domain ->
                        bgmDomain = domain
                        LoliDailyArtWorker.saveDomain(this, domain)
                    },
                    lcBadge = lcBadge,
                    onBadgeChanged = { newBadge -> updateBadge(newBadge) },
                    onReactionClick = { token, emojiValue ->
                        handleReactionClick(token, emojiValue)
                    },
                    onRefresh = { startRefresh() },
                    isSourceActivated = isSourceActivated,
                    isMuzeiInstalled = isMuzeiInstalled,
                    onOpenMuzei = { openMuzei() },
                    themeMode = themeMode,
                    onThemeModeChanged = { mode ->
                        themeMode = mode
                        saveThemeMode(mode)
                    },
                    colorSource = colorSource,
                    onColorSourceChanged = { source ->
                        colorSource = source
                        saveColorSource(source)
                        resolveSourceColor()
                    },
                    colorStyle = colorStyle,
                    onColorStyleChanged = { style ->
                        colorStyle = style
                        saveColorStyle(style)
                    },
                    manualColorArgb = manualColorArgb,
                    onManualColorChanged = { argb ->
                        manualColorArgb = argb
                        saveManualColor(argb)
                        if (colorSource == ColorSource.MANUAL) {
                            extractedArgb = argb
                        }
                    },
                    sourceColorArgb = extractedArgb,
                    onOpenDebug = {
                        startActivity(
                            Intent(this@MainActivity, AdditionalSettingsActivity::class.java),
                        )
                    },
                    onOpenAbout = {
                        startActivity(
                            Intent(this@MainActivity, AboutActivity::class.java),
                        )
                    },
                    onBookmarkToggle = { token, _, bookmarked ->
                        toggleBookmark(token, bookmarked)
                    },
                    onRemoveBookmark = { preview ->
                        removeBookmark(preview)
                    },
                    refreshProgress = refreshProgress,
                    initialTab = if (fromMuzei) 3 else if (sharedUrl != null) 2 else 0,
                    initialSourceUrl = sharedUrl,
                    onTodayPageOpened = { loadPreview() },
                )
            }
        }

        lifecycleScope.launch {
            LoliDailyArtWorker.refreshProgress.collect { fraction ->
                refreshProgress = fraction
            }
        }
        loadPreview()
        if (!LoliDailyArtWorker.isAlreadyFetchedToday(this) || todayPreviews.isEmpty()) {
            startRefresh()
        }
    }

    private fun startRefresh() {
        val workId = WorkScheduler.enqueueLoad(this, forceRefresh = true)
        if (workId != null) {
            val workManager = WorkManager.getInstance(this)
            workManager.getWorkInfoByIdLiveData(workId).observe(this) { workInfo ->
                if (workInfo != null) {
                    val state = workInfo.state
                    if (state == WorkInfo.State.SUCCEEDED || state == WorkInfo.State.FAILED || state == WorkInfo.State.CANCELLED) {
                        refreshProgress = null
                        if (state == WorkInfo.State.SUCCEEDED) {
                            loadPreview(forceRefresh = true)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyRecentsPrivacy(this, prefs.getBoolean(KEY_HIDE_RECENTS_CONTENT, false))
        val wasLoggedIn = isLoggedIn
        isLoggedIn = LoliDailyArtWorker.loadSession(this) != null
        loadAccountProfile()
        refreshLoginSession()
        loadSourceStatus()

        val needsProfile = bgmNickname == null || bgmAvatarUrl == null || lcBadge == null
        if (!wasLoggedIn && isLoggedIn) {
            if (needsProfile) {
                refreshAccountProfile(forceRefreshOnComplete = true)
            }
        } else {
            if (isLoggedIn && needsProfile) {
                refreshAccountProfile()
            }
            loadPreview()
        }
    }

    private fun refreshLoginSession() {
        if (!isLoggedIn) return
        lifecycleScope.launch {
            val session =
                withContext(Dispatchers.IO) {
                    LoliDailyArtWorker.refreshSessionIfNeeded(this@MainActivity)
                }
            isLoggedIn = session != null
            if (session == null) {
                loadAccountProfile()
            }
        }
    }

    private fun handleReactionClick(
        token: String,
        emojiValue: Int,
    ) {
        val cardIndex = LoliDailyArtWorker.getCardIndex(this, token) ?: return
        if (token in pendingReactionTokens) return

        val optimisticReaction = applyOptimisticReaction(token, emojiValue) ?: return

        pendingReactionTokens += token
        Thread {
            val ok =
                LoliDailyArtWorker.patchReaction(
                    this,
                    cardIndex,
                    emojiValue,
                    token,
                    optimisticReaction.nextEmoji,
                )
            if (ok) {
                LoliDailyArtWorker.fetchAndCacheReactions(this)
                runOnUiThread {
                    pendingReactionTokens -= token
                    buildPreviews()
                }
            } else {
                runOnUiThread {
                    pendingReactionTokens -= token
                    optimisticArtworkReactions = optimisticArtworkReactions - token
                    buildPreviews()
                }
            }
        }
            .start()
    }

    private fun applyOptimisticReaction(
        token: String,
        emojiValue: Int,
    ): OptimisticReaction? {
        val currentPreview =
            todayPreviews.firstOrNull { it.filename.substringBeforeLast('.') == token }
                ?: bookmarkPreviews.firstOrNull { it.filename.substringBeforeLast('.') == token }
                ?: return null
        val previousEmoji = currentPreview.userEmoji
        val nextEmoji = if (previousEmoji == emojiValue) null else emojiValue

        optimisticArtworkReactions = optimisticArtworkReactions + (token to nextEmoji)
        buildPreviews()
        return OptimisticReaction(nextEmoji)
    }

    private data class OptimisticReaction(val nextEmoji: Int?)

    private fun List<ArtworkPreview>.withOptimisticReaction(
        token: String,
        previousEmoji: Int?,
        nextEmoji: Int?,
    ): List<ArtworkPreview> =
        map { preview ->
            if (preview.filename.substringBeforeLast('.') != token) {
                preview
            } else {
                preview.copy(
                    reactions = preview.reactions.withOptimisticReactionCount(previousEmoji, nextEmoji),
                    userEmoji = nextEmoji,
                )
            }
        }

    private fun List<ReactionCount>.withOptimisticReactionCount(
        previousEmoji: Int?,
        nextEmoji: Int?,
    ): List<ReactionCount> {
        val currentUser = bgmNickname ?: bgmUsername
        val counts = associateBy { it.emojiValue }.toMutableMap()

        if (previousEmoji != null) {
            val previous = counts[previousEmoji]
            if (previous != null) {
                val nextCount = previous.count - 1
                if (nextCount > 0) {
                    counts[previousEmoji] =
                        previous.copy(
                            count = nextCount,
                            users = previous.users.withoutCurrentUser(currentUser),
                        )
                } else {
                    counts.remove(previousEmoji)
                }
            }
        }

        if (nextEmoji != null) {
            val next = counts[nextEmoji]
            counts[nextEmoji] =
                next?.copy(
                    count = next.count + 1,
                    users = next.users.withCurrentUser(currentUser),
                ) ?: ReactionCount(
                    emojiValue = nextEmoji,
                    count = 1,
                    users = currentUser?.let { listOf(it) } ?: emptyList(),
                )
        }

        return counts.values.sortedByDescending { it.count }
    }

    private fun List<String>.withCurrentUser(currentUser: String?): List<String> =
        if (currentUser.isNullOrBlank() || currentUser in this) this else this + currentUser

    private fun List<String>.withoutCurrentUser(currentUser: String?): List<String> =
        if (currentUser.isNullOrBlank()) this else filterNot { it == currentUser }

    private fun loadState() {
        val enabledTags = prefs.getStringSet(LoliDailyArtWorker.KEY_ENABLED_TAGS, null)
        selectedTags = enabledTags ?: setOf("LC0", "LC YJ")
        bgmDomain = LoliDailyArtWorker.loadDomain(this)
        loadAccountProfile()
        themeMode = loadThemeMode()
        colorSource = loadColorSource()
        colorStyle = loadColorStyle()
        manualColorArgb = loadManualColor()
    }

    private fun saveState() {
        prefs.edit { putStringSet(LoliDailyArtWorker.KEY_ENABLED_TAGS, selectedTags) }
        WorkScheduler.enqueueRefilter(this)
    }

    private fun loadAccountProfile() {
        val session = LoliDailyArtWorker.loadSession(this)
        bgmUsername = LoliDailyArtWorker.loadUsername(this) ?: session?.let { LoliDailyArtWorker.getUsername(it) }
        bgmNickname = LoliDailyArtWorker.loadNickname(this)
        bgmAvatarUrl = LoliDailyArtWorker.loadAvatarUrl(this)
        lcBadge = LoliDailyArtWorker.loadRawBadge(this)
    }

    private fun refreshAccountProfile(forceRefreshOnComplete: Boolean = false) {
        val username = bgmUsername ?: return
        val shouldFetchLcProfile = LoliDailyArtWorker.loadSession(this) != null
        val oldBadge = LoliDailyArtWorker.loadBadge(this)
        Thread {
            // Fetch Bangumi user profile (avatar, nickname)
            try {
                val user = BangumiApiClient.fetchUser(this@MainActivity, username)
                if (user != null) {
                    val avatarUrl =
                        listOfNotNull(user.avatar?.large, user.avatar?.medium, user.avatar?.small)
                            .firstOrNull { it.isNotBlank() }
                    LoliDailyArtWorker.saveUserProfile(
                        this@MainActivity,
                        user.username.ifBlank { username },
                        user.nickname.ifBlank { user.username.ifBlank { username } },
                        avatarUrl,
                    )
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to fetch Bangumi user profile", e)
            }

            // Fetch LC badge (independent of Bangumi profile)
            var newBadge = oldBadge
            if (shouldFetchLcProfile) {
                Log.d("MainActivity", "Fetching LC badge for $username")
                try {
                    val userInfo = LoliApiClient.fetchUserInfo(this@MainActivity, username)
                    if (userInfo != null) {
                        Log.d("MainActivity", "LC badge fetched: ${userInfo.badge}")
                        LoliDailyArtWorker.saveBadge(this@MainActivity, userInfo.badge)
                        newBadge = userInfo.badge
                    } else {
                        Log.w("MainActivity", "LC badge fetch returned null")
                    }
                } catch (e: Exception) {
                    Log.w("MainActivity", "Failed to fetch LC badge", e)
                }
            } else {
                Log.w("MainActivity", "No session available for badge fetch")
            }

            val badgeChanged = newBadge != oldBadge
            runOnUiThread {
                isLoggedIn = LoliDailyArtWorker.loadSession(this@MainActivity) != null
                loadAccountProfile()
                if (forceRefreshOnComplete && badgeChanged) {
                    startRefresh()
                }
            }
        }.start()
    }

    private fun updateBadge(badge: String) {
        if (LoliDailyArtWorker.loadSession(this) == null) return
        Thread {
            val ok = LoliApiClient.updateBadge(this@MainActivity, badge)
            if (ok) {
                LoliDailyArtWorker.saveBadge(this@MainActivity, badge)
                runOnUiThread { lcBadge = badge }
            } else {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.msg_badge_update_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }.start()
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
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    private fun loadColorSource(): ColorSource {
        val stored = prefs.getString(KEY_COLOR_SOURCE, null) ?: return ColorSource.DEFAULT
        return try {
            ColorSource.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            ColorSource.DEFAULT
        }
    }

    private fun saveColorSource(source: ColorSource) {
        prefs.edit { putString(KEY_COLOR_SOURCE, source.name) }
    }

    private fun loadColorStyle(): ColorStyle {
        val stored = prefs.getString(KEY_COLOR_STYLE, null) ?: return ColorStyle.NEUTRAL
        return try {
            ColorStyle.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            ColorStyle.NEUTRAL
        }
    }

    private fun saveColorStyle(style: ColorStyle) {
        prefs.edit { putString(KEY_COLOR_STYLE, style.name) }
    }

    private fun loadManualColor(): Int {
        return prefs.getInt(KEY_MANUAL_COLOR, 0xFFF09199.toInt())
    }

    private fun saveManualColor(argb: Int) {
        prefs.edit { putInt(KEY_MANUAL_COLOR, argb) }
    }

    private fun resolveSourceColor() {
        when (colorSource) {
            ColorSource.IMAGE -> {
                val candidates =
                    if (selectedTags.isEmpty()) {
                        todayPreviews
                    } else {
                        todayPreviews.filter { selectedTags.contains(it.tags) }
                    }
                val filename = candidates.firstOrNull()?.filename ?: todayPreviews.firstOrNull()?.filename
                if (filename != null) {
                    Thread {
                        val argb = ArtworkColorExtractor.extract(this, filename, manualColorArgb)
                        runOnUiThread {
                            extractedArgb = argb
                            prefs.edit { putInt(KEY_EXTRACTED_COLOR, argb) }
                        }
                    }.start()
                }
            }
            ColorSource.MANUAL -> {
                extractedArgb = manualColorArgb
            }
            ColorSource.DEFAULT -> {
                extractedArgb = null
            }
        }
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
                            "https://play.google.com/store/apps/details?id=$MUZEI_PACKAGE".toUri(),
                        ),
                    )
                } catch (_: Exception) {
                    try {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "market://details?id=$MUZEI_PACKAGE".toUri(),
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
        private const val KEY_COLOR_SOURCE = "color_source"
        private const val KEY_COLOR_STYLE = "color_style"
        private const val KEY_MANUAL_COLOR = "manual_color"
        private const val KEY_EXTRACTED_COLOR = "extracted_color"
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
        // Gated by a 1-minute cooldown per daily batch to avoid spamming the API.
        Thread {
            val (_, apiDate) = loadCachedDaily()
            if (apiDate.isBlank()) {
                return@Thread
            }
            LoliDailyArtWorker.fetchAndCacheReactions(this@MainActivity, forceRefresh)
            runOnUiThread { buildPreviews() }
        }
            .start()
    }

    /** Builds previews from on-disk cache without triggering network. */
    private fun buildPreviews() {
        val artworksDir = File(filesDir, "artworks")
        val files =
            artworksDir.listFiles()?.filter {
                it.isFile && it.length() > 0 && !it.name.endsWith(".tmp")
            } ?: emptyList()

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
                        characterIds =
                            card?.characterIds ?: roomFields?.characterIds ?: emptyList(),
                        sourceUrl = card?.sourceUrl ?: roomFields?.sourceUrl ?: "",
                        artistUrl = card?.artistUrl ?: roomFields?.artistUrl ?: "",
                        date = dateMap[token] ?: roomFields?.date ?: apiDate,
                        reactions = reactionsMap[token] ?: emptyList(),
                        userEmoji = userReactionsMap[token],
                        isBookmarked = roomFields?.bookmarked?.let { it != 0 } ?: false,
                        suggestedByName = card?.suggestedBy?.nickname ?: roomFields?.suggestedByNickname,
                        suggestedByUsername = card?.suggestedBy?.username ?: roomFields?.suggestedByUsername,
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

        val currentUser = bgmNickname ?: bgmUsername
        val reconciledMap = optimisticArtworkReactions.toMutableMap()
        val reconciledPreviews = previews.map { preview ->
            val token = preview.filename.substringBeforeLast('.')
            if (token !in reconciledMap) {
                preview
            } else {
                val targetEmoji = reconciledMap[token]
                val serverReflects = if (currentUser.isNullOrBlank()) {
                    false
                } else if (targetEmoji == null) {
                    preview.reactions.none { r -> r.users.contains(currentUser) }
                } else {
                    preview.reactions.any { r -> r.emojiValue == targetEmoji && r.users.contains(currentUser) }
                }

                if (serverReflects) {
                    reconciledMap.remove(token)
                    preview
                } else {
                    val previousEmoji = preview.reactions.firstOrNull { r ->
                        currentUser != null && r.users.contains(currentUser)
                    }?.emojiValue
                    preview.copy(
                        reactions = preview.reactions.withOptimisticReactionCount(previousEmoji, targetEmoji),
                        userEmoji = targetEmoji
                    )
                }
            }
        }
        if (reconciledMap != optimisticArtworkReactions) {
            optimisticArtworkReactions = reconciledMap
        }

        todayPreviews = reconciledPreviews.filter { it.date == apiDate }
        bookmarkPreviews = reconciledPreviews.filter { it.isBookmarked }
        if (colorSource == ColorSource.IMAGE) {
            resolveSourceColor()
        }
    }

    /** Loads all Room-persisted artwork fields, keyed by token. */
    private fun loadRoomArtworkFields(): Map<String, EntityMapper.CardFields> {
        return try {
            val entities =
                runBlocking {
                    DatabaseProvider.getInstance(this@MainActivity).cachedArtworkDao().getAll()
                }
            entities.associate { entity -> entity.token to EntityMapper.entityToCardFields(entity) }
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to load artwork metadata from Room", e)
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
                Log.w("MainActivity", "Failed to toggle bookmark", e)
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
                Log.w("MainActivity", "Failed to remove bookmark", e)
            }
        }.start()
    }
}
