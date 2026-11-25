package com.merso.gametranslate

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.*
import com.metallic.chiaki.lib.*
import com.merso.gametranslate.Ui.dp

// Guided pairing wizard shown after the console-type guide:
// screen pairing card -> PS5 pairing card (PSN sign-in or fake
// sign-in, then PIN) -> straight into the stream.
class SetupWizardActivity : Activity() {

    private enum class Step { SCREEN, PS5 }
    private var step = Step.SCREEN

    private lateinit var codeInput: EditText
    private lateinit var screenStatus: TextView
    private lateinit var screenContinueBtn: Button

    private lateinit var signStatus: TextView
    private lateinit var signBtn: Button
    private lateinit var pinInput: EditText
    private lateinit var pinBtn: Button
    private lateinit var pinMsg: TextView
    private lateinit var streamBtn: Button

    private var verifyDiscovery: DiscoveryService? = null
    private var verifyRegist: Regist? = null
    private var verifyLock: WifiManager.MulticastLock? = null
    private var verifyDone = false

    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            if (step == Step.SCREEN) {
                refreshScreenStatus()
                if (MqttSender.tvOnline) {
                    showPs5Pair()
                    return
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        TvServer.init(this)
        TvServer.start()
        if (Prefs.code(this).length >= 4) MqttSender.connect(this)
        showScreenPair()
        handler.post(poll)
    }

    override fun onResume() {
        super.onResume()
        if (step == Step.PS5) refreshPs5()
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        cleanupVerify()
        super.onDestroy()
    }

    // ---------- Step 1: screen pairing ----------
    private fun showScreenPair() {
        step = Step.SCREEN
        val root = Ui.screenRoot(this)
        root.addView(Ui.header(
            this,
            Str.p("ربط الشاشة", "Screen Pairing"),
            Str.p("اكتب كود الربط اللي ظاهر على التلفزيون", "Enter the pairing code shown on the TV")
        ))

        val card = Ui.card(this)
        card.addView(Ui.cardTitle(this, Str.p("ربط الشاشة بالتليفون", "Pair the TV with the Phone")))
        card.addView(Ui.bodyText(this, Str.p("اكتب كود الربط اللي ظاهر على صفحة التلفزيون", "Enter the pairing code shown on the TV page")))
        codeInput = Ui.inputField(this, Str.p("مثال: 123456", "e.g. 123456"))
        codeInput.setText(Prefs.code(this))
        card.addView(codeInput)
        card.addView(Ui.primaryButton(this, Str.p("حفظ الكود وربط", "Save Code & Connect")).apply {
            setOnClickListener {
                val c = codeInput.text.toString().trim()
                if (c.length < 4) {
                    Toast.makeText(this@SetupWizardActivity, Str.p("اكتب كود الربط الأول", "Enter the pairing code first"), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                Prefs.setCode(this@SetupWizardActivity, c)
                MqttSender.disconnect()
                MqttSender.connect(this@SetupWizardActivity)
                screenContinueBtn.isEnabled = true
                Toast.makeText(this@SetupWizardActivity, Str.p("تم الحفظ، بيتصل بالتلفزيون...", "Saved, connecting to the TV..."), Toast.LENGTH_SHORT).show()
            }
        })
        card.addView(Ui.secondaryButton(this, Str.p("إلغاء الربط", "Unlink")).apply {
            setOnClickListener {
                Prefs.setCode(this@SetupWizardActivity, "")
                codeInput.setText("")
                MqttSender.disconnect()
                screenContinueBtn.isEnabled = false
                refreshScreenStatus()
                Toast.makeText(this@SetupWizardActivity, Str.p("اتلغى الربط ✓", "Unlinked ✓"), Toast.LENGTH_SHORT).show()
            }
        })
        screenStatus = Ui.bodyText(this, "", 14f, Ui.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }
        card.addView(screenStatus)
        val ip = TvServer.localIp(this)
        card.addView(Ui.bodyText(
            this,
            if (ip != null) Str.p("رقم الربط بتاعك: $ip:${TvServer.PORT}", "Your pairing address: $ip:${TvServer.PORT}")
            else Str.p("وصّل الموبايل بالواي فاي عشان يظهر رقم الربط", "Connect to Wi-Fi to show your pairing address"),
            15f, Ui.GOLD
        ).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
        card.addView(Ui.secondaryButton(this, Str.p("شرح ربط الشاشة", "Screen Pairing Guide")).apply {
            setOnClickListener {
                startActivity(Intent(this@SetupWizardActivity, GuideCardActivity::class.java).putExtra("guide", "screen"))
            }
        })
        root.addView(card)

        screenContinueBtn = Ui.primaryButton(this, Str.p("كمل لربط الـPS5", "Continue to PS5 Pairing")).apply {
            isEnabled = Prefs.code(this@SetupWizardActivity).length >= 4
            setOnClickListener { showPs5Pair() }
        }
        root.addView(screenContinueBtn)

        setContentView(ScrollView(this).apply { addView(root) })
        Ui.animateChildren(root)
        refreshScreenStatus()
    }

    private fun refreshScreenStatus() {
        if (!::screenStatus.isInitialized) return
        screenStatus.text = when {
            MqttSender.tvOnline -> Str.p("التلفزيون: متصل ✓", "TV: connected ✓")
            Prefs.code(this).length < 4 -> Str.p("لسه مفيش كود ربط", "No pairing code yet")
            MqttSender.connected -> Str.p("التلفزيون: مستني يظهر...", "TV: waiting to appear...")
            else -> Str.p("الكود محفوظ، بيتصل بالتلفزيون...", "Code saved, connecting to the TV...")
        }
    }

    // ---------- Step 2: PS5 pairing ----------
    private fun showPs5Pair() {
        step = Step.PS5
        val modded = Prefs.consoleType(this) == "modded"
        val root = Ui.screenRoot(this)
        root.addView(Ui.header(
            this,
            Str.p("ربط الـPS5", "PS5 Pairing"),
            if (modded) Str.p("التسجيل الوهمي وبعدين رقم PIN", "Fake sign-in, then the PIN code")
            else Str.p("تسجيل دخول PSN وبعدين رقم PIN", "PSN sign-in, then the PIN code")
        ))

        val card = Ui.card(this)
        if (modded) {
            card.addView(Ui.cardTitle(this, Str.p("التسجيل الوهمي", "Fake Sign-in")))
            card.addView(Ui.bodyText(this, Str.p("فعّل الحساب المحلي على الجهاز المعدل من جوه التطبيق، من غير سوني خالص", "Activate the local account on your modded console from inside the app, no Sony at all")))
        } else {
            card.addView(Ui.cardTitle(this, Str.p("تسجيل دخول PSN", "PSN Sign-in")))
            card.addView(Ui.bodyText(this, Str.p("سجّل دخول بحسابك الثانوي في صفحة سوني الرسمية جوه التطبيق", "Sign in with your second account on Sony's official page inside the app")))
        }
        signStatus = TextView(this).apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(2))
        }
        card.addView(signStatus)
        signBtn = Ui.secondaryButton(this, "").apply {
            setOnClickListener {
                if (modded) startActivity(Intent(this@SetupWizardActivity, FakeSignInActivity::class.java))
                else startActivity(Intent(this@SetupWizardActivity, SonyLoginActivity::class.java))
            }
        }
        card.addView(signBtn)
        card.addView(Ui.secondaryButton(this, Str.p("شرح ربط الـPS5", "PS5 Pairing Guide")).apply {
            setOnClickListener {
                startActivity(Intent(this@SetupWizardActivity, GuideCardActivity::class.java).putExtra("guide", "console"))
            }
        })

        card.addView(Ui.bodyText(
            this,
            Str.p("على الـPS5: الإعدادات ← النظام ← Remote Play ← ربط جهاز، واكتب الرقم هنا.\n⚠️ اتأكد الأول إن «Enable Remote Play» شغالة (ON) للحساب اللي داخل بيه — من سوفت 10.00 وطالع الإعداد ده لكل حساب لوحده، ولو مقفول البث هيترفض حتى لو الرقم اتحفظ.", "On your PS5: Settings → System → Remote Play → Link Device, then enter the code here.\n⚠️ First make sure “Enable Remote Play” is ON for the account you're signed in with — since firmware 10.00 this setting is per-user; if it is OFF the stream will be refused even if the code saves fine."),
            14f, Ui.SUB
        ).apply { setPadding(0, dp(14), 0, 0) })
        pinInput = Ui.inputField(this, Str.p("رقم PIN من الجهاز", "PIN from the console"))
        pinInput.setText(Prefs.pin(this))
        pinInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (Prefs.pinVerified(this@SetupWizardActivity) && s.toString().trim() != Prefs.pin(this@SetupWizardActivity)) {
                    Prefs.setPinVerified(this@SetupWizardActivity, false)
                    refreshPs5()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        card.addView(pinInput)
        pinBtn = Ui.secondaryButton(this, Str.p("حفظ الرقم وربط", "Save Code & Pair")).apply {
            setOnClickListener { verifyPin() }
        }
        card.addView(pinBtn)
        pinMsg = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#FF2E2E"))
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, dp(4), 0, 0)
        }
        card.addView(pinMsg)
        root.addView(card)

        streamBtn = Ui.primaryButton(this, Str.p("الخطوة ٣ — البث والترجمة", "Step 3 — Stream & Translate")).apply {
            setOnClickListener { goToStreamStep() }
        }
        root.addView(streamBtn)
        root.addView(Ui.secondaryButton(this, Str.p("رجوع لربط الشاشة", "Back to Screen Pairing")).apply {
            setOnClickListener { showScreenPair() }
        })

        setContentView(ScrollView(this).apply { addView(root) })
        Ui.animateChildren(root)
        refreshPs5()
    }

    private fun refreshPs5() {
        if (!::signStatus.isInitialized) return
        val modded = Prefs.consoleType(this) == "modded"
        if (modded) {
            if (Prefs.fakeActivated(this)) {
                signStatus.text = Str.p("✓ التفعيل الوهمي شغال", "✓ Fake activation active") +
                    (if (Prefs.fakeAccountName(this).isNotEmpty()) " — ${Prefs.fakeAccountName(this)}" else "")
                signStatus.setTextColor(Color.parseColor("#2ED573"))
                signBtn.text = Str.p("التفعيل الوهمي ✓", "Fake Activation ✓")
            } else {
                signStatus.text = Str.p("التفعيل الوهمي: لسه", "Fake activation: not yet")
                signStatus.setTextColor(Color.parseColor("#FF2E2E"))
                signBtn.text = Str.p("تفعيل وهمي (بدون تسجيل سوني)", "Fake Activation (no Sony sign-in)")
            }
        } else {
            if (Prefs.signedIn(this)) {
                signStatus.text = Str.p("✓ تم تسجيل الدخول", "✓ Signed in")
                signStatus.setTextColor(Color.parseColor("#2ED573"))
                signBtn.text = Str.p("تم تسجيل الدخول ✓", "Signed in ✓")
            } else {
                signStatus.text = Str.p("✕ لم يتم تسجيل الدخول", "✕ Not signed in")
                signStatus.setTextColor(Color.parseColor("#FF2E2E"))
                signBtn.text = Str.p("تسجيل دخول PSN الثانوي", "Second PSN Sign-in")
            }
        }
        val paired = Prefs.pinVerified(this) && Prefs.registKey(this).isNotEmpty()
        if (paired) {
            pinBtn.text = Str.p("تم الربط ✓", "Paired ✓")
            pinBtn.background = Ui.gradientBg(this, intArrayOf(Color.parseColor("#2ED573"), Color.parseColor("#1E9E57")), 14)
            pinBtn.setTextColor(Color.WHITE)
        } else {
            pinBtn.text = Str.p("حفظ الرقم وربط", "Save Code & Pair")
            pinBtn.background = Ui.roundedBg(this, Ui.CARD2, 14, Ui.GOLD, 1)
            pinBtn.setTextColor(Color.WHITE)
        }
        pinBtn.isEnabled = true
        streamBtn.visibility = if (paired) View.VISIBLE else View.GONE
    }

    private fun setPinError(msg: String) {
        pinBtn.text = Str.p("حفظ الرقم وربط", "Save Code & Pair")
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
            setPinError(
                if (Prefs.consoleType(this) == "modded")
                    Str.p("اعمل التفعيل الوهمي الأول من الزرار اللي فوق، وبعدين جرب تاني", "Run the fake activation first from the button above, then try again")
                else
                    Str.p("سجل دخول PSN الأول من الزرار اللي فوق، وبعدين جرب تاني", "Sign in to PSN first from the button above, then try again")
            )
            return
        }
        val accountId = try {
            Base64.decode(accountIdB64, Base64.DEFAULT)
        } catch (_: Exception) {
            setPinError(Str.p("رقم الحساب مش مظبوط، سجل دخول من جديد", "Account ID is invalid, sign in again"))
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
            verifyLock = wifi.createMulticastLock("merso-wizard-verify").apply {
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
                                    Prefs.setRegistKey(this@SetupWizardActivity, h.rpRegistKey.joinToString("") { "%02x".format(it) })
                                    Prefs.setMorning(this@SetupWizardActivity, h.rpKey.joinToString("") { "%02x".format(it) })
                                    Prefs.setHostIp(this@SetupWizardActivity, ip)
                                    Prefs.setPinVerified(this@SetupWizardActivity, true)
                                    main.post {
                                        cleanupVerify()
                                        goToStreamStep()
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

    // Pairing done: land on the Step 3 stream screen. The stream itself
    // is NOT auto-started — the user taps Start Stream there himself.
    private fun goToStreamStep() {
        startActivity(Intent(this, RemotePlayActivity::class.java).apply {
            putExtra("to_stream_step", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    override fun onBackPressed() {
        if (step == Step.PS5) {
            showScreenPair()
        } else {
            finish()
        }
    }
}
