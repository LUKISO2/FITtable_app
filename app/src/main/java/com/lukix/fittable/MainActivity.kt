package com.lukix.fittable

import android.content.Intent
import android.os.Bundle
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.edit
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    companion object {
        private const val URL_A = "https://timetable.fit.cvut.cz/new/"
        private const val URL_B = "https://modtable.lukiso.me/"
        private const val PREFS = "fit_table_prefs"
        private const val KEY_LAST_URL = "last_url"
    }

    private var webViewRef: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Do NOT draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val startUrlFromPrefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_LAST_URL, URL_A) ?: URL_A

        setContent {
            var webView by remember { mutableStateOf<WebView?>(null) }
            var currentUrl by remember { mutableStateOf(startUrlFromPrefs) }

            // Android back button -> WebView history
            BackHandler(enabled = webView?.canGoBack() == true) {
                webView?.goBack()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Use both insets explicitly (works on all Compose versions)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.setSupportZoom(false)
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.setSupportMultipleWindows(true)

                            // Cookies + 3rd party for SSO
                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)

                            // Keep http(s) inside; others (tel:, mailto:, intent:) -> system
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url ?: return false
                                    val scheme = url.scheme ?: return false
                                    if (scheme == "http" || scheme == "https") return false
                                    return try {
                                        startActivity(Intent(Intent.ACTION_VIEW, url))
                                        true
                                    } catch (_: Exception) {
                                        true
                                    }
                                }
                            }

                            // Handle target=_blank/window.open in same WebView
                            webChromeClient = object : WebChromeClient() {
                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message?
                                ): Boolean {
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                                        ?: return false
                                    transport.webView = this@apply
                                    resultMsg.sendToTarget()
                                    return true
                                }
                            }

                            if (savedInstanceState != null) {
                                restoreState(savedInstanceState)
                            } else {
                                loadUrl(currentUrl)
                            }

                            webViewRef = this
                            webView = this
                        }
                    }
                )

                // Top-left round semi-transparent toggle button (Unicode icon)
                IconButton(
                    onClick = {
                        currentUrl = if (currentUrl == URL_A) URL_B else URL_A
                        webViewRef?.loadUrl(currentUrl)
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit {
                            putString(KEY_LAST_URL, currentUrl)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .alpha(0.9f)
                ) {
                    Text(
                        text = "⇄",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webViewRef?.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }
}
