package com.merso.gametranslate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.Surface
import android.view.TextureView
import com.metallic.chiaki.lib.*
import java.util.concurrent.Executors

class StreamController(
    private val ctx: Context,
    initialTexture: TextureView?,
    onStatus: (String) -> Unit,
    onCaption: (String) -> Unit,
    onDebug: (String) -> Unit,
    onLoginPin: (Boolean, (String) -> Unit) -> Unit
) {
    var onStatus: (String) -> Unit = onStatus
    var onCaption: (String) -> Unit = onCaption
    var onDebug: (String) -> Unit = onDebug
    var onLoginPin: (Boolean, (String) -> Unit) -> Unit = onLoginPin
    @Volatile private var texture: TextureView? = initialTexture
    companion object {
        private const val TAG = "MersoStream"
    }

    private val main = Handler(Looper.getMainLooper())
    // Last full stream frame, kept so the Settings crop preview can show
    // the game picture behind the crop frame.
    private val frameLock = Any()
    @Volatile private var latestFrame: Bitmap? = null

    /** A copy of the most recent stream frame, or null if none yet. */
    fun snapshotFrame(): Bitmap? = synchronized(frameLock) {
        val f = latestFrame
        if (f == null || f.isRecycled) null else f.copy(Bitmap.Config.ARGB_8888, false)
    }

    /** Grab a fresh frame straight from the stream surface (live preview). */
    fun grabFrame(width: Int, height: Int): Bitmap? {
        val tv = texture ?: return null
        return if (tv.isAvailable) {
            try { tv.getBitmap(width, height) } catch (_: Exception) { null }
        } else null
    }
    // Native setSurface can block; never call it on the main thread (ANR in PiP)
    private val surfaceExec = Executors.newSingleThreadExecutor()
    private val ocrExec = Executors.newSingleThreadExecutor()
    private var discovery: DiscoveryService? = null
    private var regist: Regist? = null
    private var session: Session? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    // The decoder renders straight into the attached TextureView's
    // surface (main view / mini player / PiP), and OCR reads frames
    // back with TextureView.getBitmap(). This is the original,
    // proven path: picture + translation work while the app is on
    // screen. (The screen-off experiments — ImageReader surface
    // swaps and a GL readback renderer — were removed at the user's
    // request; they never produced frames on his device and the GL
    // path broke the picture entirely.)
    private var currentSurface: Surface? = null
    @Volatile private var lastTextEver = ""
    private var connecting = false
    private var usedSavedKeys = false
    private var freshRegist = false
    private var gotConnected = false
    // Last connect() arguments, kept so a refused session can be
    // retried once automatically with the same pairing keys.
    private var lastIp: String? = null
    private var lastRegistKey: ByteArray? = null
    private var lastMorning: ByteArray? = null
    private var sessionRetries = 0
    // Auto-reconnect attempts after a live stream drops (reset on connect).
    private var reconnects = 0
    // Bumped on every start()/stop() so a delayed quit-handler or a
    // pending auto-retry from a previous run can't fire afterwards.
    @Volatile private var startGeneration = 0
    // Session dispose (native join) must not run on the session's own
    // callback thread; do lifecycle work here instead.
    private val lifecycleExec = Executors.newSingleThreadExecutor()
    private var logFile: java.io.File? = null
    private var ocrRunning = false
    private var frameCount = 0
    var running = false
        private set

    private fun makeListener(tv: TextureView) = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
            if (texture === tv) {
                // Keep frames at 720p even in the small mini player so OCR stays sharp
                try { st.setDefaultBufferSize(1280, 720) } catch (_: Exception) {}
                applySurface(st)
            }
        }
        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
            if (texture === tv) {
                try { currentSurface?.release() } catch (_: Exception) {}
                currentSurface = null
            }
            return true
        }
        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
    }

    // Hand the TextureView's surface to the decoder.
    private fun applySurface(st: SurfaceTexture) {
        try { currentSurface?.release() } catch (_: Exception) {}
        val sf = Surface(st)
        currentSurface = sf
        surfaceExec.execute { try { session?.setSurface(sf) } catch (_: Exception) {} }
    }

    fun attach(newTexture: TextureView) {
        detach()
        texture = newTexture
        newTexture.surfaceTextureListener = makeListener(newTexture)
        if (newTexture.isAvailable) {
            try { newTexture.surfaceTexture?.setDefaultBufferSize(1280, 720) } catch (_: Exception) {}
            newTexture.surfaceTexture?.let { applySurface(it) }
        }
    }

    fun detach() {
        texture?.surfaceTextureListener = null
        texture = null
        try { currentSurface?.release() } catch (_: Exception) {}
        currentSurface = null
    }

    fun start() {
        if (running) return
        running = true
        startGeneration++
        sessionRetries = 0
        usedSavedKeys = false
        freshRegist = false
        gotConnected = false
        lastTextEver = ""
        texture?.let { attach(it) }
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("merso-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
        MqttSender.connect(ctx)
        startDiscovery()
    }

    fun stop() {
        running = false
        startGeneration++
        sessionRetries = 0
        ocrRunning = false
        connecting = false
        try { session?.stop() } catch (_: Exception) {}
        try { session?.dispose() } catch (_: Exception) {}
        try { regist?.dispose() } catch (_: Exception) {}
        try { discovery?.dispose() } catch (_: Exception) {}
        try { multicastLock?.release() } catch (_: Exception) {}
        try { currentSurface?.release() } catch (_: Exception) {}
        currentSurface = null
        session = null
        regist = null
        discovery = null
    }

    private fun setStatus(s: String) {
        main.post { onStatus(s) }
        Log.i(TAG, s)
    }

    private fun startDiscovery() {
        try {
            discovery = DiscoveryService(
                DiscoveryServiceOptions(16U, 3U, 500U, java.net.InetSocketAddress("255.255.255.255", 987))
            ) { hosts ->
                val ps5 = hosts.firstOrNull { it.isPS5 && it.hostAddr != null }
                if (ps5 != null && !connecting && session == null && running) {
                    connecting = true
                    onHostFound(ps5)
                }
            }
        } catch (e: Exception) {
            setStatus(Str.p("معرفش يبدأ البحث: ${e.message}", "Couldn't start discovery: ${e.message}"))
        }
    }

    private fun onHostFound(host: DiscoveryHost) {
        val ip = host.hostAddr ?: return
        setStatus(Str.p("لقينا الجهاز: ${host.hostName ?: ip}", "Found console: ${host.hostName ?: ip}"))
        val savedKey = Prefs.registKey(ctx)
        val savedMorning = Prefs.morning(ctx)
        // Pairing keys are NOT bound to the console's IP (DHCP can hand
        // it a new address), so always try saved keys first. Requiring
        // an IP match here used to force a fresh Regist with a stale
        // PIN, which then failed even though the keys were fine.
        if (savedKey.isNotEmpty() && savedMorning.isNotEmpty()) {
            usedSavedKeys = true
            connect(ip, hexToBytes(savedKey), hexToBytes(savedMorning))
        } else {
            register(ip)
        }
    }

    private fun register(ip: String) {
        val pinStr = Prefs.pin(ctx)
        val pin = pinStr.toIntOrNull()
        val accountIdB64 = Prefs.accountId(ctx)
        if (pin == null) {
            setStatus(Str.p("حط رقم الـPIN من الجهاز الأول من زرار تسجيل سوني ورقم PIN", "Enter the console PIN first from the Sony sign-in & PIN section"))
            connecting = false
            return
        }
        if (accountIdB64.isEmpty()) {
            setStatus(Str.p("محتاج تسجيل سوني أو التفعيل الوهمي الأول من صفحة الريموت بلاي", "Sony sign-in or fake activation is required first from the Remote Play page"))
            connecting = false
            return
        }
        val accountId = try {
            Base64.decode(accountIdB64, Base64.DEFAULT)
        } catch (e: Exception) {
            setStatus(Str.p("رقم الحساب مش مظبوط", "Account ID is invalid"))
            connecting = false
            return
        }
        setStatus(Str.p("بيربط الجهاز برقم الـPIN...", "Linking the console with the PIN..."))
        val log = ChiakiLog(ChiakiLog.Level.ALL.value) { _, _ -> }
        try {
            regist = Regist(
                RegistInfo(com.metallic.chiaki.lib.Target.PS5_1, ip, false, null, accountId, pin),
                log
            ) { event ->
                when (event) {
                    is RegistEventSuccess -> {
                        freshRegist = true
                        val h = event.host
                        Prefs.setRegistKey(ctx, bytesToHex(h.rpRegistKey))
                        Prefs.setMorning(ctx, bytesToHex(h.rpKey))
                        Prefs.setHostIp(ctx, ip)
                        // A successful Regist just proved these keys
                        // against the console with the stored PIN, so
                        // the pairing IS verified — record it (this was
                        // missing, leaving the status stuck on
                        // "PIN stored but pairing not verified").
                        Prefs.setPinVerified(ctx, true)
                        setStatus(Str.p("تم الربط ✓ بيتصل...", "Linked ✓ connecting..."))
                        main.post { connect(ip, h.rpRegistKey, h.rpKey) }
                    }
                    is RegistEventFailed -> {
                        setStatus(Str.p("الربط فشل. اتأكد من رقم الـPIN وإن الجهاز على نفس الشبكة، وبعدين وقف وشغل تاني", "Linking failed. Check the PIN and that the console is on the same network, then stop and start again"))
                        connecting = false
                    }
                    is RegistEventCanceled -> {
                        setStatus(Str.p("الربط اتلغى", "Linking canceled"))
                        connecting = false
                    }
                }
            }
        } catch (e: Exception) {
            setStatus(Str.p("غلط في الربط: ${e.message}", "Linking error: ${e.message}"))
            connecting = false
        }
    }

    private fun connect(ip: String, registKey: ByteArray, morning: ByteArray) {
        lastIp = ip
        lastRegistKey = registKey
        lastMorning = morning
        try {
            val profile = ConnectVideoProfile.preset(
                VideoResolutionPreset.RES_720P, VideoFPSPreset.FPS_30, Codec.CODEC_H264
            )
            val info = ConnectInfo(true, ip, registKey, morning, profile)
            // Capture the native chiaki log so a refused session can show
            // the console's exact application-reason code (e.g. 0x80108b12
            // = Remote Play disabled for this user) instead of "unknown".
            val lf = java.io.File(ctx.cacheDir, "chiaki-session.log")
            try { lf.delete() } catch (_: Exception) {}
            logFile = lf
            val s = Session(info, lf.absolutePath, true)
            s.eventCallback = { event ->
                when (event) {
                    is ConnectedEvent -> {
                        gotConnected = true
                        sessionRetries = 0
                        reconnects = 0
                        // The pairing keys just proved themselves against
                        // the console: mark them verified and remember
                        // the console's current IP.
                        Prefs.setPinVerified(ctx, true)
                        lastIp?.let { Prefs.setHostIp(ctx, it) }
                        setStatus(Str.p("متصل ✓ الترجمة شغالة", "Connected ✓ translation running"))
                        startOcrLoop()
                    }
                    is LoginPinRequestEvent -> main.post {
                        onLoginPin(event.pinIncorrect) { pin -> session?.setLoginPin(pin) }
                    }
                    is QuitEvent -> {
                        ocrRunning = false
                        if (gotConnected) {
                            // Stream was running and then quit: dispose the
                            // dead session and reconnect automatically
                            // instead of leaving a zombie session that
                            // blocks every later start. Pairing keys are
                            // never touched.
                            val gen = startGeneration
                            val dead = session
                            session = null
                            gotConnected = false
                            connecting = false
                            lifecycleExec.execute {
                                try { dead?.stop() } catch (_: Exception) {}
                                try { dead?.dispose() } catch (_: Exception) {}
                            }
                            if (running && reconnects < 5) {
                                reconnects++
                                val delay = 3000L * reconnects
                                main.postDelayed({
                                    if (gen != startGeneration || !running || session != null) return@postDelayed
                                    try { discovery?.dispose() } catch (_: Exception) {}
                                    discovery = null
                                    connecting = false
                                    startDiscovery()
                                }, delay)
                            }
                            val acctB64 = Prefs.accountId(ctx)
                            val masked = if (acctB64.length >= 4) "…${acctB64.takeLast(4)}" else "—"
                            val keysKind = when {
                                freshRegist -> Str.p("ربط جديد دلوقتي", "fresh pairing just now")
                                usedSavedKeys -> Str.p("ربط محفوظ", "saved pairing")
                                else -> Str.p("من غير ربط", "no pairing")
                            }
                            val diag = Str.p(
                                " [تشخيص: كود الجهاز ${readAppReason() ?: "—"} | الحساب $masked | $keysKind]",
                                " [diag: console code ${readAppReason() ?: "—"} | account $masked | $keysKind]"
                            )
                            setStatus(if (running && reconnects in 1..5) Str.p("البث فصل — بيحاول يتصل تاني تلقائيًا...$diag", "Stream dropped — reconnecting automatically...$diag") else Str.p("البث وقف: ${event.reasonString ?: event.reason}$diag", "Stream stopped: ${event.reasonString ?: event.reason}$diag"))
                        } else {
                            // Session refused BEFORE connecting. Handle it
                            // after a short delay so the native chiaki log
                            // is flushed (reading it instantly used to show
                            // "—" instead of the console's reason code).
                            // IMPORTANT: never wipe the pairing keys here.
                            // A refused session is usually transient (e.g.
                            // the console still holding the previous
                            // session after an app update/restart, which
                            // chiaki reports as "Unknown Session Request
                            // Error"); account changes already clear the
                            // pairing at the moment the account changes.
                            val gen = startGeneration
                            val reasonText = event.reasonString ?: event.reason.toString()
                            main.postDelayed({
                                if (gen != startGeneration || !running) return@postDelayed
                                handleRefusedSession(reasonText)
                            }, 600)
                        }
                    }
                    is RumbleEvent -> {}
                }
            }
            session = s
            val err = s.start()
            if (!err.isSuccess) {
                setStatus(Str.p("معرفش يبدأ البث: $err", "Couldn't start the stream: $err"))
                return
            }
            // Give the decoder the currently attached TextureView's
            // surface (if a view is already up); later attaches hand
            // over their own surface via applySurface().
            currentSurface?.let { sf -> surfaceExec.execute { try { s.setSurface(sf) } catch (_: Exception) {} } }
            setStatus(Str.p("بيتصل بالجهاز...", "Connecting to the console..."))
        } catch (e: Exception) {
            setStatus(Str.p("غلط في الاتصال: ${e.message}", "Connection error: ${e.message}"))
        }
    }

    // The console refused the session before it connected. Decide what
    // to do WITHOUT destroying the saved pairing: retry once
    // automatically (transient refusals — e.g. a stale console-side
    // session after an app update — usually clear on the second try),
    // and only then report, keeping the keys so "Start Stream" can
    // still work after the user fixes the console side.
    private fun handleRefusedSession(reasonText: String) {
        val appReason = readAppReason()
        val acctB64 = Prefs.accountId(ctx)
        val masked = if (acctB64.length >= 4) "…${acctB64.takeLast(4)}" else "—"
        val keysKind = when {
            freshRegist -> Str.p("ربط جديد دلوقتي", "fresh pairing just now")
            usedSavedKeys -> Str.p("ربط محفوظ", "saved pairing")
            else -> Str.p("من غير ربط", "no pairing")
        }
        val diag = Str.p(
            " [تشخيص: كود الجهاز ${appReason ?: "—"} | الحساب $masked | $keysKind]",
            " [diag: console code ${appReason ?: "—"} | account $masked | $keysKind]"
        )
        if (appReason == "0x80108b12") {
            // Remote Play disabled for this user on the console
            // (per-user setting since FW 10.00). Pairing/Regist can
            // succeed while the session is refused with this code.
            // Retrying can't help until the setting is turned ON.
            setStatus(Str.p(
                "البث وقف: الريموت بلاي مقفول للحساب ده على الجهاز (0x80108b12). وانت داخل بنفس الحساب على الـPS5 روح الإعدادات ← النظام ← Remote Play وشغّل «Enable Remote Play» (ON)، وبعدين شغل البث تاني$diag",
                "Stream stopped: Remote Play is disabled for this account on the console (0x80108b12). While signed in with this same account on the PS5, go to Settings → System → Remote Play and turn ON “Enable Remote Play”, then start the stream again$diag"
            ))
            finishFailedStart()
            return
        }
        val ip = lastIp
        val rk = lastRegistKey
        val mn = lastMorning
        if (usedSavedKeys && sessionRetries < 1 && ip != null && rk != null && mn != null) {
            sessionRetries++
            setStatus(Str.p(
                "الجهاز رفض الجلسة ($reasonText) — بيحاول تاني تلقائيًا...$diag",
                "Console refused the session ($reasonText) — retrying automatically...$diag"
            ))
            val old = session
            session = null
            gotConnected = false
            lifecycleExec.execute {
                try { old?.stop() } catch (_: Exception) {}
                try { old?.dispose() } catch (_: Exception) {}
                main.post { if (running) connect(ip, rk, mn) }
            }
            return
        }
        // Final refusal: keep the pairing keys (they may still be
        // perfectly valid) and tell the user what to check, in order.
        setStatus(Str.p(
            "البث وقف: $reasonText — الربط المحفوظ لسه موجود ومتشالش. اتأكد إنك داخل بنفس الحساب على الـPS5 وإن «Enable Remote Play» شغالة (ON) للحساب ده. لو لسه بيرفض، اعمل Restart للجهاز (ممكن تكون جلسة قديمة معلقة عليه) وبعدين شغل البث تاني. لو استمرت المشكلة، خد PIN جديد من ربط جهاز وحطه في الخطوة ٢ ودوس حفظ$diag",
            "Stream stopped: $reasonText — your saved pairing was kept, not cleared. Make sure you're signed in with the same account on the PS5 and that “Enable Remote Play” is ON for this account. If it still refuses, restart the console (an old session may be stuck on it) and start the stream again. If it persists, get a NEW PIN from Link Device, enter it in Step 2 and tap Save$diag"
        ))
        finishFailedStart()
    }

    // A start attempt failed for good: release everything stop() does
    // and mark not-running so the next "Start Stream" tap works
    // without a manual Stop first. The status message set by the
    // caller stays on screen.
    private fun finishFailedStart() {
        stop()
        if (StreamHolder.controller === this) StreamHolder.controller = null
    }

    private fun startOcrLoop() {
        if (ocrRunning) return
        ocrRunning = true
        val runnable = object : Runnable {
            override fun run() {
                if (!ocrRunning || !running) return
                try {
                    val tv = texture
                    // Frames come from the visible TextureView, exactly
                    // like the original builds.
                    val bmp: Bitmap? = if (tv != null && tv.isAvailable) tv.getBitmap(1280, 720) else null
                    frameCount++
                    // Keep the last NON-empty OCR text: the raw value is
                    // overwritten by every OCR result (including empty
                    // ones), which made diagnostics claim "no text read
                    // yet" right after a successful read.
                    val rawNow = OcrEngine.lastRawText
                    if (rawNow.isNotEmpty()) lastTextEver = rawNow
                    val diag = StringBuilder(Str.p("فريمات: $frameCount", "Frames: $frameCount"))
                    if (bmp == null) diag.append(Str.p(" | مفيش صورة", " | no picture"))
                    if (lastTextEver.isNotEmpty())
                        diag.append(Str.p(" | آخر نص: ${lastTextEver.take(50)}", " | Last text: ${lastTextEver.take(50)}"))
                    else diag.append(Str.p(" | لسه مفيش نص اتقرأ", " | no text read yet"))
                    if (OcrEngine.lastError.isNotEmpty())
                        diag.append(Str.p(" | خطأ قراءة: ${OcrEngine.lastError.take(80)}", " | OCR error: ${OcrEngine.lastError.take(80)}"))
                    if (Translator.lastError.isNotEmpty())
                        diag.append(Str.p(" | خطأ ترجمة: ${Translator.lastError.take(60)}", " | Translation error: ${Translator.lastError.take(60)}"))
                    main.post { onDebug(diag.toString()) }
                    if (bmp != null) {
                        synchronized(frameLock) {
                            val copy = bmp.copy(Bitmap.Config.ARGB_8888, false)
                            val old = latestFrame
                            latestFrame = copy
                            if (old != null && old !== copy) old.recycle()
                        }
                        ocrExec.execute {
                            try {
                                val boxes = Prefs.crops(ctx)
                                OcrEngine.processBoxes(bmp, boxes,
                                    paused = Prefs.pausedBoxes(ctx),
                                    needReads = Prefs.needReads(ctx),
                                    minIntervalMs = Prefs.minIntervalMs(ctx),
                                    onRefresh = { i, en, ar, color, lines ->
                                        if (!OcrEngine.liveSuppressed()) {
                                            val b = boxes[i]
                                            MqttSender.sendLines(ctx, i, b, en, ar, if (Prefs.autoColor(ctx)) color else null, lines, keep = true, size = Prefs.boxSize(ctx, i))
                                        }
                                    },
                                    onGone = { i ->
                                        if (!OcrEngine.liveSuppressed()) MqttSender.sendClearBox(ctx, i)
                                    }
                                ) { i, en, ar, color, lines ->
                                    if (!OcrEngine.liveSuppressed()) {
                                        val b = boxes[i]
                                        MqttSender.sendLines(ctx, i, b, en, ar, if (Prefs.autoColor(ctx)) color else null, lines, size = Prefs.boxSize(ctx, i))
                                        History.add(ctx, en, ar)
                                        Tts.speak(ctx, ar)
                                        main.post { onCaption(ar) }
                                    }
                                }
                                bmp.recycle()
                            } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    val frames = e.stackTrace.take(3).joinToString(" <- ") {
                        "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
                    }
                    main.post { onDebug(Str.p("خطأ: ${e.message} @ $frames", "Error: ${e.message} @ $frames")) }
                }
                main.postDelayed(this, 600)
            }
        }
        main.post(runnable)
    }

    // Last "Reported Application Reason: 0x..." code from the native
    // chiaki session log, if any (e.g. 0x80108b12 = Remote Play disabled
    // for this user; chiaki itself only shows "Unknown" for it).
    private fun readAppReason(): String? {
        return try {
            val f = logFile ?: return null
            if (!f.exists()) return null
            val text = f.readText()
            val re = Regex("Application Reason:\\s*(0x[0-9a-fA-F]+)")
            re.findAll(text).lastOrNull()?.groupValues?.get(1)?.lowercase()
        } catch (_: Exception) {
            null
        }
    }

    private fun bytesToHex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
    private fun hexToBytes(s: String): ByteArray =
        ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
