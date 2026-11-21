package com.d4nzxml.kytheraai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Msg(val user: Boolean, val text: String)
class ApiError(val code: Int, val body: String) : Exception("HTTP $code")

object GeminiClient {
    fun stream(key: String, model: String, system: String, history: List<Msg>,
               onConn: (HttpURLConnection) -> Unit, onToken: (String) -> Unit) {
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse")
            .openConnection() as HttpURLConnection
        onConn(c)
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 15000
        c.readTimeout = 90000
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("x-goog-api-key", key)
        val contents = JSONArray()
        history.forEach {
            contents.put(JSONObject()
                .put("role", if (it.user) "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", it.text))))
        }
        val body = JSONObject().put("contents", contents)
        if (system.isNotBlank()) body.put("systemInstruction",
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        if (c.responseCode !in 200..299) {
            throw ApiError(c.responseCode, c.errorStream?.bufferedReader()?.readText().orEmpty())
        }
        c.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { l ->
                if (l.startsWith("data:")) {
                    val parts = JSONObject(l.removePrefix("data:").trim())
                        .optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts")
                    if (parts != null) for (i in 0 until parts.length()) {
                        val p = parts.getJSONObject(i)
                        if (!p.optBoolean("thought")) onToken(p.optString("text"))
                    }
                }
            }
        }
    }
}
