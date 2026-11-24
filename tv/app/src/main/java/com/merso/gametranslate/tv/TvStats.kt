package com.merso.gametranslate.tv

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

/** Anonymous daily-active-device ping — same backend as the phone app. */
object TvStats {

    private const val TAG = "TvStats"
    const val ENDPOINT = "https://script.google.com/macros/s/AKfycbwl3lT6TuYgd7k-o5Fu2xRjiGpepfQEKILOm0C3Rux6eH6hv1Lu934zFn2yTcm_4ktQ/exec"

    private val exec = Executors.newSingleThreadExecutor()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("merso_tv", Context.MODE_PRIVATE)

    fun pingDaily(ctx: Context) {
        if (ENDPOINT == "STATS_ENDPOINT_URL" || ENDPOINT.isBlank()) return
        val sp = prefs(ctx)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (sp.getString("stats_ping_date", "") == today) return
        sp.edit().putString("stats_ping_date", today).apply()
        var id = sp.getString("stats_device_id", "") ?: ""
        if (id.isBlank()) {
            id = UUID.randomUUID().toString()
            sp.edit().putString("stats_device_id", id).apply()
        }
        val deviceId = id
        val ver = try {
            @Suppress("DEPRECATION") ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (_: Exception) { "" }
        exec.execute {
            try {
                val q = "action=ping&id=" + URLEncoder.encode(deviceId, "UTF-8") +
                    "&app=tv&v=" + URLEncoder.encode(ver, "UTF-8") + "&d=" + today
                val conn = URL("$ENDPOINT?$q").openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "MersoTranslatorTV-Stats")
                val code = conn.responseCode
                conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                Log.d(TAG, "ping sent: $code")
            } catch (e: Exception) {
                Log.w(TAG, "ping failed: $e")
            }
        }
    }
}
