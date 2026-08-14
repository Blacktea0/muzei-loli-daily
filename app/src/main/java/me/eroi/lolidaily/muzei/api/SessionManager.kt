package me.eroi.lolidaily.muzei.api

import android.content.Context
import androidx.core.content.edit
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import me.eroi.lolidaily.muzei.LoliDailyArtWorker

/** Mirrors the JS session data structure: { token: "JWT", expiresAt: 1234567890000 }. */
@Serializable
data class Session(val token: String, val expiresAt: Long) {
    val isValid: Boolean
        get() = isValidAt(System.currentTimeMillis())

    internal fun isValidAt(now: Long): Boolean = token.isNotBlank() && expiresAt > now

    internal fun shouldRefreshAt(now: Long): Boolean =
        isValidAt(now) && expiresAt - now <= REFRESH_WINDOW_MS

    companion object {
        internal const val REFRESH_WINDOW_MS = 5L * 24L * 60L * 60L * 1000L
    }
}

object SessionManager {
    private const val KEY_LC_SESSION = "lc_session"
    private const val KEY_BGM_USERNAME = "bgm_username"
    private const val KEY_BGM_NICKNAME = "bgm_nickname"
    private const val KEY_BGM_AVATAR_URL = "bgm_avatar_url"
    private const val KEY_BGM_DOMAIN = "bgm_domain"
    private const val KEY_LC_BADGE = "lc_badge"
    private const val KEY_PIXIV_SESSION_ID = "pixiv_session_id"
    private const val KEY_PREFER_CHINESE_ROLE = "prefer_chinese_role"
    private const val DEFAULT_BGM_DOMAIN = "chii.in"
    private val json = Json { ignoreUnknownKeys = true }
    private val refreshLock = Any()

    fun loadSession(context: Context): Session? =
        loadStoredSession(context)?.takeIf { it.isValid }

    fun refreshSessionIfNeeded(context: Context): Session? =
        synchronized(refreshLock) {
            val current = loadStoredSession(context) ?: return@synchronized null
            val now = System.currentTimeMillis()
            if (!current.isValidAt(now)) return@synchronized null
            if (!current.shouldRefreshAt(now)) return@synchronized current

            when (
                val result =
                    LoliApiClient.refreshSession(
                        baseUrl = LoliApiClient.getApiBaseUrl(context),
                        token = current.token,
                    )
            ) {
                is SessionRefreshResult.Success -> {
                    saveSession(context, result.session)
                    result.session
                }
                SessionRefreshResult.Unauthorized -> {
                    clearSession(context)
                    null
                }
                SessionRefreshResult.Failed -> current
            }
        }

    private fun loadStoredSession(context: Context): Session? {
        val prefs =
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LC_SESSION, null) ?: return null
        return try {
            json.decodeFromString<Session>(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun saveSession(
        context: Context,
        session: Session,
    ) {
        val prefs =
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = json.encodeToString(Session.serializer(), session)
        prefs.edit { putString(KEY_LC_SESSION, raw) }
    }

    fun clearSession(context: Context) {
        context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                remove(KEY_LC_SESSION)
                remove(KEY_BGM_USERNAME)
                remove(KEY_BGM_NICKNAME)
                remove(KEY_BGM_AVATAR_URL)
                remove(KEY_LC_BADGE)
                remove("user_reactions")
            }
    }

    fun saveUsername(
        context: Context,
        username: String,
    ) {
        context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_BGM_USERNAME, username)
            }
    }

    fun loadUsername(context: Context): String? {
        return context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BGM_USERNAME, null)
    }

    fun saveUserProfile(
        context: Context,
        username: String,
        nickname: String?,
        avatarUrl: String?,
    ) {
        context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_BGM_USERNAME, username)
                putString(KEY_BGM_NICKNAME, nickname.orEmpty())
                putString(KEY_BGM_AVATAR_URL, avatarUrl.orEmpty())
            }
    }

    fun loadNickname(context: Context): String? {
        return context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BGM_NICKNAME, null)
            ?.ifBlank { null }
    }

    fun loadAvatarUrl(context: Context): String? {
        return context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BGM_AVATAR_URL, null)
            ?.ifBlank { null }
    }

    fun saveDomain(
        context: Context,
        domain: String,
    ) {
        context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_BGM_DOMAIN, domain)
            }
    }

    fun loadDomain(context: Context): String {
        return context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BGM_DOMAIN, DEFAULT_BGM_DOMAIN) ?: DEFAULT_BGM_DOMAIN
    }

    fun saveBadge(
        context: Context,
        badge: String,
    ) {
        context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_LC_BADGE, badge)
            }
    }

    fun loadBadge(context: Context): String {
        return context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LC_BADGE, null)
            ?.takeIf { it.isNotBlank() && it != "未授权" }
            ?: LoliApiClient.DEFAULT_BADGE
    }

    fun loadRawBadge(context: Context): String? {
        return context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LC_BADGE, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun getUsername(session: Session): String? {
        val payload = decodeJwtPayload(session.token) ?: return null
        return payload["bgmUsername"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            ?: payload["username"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            ?: payload["sub"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
    }

    internal fun sessionFromJwt(token: String): Session? {
        if (token.isBlank()) return null
        val expiresAt =
            decodeJwtPayload(token)
                ?.get("expiresAt")
                ?.jsonPrimitive
                ?.longOrNull
                ?: return null
        return Session(token = token, expiresAt = expiresAt)
    }

    private fun decodeJwtPayload(token: String): JsonObject? {
        return try {
            val parts = token.split('.')
            if (parts.size < 2) return null
            val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
            json.parseToJsonElement(payload).jsonObject
        } catch (_: Exception) {
            null
        }
    }

    fun savePixivSessionId(
        context: Context,
        sessionId: String,
    ) {
        context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_PIXIV_SESSION_ID, sessionId)
            }
    }

    fun loadPixivSessionId(context: Context): String? {
        return context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PIXIV_SESSION_ID, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun clearPixivSession(context: Context) {
        context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                remove(KEY_PIXIV_SESSION_ID)
            }
    }

    fun loadPreferChineseRole(context: Context): Boolean {
        val prefs = context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PREFER_CHINESE_ROLE, false)
    }

    fun savePreferChineseRole(context: Context, value: Boolean) {
        val prefs = context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_PREFER_CHINESE_ROLE, value) }
    }
}
