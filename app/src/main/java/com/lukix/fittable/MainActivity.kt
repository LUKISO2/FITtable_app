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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS = "fit_table_prefs"
        private const val KEY_LAST_URL = "last_url"

        // Sites
        private const val URL_FITTABLE = "https://timetable.fit.cvut.cz/new/"
        private const val URL_MODTABLE = "https://modtable.lukiso.me/"
        private const val URL_MENZA_T = "https://agata.suz.cvut.cz/jidelnicky/?clPodsystem=3&lang=cs"
        private const val URL_MENZA_SD = "https://agata.suz.cvut.cz/jidelnicky/?clPodsystem=2&lang=cs"
        private const val URL_STRAVNIK = "https://agata.suz.cvut.cz/secure/index.php"
    }

    private data class Site(val label: String, val url: String)

    private val sites = listOf(
        Site("FitTable", URL_FITTABLE),
        Site("ModTable", URL_MODTABLE),
        Site("Menza T", URL_MENZA_T),
        Site("Menza SD", URL_MENZA_SD),
        Site("Strávník", URL_STRAVNIK),
    )

    private var webViewRef: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Do NOT draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val startUrlFromPrefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_LAST_URL, URL_FITTABLE) ?: URL_FITTABLE

        setContent {
            var webView by remember { mutableStateOf<WebView?>(null) }
            var currentUrl by remember { mutableStateOf(startUrlFromPrefs) }
            var menuExpanded by remember { mutableStateOf(false) }

            // Back button -> WebView history
            BackHandler(enabled = webView?.canGoBack() == true) {
                webView?.goBack()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
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

                            // Optional tweaks if a site misbehaves in WebView:
                            // settings.useWideViewPort = true
                            // settings.loadWithOverviewMode = true
                            // settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

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

                // ── Top-left button + dropdown menu ────────────────────────────────
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.40f), CircleShape)
                            .alpha(0.9f)
                    ) {
                        Text(
                            text = "⇄",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        offset = DpOffset(0.dp, 6.dp) // show just below the button
                    ) {
                        sites.forEach { site ->
                            DropdownMenuItem(
                                text = {
                                    val isCurrent = currentUrl == site.url
                                    Text(
                                        text = if (isCurrent) site.label else site.label,
                                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    if (currentUrl != site.url) {
                                        currentUrl = site.url
                                        webViewRef?.loadUrl(site.url)
                                        // remember selection for next launch
                                        getSharedPreferences(PREFS, MODE_PRIVATE).edit {
                                            putString(KEY_LAST_URL, site.url)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                // ───────────────────────────────────────────────────────────────────
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
