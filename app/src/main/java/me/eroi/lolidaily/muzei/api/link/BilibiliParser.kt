package me.eroi.lolidaily.muzei.api.link

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.model.ArtistResolveResponse
import okhttp3.Request

private const val TAG = "BilibiliParser"

object BilibiliParser : SourceLinkParser {
    override val type = "bilibili"

    private val URL_RE = Regex("^https?://(?:www\\.)?bilibili\\.com/opus/(\\d+)")

    override fun match(url: String): SourceMatch? {
        val m = URL_RE.find(url) ?: return null
        return SourceMatch(type, m.groupValues[1])
    }

    override suspend fun fetchSourceImage(context: Context, url: String): Pair<ByteArray, String>? {
        val m = URL_RE.find(url) ?: return null
        val opusId = m.groupValues[1]
        val imageUrl = resolveImageViaWebView(context, opusId) ?: return null
        return LoliApiClient.downloadImage(imageUrl)
    }

    override fun resolveArtist(context: Context, resourceId: String): ArtistResolveResponse? {
        return LoliApiClient.resolveArtist(context, type, resourceId)
    }

    /**
     * Resolves bilibili opus image URL using a WebView to bypass captcha.
     * Loads the page with real WebView UA, extracts image from __INITIAL_STATE__.
     */
    private suspend fun resolveImageViaWebView(context: Context, opusId: String): String? {
        val url = "https://www.bilibili.com/opus/$opusId"
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val handler = Handler(Looper.getMainLooper())
                handler.post {
                    if (!cont.isActive) return@post
                    val webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.blockNetworkImage = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, pageUrl: String?) {
                                if (!cont.isActive) return
                                view.evaluateJavascript(
                                    "(function(){var s=window.__INITIAL_STATE__;" +
                                        "if(!s)return null;" +
                                        "var j=JSON.stringify(s);" +
                                        "var m=j.match(/https?:\\/\\/i\\d+\\.hdslb\\.com\\/bfs\\/new_dyn\\/[^\"'\\\\\\s@]+/);" +
                                        "return m?m[0].replace('http://','https://'):null})()"
                                ) { value ->
                                    if (!cont.isActive) return@evaluateJavascript
                                    val cleaned = value?.removeSurrounding("\"")?.removeSurrounding("null")
                                    cont.resume(
                                        if (cleaned.isNullOrEmpty() || cleaned == "null") null else cleaned,
                                        onCancellation = { _, _, _ -> },
                                    )
                                }
                            }
                        }
                    }
                    cont.invokeOnCancellation { webView.destroy() }
                    webView.loadUrl(url)
                }
            }
        }
    }
}
