package com.merso.gametranslate

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Translator {
    @Volatile var lastError: String = ""

    private val cache = mutableMapOf<String, String>()

    fun translateEnToAr(text: String): String {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return ""
        cache[clean]?.let { return it }
        val result = try {
            val r = google(clean)
            lastError = ""
            r
        } catch (e: Exception) {
            try {
                val r = myMemory(clean)
                lastError = ""
                r
            } catch (e2: Exception) {
                lastError = e2.message ?: e2.javaClass.simpleName
                ""
            }
        }
        if (result.isNotBlank()) {
            if (cache.size > 200) cache.clear()
            cache[clean] = result
        }
        return result
    }

    private fun get(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream.bufferedReader().readText()
        conn.disconnect()
        if (code !in 200..299) throw RuntimeException("HTTP $code")
        return body
    }

    private fun myMemory(text: String): String {
        val q = URLEncoder.encode(text, "UTF-8")
        val body = get("https://api.mymemory.translated.net/get?q=$q&langpair=en%7Car")
        val j = JSONObject(body)
        val t = j.optJSONObject("responseData")?.optString("translatedText") ?: ""
        if (t.isBlank() || t.contains("INVALID") || t.contains("QUERY LENGTH")) throw RuntimeException("bad")
        return t
    }

    private fun google(text: String): String {
        val q = URLEncoder.encode(text, "UTF-8")
        val body = get("https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=ar&dt=t&q=$q")
        val arr = JSONArray(body)
        val parts = arr.optJSONArray(0) ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val seg = parts.optJSONArray(i) ?: continue
            sb.append(seg.optString(0))
        }
        val out = sb.toString()
        if (out.isBlank()) throw RuntimeException("empty")
        return out
    }
}
