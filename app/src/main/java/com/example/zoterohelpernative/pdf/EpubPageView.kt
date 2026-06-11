package com.example.zoterohelpernative.pdf

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun EpubPageView(
    epubHtmlContent: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                // In Dark Mode, we can invert the webview colors via CSS injection
                
                // Stub HTML for now
                val htmlData = if (epubHtmlContent.isEmpty()) {
                    "<html><body style=\"background-color:#0f0f11; color:#ededed; font-family: sans-serif; padding: 20px;\"><h2>EPUB Reader</h2><p>Contenuto dell'EPUB qui.</p></body></html>"
                } else {
                    epubHtmlContent
                }
                
                loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            // Update logic here if content changes
        },
        modifier = modifier.fillMaxSize()
    )
}
