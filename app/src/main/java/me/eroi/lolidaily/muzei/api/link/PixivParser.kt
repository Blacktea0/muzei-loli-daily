package me.eroi.lolidaily.muzei.api.link

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.api.SessionManager
import me.eroi.lolidaily.muzei.model.ArtistResolveResponse

private const val TAG = "PixivParser"

object PixivParser : SourceLinkParser {
    override val type = "pixiv"
    private val URL_RE = Regex("^https?://(?:www\\.)?pixiv\\.net/(?:en/)?artworks/(\\d+)")

    override fun match(url: String): SourceMatch? {
        val m = URL_RE.find(url) ?: return null
        return SourceMatch(type, m.groupValues[1])
    }

    override fun canonicalUrl(url: String): String {
        val canonical = url.replace("://touch.pixiv.net/", "://www.pixiv.net/")
        return stripTrackingParams(canonical)
    }

    override suspend fun fetchSourceImage(context: Context, url: String): Pair<ByteArray, String>? {
        val m = URL_RE.find(url) ?: return null
        val illustId = m.groupValues[1]
        var sessionId = SessionManager.loadPixivSessionId(context)
        Log.d(TAG, "fetchSourceImage: illustId=$illustId, sessionId=${sessionId?.take(8)}")
        val imageUrl = resolveImageUrlViaWebView(context, illustId, sessionId) ?: return null
        Log.d(TAG, "Resolved image URL: ${imageUrl.take(100)}")
        // Re-read session in case validation cleared it
        sessionId = SessionManager.loadPixivSessionId(context)
        // Download using OkHttp with all cookies (works for pximg.net — no Cloudflare)
        val cookies = CookieManager.getInstance().getCookie(imageUrl)
            ?: sessionId?.let { "PHPSESSID=$it" }
        Log.d(TAG, "Downloading image with cookie=${cookies?.take(20)}")
        val result = LoliApiClient.downloadImage(imageUrl, referer = "https://www.pixiv.net/", cookie = cookies)
        Log.d(TAG, "Download result: ${if (result != null) "${result.first.size} bytes, ${result.second}" else "null"}")
        return result
    }

    override suspend fun resolveArtist(context: Context, resourceId: String): ArtistResolveResponse? {
        return LoliApiClient.resolveArtist(context, type, resourceId)
    }

    /**
     * Uses a hidden WebView to resolve the image URL.
     * Pixiv API is behind Cloudflare which blocks OkHttp (TLS fingerprint mismatch),
     * but lets WebView through since it shares the browser's TLS stack and cookies.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun resolveImageUrlViaWebView(
        context: Context,
        illustId: String,
        sessionId: String?,
    ): String? {
        val result = CompletableDeferred<String?>()
        var fetched = false
        var webViewRef: WebView? = null
        runOnUiThread(context) {
            // Seed the pixiv session cookie so the WebView can access R-18 content
            if (sessionId != null) {
                CookieManager.getInstance().setCookie(
                    "https://www.pixiv.net",
                    "PHPSESSID=$sessionId; domain=.pixiv.net; path=/",
                )
            }
            val webView = WebView(context.applicationContext).apply {
                webViewRef = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (fetched) return
                        fetched = true
                        // First validate session, then resolve image URL
                        val js = """
                        (async function() {
                          // Validate session — if expired (401), clear it on the native side
                          var validSession = true;
                          try {
                            var vr = await fetch('/ajax/user/self', {credentials:'include'});
                            if (vr.status === 401) {
                              validSession = false;
                              Android.onSessionExpired();
                            }
                          } catch(e) {}
                          // Resolve image URL via API
                          var done = false;
                          try {
                            var r = await fetch('/ajax/illust/$illustId/pages', {credentials:'include'});
                            var t = await r.text();
                            if (r.ok) {
                              var j = JSON.parse(t.replace(/<br\s*\/?>/g, ''));
                              if (!j.error) { done = true; Android.onJson(t); }
                            }
                          } catch(e) {}
                          if (done) return;
                          try {
                            var r2 = await fetch('/ajax/illust/$illustId', {credentials:'include'});
                            var t2 = await r2.text();
                            var j2 = JSON.parse(t2.replace(/<br\s*\/?>/g, ''));
                            if (!j2.error) { done = true; Android.onJson(t2); }
                          } catch(e) {}
                          if (done) return;
                          try {
                            var imgs = document.querySelectorAll('img[src*="pximg.net"][src*="img-master"]');
                            for (var i = 0; i < imgs.length; i++) {
                              var src = imgs[i].src;
                              if (src.indexOf('_square') === -1 && src.indexOf('_custom') === -1 && src.indexOf('user-profile') === -1) {
                                done = true;
                                Android.onImageUrl(src);
                                return;
                              }
                            }
                            Android.onError('no artwork image in DOM');
                          } catch(e2) { Android.onError(e2.toString()); }
                        })();
                        """.trimIndent()
                        view?.evaluateJavascript(js, null)
                    }
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onJson(json: String) {
                        Log.d(TAG, "WebView API fetch OK, len=${json.length}")
                        val parsed = parseImageUrl(json.replace("<br>", "").replace("<br/>", "").replace("<br />", ""), sessionId)
                        result.complete(parsed)
                    }
                    @JavascriptInterface
                    fun onImageUrl(url: String) {
                        Log.d(TAG, "WebView DOM fallback: ${url.take(100)}")
                        result.complete(url)
                    }
                    @JavascriptInterface
                    fun onError(error: String) {
                        Log.w(TAG, "WebView fetch error: $error")
                        result.complete(null)
                    }
                    @JavascriptInterface
                    fun onSessionExpired() {
                        Log.w(TAG, "Pixiv session expired (401) — clearing stored session")
                        SessionManager.clearPixivSession(context)
                    }
                }, "Android")
                loadUrl("https://www.pixiv.net/artworks/$illustId")
            }
        }
        // Destroy WebView on main thread (invokeOnCompletion runs on JavaBridge thread)
        result.invokeOnCompletion {
            val wv = webViewRef ?: return@invokeOnCompletion
            runOnUiThread(context) { wv.destroy() }
        }
        return withTimeout(result, 30_000)
    }

    private fun parseImageUrl(json: String, sessionId: String?): String? {
        // Prefer regular over original — original can exceed the 3 MB upload limit
        fun extractUrl(urls: Map<String, kotlinx.serialization.json.JsonElement>): String? {
            return urls["regular"]?.jsonPrimitive?.content
                ?: urls["original"]?.jsonPrimitive?.content
        }
        return try {
            val root = LoliApiClient.json.parseToJsonElement(json).jsonObject
            if (root["error"]?.jsonPrimitive?.content == "true") return null
            val body = root["body"] ?: return null
            // /pages endpoint: body is an array of page objects
            val pages = body.jsonArray
            val urls = pages.firstOrNull()?.jsonObject?.get("urls")?.jsonObject ?: return null
            extractUrl(urls)
        } catch (e: Exception) {
            // body might be an object (from /ajax/illust/{id})
            try {
                val root = LoliApiClient.json.parseToJsonElement(json).jsonObject
                val urls = root["body"]?.jsonObject?.get("urls")?.jsonObject ?: return null
                extractUrl(urls)
            } catch (e2: Exception) {
                Log.w(TAG, "Failed to parse image URL", e2)
                null
            }
        }
    }

    private fun runOnUiThread(context: Context, block: () -> Unit) {
        android.os.Handler(context.mainLooper).post(block)
    }

    private suspend fun <T> withTimeout(deferred: CompletableDeferred<T>, ms: Long = 15_000): T? {
        return try {
            kotlinx.coroutines.withTimeout(ms) { deferred.await() }
        } catch (e: Exception) {
            Log.w(TAG, "WebView API timeout", e)
            null
        }
    }
}