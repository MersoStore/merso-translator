package com.merso.gametranslate

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

// Speaks Arabic translations aloud on the phone when enabled in Settings.
object Tts {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var pending: String? = null

    fun speak(ctx: Context, text: String) {
        if (!Prefs.ttsOn(ctx) || text.isBlank()) return
        val app = ctx.applicationContext
        if (tts == null) {
            pending = text
            tts = TextToSpeech(app) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try { tts?.language = Locale("ar") } catch (_: Exception) {}
                    ready = true
                    pending?.let { speakNow(it) }
                    pending = null
                }
            }
            return
        }
        if (ready) speakNow(text) else pending = text
    }

    private fun speakNow(text: String) {
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "merso-" + System.currentTimeMillis())
        } catch (_: Exception) {}
    }
}
