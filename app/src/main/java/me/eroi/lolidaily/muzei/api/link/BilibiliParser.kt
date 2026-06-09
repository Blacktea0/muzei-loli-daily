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

private const val TAG = "BilibiliParser"
@android.annotation.SuppressLint("SetJavaScriptEnabled")
object BilibiliParser : SourceLinkParser {
    override val type = "bilibili"

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val URL_RE = Regex("^https?://(?:www\\.|m\\.)?bilibili\\.com/opus/(\\d+)")

    override fun match(url: String): SourceMatch? {
        val m = URL_RE.find(url) ?: return null
        return SourceMatch(type, m.groupValues[1])
    }

    override fun canonicalUrl(url: String): String {
        val canonical = url.replace("://m.bilibili.com/", "://www.bilibili.com/")
        return stripTrackingParams(canonical)
    }

    override suspend fun fetchSourceImage(context: Context, url: String): Pair<ByteArray, String>? {
        val variants = fetchSourceImageUrls(context, url)
        val first = variants?.firstOrNull() ?: return null
        return LoliApiClient.downloadImage(first.fullUrl)
    }

    override suspend fun fetchSourceImageUrls(context: Context, url: String): List<SourceImageVariant>? {
        val m = URL_RE.find(url) ?: return null
        val opusId = m.groupValues[1]
        val imageUrls = resolveImagesViaWebView(context, opusId) ?: return null
        return imageUrls.map { url ->
            // Bilibili supports on-the-fly resizing via URL suffix
            val thumbUrl = "$url@240w_240h.jpg"
            SourceImageVariant(thumbUrl, url)
        }
    }

    override suspend fun resolveArtist(context: Context, resourceId: String): ArtistResolveResponse? {
        val (name, mid) = resolveArtistViaWebView(context, resourceId) ?: return null
        return ArtistResolveResponse(
            name = name,
            link = if (mid != null) "https://space.bilibili.com/$mid" else null,
        )
    }

    /**
     * Resolves all bilibili opus image URLs using a WebView to bypass captcha.
     * Loads the page with real WebView UA, extracts images from __INITIAL_STATE__.
     */
    private suspend fun resolveImagesViaWebView(context: Context, opusId: String): List<String>? {
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
                        settings.userAgentString = DESKTOP_UA
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, pageUrl: String?) {
                                if (!cont.isActive) return
                                view.evaluateJavascript(
                                    "(function(){var s=window.__INITIAL_STATE__;" +
                                        "if(!s)return null;" +
                                        "var j=JSON.stringify(s);" +
                                        "var m=j.match(/https?:\\/\\/i\\d+\\.hdslb\\.com\\/bfs\\/new_dyn\\/[^\"'\\\\\\s@]+/g);" +
                                        "if(!m)return null;" +
                                        "return JSON.stringify(m.map(function(u){return u.replace('http://','https://')}))})()"
                                ) { value ->
                                    if (!cont.isActive) return@evaluateJavascript
                                    if (value.isNullOrEmpty() || value == "null") {
                                        cont.resume(emptyList(), onCancellation = { _, _, _ -> })
                                        return@evaluateJavascript
                                    }
                                    try {
                                        // evaluateJavascript wraps string results in JSON quotes
                                        val inner = org.json.JSONObject("{\"v\":$value}").optString("v", "")
                                        if (inner.isEmpty()) {
                                            cont.resume(emptyList(), onCancellation = { _, _, _ -> })
                                            return@evaluateJavascript
                                        }
                                        val arr = org.json.JSONArray(inner)
                                        val urls = (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotEmpty) }
                                        cont.resume(urls, onCancellation = { _, _, _ -> })
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to parse images from __INITIAL_STATE__", e)
                                        cont.resume(emptyList(), onCancellation = { _, _, _ -> })
                                    }
                                }
                            }
                        }
                    }
                    cont.invokeOnCancellation { webView.destroy() }
                    webView.loadUrl(url)
                }
            }
        }?.ifEmpty { null }
    }

    /**
     * Extracts author name and user ID from bilibili opus page via WebView.
     * Returns (authorName, mid) or null.
     */
    private suspend fun resolveArtistViaWebView(context: Context, opusId: String): Pair<String, Long?>? {
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
                        settings.userAgentString = DESKTOP_UA
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, pageUrl: String?) {
                                if (!cont.isActive) return
                                view.evaluateJavascript(
                                    "(function(){" +
                                        "var s=window.__INITIAL_STATE__;" +
                                        "if(!s)return null;" +
                                        "var j=JSON.stringify(s);" +
                                        "var m=j.match(/\"mid\":(\\d+).*?\"name\":\"([^\"]+)\"/);" +
                                        "if(!m)return null;" +
                                        "return JSON.stringify({n:m[2],m:m[1]})" +
                                    "})()"
                                ) { value ->
                                    if (!cont.isActive) return@evaluateJavascript
                                    if (value.isNullOrEmpty() || value == "null") {
                                        cont.resume(null, onCancellation = { _, _, _ -> })
                                        return@evaluateJavascript
                                    }
                                    try {
                                        // evaluateJavascript wraps string results in JSON quotes
                                        val inner = org.json.JSONObject("{\"v\":$value}").optString("v", "")
                                        if (inner.isEmpty()) {
                                            cont.resume(null, onCancellation = { _, _, _ -> })
                                            return@evaluateJavascript
                                        }
                                        val obj = org.json.JSONObject(inner)
                                        val name = obj.optString("n", "")
                                        val mid = if (obj.has("m") && !obj.isNull("m")) obj.optLong("m") else null
                                        cont.resume(
                                            if (name.isNotEmpty()) name to mid else null,
                                            onCancellation = { _, _, _ -> },
                                        )
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to parse artist from __INITIAL_STATE__", e)
                                        cont.resume(null, onCancellation = { _, _, _ -> })
                                    }
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
