package me.eroi.lolidaily.muzei

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import me.eroi.lolidaily.muzei.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
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
import me.eroi.lolidaily.muzei.api.LoliApiClient
import me.eroi.lolidaily.muzei.api.Session
import me.eroi.lolidaily.muzei.api.WebCookieStore
import me.eroi.lolidaily.muzei.ui.theme.LoliDailyTheme

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val existing = LoliDailyArtWorker.loadSession(this)
        if (existing != null) {
            Toast.makeText(this, getString(R.string.msg_already_logged_in), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            LoliDailyTheme {
                LoginScreen(
                    onSessionReceived = { session ->
                        LoliDailyArtWorker.saveSession(this, session)
                        Toast.makeText(this, getString(R.string.msg_logged_in), Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        private val BANGUMI_COOKIE_DOMAINS = listOf("chii.in", "bgm.tv", "bangumi.tv")

        fun oauthUrl(context: android.content.Context) = "${LoliApiClient.getApiBaseUrl(context)}/api/v1/oauth/request"

        fun clearBgmCookies() {
            WebCookieStore.clearDomains(BANGUMI_COOKIE_DOMAINS)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginScreen(
    onSessionReceived: (Session) -> Unit,
    onClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val msgTitleLogin = stringResource(R.string.title_login_bangumi)
    val msgConnecting = stringResource(R.string.msg_connecting_bangumi)
    val msgLoading = stringResource(R.string.msg_loading)
    val msgAuthenticating = stringResource(R.string.msg_authenticating)
    val msgWaitingAuth = stringResource(R.string.msg_waiting_auth)
    val msgProcessing = stringResource(R.string.msg_processing)
    val msgRedirecting = stringResource(R.string.msg_redirecting)
    val msgLoginFailed = stringResource(R.string.msg_login_failed)
    val descBack = stringResource(R.string.content_desc_back)
    val descRefresh = stringResource(R.string.content_desc_refresh)
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
                    userAgentString = LoliApiClient.MOBILE_UA
                }

                webViewClient =
                    object : WebViewClient() {
                        // Once the domain page loads, navigate to the LC OAuth URL.
                        // The browser will automatically set the correct Referer header.
                        private var oauthTriggered = false

                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: Bitmap?,
                        ) {
                            if (authDone) return
                            isLoading = true
                            val domain = LoliDailyArtWorker.loadDomain(context)
                            if (url != null && !oauthTriggered && url.contains(domain)) {
                                oauthTriggered = true
                                Log.d("LoginActivity", "Domain loaded — navigating to OAuth")
                                view?.loadUrl(LoginActivity.oauthUrl(context))
                                return
                            }
                            statusMessage =
                                when {
                                    url == null -> msgLoading
                                    url.contains("bgm.tv/") ||
                                        url.contains("bangumi.tv/") ||
                                        url.contains("chii.in/") -> msgAuthenticating
                                    url.contains("oauth") -> msgWaitingAuth
                                    url.contains("loliconey") || url.contains("lc-coney") ->
                                        msgProcessing
                                    else -> msgRedirecting
                                }
                            Log.d("LoginActivity", "onPageStarted: $url")
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            if (authDone) return true
                            val url = request?.url?.toString() ?: return false
                            Log.d("LoginActivity", "shouldOverrideUrlLoading: $url")

                            if (url.contains("bgm-lcjs-session")) {
                                authDone = true
                                isLoading = false
                                val token = request.url.getQueryParameter("bgm-lcjs-session")
                                val expiresAt =
                                    request.url.getQueryParameter("expiresAt")?.toLongOrNull()

                                // Extract bgm.tv username from the redirect path: /user/{username}
                                val pathSegments = request.url.path?.split("/") ?: emptyList()
                                val userIndex = pathSegments.indexOfLast { it == "user" }
                                val username =
                                    if (userIndex >= 0 && userIndex + 1 < pathSegments.size) {
                                        pathSegments[userIndex + 1]
                                    } else {
                                        null
                                    }
                                Log.d(
                                    "LoginActivity",
                                    "Extracted username: $username from path: ${request.url.path}",
                                )

                                if (
                                    token != null &&
                                    expiresAt != null &&
                                    expiresAt > System.currentTimeMillis()
                                ) {
                                    username?.let { LoliDailyArtWorker.saveUsername(context, it) }
                                    onSessionReceived(Session(token, expiresAt))
                                } else {
                                    Toast.makeText(context, msgLoginFailed, Toast.LENGTH_LONG).show()
                                    onClose()
                                }
                                return true
                            }

                            // bgm.tv/chii.in/bangumi.tv drops redirect_uri when encoding
                            // chii_referer for the login flow. After login, the redirect
                            // back to /oauth/authorize lacks redirect_uri, causing
                            // "invalid_uri". Re-start the OAuth flow from LC instead.
                            if (url.contains("oauth/authorize") && !url.contains("redirect_uri")) {
                                Log.d("LoginActivity", "Detected truncated OAuth — reloading from LC")
                                view?.loadUrl(LoginActivity.oauthUrl(context))
                                return true
                            }

                            return false
                        }

                        override fun onPageFinished(
                            view: WebView?,
                            url: String?,
                        ) {
                            isLoading = false
                            view?.title?.let { if (it.isNotBlank()) pageTitle = it }
                        }
                    }

                val domain = LoliDailyArtWorker.loadDomain(context)
                Log.d("LoginActivity", "OAuth domain: $domain")

                // Navigate to the target Bangumi domain first so the browser
                // sets its own Referer naturally on the subsequent redirect to LC.
                // This avoids relying on the deprecated loadUrl(url, headers).
                loadUrl("https://$domain/")
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = descBack)
                    }
                },
                actions = {
                    IconButton(onClick = { webView.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = descRefresh)
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
