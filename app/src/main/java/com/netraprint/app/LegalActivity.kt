package com.netraprint.app

import android.os.Bundle
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebResourceRequest
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import java.io.ByteArrayInputStream

class LegalActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TYPE = "extra_legal_type"
        const val TYPE_PRIVACY_POLICY = "privacy_policy"
        const val TYPE_TERMS_OF_SERVICE = "terms_of_service"
        const val TYPE_OPEN_SOURCE_LICENSES = "open_source_licenses"
        const val TYPE_API_DOCS = "api_documentation"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legal)

        val appBarLayout = findViewById<AppBarLayout>(R.id.appBarLayoutLegal)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, statusBarInsets.top, 0, 0)
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarLegal)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        val webView = findViewById<WebView>(R.id.webViewLegal)
        val type = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_PRIVACY_POLICY
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.allowFileAccessFromFileURLs = false
        webView.settings.allowUniversalAccessFromFileURLs = false
        webView.settings.javaScriptEnabled = false
        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse {
                return assetLoader.shouldInterceptRequest(request.url)
                    ?: WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return request.url.scheme != "https" ||
                    request.url.host != WebViewAssetLoader.DEFAULT_DOMAIN
            }
        }

        when (type) {
            TYPE_API_DOCS -> {
                toolbar.title = getString(R.string.kt_legal_api_docs_title)
                webView.loadUrl(assetUrl("api_documentation.html"))
            }
            TYPE_PRIVACY_POLICY -> {
                toolbar.title = getString(R.string.kt_legal_privacy_title)
                webView.loadUrl(assetUrl("privacy_policy.html"))
            }
            TYPE_TERMS_OF_SERVICE -> {
                toolbar.title = getString(R.string.kt_legal_terms_title)
                webView.loadUrl(assetUrl("terms_of_service.html"))
            }
            TYPE_OPEN_SOURCE_LICENSES -> {
                toolbar.title = getString(R.string.kt_legal_oss_title)
                loadOpenSourceLicenses(webView)
            }
            else -> {
                toolbar.title = getString(R.string.kt_legal_default_title)
                webView.loadUrl(assetUrl("privacy_policy.html"))
            }
        }
    }

    private fun loadOpenSourceLicenses(webView: WebView) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { font-family: sans-serif; line-height: 1.5; color: #263238; padding: 16px; background-color: #FAFAFA; }
                    h1 { color: #0F766E; font-size: 20px; border-bottom: 2px solid #0F766E; padding-bottom: 6px; }
                    h2 { color: #0D9488; font-size: 15px; margin-top: 18px; margin-bottom: 4px; }
                    pre { background-color: #F1F5F9; padding: 10px; border-radius: 6px; font-size: 12px; overflow-x: auto; color: #37474F; }
                    p { font-size: 13px; margin: 4px 0; }
                </style>
            </head>
            <body>
                <h1>Lisensi Sumber Terbuka (Open Source)</h1>
                <p>Aplikasi PrinterJTM menggunakan library pihak ketiga berikut yang dilisensikan secara legal:</p>

                <h2>1. Google ML Kit (Barcode Scanning & Text Recognition)</h2>
                <p>Hak Cipta: The Android Open Source Project / Google LLC</p>
                <pre>Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0</pre>

                <h2>2. AndroidX CameraX</h2>
                <p>Hak Cipta: The Android Open Source Project</p>
                <pre>Licensed under the Apache License, Version 2.0</pre>

                <h2>3. ZXing (Zebra Crossing)</h2>
                <p>Hak Cipta: 2007 ZXing authors</p>
                <pre>Licensed under the Apache License, Version 2.0</pre>

                <h2>4. Google Material Components for Android</h2>
                <p>Hak Cipta: The Android Open Source Project</p>
                <pre>Licensed under the Apache License, Version 2.0</pre>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(assetUrl(""), html, "text/html", "UTF-8", null)
    }

    private fun assetUrl(fileName: String): String {
        return "https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/$fileName"
    }

    override fun onDestroy() {
        findViewById<WebView>(R.id.webViewLegal)?.let { webView ->
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.clearCache(true)
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }
}
