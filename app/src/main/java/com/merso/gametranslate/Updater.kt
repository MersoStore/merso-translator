package com.merso.gametranslate

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object Updater {

    private const val TAG = "Updater"
    const val VERSION_URL = "https://mersostore.github.io/merso-translator-page/version.json"
    private const val APK_NAME = "merso-translate-update.apk"

    private val exec = Executors.newSingleThreadExecutor()
    @Volatile private var receiverRegistered = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("merso_update", Context.MODE_PRIVATE)

    fun currentVersionCode(ctx: Context): Long {
        return try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        } catch (_: Exception) {
            0L
        }
    }

    /** Silent check on app launch. Shows a dialog only if a newer version exists. */
    fun checkOnLaunch(activity: Activity) {
        registerReceiver(activity.applicationContext)
        checkPendingDownload(activity)
        fetch(activity, manual = false)
    }

    /** Manual check from Settings. Always gives feedback. */
    fun checkManual(activity: Activity) {
        registerReceiver(activity.applicationContext)
        Toast.makeText(activity, Str.p("بنتحقق من التحديثات…", "Checking for updates…"), Toast.LENGTH_SHORT).show()
        fetch(activity, manual = true)
    }

    private fun fetch(activity: Activity, manual: Boolean) {
        exec.execute {
            try {
                val conn = URL(VERSION_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) MersoTranslator")
                conn.setRequestProperty("Accept", "application/json")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = parseVersionJson(body)
                val latestCode = json.optLong("versionCode", 0L)
                val latestName = json.optString("versionName", "")
                val url = json.optString("url", "")
                val notes = if (Str.lang == "en") json.optString("notes_en", "") else json.optString("notes_ar", "")
                val current = currentVersionCode(activity)
                activity.runOnUiThread {
                    if (latestCode > current && url.isNotBlank()) {
                        if (!manual && prefs(activity).getLong("dismissed_code", 0L) == latestCode) return@runOnUiThread
                        showUpdateDialog(activity, latestName, notes, url, latestCode)
                    } else if (manual) {
                        Toast.makeText(
                            activity,
                            Str.p("أنت على أحدث إصدار ✓", "You're on the latest version ✓"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "update check failed: $e")
                if (manual) {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            Str.p("مش قادر أوصل لخادم التحديثات، جرّب تاني", "Couldn't reach the update server, try again"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    /** Tolerates servers that wrap the JSON in extra page content. */
    private fun parseVersionJson(body: String): JSONObject {
        val text = body.trim()
        return try {
            JSONObject(text)
        } catch (_: Exception) {
            val start = text.indexOf('{')
            val end = text.indexOf('}', start)
            JSONObject(text.substring(start, end + 1))
        }
    }

    private fun showUpdateDialog(activity: Activity, versionName: String, notes: String, url: String, latestCode: Long) {
        if (activity.isFinishing || activity.isDestroyed) return
        val msg = StringBuilder()
        msg.append(Str.p("في إصدار جديد", "A new version is available"))
        if (versionName.isNotBlank()) msg.append(" ").append(versionName)
        if (notes.isNotBlank()) msg.append("\n\n").append(notes)
        AlertDialog.Builder(activity)
            .setTitle(Str.p("تحديث جديد", "Update Available"))
            .setMessage(msg.toString())
            .setPositiveButton(Str.p("تحديث الآن", "Update Now")) { _, _ -> startDownload(activity, url) }
            .setNegativeButton(Str.p("لاحقًا", "Later")) { _, _ ->
                prefs(activity).edit().putLong("dismissed_code", latestCode).apply()
            }
            .setCancelable(true)
            .show()
    }

    private fun startDownload(ctx: Context, url: String) {
        try {
            val dm = ctx.getSystemService(DownloadManager::class.java)
            // Remove any previous pending download to avoid clutter/duplicates
            val oldId = prefs(ctx).getLong("download_id", -1L)
            if (oldId != -1L) {
                try { dm.remove(oldId) } catch (_: Exception) {}
            }
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("Merso Translator")
                setDescription(Str.p("بيتم تحميل التحديث…", "Downloading update…"))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, APK_NAME)
                setMimeType("application/vnd.android.package-archive")
            }
            val id = dm.enqueue(request)
            prefs(ctx).edit().putLong("download_id", id).apply()
            Toast.makeText(
                ctx,
                Str.p("بدأ التحميل، هيظهر إشعار لما يخلص", "Download started, you'll get a notification when it's done"),
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "download failed", e)
            Toast.makeText(
                ctx,
                Str.p("التحميل فشل، جرّب تاني", "Download failed, please try again"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun registerReceiver(appCtx: Context) {
        if (receiverRegistered) return
        receiverRegistered = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == prefs(context).getLong("download_id", -2L)) {
                    installDownloaded(context, id)
                }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) {
            appCtx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appCtx.registerReceiver(receiver, filter)
        }
    }

    /** If a previous update download finished while the app was closed, offer to install it. */
    private fun checkPendingDownload(activity: Activity) {
        val id = prefs(activity).getLong("download_id", -1L)
        if (id == -1L) return
        exec.execute {
            try {
                val dm = activity.getSystemService(DownloadManager::class.java)
                val cursor: Cursor = dm.query(DownloadManager.Query().setFilterById(id))
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        activity.runOnUiThread {
                            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                            AlertDialog.Builder(activity)
                                .setTitle(Str.p("التحديث جاهز", "Update Ready"))
                                .setMessage(Str.p("التحديث اتحمّل، تثبّته دلوقتي؟", "The update has been downloaded. Install it now?"))
                                .setPositiveButton(Str.p("تثبيت", "Install")) { _, _ -> installDownloaded(activity, id) }
                                .setNegativeButton(Str.p("لاحقًا", "Later")) { _, _ ->
                                    prefs(activity).edit().remove("download_id").apply()
                                }
                                .show()
                        }
                    }
                }
                cursor.close()
            } catch (e: Exception) {
                Log.w(TAG, "pending download check failed: $e")
            }
        }
    }

    private fun installDownloaded(ctx: Context, id: Long) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(
                    ctx,
                    Str.p("فعّل السماح بتثبيت التطبيقات من البرنامج ده عشان التحديث يكمل", "Allow installing apps from this app so the update can continue"),
                    Toast.LENGTH_LONG
                ).show()
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + ctx.packageName)
                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                ctx.startActivity(settingsIntent)
                return
            }
            val dm = ctx.getSystemService(DownloadManager::class.java)
            val uri = dm.getUriForDownloadedFile(id) ?: return
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            ctx.startActivity(intent)
            prefs(ctx).edit().remove("download_id").apply()
        } catch (e: Exception) {
            Log.e(TAG, "install launch failed", e)
        }
    }
}
