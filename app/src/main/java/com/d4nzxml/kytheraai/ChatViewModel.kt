package com.d4nzxml.kytheraai

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("kythera", 0)
    private val main = Handler(Looper.getMainLooper())
    private var conn: HttpURLConnection? = null
    @Volatile private var stopped = false

    val messages = mutableStateListOf<Msg>()
    var apiKey by mutableStateOf(prefs.getString("key", "") ?: "")
    var model by mutableStateOf(prefs.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash")
    var system by mutableStateOf(prefs.getString("system", "") ?: "")
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    private val base = "Kamu adalah Kythera AI, asisten yang cerdas, ramah, dan akurat. Jawab dengan bahasa yang sama dengan pengguna (default Bahasa Indonesia) dan gunakan Markdown yang rapi bila membantu."

    init {
        try {
            val arr = JSONArray(prefs.getString("chat", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i); messages.add(Msg(o.getBoolean("u"), o.getString("t")))
            }
        } catch (_: Exception) {}
    }

    private fun persist() {
        val arr = JSONArray()
        messages.forEach { arr.put(JSONObject().put("u", it.user).put("t", it.text)) }
        prefs.edit().putString("chat", arr.toString()).apply()
    }

    fun saveSettings(k: String, m: String, s: String) {
        apiKey = k; model = m.ifBlank { "gemini-2.5-flash" }; system = s
        prefs.edit().putString("key", apiKey).putString("model", model).putString("system", system).apply()
        error = null
    }

    fun newChat() { stop(); messages.clear(); error = null; persist() }

    fun stop() { stopped = true; conn?.disconnect() }

    fun retry() {
        val i = messages.indexOfLast { it.user }
        if (i < 0) return
        val t = messages[i].text
        while (messages.size > i) messages.removeAt(messages.lastIndex)
        error = null
        send(t)
    }

    fun send(text: String) {
        if (busy || text.isBlank()) return
        if (apiKey.isBlank()) { error = "Isi API key di Pengaturan dulu."; return }
        error = null; stopped = false
        messages.add(Msg(true, text)); messages.add(Msg(false, "")); busy = true
        val hist = messages.dropLast(1).toList()
        val k = apiKey; val m = model; val s = base + (if (system.isNotBlank()) "\n\n$system" else "")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                GeminiClient.stream(k, m, s, hist, { conn = it }) { t -> main.post { append(t) } }
            } catch (e: Exception) {
                if (!stopped) main.post { error = friendly(e) }
            }
            main.post {
                busy = false
                val last = messages.lastOrNull()
                if (last != null && !last.user && last.text.isEmpty()) messages.removeAt(messages.lastIndex)
                persist()
            }
        }
    }

    private fun append(t: String) {
        val i = messages.lastIndex
        if (i >= 0) messages[i] = messages[i].copy(text = messages[i].text + t)
    }

    private fun friendly(e: Exception) = when {
        e is ApiError && e.code in listOf(400, 401, 403) -> "API key tidak valid atau tidak punya akses. Periksa di Pengaturan."
        e is ApiError && e.code == 404 -> "Model tidak ditemukan. Ganti nama model di Pengaturan."
        e is ApiError && e.code == 429 -> "Batas kuota tercapai. Tunggu sebentar lalu coba lagi."
        e is java.io.IOException -> "Koneksi terputus. Periksa jaringan lalu coba lagi."
        else -> "Terjadi kendala: ${e.message}"
    }
}
