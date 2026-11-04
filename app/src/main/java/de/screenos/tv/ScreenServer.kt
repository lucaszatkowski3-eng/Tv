package de.screenos.tv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import java.util.UUID

private object TokenVault {
    private const val ALIAS = "ScreenOSSpotifyTokenKey"
    private fun key(): java.security.Key {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generator.generateKey()
        }
        return store.getKey(ALIAS, null)
    }
    fun seal(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    fun open(value: String?): String? {
        return try {
            if (value == null) null else {
                val data = Base64.decode(value, Base64.NO_WRAP)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
                String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8)
            }
        } catch (_: Exception) { null }
    }
}

class ScreenServer(private val context: Context, private val onAction: (String) -> Unit) : NanoHTTPD(8765) {
    private val prefs = context.getSharedPreferences("screenos", Context.MODE_PRIVATE)
    private var verifier: String? = null
    private var expectedState: String? = null
    private var clientId: String? = null

    override fun serve(session: IHTTPSession): Response {
        return try {
            if (session.method == Method.OPTIONS) {
                newFixedLengthResponse(Response.Status.OK, "text/plain", "").withCors()
            } else when (session.uri) {
                "/", "/remote" -> asset("remote.html")
                "/spotify-callback" -> asset("spotify-callback.html")
                "/api/status" -> json(JSONObject().put("ok", true).put("spotifyConnected", prefs.contains("spotify_access")))
                "/api/action" -> {
                    val action = body(session).optString("action")
                    if (action in listOf("next", "previous", "playpause", "back", "home", "open_photos")) onAction(action)
                    json(JSONObject().put("ok", true))
                }
                "/api/spotify/start" -> startSpotify(body(session).optString("clientId"))
                "/api/spotify/finish" -> finishSpotify(body(session))
                "/api/spotify/now" -> nowPlaying()
                "/api/spotify/disconnect" -> {
                    prefs.edit().remove("spotify_access").remove("spotify_refresh").remove("spotify_expiry").remove("spotify_client_id").apply()
                    json(JSONObject().put("ok", true))
                }
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found").withCors()
            }
        } catch (e: Exception) {
            json(JSONObject().put("ok", false).put("error", e.message ?: "Unbekannter Fehler"), Response.Status.INTERNAL_ERROR)
        }
    }

    private fun Response.withCors(): Response {
        addHeader("Access-Control-Allow-Origin", "*")
        addHeader("Access-Control-Allow-Headers", "Content-Type")
        addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        return this
    }
    private fun body(session: IHTTPSession): JSONObject {
        val map = HashMap<String, String>()
        session.parseBody(map)
        return JSONObject(map["postData"] ?: "{}")
    }
    private fun asset(name: String): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", context.assets.open(name).bufferedReader().use { it.readText() }).withCors()
    private fun json(value: JSONObject, status: Response.Status = Response.Status.OK): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", value.toString()).withCors()
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun startSpotify(id: String): Response {
        require(id.matches(Regex("[A-Za-z0-9]{20,40}"))) { "Bitte eine gültige Spotify Client-ID eingeben." }
        clientId = id
        prefs.edit().putString("spotify_client_id", id).apply()
        val bytes = ByteArray(64).also { SecureRandom().nextBytes(it) }
        verifier = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        expectedState = UUID.randomUUID().toString()
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier!!.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val redirect = "https://lucaszatkowski3-eng.github.io/Tv/spotify-callback.html"
        val params = listOf("client_id" to id, "response_type" to "code", "redirect_uri" to redirect,
            "code_challenge_method" to "S256", "code_challenge" to challenge, "state" to expectedState!!,
            "scope" to "user-read-currently-playing user-read-playback-state")
        val auth = "https://accounts.spotify.com/authorize?" + params.joinToString("&") { enc(it.first) + "=" + enc(it.second) }
        return json(JSONObject().put("ok", true).put("authUrl", auth).put("state", expectedState))
    }

    private fun finishSpotify(data: JSONObject): Response {
        val code = data.optString("code")
        require(code.isNotBlank() && data.optString("state") == expectedState) { "Code oder Sicherheitsprüfung ungültig. Bitte erneut anmelden." }
        val id = clientId ?: error("Client-ID fehlt. Bitte erneut starten.")
        val form = listOf("client_id" to id, "grant_type" to "authorization_code", "code" to code,
            "redirect_uri" to "https://lucaszatkowski3-eng.github.io/Tv/spotify-callback.html",
            "code_verifier" to (verifier ?: error("Anmeldung abgelaufen.")))
            .joinToString("&") { enc(it.first) + "=" + enc(it.second) }
        val conn = URL("https://accounts.spotify.com/api/token").openConnection() as HttpURLConnection
        conn.connectTimeout = 12000; conn.readTimeout = 12000
        conn.requestMethod = "POST"; conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.use { it.write(form.toByteArray()) }
        val response = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
        val token = JSONObject(response)
        require(conn.responseCode in 200..299) { token.optJSONObject("error")?.optString("message") ?: "Spotify-Anmeldung fehlgeschlagen." }
        prefs.edit().putString("spotify_access", TokenVault.seal(token.optString("access_token")))
            .putString("spotify_refresh", TokenVault.seal(token.optString("refresh_token")))
            .putLong("spotify_expiry", System.currentTimeMillis() + token.optLong("expires_in") * 1000L).apply()
        verifier = null; expectedState = null
        return json(JSONObject().put("ok", true).put("message", "Spotify ist verbunden."))
    }

    private fun accessToken(): String? {
        val expiry = prefs.getLong("spotify_expiry", 0L)
        val current = TokenVault.open(prefs.getString("spotify_access", null))
        if (current != null && System.currentTimeMillis() < expiry - 60000) return current
        val refresh = TokenVault.open(prefs.getString("spotify_refresh", null)) ?: return current
        val id = prefs.getString("spotify_client_id", null) ?: return current
        val form = listOf("client_id" to id, "grant_type" to "refresh_token", "refresh_token" to refresh)
            .joinToString("&") { enc(it.first) + "=" + enc(it.second) }
        val conn = URL("https://accounts.spotify.com/api/token").openConnection() as HttpURLConnection
        conn.connectTimeout = 12000; conn.readTimeout = 12000
        conn.requestMethod = "POST"; conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.use { it.write(form.toByteArray()) }
        if (conn.responseCode !in 200..299) return null
        val token = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val newAccess = token.optString("access_token")
        val newRefresh = token.optString("refresh_token").ifBlank { refresh }
        prefs.edit().putString("spotify_access", TokenVault.seal(newAccess))
            .putString("spotify_refresh", TokenVault.seal(newRefresh))
            .putLong("spotify_expiry", System.currentTimeMillis() + token.optLong("expires_in") * 1000L).apply()
        return newAccess
    }

    private fun nowPlaying(): Response {
        val token = accessToken() ?: return json(JSONObject().put("ok", false).put("connected", false).put("message", "Bitte Spotify erneut verbinden."))
        val conn = URL("https://api.spotify.com/v1/me/player/currently-playing").openConnection() as HttpURLConnection
        conn.connectTimeout = 10000; conn.readTimeout = 10000
        conn.setRequestProperty("Authorization", "Bearer $token")
        if (conn.responseCode == 204) return json(JSONObject().put("ok", true).put("connected", true).put("playing", false))
        val raw = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
        if (conn.responseCode !in 200..299) return json(JSONObject().put("ok", false).put("connected", true).put("message", "Spotify API HTTP " + conn.responseCode))
        val root = JSONObject(raw); val item = root.optJSONObject("item") ?: JSONObject()
        val artists = item.optJSONArray("artists")
        val names = (0 until (artists?.length() ?: 0)).map { artists!!.getJSONObject(it).optString("name") }.joinToString(", ")
        val images = item.optJSONObject("album")?.optJSONArray("images")
        val image = if (images != null && images.length() > 0) images.getJSONObject(0).optString("url") else ""
        return json(JSONObject().put("ok", true).put("connected", true).put("playing", root.optBoolean("is_playing"))
            .put("track", item.optString("name")).put("artist", names).put("image", image)
            .put("progress", root.optLong("progress_ms")).put("duration", item.optLong("duration_ms")))
    }
}
