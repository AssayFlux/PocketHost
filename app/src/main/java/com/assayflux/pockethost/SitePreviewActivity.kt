package com.assayflux.pockethost

import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.assayflux.pockethost.ui.theme.PocketHostTheme

class SitePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val port = getSharedPreferences("pockethost_state", MODE_PRIVATE).getInt("port", 8080)
        val localUrl = "http://127.0.0.1:$port/"
        setContent {
            PocketHostTheme {
                Column(Modifier.fillMaxSize()) {
                    Text("Site preview", modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.allowFileAccessFromFileURLs = false
                                settings.allowUniversalAccessFromFileURLs = false
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        val target = request.url
                                        return target.host != "127.0.0.1" || target.port != port
                                    }
                                }
                                loadUrl(localUrl)
                            }
                        }
                    )
                }
            }
        }
    }
}
