package me.eroi.lolidaily.muzei.api.link

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.json.contentOrNull
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
        val canonical = url
            .replace("://touch.pixiv.net/", "://www.pixiv.net/")
            .replace(Regex("://www\\.pixiv\\.net/[a-z]{2}/"), "://www.pixiv.net/")
        return stripTrackingParams(canonical)
    }

    override suspend fun fetchSourceImage(context: Context, url: String): Pair<ByteArray, String>? {
        val variants = fetchSourceImageUrls(context, url)
        val first = variants?.firstOrNull() ?: return null
        return downloadPixivImage(context, first.fullUrl)
    }

    override suspend fun fetchSourceImageUrls(context: Context, url: String): List<SourceImageVariant>? {
        val m = URL_RE.find(url) ?: return null
        val illustId = m.groupValues[1]
        val sessionId = SessionManager.loadPixivSessionId(context)
        Log.d(TAG, "fetchSourceImageUrls: illustId=$illustId, sessionId=${sessionId?.take(8)}")
        val variants = resolveImageUrlsViaWebView(context, illustId, sessionId)
        Log.d(TAG, "Resolved ${variants?.size ?: 0} image variants")
        return variants
    }

    /**
     * Downloads a single pixiv image by its full-quality URL.
     */
    private fun downloadPixivImage(context: Context, imageUrl: String): Pair<ByteArray, String>? {
        val sessionId = SessionManager.loadPixivSessionId(context)
        val cookies = CookieManager.getInstance().getCookie(imageUrl)
            ?: sessionId?.let { "PHPSESSID=$it" }
        Log.d(TAG, "Downloading image with cookie=${cookies?.take(20)}")
        return LoliApiClient.downloadImage(imageUrl, referer = "https://www.pixiv.net/", cookie = cookies)
    }

    override suspend fun resolveArtist(context: Context, resourceId: String): ArtistResolveResponse? {
        return LoliApiClient.resolveArtist(context, type, resourceId)
    }

    /**
     * Uses a hidden WebView to resolve all image URLs for a pixiv illustration.
     * Pixiv API is behind Cloudflare which blocks OkHttp (TLS fingerprint mismatch),
     * but lets WebView through since it shares the browser's TLS stack and cookies.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun resolveImageUrlsViaWebView(
        context: Context,
        illustId: String,
        sessionId: String?,
    ): List<SourceImageVariant>? {
        val result = CompletableDeferred<List<SourceImageVariant>?>()
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
            WebView(context.applicationContext).apply {
                webViewRef = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (fetched) return
                        fetched = true
                        // First validate session, then resolve image URLs
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
                          // Resolve image URLs via API
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
                @Suppress("unused")
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onJson(json: String) {
                        Log.d(TAG, "WebView API fetch OK, len=${json.length}")
                        val parsed = parseImageUrls(json.replace("<br>", "").replace("<br/>", "").replace("<br />", ""))
                        result.complete(parsed)
                    }
                    @JavascriptInterface
                    fun onImageUrl(url: String) {
                        Log.d(TAG, "WebView DOM fallback: ${url.take(100)}")
                        result.complete(listOf(SourceImageVariant(url, url)))
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

    /**
     * Parses all image variants from the Pixiv pages API response.
     * Returns a list of [SourceImageVariant] (thumb + full URL pairs) for each page.
     */
    private fun parseImageUrls(json: String): List<SourceImageVariant>? {
        // Prefer original; the submit flow compresses it only if it exceeds the upload limit.
        fun extractVariant(urls: Map<String, kotlinx.serialization.json.JsonElement>): SourceImageVariant? {
            fun urlValue(key: String): String? =
                urls[key]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            val full = urlValue("original")
                ?: urlValue("regular")
                ?: return null
            val thumb = urlValue("small")
                ?: urlValue("thumb_mini")
                ?: full
            return SourceImageVariant(thumb, full)
        }
        return try {
            val root = LoliApiClient.json.parseToJsonElement(json).jsonObject
            if (root["error"]?.jsonPrimitive?.content == "true") return null
            val body = root["body"] ?: return null
            // /pages endpoint: body is an array of page objects
            val pages = body.jsonArray
            pages.mapNotNull { page ->
                page.jsonObject["urls"]?.jsonObject?.let { extractVariant(it) }
            }.ifEmpty { null }
        } catch (_: Exception) {
            // body might be an object (from /ajax/illust/{id})
            try {
                val root = LoliApiClient.json.parseToJsonElement(json).jsonObject
                val urls = root["body"]?.jsonObject?.get("urls")?.jsonObject ?: return null
                val variant = extractVariant(urls) ?: return null
                listOf(variant)
            } catch (e2: Exception) {
                Log.w(TAG, "Failed to parse image URLs", e2)
                null
            }
        }
    }

    private fun runOnUiThread(context: Context, block: () -> Unit) {
        android.os.Handler(context.mainLooper).post(block)
    }

    private suspend fun <T> withTimeout(deferred: CompletableDeferred<T>, ms: Long = 15_000): T? {
        return try {
            kotlinx.coroutines.withTimeout(ms.milliseconds) { deferred.await() }
        } catch (e: Exception) {
            Log.w(TAG, "WebView API timeout", e)
            null
        }
    }
}
