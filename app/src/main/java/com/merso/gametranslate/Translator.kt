package com.merso.gametranslate

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Translator {
    @Volatile var lastError: String = ""

    // Insertion-ordered cache; evicts the oldest entry instead of wiping
    // everything, so repeated lines stay instant all session.
    private val cache = LinkedHashMap<String, String>()

    private fun cachePut(key: String, value: String) {
        synchronized(cache) {
            cache[key] = value
            while (cache.size > 2000) {
                val eldest = cache.keys.firstOrNull() ?: break
                cache.remove(eldest)
            }
        }
    }

    private fun cacheGet(key: String): String? = synchronized(cache) { cache[key] }

    // Fetch the Microsoft auth token ahead of the first subtitle so the
    // first translation does not pay for it.
    fun warmUp() {
        Thread {
            try { msAuthToken() } catch (_: Exception) {}
        }.start()
    }

    @Volatile private var msToken: String = ""
    @Volatile private var msTokenAt: Long = 0

    fun translateEnToAr(text: String): String {
        if (!text.contains("\n")) return translateLine(text)
        // Multi-line text: translate all missing lines in ONE request so the
        // lines keep their order without paying a network round trip per line.
        val lines = text.split("\n")
        val cleaned = lines.map { it.trim().replace(Regex("\\s+"), " ") }
        val missing = cleaned.filter { it.isNotEmpty() && cacheGet(it) == null }.distinct()
        if (missing.isNotEmpty()) {
            val translated = try {
                microsoftBatch(missing)
            } catch (_: Exception) {
                emptyMap()
            }
            for (line in missing) {
                val r = translated[line] ?: try {
                    myMemory(line)
                } catch (e: Exception) {
                    lastError = e.message ?: e.javaClass.simpleName
                    ""
                }
                if (r.isNotBlank()) cachePut(line, r)
            }
        }
        return cleaned.joinToString("\n") { if (it.isEmpty()) "" else cacheGet(it) ?: "" }
    }

    private fun translateLine(text: String): String {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return ""
        cacheGet(clean)?.let { return it }
        val result = try {
            val r = microsoftBatch(listOf(clean))[clean] ?: throw RuntimeException("empty")
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
        if (result.isNotBlank()) cachePut(clean, result)
        return result
    }

    private fun get(urlStr: String, auth: String? = null): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
        if (auth != null) conn.setRequestProperty("Authorization", "Bearer $auth")
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        // Read fully and close the stream (no disconnect) so the connection
        // returns to the pool and the next request reuses it.
        val body = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw RuntimeException("HTTP $code")
        return body
    }

    private fun post(urlStr: String, auth: String, json: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
        conn.setRequestProperty("Authorization", "Bearer $auth")
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw RuntimeException("HTTP $code")
        return body
    }

    private fun msAuthToken(force: Boolean = false): String {
        val now = System.currentTimeMillis()
        if (!force && msToken.isNotBlank() && now - msTokenAt < 8 * 60 * 1000) return msToken
        val token = get("https://edge.microsoft.com/translate/auth").trim()
        if (token.isBlank()) throw RuntimeException("no token")
        msToken = token
        msTokenAt = now
        return token
    }

    private fun microsoftBatch(texts: List<String>): Map<String, String> {
        val arr = JSONArray()
        for (t in texts) arr.put(JSONObject().put("Text", t))
        val body = arr.toString()
        val url = "https://api-edge.cognitive.microsofttranslator.com/translate?api-version=3.0&from=en&to=ar&textType=plain"
        val resp = try {
            post(url, msAuthToken(), body)
        } catch (e: Exception) {
            // Only an auth failure is worth a retry with a fresh token;
            // retrying timeouts would just double the wait.
            val msg = e.message ?: ""
            if (msg.contains("401") || msg.contains("403")) {
                post(url, msAuthToken(force = true), body)
            } else throw e
        }
        val out = JSONArray(resp)
        val result = mutableMapOf<String, String>()
        for (i in texts.indices) {
            val item = out.optJSONObject(i) ?: continue
            val translations = item.optJSONArray("translations") ?: continue
            val t = translations.optJSONObject(0)?.optString("text") ?: ""
            if (t.isNotBlank()) result[texts[i]] = t
        }
        if (result.isEmpty()) throw RuntimeException("empty")
        return result
    }

    private fun myMemory(text: String): String {
        val q = URLEncoder.encode(text, "UTF-8")
        val body = get("https://api.mymemory.translated.net/get?q=$q&langpair=en%7Car")
        val j = JSONObject(body)
        val t = j.optJSONObject("responseData")?.optString("translatedText") ?: ""
        if (t.isBlank() || t.contains("INVALID") || t.contains("QUERY LENGTH")) throw RuntimeException("bad")
        return t
    }
}
