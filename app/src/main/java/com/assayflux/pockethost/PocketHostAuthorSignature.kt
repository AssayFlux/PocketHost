package com.assayflux.pockethost

import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Displays the original, locally packaged AssayFlux author signature.
 * JavaScript and network access are disabled; the SVG is loaded only from app assets.
 */
@Composable
internal fun PocketHostAuthorSignature(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.apply {
                    javaScriptEnabled = false
                    domStorageEnabled = false
                    allowFileAccess = true
                    allowContentAccess = false
                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = false
                    blockNetworkLoads = true
                    cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                }
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                isFocusable = false
                isFocusableInTouchMode = false
                isClickable = false
                overScrollMode = View.OVER_SCROLL_NEVER
                webViewClient = WebViewClient()
                loadDataWithBaseURL(
                    "file:///android_asset/",
                    """
                    <!doctype html>
                    <html>
                      <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                        <style>
                          html, body { width:100%; height:100%; margin:0; padding:0; overflow:hidden; background:transparent; }
                          img { display:block; width:100%; height:100%; object-fit:contain; }
                        </style>
                      </head>
                      <body><img src="author-signature.svg" alt="Md Safarujjaman's signature"></body>
                    </html>
                    """.trimIndent(),
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
    )
}
