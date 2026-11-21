package com.merso.gametranslate

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

object OcrEngine {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val exec = Executors.newSingleThreadExecutor()

    @Volatile private var lastText: String = ""
    @Volatile private var lastSentAt: Long = 0
    @Volatile private var candidate: String = ""
    @Volatile private var candidateCount: Int = 0
    @Volatile var lastRawText: String = ""
    @Volatile var lastError: String = ""
    @Volatile private var staticText: String = ""
    @Volatile private var staticSince: Long = 0

    private fun normalize(t: String): String =
        t.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    // Same sentence read slightly differently by OCR counts as the same line,
    // so the first translation stays locked and never flickers.
    private fun similar(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true
        val minLen = minOf(na.length, nb.length)
        if (minLen >= 10 && (na.startsWith(nb) || nb.startsWith(na))) return true
        val wa = na.split(" ").toSet()
        val wb = nb.split(" ").toSet()
        val union = wa.union(wb).size
        return union > 0 && wa.intersect(wb).size.toFloat() / union >= 0.7f
    }

    // Only real subtitle sentences pass: no logos, plates, numbers or UI junk
    // lenient=true (camera): handheld TV reads are noisy, accept rougher text
    private fun looksLikeSubtitle(t: String, lenient: Boolean = false): Boolean {
        val s = t.trim()
        if (s.length < 3) return false
        val letters = s.count { it.isLetter() }
        val digits = s.count { it.isDigit() }
        if (letters == 0) return false
        if (lenient) {
            if (letters < 4) return false
            if (letters.toFloat() / (letters + digits) < 0.6f) return false
            return true
        }
        if (letters.toFloat() / s.length < 0.55f) return false
        if (digits.toFloat() / s.length > 0.25f) return false
        val words = s.split(Regex("\\s+")).filter { w -> w.any { it.isLetter() } }
        if (words.isEmpty()) return false
        val hasLower = s.any { it.isLowerCase() }
        val allCaps = !hasLower && s.any { it.isUpperCase() }
        if (allCaps && words.size < 4) return false
        if (words.size == 1) {
            val w = words[0].filter { it.isLetter() }
            if (!(w.length >= 5 && hasLower)) return false
        }
        return true
    }

    fun cropBitmap(src: Bitmap, frac: FloatArray): Bitmap {
        val l = (src.width * frac[0]).toInt().coerceIn(0, src.width - 1)
        val t = (src.height * frac[1]).toInt().coerceIn(0, src.height - 1)
        val r = (src.width * frac[2]).toInt().coerceIn(l + 1, src.width)
        val b = (src.height * frac[3]).toInt().coerceIn(t + 1, src.height)
        val cut = Bitmap.createBitmap(src, l, t, r - l, b - t)
        return Bitmap.createScaledBitmap(cut, cut.width * 2, cut.height * 2, true)
    }

    fun recognize(bitmap: Bitmap, onText: (String) -> Unit) {
        val safe = if (bitmap.config == Bitmap.Config.HARDWARE)
            bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val image = InputImage.fromBitmap(safe, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val txt = result.text.replace("\n", " ").trim()
                lastRawText = txt
                lastError = ""
                if (txt.length >= 3) onText(txt)
            }
            .addOnFailureListener { e ->
                lastError = e.message ?: e.javaClass.simpleName
            }
    }

    // Full pipeline: OCR -> stabilize -> dedupe -> translate -> callback(en, ar)
    // immediate=true (camera mode): handheld reads are noisy, so send the first
    // good read instead of waiting for 2 identical frames.
    fun processFrame(bitmap: Bitmap, immediate: Boolean = false, onResult: (String, String) -> Unit) {
        recognize(bitmap) { en ->
            if (!looksLikeSubtitle(en, lenient = immediate)) {
                candidate = ""
                candidateCount = 0
                return@recognize
            }
            if (immediate) {
                val now = System.currentTimeMillis()
                if (similar(en, lastText) && now - lastSentAt < 15000) return@recognize
                lastText = en
                lastSentAt = now
                exec.execute {
                    val ar = Translator.translateEnToAr(en)
                    if (ar.isNotBlank()) onResult(en, ar)
                }
                return@recognize
            }
            // Static UI text (titles, logos) stays forever; subtitles change.
            // If the same line sits unchanged for over 6 seconds, stop sending it.
            val nowMs = System.currentTimeMillis()
            if (normalize(en) == normalize(staticText)) {
                if (nowMs - staticSince > 6000) return@recognize
            } else {
                staticText = en
                staticSince = nowMs
            }
            // Accept text seen identically in 2 consecutive frames so partial
            // reads don't flicker; but flush a long line seen once when the
            // next frame already moved on, so fast subtitles aren't missed.
            if (similar(en, candidate)) {
                candidateCount++
            } else {
                val prev = candidate
                val prevCount = candidateCount
                candidate = en
                candidateCount = 1
                if (prevCount == 1 && prev.length >= 12 && !similar(prev, lastText)) {
                    lastText = prev
                    lastSentAt = System.currentTimeMillis()
                    exec.execute {
                        val ar = Translator.translateEnToAr(prev)
                        if (ar.isNotBlank()) onResult(prev, ar)
                    }
                }
                return@recognize
            }
            if (candidateCount < 2) return@recognize
            val now = System.currentTimeMillis()
            // Locked: same sentence (even read slightly differently) is never
            // re-translated; only a clearly different sentence replaces it.
            if (similar(candidate, lastText) && now - lastSentAt < 15000) return@recognize
            val toSend = candidate
            lastText = toSend
            lastSentAt = now
            exec.execute {
                val ar = Translator.translateEnToAr(toSend)
                if (ar.isNotBlank()) onResult(toSend, ar)
            }
        }
    }

    fun reset() { lastText = "" }
}
