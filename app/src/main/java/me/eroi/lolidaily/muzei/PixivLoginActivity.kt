package me.eroi.lolidaily.muzei

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.eroi.lolidaily.muzei.api.SessionManager
import me.eroi.lolidaily.muzei.ui.theme.LoliDailyTheme
import org.json.JSONObject

private const val TAG = "PixivLoginActivity"
private const val PIXIV_LOGIN_URL = "https://accounts.pixiv.net/login"
private const val PIXIV_COOKIE_DOMAIN = ".pixiv.net"
private const val PHPSESSID = "PHPSESSID"

class PixivLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LoliDailyTheme {
                PixivLoginScreen(
                    onSessionReceived = { sessionId ->
                        SessionManager.savePixivSessionId(this, sessionId)
                        Toast.makeText(this, getString(R.string.msg_pixiv_logged_in), Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        fun clearPixivCookies() {
            val cm = CookieManager.getInstance()
            val cookie = cm.getCookie(PIXIV_COOKIE_DOMAIN)
            if (cookie != null) {
                cm.removeAllCookies(null)
                cm.flush()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PixivLoginScreen(
    onSessionReceived: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val msgTitleLogin = stringResource(R.string.title_login_pixiv)
    val msgConnecting = stringResource(R.string.msg_connecting_pixiv)
    val msgLoading = stringResource(R.string.msg_loading)
    var isLoading by remember { mutableStateOf(true) }
    var pageTitle by remember { mutableStateOf(msgTitleLogin) }
    var statusMessage by remember { mutableStateOf(msgConnecting) }
    var authDone by remember { mutableStateOf(false) }

    val webView =
        remember {
            WebView(context).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }

                webViewClient =
                    object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: Bitmap?,
                        ) {
                            if (authDone) return
                            isLoading = true
                            Log.d(TAG, "onPageStarted: $url")
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            if (authDone) return true
                            return false
                        }

                        override fun onPageFinished(
                            view: WebView?,
                            url: String?,
                        ) {
                            if (authDone) return
                            isLoading = false
                            view?.title?.let { if (it.isNotBlank()) pageTitle = it }
                            val currentUrl = url.orEmpty()
                            Log.d(TAG, "onPageFinished: $currentUrl")
                            // Validate login by calling /ajax/user/self —
                            // PHPSESSID alone is not proof of login (Pixiv sets it for all visitors).
                            val js = """
                            (async function() {
                              try {
                                var r = await fetch('/ajax/user/self', {credentials:'include'});
                                var t = await r.text();
                                Android.onUserSelf(t, r.status);
                              } catch(e) { Android.onUserSelfError(e.toString()); }
                            })();
                            """.trimIndent()
                            view?.evaluateJavascript(js, null)
                        }
                    }

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onUserSelf(json: String, status: Int) {
                        Log.d(TAG, "/ajax/user/self status=$status, body=${json.take(200)}")
                        if (status == 401) {
                            Log.w(TAG, "Pixiv session expired (401)")
                            return
                        }
                        try {
                            val root = JSONObject(json)
                            val userData = root.optJSONObject("userData")
                            if (userData != null && !userData.has("error")) {
                                // User is actually logged in — capture PHPSESSID
                                val cookieManager = CookieManager.getInstance()
                                val cookies = cookieManager.getCookie("https://www.pixiv.net")
                                val phpSessId = cookies?.split(";")
                                    ?.map { it.trim() }
                                    ?.find { it.startsWith("$PHPSESSID=") }
                                    ?.substringAfter("=", "")
                                    ?.takeIf { it.isNotBlank() }
                                if (phpSessId != null) {
                                    authDone = true
                                    Log.d(TAG, "Pixiv login validated, PHPSESSID captured")
                                    onSessionReceived(phpSessId)
                                } else {
                                    Log.w(TAG, "userData present but PHPSESSID cookie missing")
                                }
                            } else {
                                Log.d(TAG, "userData is null — not logged in yet")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse /ajax/user/self response", e)
                        }
                    }

                    @JavascriptInterface
                    fun onUserSelfError(error: String) {
                        Log.w(TAG, "/ajax/user/self fetch error: $error")
                    }
                }, "Android")

                Log.d(TAG, "Loading Pixiv login page")
                loadUrl(PIXIV_LOGIN_URL)
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = pageTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (webView.canGoBack()) webView.goBack() else onClose() },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                },
                actions = {
                    IconButton(onClick = { webView.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.content_desc_refresh))
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

                if (isLoading && !authDone) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
