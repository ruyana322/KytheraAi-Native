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
    val profiles = mutableStateListOf<Profile>()
    var activeId by mutableStateOf(prefs.getString("active", "gemini") ?: "gemini")
    var system by mutableStateOf(prefs.getString("system", "") ?: "")
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    val active: Profile get() = profiles.firstOrNull { it.id == activeId } ?: profiles.first()

    private val base = "Kamu adalah Kythera AI, asisten yang cerdas, ramah, dan akurat. Jawab dengan bahasa yang sama dengan pengguna (default Bahasa Indonesia) dan gunakan Markdown yang rapi bila membantu."

    private fun defaults() = listOf(
        Profile("gemini", "Gemini", "gemini", "", "gemini-3.8-flash", prefs.getString("key", "") ?: ""),
        Profile("claude", "Claude", "anthropic", "https://api.anthropic.com", "claude-haiku-5-5", ""),
        Profile("qwen", "Qwen", "openai", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", "qwen-plus", ""),
        Profile("openrouter", "OpenRouter", "openai", "https://openrouter.ai/api/v1", "openrouter/auto", ""),
        Profile("custom", "Custom", "openai", "https://api.openai.com/v1", "", "")
    )

    init {
        try {
            val arr = JSONArray(prefs.getString("chat", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i); messages.add(Msg(o.getBoolean("u"), o.getString("t")))
            }
        } catch (_: Exception) {}
        try {
            val raw = prefs.getString("profiles", null)
            if (raw != null) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    profiles.add(Profile(o.getString("id"), o.getString("name"), o.getString("type"),
                        o.optString("baseUrl"), o.optString("model"), o.optString("key")))
                }
            }
        } catch (_: Exception) {}
        if (profiles.isEmpty()) profiles.addAll(defaults())
    }

    private fun persistChat() {
        val arr = JSONArray()
        messages.forEach { arr.put(JSONObject().put("u", it.user).put("t", it.text)) }
        prefs.edit().putString("chat", arr.toString()).apply()
    }

    private fun persistSettings() {
        val arr = JSONArray()
        profiles.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("type", it.type)
                .put("baseUrl", it.baseUrl).put("model", it.model).put("key", it.key))
        }
        prefs.edit().putString("profiles", arr.toString()).putString("active", activeId).putString("system", system).apply()
    }

    fun saveProfiles(l: List<Profile>, sel: String, sys: String) {
        profiles.clear(); profiles.addAll(l); activeId = sel; system = sys
        persistSettings(); error = null
    }

    fun select(id: String) { activeId = id; persistSettings(); error = null }

    fun newChat() { stop(); messages.clear(); error = null; persistChat() }

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
        val p = active
        if (p.key.isBlank()) { error = "Isi API key ${p.name} di Pengaturan dulu."; return }
        if (p.model.isBlank()) { error = "Isi nama model ${p.name} di Pengaturan dulu."; return }
        error = null; stopped = false
        messages.add(Msg(true, text)); messages.add(Msg(false, "")); busy = true
        val hist = messages.dropLast(1).toList()
        val s = base + (if (system.isNotBlank()) "\n\n$system" else "")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Llm.stream(p, s, hist, { conn = it }) { t -> main.post { append(t) } }
            } catch (e: Exception) {
                if (!stopped) main.post { error = friendly(e) }
            }
            main.post {
                busy = false
                val last = messages.lastOrNull()
                if (last != null && !last.user && last.text.isEmpty()) messages.removeAt(messages.lastIndex)
                persistChat()
            }
        }
    }

    private fun append(t: String) {
        val i = messages.lastIndex
        if (i >= 0) messages[i] = messages[i].copy(text = messages[i].text + t)
    }

    private fun friendly(e: Exception): String {
        if (e is ApiError) {
            val d = e.message.orEmpty()
            return when {
                e.code == 0 -> "Balasan kosong. Coba ganti model di Pengaturan."
                e.code == 404 -> "Model atau alamat tidak ditemukan. Periksa nama model dan Base URL. ($d)"
                e.code == 429 -> "Batas kuota tercapai. Tunggu sebentar lalu coba lagi."
                e.code in listOf(401, 403) || d.contains("API key", true) -> "API key bermasalah: $d"
                else -> "Ditolak (${e.code}): $d"
            }
        }
        return if (e is java.io.IOException) "Koneksi terputus atau terlalu lama. Periksa jaringan lalu coba lagi."
        else "Terjadi kendala: ${e.message}"
    }
}
