package de.screenos.tv

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.net.NetworkInterface

class MainActivity : Activity() {
    private lateinit var screen: WebView
    private var server: ScreenServer? = null
    private val usbRequest = 4812
    inner class TvBridge {
        @JavascriptInterface fun remoteAddress(): String = "http://" + localIp() + ":8765"
        @JavascriptInterface fun chooseUsb() { runOnUiThread { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), usbRequest) } }
        @JavascriptInterface fun mediaList(): String {
            val uri = getSharedPreferences("screenos", MODE_PRIVATE).getString("usb_tree", null) ?: return "[]"
            return try {
                val root = DocumentFile.fromTreeUri(this@MainActivity, Uri.parse(uri)) ?: return "[]"
                val items = JSONArray()
                root.listFiles().filter { it.isFile && (it.type?.startsWith("image/") == true || it.type?.startsWith("video/") == true) }
                    .take(300).forEach { file ->
                        items.put(JSONObject().put("name", file.name ?: "Medium").put("uri", file.uri.toString())
                            .put("type", if (file.type?.startsWith("video/") == true) "video" else "image"))
                    }
                items.toString()
            } catch (_: Exception) { "[]" }
        }
    }
    private fun localIp(): String = try {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }?.hostAddress ?: "TV-IP"
    } catch (_: Exception) { "TV-IP" }
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
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val uri = request?.url ?: return null
                    if (uri.scheme == "content") return try {
                        WebResourceResponse(contentResolver.getType(uri) ?: "application/octet-stream", null, contentResolver.openInputStream(uri))
                    } catch (_: Exception) { null }
                    return super.shouldInterceptRequest(view, request)
                }
            }
            webChromeClient = WebChromeClient()
            isFocusable = true; isFocusableInTouchMode = true
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(screen)
        server = ScreenServer(this) { action -> runOnUiThread { handleAction(action) } }
        try { server?.start(5000, false) } catch (_: Exception) { }
    }
    private fun sendKey(key: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        screen.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0))
        screen.dispatchKeyEvent(KeyEvent(android.os.SystemClock.uptimeMillis(), now, KeyEvent.ACTION_UP, key, 0))
    }
    @Suppress("DEPRECATION")
    private fun handleAction(action: String) {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        when (action) {
            "open_photos", "open_usb" -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), usbRequest)
            "dashboard" -> screen.evaluateJavascript("window.screenOSRemote && window.screenOSRemote('dashboard')", null)
            "menu" -> screen.evaluateJavascript("window.screenOSRemote && window.screenOSRemote('menu')", null)
            "slideshow_toggle" -> screen.evaluateJavascript("window.screenOSRemote && window.screenOSRemote('slideshow_toggle')", null)
            "slideshow_stop" -> screen.evaluateJavascript("window.screenOSRemote && window.screenOSRemote('slideshow_stop')", null)
            "slideshow_prev" -> screen.evaluateJavascript("window.screenOSRemote && window.screenOSRemote('slideshow_prev')", null)
            "slideshow_next" -> screen.evaluateJavascript("window.screenOSRemote && window.screenOSRemote('slideshow_next')", null)
            "home" -> startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "back" -> sendKey(KeyEvent.KEYCODE_ESCAPE)
            "settings" -> startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "volume_up" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "volume_down" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "mute" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE,
                AudioManager.FLAG_SHOW_UI)
            else -> {
                val key = when (action) {
                    "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                    "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    "playpause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    "rewind" -> KeyEvent.KEYCODE_MEDIA_REWIND
                    "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
                    "up" -> KeyEvent.KEYCODE_DPAD_UP
                    "down" -> KeyEvent.KEYCODE_DPAD_DOWN
                    "left" -> KeyEvent.KEYCODE_DPAD_LEFT
                    "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
                    "ok" -> KeyEvent.KEYCODE_DPAD_CENTER
                    "channel_up" -> KeyEvent.KEYCODE_CHANNEL_UP
                    "channel_down" -> KeyEvent.KEYCODE_CHANNEL_DOWN
                    "source" -> KeyEvent.KEYCODE_TV_INPUT
                    "guide" -> KeyEvent.KEYCODE_GUIDE
                    "info" -> KeyEvent.KEYCODE_INFO
                    else -> if (action.startsWith("digit_")) {
                        val digit = action.removePrefix("digit_").toIntOrNull()
                        if (digit == null) return else KeyEvent.KEYCODE_0 + digit
                    } else return
                }
                if (key == KeyEvent.KEYCODE_MEDIA_NEXT || key == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
                    key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || key == KeyEvent.KEYCODE_MEDIA_REWIND ||
                    key == KeyEvent.KEYCODE_MEDIA_STOP) {
                    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
                    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
                } else sendKey(key)
            }
        }
    }
    @Deprecated("Deprecated by Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == usbRequest && resultCode == RESULT_OK) data?.data?.let { uri ->
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            getSharedPreferences("screenos", MODE_PRIVATE).edit().putString("usb_tree", uri.toString()).apply()
            screen.evaluateJavascript("document.getElementById('usbStatus').textContent='USB-Ordner ausgewählt.';renderMedia();", null)
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) immersive() }
    override fun onDestroy() { server?.stop(); server = null; super.onDestroy() }
    @Deprecated("Deprecated by Android")
    override fun onBackPressed() { if (::screen.isInitialized) sendKey(KeyEvent.KEYCODE_ESCAPE) else super.onBackPressed() }
}
