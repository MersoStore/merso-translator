package com.merso.gametranslate

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.widget.*
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import com.metallic.chiaki.lib.*
import com.merso.gametranslate.Ui.dp


class RemotePlayActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var pinInput: EditText
    private lateinit var loginStatus: TextView
    private lateinit var loginBtn: Button
    private lateinit var fakeStatus: TextView
    private lateinit var reconfirmBtn: Button
    private lateinit var pinBtn: Button
    private lateinit var pinMsg: TextView
    private var verifyDiscovery: DiscoveryService? = null
    private var verifyRegist: Regist? = null
    private var verifyLock: WifiManager.MulticastLock? = null
    private var verifyDone = false
    private lateinit var lastLine: TextView
    private lateinit var streamBox: LinearLayout
    private lateinit var streamTexture: TextureView
    private lateinit var streamStatus: TextView
    private var streamController: StreamController? = null
    private val hideInPip = mutableListOf<View>()
    private var suppressPip = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = Ui.screenRoot(this)
        fun addTracked(v: View) {
            root.addView(v)
            hideInPip.add(v)
        }

        addTracked(Ui.header(this, "Remote Play", Str.p("ترجمة عن طريق البث المباشر من الجهاز", "Translation via live stream from your console")))

        // Step 1: Sony login
        val c1 = Ui.card(this)
        c1.addView(Ui.cardTitle(this, Str.p("الخطوة ١ — تسجيل سوني", "Step 1 — Sony Sign-in")))
        c1.addView(Ui.bodyText(this, Str.p("سجّل دخول بحسابك الثانوي في صفحة سوني الرسمية جوه التطبيق", "Sign in with your second account on Sony's official page inside the app")))
        loginStatus = TextView(this).apply {
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(2))
        }
        c1.addView(loginStatus)
        loginBtn = Ui.secondaryButton(this, Str.p("تسجيل دخول PSN الثانوي", "Second PSN Sign-in")).apply {
            setOnClickListener {
                suppressPip = true
                startActivity(Intent(this@RemotePlayActivity, SonyLoginActivity::class.java))
            }
        }
        c1.addView(loginBtn)
        addTracked(c1)

        // Alternative to Step 1: offline activation + fake sign-in for
        // modded consoles whose local accounts demand a PSN sign-in.
        val cFake = Ui.card(this)
        cFake.addView(Ui.cardTitle(this, Str.p("أو — تفعيل الريموت بلاي بدون PSN", "Or — Enable Remote Play without PSN")))
        cFake.addView(Ui.bodyText(this, Str.p("لو جهازك معدل والحساب بيطلب تسجيل دخول PSN ومش بيشغل الريموت بلاي، التفعيل الوهمي بيحلها من جوه التطبيق من غير سوني خالص", "If your console is modded and the account demands a PSN sign-in and won't run Remote Play, fake activation fixes it from inside the app, no Sony at all")))
        fakeStatus = TextView(this).apply {
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(2))
        }
        cFake.addView(fakeStatus)
        cFake.addView(Ui.secondaryButton(this, Str.p("تفعيل وهمي (بدون تسجيل سوني)", "Fake Activation (no Sony sign-in)")).apply {
            setOnClickListener {
                suppressPip = true
                startActivity(Intent(this@RemotePlayActivity, FakeSignInActivity::class.java))
            }
        })
        // Re-run only the fake sign-in step for the already-activated
        // account (console may have cleared the sign-in state if it
        // reached PSN). Full activation stays in FakeSignInActivity.
        reconfirmBtn = Ui.secondaryButton(this, Str.p("تأكيد التسجيل الوهمي", "Re-confirm Fake Sign-in")).apply {
            setOnClickListener { reconfirmFakeSignIn() }
        }
        cFake.addView(reconfirmBtn)
        addTracked(cFake)

        // Step 2: PIN
        val c2 = Ui.card(this)
        c2.addView(Ui.cardTitle(this, Str.p("الخطوة ٢ — رقم PIN", "Step 2 — PIN Code")))
        c2.addView(Ui.bodyText(this, Str.p("على الـPS5: الإعدادات ← النظام ← Remote Play ← ربط جهاز، واكتب الرقم هنا.\n⚠️ اتأكد الأول إن «Enable Remote Play» شغالة (ON) للحساب اللي داخل بيه — من سوفت 10.00 وطالع الإعداد ده لكل حساب لوحده، ولو مقفول البث هيترفض حتى لو الرقم اتحفظ.", "On your PS5: Settings → System → Remote Play → Link Device, then enter the code here.\n⚠️ First make sure “Enable Remote Play” is ON for the account you're signed in with — since firmware 10.00 this setting is per-user; if it is OFF the stream will be refused even if the code saves fine.")))
        pinInput = Ui.inputField(this, Str.p("رقم PIN من الجهاز", "PIN from the console"))
        pinInput.setText(Prefs.pin(this))
        pinInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (Prefs.pinVerified(this@RemotePlayActivity) && s.toString().trim() != Prefs.pin(this@RemotePlayActivity)) {
                    Prefs.setPinVerified(this@RemotePlayActivity, false)
                    setPinDefault()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        c2.addView(pinInput)
        pinBtn = Ui.secondaryButton(this, Str.p("حفظ الرقم", "Save Code")).apply {
            setOnClickListener { verifyPin() }
        }
        c2.addView(pinBtn)
        pinMsg = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#FF2E2E"))
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, dp(4), 0, 0)
        }
        c2.addView(pinMsg)
        if (Prefs.pinVerified(this) && Prefs.pin(this).isNotEmpty()) setPinSaved()
        addTracked(c2)

        // Step 3: stream
        val c3 = Ui.card(this)
        c3.addView(Ui.cardTitle(this, Str.p("الخطوة ٣ — البث والترجمة", "Step 3 — Stream & Translate")))
        c3.addView(Ui.bodyText(this, Str.p("دوس بدء البث، والصورة هتظهر هنا والترجمة هتتبعت للتلفزيون على طول", "Tap Start Stream; the picture appears here and translations go straight to the TV")))
        c3.addView(Ui.primaryButton(this, Str.p("بدء البث", "Start Stream")).apply {
            setOnClickListener {
                if (Prefs.code(this@RemotePlayActivity).length < 4) {
                    Toast.makeText(this@RemotePlayActivity, Str.p("ارجع وحط كود ربط التلفزيون الأول", "Go back and enter the TV pairing code first"), Toast.LENGTH_LONG).show()
                } else {
                    startEmbeddedStream()
                }
            }
        })
        addTracked(c3)

        streamBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = Ui.roundedBg(this@RemotePlayActivity, Ui.CARD, 18, "#232D42", 1)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10); bottomMargin = dp(10) }
        }
        streamTexture = TextureView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(240)
            )
        }
        streamBox.addView(streamTexture)
        streamStatus = Ui.bodyText(this, "", 13f, Ui.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(2))
        }
        streamBox.addView(streamStatus)
        lastLine = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.parseColor("#FFC400"))
            setBackgroundColor(Color.parseColor("#CC000000"))
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6); bottomMargin = dp(6) }
        }
        streamBox.addView(lastLine)
        fun streamBtn(text: String, onClick: () -> Unit): Button =
            Ui.smallButton(this, text).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(4); marginEnd = dp(4) }
                setOnClickListener { onClick() }
            }
        val btnRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        btnRow1.addView(streamBtn(Str.p("ملء الشاشة", "Full Screen")) {
            if (streamController?.running == true) {
                StreamHolder.handedOff = true
                streamController?.detach()
                streamController = null
            }
            startActivity(Intent(this@RemotePlayActivity, StreamActivity::class.java))
        })
        btnRow1.addView(streamBtn(Str.p("صورة مصغرة", "Mini Window")) {
            if (streamController?.running == true) handoffToMain()
        })
        streamBox.addView(btnRow1)
        val btnRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        btnRow2.addView(streamBtn(Str.p("إيقاف", "Stop")) { stopEmbeddedStream() })
        val muteBtn = streamBtn(Str.p("🔇 كتم الصوت", "🔇 Mute")) {}
        muteBtn.setOnClickListener {
            val m = Mute.toggle(this@RemotePlayActivity)
            muteBtn.text = if (m) Str.p("🔊 تشغيل الصوت", "🔊 Unmute") else Str.p("🔇 كتم الصوت", "🔇 Mute")
        }
        btnRow2.addView(muteBtn)
        streamBox.addView(btnRow2)
        hideInPip.add(streamStatus)
        hideInPip.add(lastLine)
        hideInPip.add(btnRow1)
        hideInPip.add(btnRow2)
        root.addView(streamBox)

        // Status card
        val c4 = Ui.card(this)
        c4.addView(Ui.cardTitle(this, Str.p("الحالة", "Status")))
        status = Ui.bodyText(this, "", 15f, Ui.TEXT)
        c4.addView(status)
        addTracked(c4)

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        Ui.animateChildren(root)
    }

    private fun adoptStream(c: StreamController) {
        streamController = c
        c.onStatus = { streamStatus.text = it }
        c.onCaption = { lastLine.text = it }
        c.onDebug = { }
        c.onLoginPin = { incorrect, submit -> askLoginPin(incorrect, submit) }
        c.attach(streamTexture)
        StreamHolder.handedOff = false
        streamBox.visibility = View.VISIBLE
    }

    private fun startEmbeddedStream() {
        if (streamController?.running == true) return
        val existing = StreamHolder.controller
        if (existing != null && existing.running) {
            adoptStream(existing)
            return
        }
        streamBox.visibility = View.VISIBLE
        streamStatus.text = Str.p("بيدور على الـPS5 على الشبكة...", "Searching for your PS5 on the network...")
        streamController = StreamController(
            this, streamTexture,
            onStatus = { streamStatus.text = it },
            onCaption = { lastLine.text = it },
            onDebug = { },
            onLoginPin = { incorrect, submit -> askLoginPin(incorrect, submit) }
        )
        StreamHolder.controller = streamController
        streamController?.start()
    }

    private fun stopEmbeddedStream() {
        Mute.unmute(this)
        streamController?.stop()
        if (StreamHolder.controller === streamController) StreamHolder.controller = null
        streamController = null
        if (::streamBox.isInitialized) streamBox.visibility = View.GONE
    }

    private fun handoffToMain() {
        StreamHolder.handedOff = true
        streamController?.detach()
        streamController = null
        Nav.goHomeKeep(this)
    }

    private fun askLoginPin(incorrect: Boolean, submit: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = Str.p("رقم PIN تسجيل دخول المستخدم على الجهاز", "User sign-in PIN on the console")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle(if (incorrect) Str.p("الرقم غلط، جرب تاني", "Wrong PIN, try again") else Str.p("الجهاز طالب رقم PIN", "The console is asking for a PIN"))
            .setView(input)
            .setPositiveButton(Str.p("تمام", "OK")) { _, _ -> submit(input.text.toString()) }
            .setNegativeButton(Str.p("إلغاء", "Cancel"), null)
            .show()
    }

    private fun enterPip(): Boolean {
        inPip = true
        return try {
            val builder = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(16, 9))
            if (::streamTexture.isInitialized) {
                val rect = android.graphics.Rect()
                if (streamTexture.getGlobalVisibleRect(rect)) builder.setSourceRectHint(rect)
            }
            val ok = enterPictureInPictureMode(builder.build())
            if (!ok) inPip = false
            ok
        } catch (_: Exception) {
            inPip = false
            false
        }
    }

    private var inPip = false

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!suppressPip && streamController?.running == true) enterPip()
    }

    override fun onStop() {
        super.onStop()
        // PiP window closed by the user: stop the stream.
        if (inPip) {
            inPip = false
            streamController?.stop()
            if (StreamHolder.controller === streamController) StreamHolder.controller = null
            streamController = null
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        try {
            val vis = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
            for (v in hideInPip) v.visibility = vis
            if (::streamTexture.isInitialized) {
                if (isInPictureInPictureMode) {
                    // MATCH_PARENT height inside a ScrollView collapses to 0;
                    // size the video to the actual PiP window width instead
                    streamTexture.post {
                        val w = streamTexture.width
                        if (w > 0) {
                            val lp = streamTexture.layoutParams as LinearLayout.LayoutParams
                            lp.height = w * 9 / 16
                            streamTexture.layoutParams = lp
                        }
                    }
                    streamController?.attach(streamTexture)
                } else {
                    val lp = streamTexture.layoutParams as LinearLayout.LayoutParams
                    lp.height = dp(240)
                    streamTexture.layoutParams = lp
                }
            }
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        suppressPip = false
        refresh()
        val existing = StreamHolder.controller
        if (existing != null && existing.running && streamController == null) adoptStream(existing)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && !StreamHolder.handedOff) {
            streamController?.stop()
            if (StreamHolder.controller === streamController) StreamHolder.controller = null
        }
        cleanupVerify()
    }

    private fun setPinDefault() {
        pinBtn.text = Str.p("حفظ الرقم", "Save Code")
        pinBtn.background = Ui.roundedBg(this, Ui.CARD2, 14, Ui.GOLD, 1)
        pinBtn.setTextColor(Color.WHITE)
        pinBtn.isEnabled = true
        pinMsg.visibility = View.GONE
    }

    private fun setPinSaved() {
        pinBtn.text = Str.p("تم الحفظ ✓", "Saved ✓")
        pinBtn.background = Ui.gradientBg(this, intArrayOf(Color.parseColor("#2ED573"), Color.parseColor("#1E9E57")), 14)
        pinBtn.setTextColor(Color.WHITE)
        pinBtn.isEnabled = true
        pinMsg.visibility = View.GONE
    }

    private fun setPinError(msg: String) {
        pinBtn.text = Str.p("حفظ الرقم", "Save Code")
        pinBtn.background = Ui.gradientBg(this, intArrayOf(Color.parseColor("#E00000"), Color.parseColor("#8B0000")), 14)
        pinBtn.setTextColor(Color.WHITE)
        pinBtn.isEnabled = true
        pinMsg.text = msg
        pinMsg.visibility = View.VISIBLE
    }

    private fun cleanupVerify() {
        try { verifyRegist?.dispose() } catch (_: Exception) {}
        try { verifyDiscovery?.dispose() } catch (_: Exception) {}
        try { verifyLock?.release() } catch (_: Exception) {}
        verifyRegist = null
        verifyDiscovery = null
        verifyLock = null
    }

    private fun verifyPin() {
        val pinStr = pinInput.text.toString().trim()
        val pin = pinStr.toIntOrNull()
        if (pinStr.length != 8 || pin == null) {
            setPinError(Str.p("الرقم غلط، لازم يكون 8 أرقام. اكتب الرقم صح وجرب تاني", "Wrong code, it must be 8 digits. Enter the correct code and try again"))
            return
        }
        val accountIdB64 = Prefs.accountId(this)
        if (accountIdB64.isEmpty()) {
            setPinError(Str.p("سجل دخول سوني الأول من الخطوة ١، أو اعمل التفعيل الوهمي من الكارت اللي تحتها، وبعدين جرب تاني", "Sign in to Sony first in Step 1, or run the fake activation from the card below it, then try again"))
            return
        }
        val accountId = try {
            Base64.decode(accountIdB64, Base64.DEFAULT)
        } catch (_: Exception) {
            setPinError(Str.p("رقم الحساب مش مظبوط، سجل دخول سوني من جديد", "Account ID is invalid, sign in to Sony again"))
            return
        }
        Prefs.setPin(this, pinStr)
        Prefs.setPinVerified(this, false)
        pinBtn.text = Str.p("بيتأكد من الرقم...", "Verifying the code...")
        pinBtn.isEnabled = false
        pinMsg.visibility = View.GONE
        verifyDone = false
        val main = Handler(Looper.getMainLooper())
        val timeout = Runnable {
            if (!verifyDone) {
                verifyDone = true
                cleanupVerify()
                setPinError(Str.p("مش لاقي الجهاز. اتأكد إن الـPS5 شغال وعلى نفس الشبكة وصفحة ربط جهاز مفتوحة، وجرب تاني", "Can't find the console. Make sure the PS5 is on, on the same network, with the Link Device screen open, then try again"))
            }
        }
        main.postDelayed(timeout, 12000)
        try {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            verifyLock = wifi.createMulticastLock("merso-verify").apply {
                setReferenceCounted(false)
                acquire()
            }
            verifyDiscovery = DiscoveryService(
                DiscoveryServiceOptions(16U, 3U, 500U, java.net.InetSocketAddress("255.255.255.255", 987))
            ) { hosts ->
                val ps5 = hosts.firstOrNull { it.isPS5 && it.hostAddr != null }
                if (ps5 != null && !verifyDone) {
                    val ip = ps5.hostAddr!!
                    val log = ChiakiLog(ChiakiLog.Level.ALL.value) { _, _ -> }
                    try {
                        verifyRegist = Regist(
                            RegistInfo(com.metallic.chiaki.lib.Target.PS5_1, ip, false, null, accountId, pin),
                            log
                        ) { event ->
                            if (verifyDone) return@Regist
                            when (event) {
                                is RegistEventSuccess -> {
                                    verifyDone = true
                                    main.removeCallbacks(timeout)
                                    val h = event.host
                                    Prefs.setRegistKey(this@RemotePlayActivity, h.rpRegistKey.joinToString("") { "%02x".format(it) })
                                    Prefs.setMorning(this@RemotePlayActivity, h.rpKey.joinToString("") { "%02x".format(it) })
                                    Prefs.setHostIp(this@RemotePlayActivity, ip)
                                    Prefs.setPinVerified(this@RemotePlayActivity, true)
                                    main.post {
                                        cleanupVerify()
                                        setPinSaved()
                                        refresh()
                                    }
                                }
                                is RegistEventFailed -> {
                                    verifyDone = true
                                    main.removeCallbacks(timeout)
                                    main.post {
                                        cleanupVerify()
                                        setPinError(Str.p("الرقم غلط. اكتب الرقم صح من صفحة ربط جهاز على الـPS5 وجرب تاني", "Wrong code. Enter the correct code from the Link Device screen on the PS5 and try again"))
                                    }
                                }
                                is RegistEventCanceled -> {
                                    verifyDone = true
                                    main.removeCallbacks(timeout)
                                    main.post {
                                        cleanupVerify()
                                        setPinError(Str.p("الربط اتلغى، جرب تاني", "Linking canceled, try again"))
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        verifyDone = true
                        main.removeCallbacks(timeout)
                        main.post {
                            cleanupVerify()
                            setPinError(Str.p("حصلت مشكلة في التأكد، جرب تاني", "Something went wrong while verifying, try again"))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            main.removeCallbacks(timeout)
            cleanupVerify()
            setPinError(Str.p("حصلت مشكلة في التأكد، جرب تاني", "Something went wrong while verifying, try again"))
        }
    }

    // Re-sends the activation payload and re-runs /signin for the saved
    // fake account, in case the console cleared the fake sign-in state.
    private fun reconfirmFakeSignIn() {
        val ip = Prefs.hostIp(this)
        val uid = Prefs.fakeUserId(this)
        if (ip.isEmpty()) {
            Toast.makeText(this, Str.p("افتح التفعيل الوهمي الأول واكتب IP الجهاز هناك", "Open Fake Activation first and enter the console IP there"), Toast.LENGTH_LONG).show()
            return
        }
        if (uid == 0L) {
            Toast.makeText(this, Str.p("افتح التفعيل الوهمي ودوس على الحساب مرة واحدة عشان يتحفظ، وبعدين جرب تاني", "Open Fake Activation and tap the account once so it gets saved, then try again"), Toast.LENGTH_LONG).show()
            return
        }
        reconfirmBtn.isEnabled = false
        reconfirmBtn.text = Str.p("بيأكد التسجيل الوهمي...", "Re-confirming fake sign-in...")
        Thread {
            val up = FakePayload.ensureUp(this, ip)
            val (ok, msg) = if (up) FakePayload.signin(ip, uid) else false to ""
            if (up) FakePayload.exit(ip)
            runOnUiThread {
                reconfirmBtn.isEnabled = true
                reconfirmBtn.text = Str.p("تأكيد التسجيل الوهمي", "Re-confirm Fake Sign-in")
                if (ok) {
                    AlertDialog.Builder(this)
                        .setTitle(Str.p("✓ التسجيل الوهمي اتأكد", "✓ Fake sign-in re-confirmed"))
                        .setMessage(Str.p(
                            "اعمل إعادة تشغيل (Restart) للـPS5، وبعدين:\n١) سجّل دخول بنفس الحساب «${Prefs.fakeAccountName(this)}» وخليه قدامك على الشاشة.\n٢) الإعدادات ← النظام ← Remote Play وشغّل «Enable Remote Play» (ON) للحساب ده.\n٣) خد PIN جديد من ربط جهاز وحطه في الخطوة ٢ ودوس حفظ، وبعدين بدء البث.\n\nوخلي الجهاز أوفلاين أو احجب PSN (nanoDNS) عشان التسجيل الوهمي ميتلغيش تاني.",
                            "Restart the PS5, then:\n1) Log in with the same account “${Prefs.fakeAccountName(this)}” and keep it on screen.\n2) Settings → System → Remote Play and turn ON “Enable Remote Play” for this account.\n3) Get a NEW PIN from Link Device, enter it in Step 2, tap Save, then Start Stream.\n\nKeep the console offline or block PSN (nanoDNS) so the fake sign-in isn't cleared again."
                        ))
                        .setPositiveButton(Str.p("تمام", "OK"), null)
                        .show()
                } else {
                    Toast.makeText(this, Str.p(
                        "التأكيد فشل${if (msg.isNotEmpty()) ": $msg" else ""}. اتأكد إن الجيلبريك شغال وPayload Manager مفتوح والتليفون على نفس الشبكة",
                        "Re-confirm failed${if (msg.isNotEmpty()) ": $msg" else ""}. Make sure the jailbreak is running, Payload Manager is open, and the phone is on the same network"
                    ), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun refresh() {
        if (::loginStatus.isInitialized) {
            if (Prefs.signedIn(this)) {
                loginStatus.text = Str.p("✓ تم تسجيل الدخول", "✓ Signed in")
                loginStatus.setTextColor(Color.parseColor("#2ED573"))
                loginBtn.text = Str.p("تم تسجيل الدخول ✓", "Signed in ✓")
                loginBtn.background = Ui.gradientBg(this, intArrayOf(Color.parseColor("#2ED573"), Color.parseColor("#1E9E57")), 14)
                loginBtn.setTextColor(Color.WHITE)
            } else {
                loginStatus.text = Str.p("✕ لم يتم تسجيل الدخول", "✕ Not signed in")
                loginStatus.setTextColor(Color.parseColor("#FF2E2E"))
                loginBtn.text = Str.p("تسجيل دخول PSN الثانوي", "Second PSN Sign-in")
                loginBtn.background = Ui.roundedBg(this, Ui.CARD2, 14, Ui.GOLD, 1)
                loginBtn.setTextColor(Color.WHITE)
            }
        }
        if (::fakeStatus.isInitialized) {
            if (Prefs.fakeActivated(this)) {
                fakeStatus.text = Str.p("✓ التفعيل الوهمي شغال", "✓ Fake activation active") +
                    (if (Prefs.fakeAccountName(this).isNotEmpty()) " — ${Prefs.fakeAccountName(this)}" else "")
                fakeStatus.setTextColor(Color.parseColor("#2ED573"))
            } else {
                fakeStatus.text = Str.p("التفعيل الوهمي: لسه", "Fake activation: not yet")
                fakeStatus.setTextColor(Color.parseColor("#FF2E2E"))
            }
        }
        if (::reconfirmBtn.isInitialized) {
            reconfirmBtn.visibility = if (Prefs.fakeActivated(this)) View.VISIBLE else View.GONE
        }
        val login = when {
            Prefs.fakeActivated(this) -> Str.p("الحساب: تفعيل وهمي ✓", "Account: fake-activated ✓")
            Prefs.signedIn(this) -> Str.p("الحساب الثانوي: متسجل ✓", "Second account: signed in ✓")
            else -> Str.p("الحساب الثانوي: لسه", "Second account: not yet")
        }
        val pin = when {
            Prefs.pinVerified(this) && Prefs.registKey(this).isNotEmpty() ->
                Str.p("الربط (Regist): متأكد ✓", "Pairing (Regist): verified ✓")
            Prefs.pin(this).isNotEmpty() ->
                Str.p("رقم الـPIN: محفوظ بس الربط مش متأكد — دوس حفظ", "PIN: stored but pairing not verified — tap Save")
            else -> Str.p("رقم الـPIN: لسه", "PIN: not yet")
        }
        val tv = when {
            MqttSender.tvOnline -> Str.p("التلفزيون: متصل ✓", "TV: connected ✓")
            MqttSender.connected -> Str.p("التلفزيون: مستني...", "TV: waiting...")
            else -> Str.p("التلفزيون: مش متصل", "TV: not connected")
        }
        // Diagnostic: masked account ID (last 4 chars) so a screenshot
        // shows exactly which account the pairing used.
        val acctB64 = Prefs.accountId(this)
        val diag = if (acctB64.length >= 4)
            Str.p("رقم الحساب المستخدم: …${acctB64.takeLast(4)}", "Account ID in use: …${acctB64.takeLast(4)}")
        else Str.p("رقم الحساب المستخدم: مفيش", "Account ID in use: none")
        status.text = "$login\n$pin\n$diag\n$tv"
    }

    override fun onBackPressed() {
        if (streamController?.running == true) {
            handoffToMain()
        } else {
            Nav.goHome(this)
        }
    }
}
