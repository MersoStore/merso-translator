package com.merso.gametranslate

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class CrashApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val l = Prefs.lang(this)
            if (l.isNotEmpty()) Str.lang = l
        } catch (_: Exception) {}
        try {
            com.google.mlkit.common.MlKit.initialize(this)
        } catch (_: Exception) {}
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val text = "Thread: ${thread.name}\n$sw"
                Log.e("MersoCrash", text)
                try {
                    val dir = getExternalFilesDir(null) ?: filesDir
                    File(dir, "crash.txt").writeText(text)
                } catch (_: Exception) {}
                try {
                    File(filesDir, "crash.txt").writeText(text)
                } catch (_: Exception) {}
            } catch (_: Exception) {}
            prev?.uncaughtException(thread, e)
        }
    }
}
