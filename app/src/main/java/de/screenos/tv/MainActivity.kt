package de.screenos.tv

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private lateinit var screen: WebView

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        immersive()
        screen = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            isFocusable = true
            isFocusableInTouchMode = true
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(screen)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    @Deprecated("Deprecated by Android")
    override fun onBackPressed() {
        if (::screen.isInitialized && screen.canGoBack()) screen.goBack() else super.onBackPressed()
    }
}
