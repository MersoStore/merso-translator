package com.merso.gametranslate

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Last translated lines, newest first, kept in SharedPreferences.
object History {
    private const val MAX = 60

    private fun sp(ctx: Context) = ctx.getSharedPreferences("merso", Context.MODE_PRIVATE)

    fun add(ctx: Context, en: String, ar: String) {
        if (ar.isBlank()) return
        try {
            val arr = load(ctx)
            // Skip exact repeat of the newest entry
            if (arr.length() > 0) {
                val last = arr.optJSONObject(0)
                if (last != null && last.optString("ar") == ar && last.optString("en") == en) return
            }
            val out = JSONArray()
            out.put(JSONObject().apply {
                put("en", en)
                put("ar", ar)
                put("t", System.currentTimeMillis())
            })
            for (i in 0 until minOf(arr.length(), MAX - 1)) out.put(arr.get(i))
            sp(ctx).edit().putString("history", out.toString()).apply()
        } catch (_: Exception) {}
    }

    fun list(ctx: Context): List<Triple<String, String, Long>> {
        val out = mutableListOf<Triple<String, String, Long>>()
        try {
            val arr = load(ctx)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(Triple(o.optString("en"), o.optString("ar"), o.optLong("t")))
            }
        } catch (_: Exception) {}
        return out
    }

    fun clear(ctx: Context) = sp(ctx).edit().remove("history").apply()

    private fun load(ctx: Context): JSONArray {
        val raw = sp(ctx).getString("history", "") ?: ""
        return if (raw.isBlank()) JSONArray() else try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
    }
}
