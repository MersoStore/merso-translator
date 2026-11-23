package com.merso.gametranslate

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

object OcrEngine {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val exec = Executors.newSingleThreadExecutor()

    @Volatile var lastRawText: String = ""
    @Volatile var lastError: String = ""

    // Per-stream stabilization state. The main pipeline (single frame) uses
    // mainState; each crop box gets its own Stabilizer so boxes never fight
    // over the candidate/last-sent tracking.
    class Stabilizer {
        @Volatile var lastText: String = ""
        @Volatile var lastSentAt: Long = 0
        @Volatile var candidate: String = ""
        @Volatile var candidateCount: Int = 0
        @Volatile var candidateSince: Long = 0
        @Volatile var candidateColor: String? = null
        // Per-line centers (fractions inside the crop) of the candidate read,
        // so each translated line can be shown at its own position.
        @Volatile var candidateLines: List<FloatArray> = emptyList()
        @Volatile var staticText: String = ""
        @Volatile var staticSince: Long = 0
        // Sticky captions: while the same text stays on screen, the shown
        // caption is refreshed so the TV never auto-hides it; when the text
        // vanishes for a few frames, the caption is cleared; when the same
        // text returns, it is sent again (instantly, from translation cache).
        @Volatile var shown: Boolean = false
        @Volatile var goneCount: Int = 0
        @Volatile var lastColor: String? = null
        @Volatile var lastLines: List<FloatArray> = emptyList()
    }

    private val mainState = Stabilizer()
    private var boxStates: List<Stabilizer> = emptyList()

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
        // Stylized game fonts make OCR wobble by a letter or two between
        // frames ("exhibition" -> "xhibition"); treat near-identical reads
        // as the same line so captions don't flicker.
        val maxLen = maxOf(na.length, nb.length)
        if (maxLen >= 5 && levenshtein(na, nb) <= maxOf(1, maxLen / 6)) return true
        val minLen = minOf(na.length, nb.length)
        if (minLen >= 10 && (na.startsWith(nb) || nb.startsWith(na))) return true
        val wa = na.split(" ").toSet()
        val wb = nb.split(" ").toSet()
        // Overlap coefficient: a read that catches a subset of the same
        // menu items ("exhibition create" vs "exhibition create season")
        // is still the same static screen, not new dialogue.
        val minSize = minOf(wa.size, wb.size)
        if (minSize >= 2 && wa.intersect(wb).size.toFloat() / minSize >= 0.75f) return true
        val union = wa.union(wb).size
        return union > 0 && wa.intersect(wb).size.toFloat() / union >= 0.7f
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(minOf(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }

    // While full-screen translations are on the TV, live box captions are
    // held back so the two don't overlap and churn.
    @Volatile var suppressUntil: Long = 0
    fun suppressLive(ms: Long) {
        suppressUntil = System.currentTimeMillis() + ms
    }
    fun liveSuppressed(): Boolean = System.currentTimeMillis() < suppressUntil

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

    fun recognize(bitmap: Bitmap, onText: (String, String?, List<FloatArray>) -> Unit, onEmpty: () -> Unit = {}) {
        val safe = if (bitmap.config == Bitmap.Config.HARDWARE)
            bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val image = InputImage.fromBitmap(safe, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                // Keep the game's line breaks: lines joined with "\n" in
                // reading order, so a list (1. 2. 3.) stays a list instead
                // of being flattened into one line. Each line's center
                // (as a fraction of the crop) rides along so translations
                // can be placed exactly where the original text sits.
                val pairs = result.textBlocks
                    .flatMap { it.lines }
                    .mapNotNull { l ->
                        val t = l.text.trim()
                        if (t.isEmpty()) null else t to l.boundingBox
                    }
                val txt = pairs.joinToString("\n") { it.first }
                    .ifEmpty { result.text.trim() }
                lastRawText = txt
                lastError = ""
                if (txt.length >= 3) {
                    val rects = pairs.mapNotNull { it.second }
                    val centers: List<FloatArray> = if (rects.size == pairs.size && pairs.isNotEmpty()) {
                        rects.map { r ->
                            floatArrayOf(
                                ((r.left + r.right) / 2f / safe.width).coerceIn(0f, 1f),
                                ((r.top + r.bottom) / 2f / safe.height).coerceIn(0f, 1f)
                            )
                        }
                    } else emptyList()
                    onText(txt, sampleTextColor(safe, rects), centers)
                } else {
                    // OCR ran fine but found no usable text in this crop:
                    // the subtitle may have left the screen.
                    onEmpty()
                }
            }
            .addOnFailureListener { e ->
                lastError = e.message ?: e.javaClass.simpleName
            }
    }

    private fun lum(c: Int): Int =
        (299 * Color.red(c) + 587 * Color.green(c) + 114 * Color.blue(c)) / 1000

    // Estimate the game's subtitle text color from the pixels inside the
    // recognized text lines: bright text on a darker background (the usual
    // case) -> average of the brightest pixels; dark text on a light
    // background -> average of the darkest. Returns "#RRGGBB" or null when
    // no confident color can be picked (caller falls back to manual color).
    private fun sampleTextColor(bmp: Bitmap, rects: List<Rect>): String? {
        if (rects.isEmpty()) return null
        val pixels = ArrayList<Int>(16384)
        for (r in rects) {
            val l = r.left.coerceIn(0, bmp.width - 1)
            val t = r.top.coerceIn(0, bmp.height - 1)
            val w = (r.right - r.left).coerceIn(1, bmp.width - l)
            val h = (r.bottom - r.top).coerceIn(1, bmp.height - t)
            val arr = IntArray(w * h)
            try { bmp.getPixels(arr, 0, w, l, t, w, h) } catch (_: Exception) { continue }
            var i = 0
            while (i < arr.size) { pixels.add(arr[i]); i += 3 }
        }
        if (pixels.size < 60) return null
        val lums = pixels.map { lum(it) }.sorted()
        val medianLum = lums[lums.size / 2]
        val n = maxOf(12, pixels.size * 12 / 100)
        fun avg(list: List<Int>): IntArray {
            var sr = 0L; var sg = 0L; var sb = 0L
            for (c in list) { sr += Color.red(c); sg += Color.green(c); sb += Color.blue(c) }
            val m = list.size.coerceAtLeast(1)
            return intArrayOf((sr / m).toInt(), (sg / m).toInt(), (sb / m).toInt())
        }
        fun scale(rgb: IntArray, f: Float): IntArray = intArrayOf(
            (rgb[0] * f).toInt().coerceAtMost(255),
            (rgb[1] * f).toInt().coerceAtMost(255),
            (rgb[2] * f).toInt().coerceAtMost(255)
        )
        var rgb: IntArray
        if (medianLum < 150) {
            val top = pixels.sortedByDescending {
                maxOf(Color.red(it), Color.green(it), Color.blue(it))
            }.take(n)
            rgb = avg(top)
            val mx = maxOf(rgb[0], rgb[1], rgb[2])
            if (mx < 130) return null
            val mn = minOf(rgb[0], rgb[1], rgb[2])
            rgb = if (mx - mn > 35) {
                // vivid game color: normalize so the strongest channel is full
                scale(rgb, 255f / mx)
            } else {
                // white/gray text: keep it bright enough to read on TV
                val l = (299 * rgb[0] + 587 * rgb[1] + 114 * rgb[2]) / 1000
                if (l < 180) scale(rgb, 200f / maxOf(l, 1)) else rgb
            }
        } else {
            val bottom = pixels.sortedBy { lum(it) }.take(n)
            rgb = avg(bottom)
            val l = (299 * rgb[0] + 587 * rgb[1] + 114 * rgb[2]) / 1000
            if (l > 110) return null
        }
        return String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2])
    }

    // Full pipeline: OCR -> stabilize -> dedupe -> translate -> callback(en, ar, color)
    // immediate=true (camera mode): handheld reads are noisy, so send the first
    // good read instead of waiting for 2 identical frames.
    fun processFrame(bitmap: Bitmap, immediate: Boolean = false, onResult: (String, String, String?) -> Unit) {
        recognize(bitmap, { en, color, _ -> processText(en, immediate, mainState, color) { e2, ar, c, _ -> onResult(e2, ar, c) } })
    }

    // Multi-box pipeline: each crop box is OCR'd and stabilized on its own,
    // and results are reported per box (index, en, ar, color, line centers)
    // so the TV can show each translated line at its own position.
    fun processBoxes(
        src: Bitmap,
        boxes: List<FloatArray>,
        paused: Set<Int> = emptySet(),
        needReads: Int = 2,
        minIntervalMs: Long = 1800,
        onRefresh: (Int, String, String, String?, List<FloatArray>) -> Unit = { _, _, _, _, _ -> },
        onGone: (Int) -> Unit = {},
        onResult: (Int, String, String, String?, List<FloatArray>) -> Unit
    ) {
        if (boxes.isEmpty()) return
        if (boxStates.size != boxes.size) boxStates = List(boxes.size) { Stabilizer() }
        boxes.forEachIndexed { i, frac ->
            if (i in paused) {
                // Box paused by the user: skip OCR entirely and reset its
                // state so resuming starts clean.
                val st = boxStates[i]
                st.shown = false
                st.lastText = ""
                st.candidate = ""
                st.candidateCount = 0
                st.goneCount = 0
                return@forEachIndexed
            }
            val cropped = cropBitmap(src, frac)
            recognize(cropped, { txt, color, lines ->
                processText(txt, false, boxStates[i], color, lines,
                    needReads = needReads, minIntervalMs = minIntervalMs,
                    onRefresh = { en, ar, c, ls -> onRefresh(i, en, ar, c, ls) },
                    onGone = { onGone(i) }
                ) { en, ar, c, ls -> onResult(i, en, ar, c, ls) }
            }, onEmpty = {
                processText("", false, boxStates[i], null, emptyList(),
                    needReads = needReads, minIntervalMs = minIntervalMs,
                    onRefresh = { _, _, _, _ -> },
                    onGone = { onGone(i) }
                ) { _, _, _, _ -> }
            })
        }
    }

    // One-shot full-screen translation: OCRs the whole frame and reports
    // every text LINE with its center position (fractions) so the TV can
    // show each translation exactly where the original text sits.
    fun translateFullScreen(
        bitmap: Bitmap,
        onBlock: (String, String, Float, Float, String?) -> Unit,
        onDone: (Int) -> Unit
    ) {
        val safe = if (bitmap.config == Bitmap.Config.HARDWARE)
            bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val scaled = if (safe.width <= 1280)
            Bitmap.createScaledBitmap(safe, safe.width * 2, safe.height * 2, true) else safe
        recognizer.process(InputImage.fromBitmap(scaled, 0))
            .addOnSuccessListener { result ->
                exec.execute {
                    var count = 0
                    val lines = result.textBlocks
                        .flatMap { it.lines }
                        .mapNotNull { l -> l.boundingBox?.let { rect -> l to rect } }
                        .sortedBy { it.second.top }
                    for ((line, rect) in lines) {
                        if (count >= 20) break
                        val txt = line.text.trim()
                        if (txt.count { it.isLetter() } < 2) continue
                        val ar = Translator.translateEnToAr(txt)
                        if (ar.isBlank()) continue
                        val cx = ((rect.left + rect.right) / 2f / scaled.width).coerceIn(0f, 1f)
                        val cy = ((rect.top + rect.bottom) / 2f / scaled.height).coerceIn(0f, 1f)
                        val color = sampleTextColor(scaled, listOf(rect))
                        onBlock(txt, ar, cx, cy, color)
                        count++
                    }
                    onDone(count)
                }
            }
            .addOnFailureListener { onDone(0) }
    }

    fun processText(
        en: String,
        immediate: Boolean,
        st: Stabilizer,
        color: String?,
        lines: List<FloatArray> = emptyList(),
        needReads: Int = 2,
        minIntervalMs: Long = 1800,
        onRefresh: (String, String, String?, List<FloatArray>) -> Unit = { _, _, _, _ -> },
        onGone: () -> Unit = {},
        onResult: (String, String, String?, List<FloatArray>) -> Unit
    ) {
        if (!looksLikeSubtitle(en, lenient = immediate)) {
            st.candidate = ""
            st.candidateCount = 0
            st.candidateColor = null
            st.candidateLines = emptyList()
            // The text left the box: after a few empty reads, clear the
            // caption from the TV and forget it, so the same text coming
            // back is sent fresh (from cache) right away.
            st.goneCount++
            if (st.shown && st.goneCount >= 3) {
                st.shown = false
                st.lastText = ""
                st.lastColor = null
                st.lastLines = emptyList()
                onGone()
            }
            return
        }
        if (immediate) {
            val now = System.currentTimeMillis()
            if (similar(en, st.lastText) && now - st.lastSentAt < 15000) return
            st.lastText = en
            st.lastSentAt = now
            exec.execute {
                val ar = Translator.translateEnToAr(en)
                if (ar.isNotBlank()) onResult(en, ar, color, lines)
            }
            return
        }
        // Sticky caption: the text already on the TV is still on screen,
        // so keep it there — refresh it every few seconds to reset the
        // TV's auto-hide timer instead of letting it vanish. Only a
        // clearly different sentence (handled below) replaces it, and it
        // is cleared only when the text actually leaves the box.
        val nowMs = System.currentTimeMillis()
        st.goneCount = 0
        if (st.shown && similar(en, st.lastText)) {
            if (nowMs - st.lastSentAt >= 4000) {
                st.lastSentAt = nowMs
                val keepText = st.lastText
                val keepColor = st.lastColor
                val keepLines = st.lastLines
                exec.execute {
                    val ar = Translator.translateEnToAr(keepText)
                    if (ar.isNotBlank()) onRefresh(keepText, ar, keepColor, keepLines)
                }
            }
            return
        }
        // Accept text seen identically in 2 consecutive frames so partial
        // reads don't flicker; but flush a long line seen once when the
        // next frame already moved on, so fast subtitles aren't missed.
        // The flush only fires if the previous line actually sat there for
        // a second — on a static menu with wobbling reads, replacements
        // arrive every OCR cycle and must NOT be flushed, or the caption
        // churns forever.
        if (similar(en, st.candidate)) {
            st.candidateCount++
            if (color != null) st.candidateColor = color
            if (lines.isNotEmpty()) st.candidateLines = lines
        } else {
            val prev = st.candidate
            val prevCount = st.candidateCount
            val prevColor = st.candidateColor
            val prevLines = st.candidateLines
            val prevAge = nowMs - st.candidateSince
            st.candidate = en
            st.candidateCount = 1
            st.candidateSince = nowMs
            st.candidateColor = color
            st.candidateLines = lines
            if (prevCount == 1 && prev.length >= 12 && prevAge >= 1000 && !similar(prev, st.lastText)) {
                st.lastText = prev
                st.lastSentAt = System.currentTimeMillis()
                st.shown = true
                st.lastColor = prevColor
                st.lastLines = prevLines
                exec.execute {
                    val ar = Translator.translateEnToAr(prev)
                    if (ar.isNotBlank()) onResult(prev, ar, prevColor, prevLines)
                }
            }
            // Fast response mode accepts the very first read.
            if (needReads > 1) return
        }
        if (st.candidateCount < needReads) return
        val now = System.currentTimeMillis()
        // Locked: same sentence (even read slightly differently) is never
        // re-translated; only a clearly different sentence replaces it.
        if (similar(st.candidate, st.lastText) && now - st.lastSentAt < 15000) return
        // Never replace a caption faster than the viewer can read it.
        if (st.lastText.isNotEmpty() && now - st.lastSentAt < minIntervalMs) return
        val toSend = st.candidate
        val sendColor = color ?: st.candidateColor
        val sendLines = st.candidateLines
        st.lastText = toSend
        st.lastSentAt = now
        st.shown = true
        st.lastColor = sendColor
        st.lastLines = sendLines
        exec.execute {
            val ar = Translator.translateEnToAr(toSend)
            if (ar.isNotBlank()) onResult(toSend, ar, sendColor, sendLines)
        }
    }

    fun reset() {
        mainState.lastText = ""
        mainState.shown = false
        boxStates.forEach { it.lastText = ""; it.shown = false }
    }
}
