package de.screenos.tv

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.NetworkInterface

class MainActivity : Activity() {
    private lateinit var screen: WebView
    private var server: ScreenServer? = null
    private val usbRequest = 4812

    inner class TvBridge {
        @JavascriptInterface fun remoteAddress(): String = "http://" + localIp() + ":8765"
        @JavascriptInterface fun chooseUsb() { runOnUiThread { val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); startActivityForResult(i, usbRequest) } }
    }

    private fun localIp(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }?.hostAddress ?: "TV-IP"
        } catch (_: Exception) { "TV-IP" }
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        immersive()
        screen = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowContentAccess = true
            addJavascriptInterface(TvBridge(), "ScreenOS")
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            isFocusable = true
            isFocusableInTouchMode = true
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(screen)
        server = ScreenServer(this) { action -> runOnUiThread { handleAction(action) } }
        try { server?.start(NanoHttpdTimeout.SOCKET_READ, false) } catch (_: Exception) { }
    }

    private fun handleAction(action: String) {
        when (action) {
            "open_photos" -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(intent, usbRequest)
            }
            else -> {
                val key = when (action) {
                    "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                    "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    "playpause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    "back" -> KeyEvent.KEYCODE_BACK
                    "home" -> KeyEvent.KEYCODE_HOME
                    else -> return
                }
                dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
                dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
            }
        }
    }

    @Deprecated("Deprecated by Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == usbRequest && resultCode == RESULT_OK) {
            data?.data?.let { uri: Uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                getSharedPreferences("screenos", MODE_PRIVATE).edit().putString("usb_tree", uri.toString()).apply()
                screen.evaluateJavascript("document.getElementById('usbStatus').textContent='USB-Ordner ausgewählt.';", null)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) immersive() }
    override fun onDestroy() { server?.stop(); server = null; super.onDestroy() }
    @Deprecated("Deprecated by Android")
    override fun onBackPressed() { if (::screen.isInitialized && screen.canGoBack()) screen.goBack() else super.onBackPressed() }

    private object NanoHttpdTimeout { const val SOCKET_READ = 5000 }
}
