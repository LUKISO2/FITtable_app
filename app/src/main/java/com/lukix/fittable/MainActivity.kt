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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    companion object {
        private const val START_URL = "https://timetable.fit.cvut.cz/new/"
    }

    // Keep a reference so we can save/restore state across rotations
    private var webViewRef: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make window respect system bars (no drawing behind status/nav bars)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            val context = LocalContext.current
            var webView by remember { mutableStateOf<WebView?>(null) }

            // Handle Android back button (go back in WebView history)
            BackHandler(enabled = webView?.canGoBack() == true) {
                webView?.goBack()
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        // --- Settings for modern sites and SSO ---
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportZoom(false)
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.setSupportMultipleWindows(true)

                        // Cookies persisted by default; enable 3rd-party for SSO hops
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)


                        // Keep http(s) inside; hand off other schemes (tel:, mailto:, intent:) to system
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url ?: return false
                                val scheme = url.scheme ?: return false
                                if (scheme == "http" || scheme == "https") return false
                                return try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, url))
                                    true
                                } catch (_: Exception) {
                                    true
                                }
                            }
                        }

                        // Handle window.open / target="_blank" during SSO by loading in same WebView
                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                transport.webView = this@apply   // route new window into this WebView
                                resultMsg.sendToTarget()
                                return true
                            }
                        }

                        if (savedInstanceState != null) {
                            restoreState(savedInstanceState)
                        } else {
                            loadUrl(START_URL)
                        }

                        webViewRef = this
                        webView = this
                    }
                },
                update = { /* no-op */ }
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Persist WebView history and form state across recreation
        webViewRef?.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }
}
