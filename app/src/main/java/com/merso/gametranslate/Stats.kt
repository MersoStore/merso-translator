package com.merso.gametranslate

import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Anonymous daily-active-device stats.
 * One fire-and-forget ping per device per day (random UUID, no personal
 * data). Backend is a Google Apps Script web app writing to a Sheet
 * owned by Merso; it dedupes by (date, device) and serves counts.
 * Endpoint is filled in by build after the script is deployed.
 */
object Stats {

    private const val TAG = "Stats"
    const val ENDPOINT = "https://script.google.com/macros/s/AKfycbwl3lT6TuYgd7k-o5Fu2xRjiGpepfQEKILOm0C3Rux6eH6hv1Lu934zFn2yTcm_4ktQ/exec"

    private val exec = Executors.newSingleThreadExecutor()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("merso", Context.MODE_PRIVATE)

    private fun deviceId(ctx: Context): String {
        val sp = prefs(ctx)
        var id = sp.getString("stats_device_id", "") ?: ""
        if (id.isBlank()) {
            id = UUID.randomUUID().toString()
            sp.edit().putString("stats_device_id", id).apply()
        }
        return id
    }

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Ping once per day per device. Safe to call on every launch. */
    fun pingDaily(ctx: Context, app: String = "phone") {
        if (ENDPOINT == "STATS_ENDPOINT_URL" || ENDPOINT.isBlank()) return
        val sp = prefs(ctx)
        if (sp.getString("stats_ping_date", "") == today()) return
        sp.edit().putString("stats_ping_date", today()).apply()
        exec.execute {
            try {
                val q = "action=ping&id=" + URLEncoder.encode(deviceId(ctx), "UTF-8") +
                    "&app=" + URLEncoder.encode(app, "UTF-8") +
                    "&v=" + URLEncoder.encode(BuildConfigVersion.name(ctx), "UTF-8") +
                    "&d=" + today()
                val conn = URL("$ENDPOINT?$q").openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "MersoTranslator-Stats")
                val code = conn.responseCode
                conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                Log.d(TAG, "ping sent: $code")
            } catch (e: Exception) {
                Log.w(TAG, "ping failed: $e")
            }
        }
    }

    /** Fetch today's distinct-device count. Callback runs on the calling thread (background). */
    fun fetchTodayCount(onResult: (Int) -> Unit) {
        if (ENDPOINT == "STATS_ENDPOINT_URL" || ENDPOINT.isBlank()) {
            onResult(-1); return
        }
        exec.execute {
            try {
                val conn = URL("$ENDPOINT?action=count&d=" + today()).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val start = body.indexOf("\"today\"")
                val colon = body.indexOf(':', start)
                val end = body.indexOf(',', colon).let { if (it == -1) body.indexOf('}', colon) else it }
                onResult(body.substring(colon + 1, end).trim().toIntOrNull() ?: -1)
            } catch (e: Exception) {
                Log.w(TAG, "count fetch failed: $e")
                onResult(-1)
            }
        }
    }
}

/** Tiny helper: app version name without touching Updater. */
private object BuildConfigVersion {
    fun name(ctx: Context): String {
        return try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            @Suppress("DEPRECATION") info.versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
