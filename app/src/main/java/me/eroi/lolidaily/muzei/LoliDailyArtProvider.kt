package me.eroi.lolidaily.muzei

import android.app.PendingIntent
import android.content.Intent
import me.eroi.lolidaily.muzei.util.Log
import androidx.core.app.RemoteActionCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import com.google.android.apps.muzei.api.provider.Artwork
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider
import kotlinx.serialization.json.Json
import me.eroi.lolidaily.muzei.model.Card
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import me.eroi.lolidaily.muzei.worker.WorkScheduler

/**
 * Muzei Art Provider that sources daily artwork from the Loli Daily API.
 *
 * Adds command actions in Muzei's UI for:
 * - Force Refresh (bypass date cache)
 * - View Source (open sourceUrl in browser)
 * - View Artist (open artistUrl in browser)
 */
class LoliDailyArtProvider : MuzeiArtProvider() {
    override fun onLoadRequested(initial: Boolean) {
        val appContext =
            context
                ?: run {
                    Log.w(TAG, "Context is null, cannot load artwork")
                    return
                }

        Log.d(TAG, "onLoadRequested(initial=$initial) — enqueuing load")
        WorkScheduler.ensureDailyRefreshScheduled(appContext)
        WorkScheduler.enqueueLoad(appContext, forceRefresh = false, initial = initial)
    }

    /** Provides command actions visible when viewing the current wallpaper in Muzei. */
    override fun getCommandActions(artwork: Artwork): List<RemoteActionCompat> {
        val ctx = context ?: return emptyList()
        val actions = mutableListOf<RemoteActionCompat>()

        // Parse card data from metadata to extract URLs
        val card =
            try {
                artwork.metadata?.let { json.decodeFromString<Card>(it) }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse artwork metadata", e)
                null
            }

        // ── View Source ──────────────────────────────────────
        if (card?.sourceUrl?.isNotBlank() == true) {
            val sourceIntent =
                Intent.createChooser(
                    Intent(Intent.ACTION_VIEW, card.sourceUrl.toUri()).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    null,
                )
            val sourcePending =
                PendingIntent.getActivity(
                    ctx,
                    (artwork.token.hashCode() + 1),
                    sourceIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            actions.add(
                RemoteActionCompat(
                    IconCompat.createWithResource(ctx, R.drawable.ic_view_source),
                    ctx.getString(R.string.cmd_view_source),
                    card.sourceUrl,
                    sourcePending,
                ),
            )
        }

        // ── View Artist ──────────────────────────────────────
        if (card?.artistUrl?.isNotBlank() == true) {
            val artistIntent =
                Intent.createChooser(
                    Intent(Intent.ACTION_VIEW, card.artistUrl.toUri()).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    null,
                )
            val artistPending =
                PendingIntent.getActivity(
                    ctx,
                    (artwork.token.hashCode() + 2),
                    artistIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            actions.add(
                RemoteActionCompat(
                    IconCompat.createWithResource(ctx, R.drawable.ic_person),
                    ctx.getString(R.string.cmd_view_artist),
                    card.artistUrl,
                    artistPending,
                ),
            )
        }

        // ── Force Refresh ────────────────────────────────────
        val refreshIntent =
            Intent(ctx, RefreshReceiver::class.java).apply {
                action = RefreshReceiver.ACTION_FORCE_REFRESH
            }
        val refreshPending =
            PendingIntent.getBroadcast(
                ctx,
                REQUEST_CODE_REFRESH,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        actions.add(
            RemoteActionCompat(
                IconCompat.createWithResource(ctx, R.drawable.ic_refresh),
                ctx.getString(R.string.cmd_force_refresh),
                ctx.getString(R.string.cmd_force_refresh_desc),
                refreshPending,
            ),
        )

        return actions
    }

    @Throws(IOException::class)
    override fun openFile(artwork: Artwork): InputStream {
        val token = artwork.token ?: throw FileNotFoundException("Artwork has no token")

        val appContext = context ?: throw IOException("Provider context is null")

        val dir = File(appContext.filesDir, ARTWORKS_DIR)

        val file =
            dir.listFiles { f -> f.name.startsWith("$token.") && f.length() > 0 }?.firstOrNull()

        if (file != null) {
            Log.d(TAG, "openFile: serving ${file.name} (${file.length()} bytes)")
            return FileInputStream(file)
        }

        throw FileNotFoundException("No cached file for token $token in ${dir.absolutePath}")
    }

    companion object {
        private const val TAG = "LoliDailyProvider"
        private const val ARTWORKS_DIR = "artworks"
        private const val REQUEST_CODE_REFRESH = 1001
        private val json = Json { ignoreUnknownKeys = true }
    }
}
