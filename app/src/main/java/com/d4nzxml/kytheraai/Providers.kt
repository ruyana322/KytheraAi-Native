package com.d4nzxml.kytheraai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Profile(val id: String, val name: String, val type: String, val baseUrl: String, val model: String, val key: String)

object Llm {
    fun stream(p: Profile, system: String, hist: List<Msg>, onConn: (HttpURLConnection) -> Unit, onToken: (String) -> Unit) {
        when (p.type) {
            "anthropic" -> {
                val msgs = JSONArray()
                hist.forEach { msgs.put(JSONObject().put("role", if (it.user) "user" else "assistant").put("content", it.text)) }
                val body = JSONObject().put("model", p.model).put("max_tokens", 4096).put("stream", true)
                    .put("system", system).put("messages", msgs)
                val c = post(p.baseUrl.trimEnd('/') + "/v1/messages", mapOf(
                    "Content-Type" to "application/json", "x-api-key" to p.key, "anthropic-version" to "2023-06-01"), body, onConn)
                sse(c, { j -> if (j.optString("type") == "content_block_delta") j.optJSONObject("delta")?.optString("text") else null }, onToken)
            }
            "openai" -> {
                val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
                hist.forEach { msgs.put(JSONObject().put("role", if (it.user) "user" else "assistant").put("content", it.text)) }
                val body = JSONObject().put("model", p.model).put("stream", true).put("messages", msgs)
                val c = post(p.baseUrl.trimEnd('/') + "/chat/completions", mapOf(
                    "Content-Type" to "application/json", "Authorization" to "Bearer ${p.key}"), body, onConn)
                sse(c, { j ->
                    val d = j.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                    if (d != null && d.has("content") && !d.isNull("content")) d.getString("content") else null
                }, onToken)
            }
            else -> GeminiClient.stream(p.key, p.model, system, hist, onConn, onToken)
        }
    }

    private fun post(url: String, headers: Map<String, String>, body: JSONObject, onConn: (HttpURLConnection) -> Unit): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        onConn(c)
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 15000
        c.readTimeout = 60000
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        if (c.responseCode !in 200..299) throw ApiError(c.responseCode, c.errorStream?.bufferedReader()?.readText().orEmpty())
        return c
    }

    private fun sse(c: HttpURLConnection, pick: (JSONObject) -> String?, onToken: (String) -> Unit) {
        var got = false
        c.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { l ->
                if (l.startsWith("data:")) {
                    val d = l.removePrefix("data:").trim()
                    if (d.isNotEmpty() && d != "[DONE]") {
                        val t = try { pick(JSONObject(d)) } catch (_: Exception) { null }
                        if (!t.isNullOrEmpty()) { got = true; onToken(t) }
                    }
                }
            }
        }
        if (!got) throw ApiError(0, "")
    }
}
