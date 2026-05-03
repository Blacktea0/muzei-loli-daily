package me.eroi.lolidaily.muzei.api

import android.content.Context
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.eroi.lolidaily.muzei.LoliDailyArtWorker

/** Mirrors the JS session data structure: { token: "JWT", expiresAt: 1234567890000 }. */
@Serializable
data class Session(val token: String, val expiresAt: Long) {
    val isValid: Boolean
        get() = token.isNotBlank() && expiresAt > System.currentTimeMillis()
}

object SessionManager {
    private const val KEY_LC_SESSION = "lc_session"
    private const val KEY_BGM_USERNAME = "bgm_username"
    private const val KEY_BGM_DOMAIN = "bgm_domain"
    private const val DEFAULT_BGM_DOMAIN = "chii.in"

    private val json = Json { ignoreUnknownKeys = true }

    fun loadSession(context: Context): Session? {
        val prefs =
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LC_SESSION, null) ?: return null
        return try {
            val s = json.decodeFromString<Session>(raw)
            if (s.isValid) s else null
        } catch (_: Exception) {
            null
        }
    }

    fun saveSession(context: Context, session: Session) {
        val prefs =
            context.getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = json.encodeToString(Session.serializer(), session)
        prefs.edit().putString(KEY_LC_SESSION, raw).apply()
    }

    fun clearSession(context: Context) {
        context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LC_SESSION)
            .remove(KEY_BGM_USERNAME)
            .remove("user_reactions")
            .apply()
    }

    fun saveUsername(context: Context, username: String) {
        context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BGM_USERNAME, username)
            .apply()
    }

    fun loadUsername(context: Context): String? {
        return context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BGM_USERNAME, null)
    }

    fun saveDomain(context: Context, domain: String) {
        context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BGM_DOMAIN, domain)
            .apply()
    }

    fun loadDomain(context: Context): String {
        return context
            .getSharedPreferences(LoliDailyArtWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BGM_DOMAIN, DEFAULT_BGM_DOMAIN) ?: DEFAULT_BGM_DOMAIN
    }

    fun getUsername(session: Session): String? {
        return try {
            val parts = session.token.split('.')
            if (parts.size < 2) return null
            val payload = String(Base64.decode(parts[1], Base64.DEFAULT))
            val json = org.json.JSONObject(payload)
            json.optString("username", "").ifEmpty { null }
                ?: json.optString("sub", "").ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }
}
