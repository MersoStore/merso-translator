package com.merso.gametranslate

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.widget.*
import com.merso.gametranslate.Ui.dp

class MainActivity : Activity() {

    private lateinit var codeInput: EditText
    private lateinit var statusText: TextView
    private lateinit var pairBtn: Button
    private lateinit var miniBox: FrameLayout
    private lateinit var miniTexture: TextureView
    private lateinit var miniClose: Button
    private lateinit var tabBar: View
    private var inPip = false
    private var suppressPip = false
    private var pairSavedAt: Long = 0
    private var pairState: Int = -1
    private lateinit var translateScroll: ScrollView
    private lateinit var guideScroll: ScrollView
    private lateinit var settingsScroll: ScrollView
    private lateinit var howtoScroll: ScrollView
    private lateinit var telegramScroll: ScrollView
    private lateinit var supportScroll: ScrollView
    private var howtoWeb: android.webkit.WebView? = null
    private var howtoLoaded = false
    private lateinit var step1Body: LinearLayout
    private lateinit var step1Chevron: TextView
    private lateinit var step1Check: TextView
    private lateinit var step2Card: LinearLayout
    private lateinit var step3Card: LinearLayout
    private var step3Shown = false
    private var step1Open = true
    private var step2Shown = false
    private var step1CollapsedByTv = false
    private var currentTab = 0
    private val tabItems = mutableListOf<LinearLayout>()
    private val tabIcons = mutableListOf<TextView>()
    private val tabLabels = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try {
            buildUi()
            Updater.checkOnLaunch(this)
            val tab = intent?.getIntExtra("tab", -1) ?: -1
            if (tab >= 0) selectTab(tab)
        } catch (e: Throwable) {
            val tv = TextView(this).apply {
                text = "خطأ: ${e.javaClass.simpleName}: ${e.message}"
                setTextColor(Color.RED)
                setBackgroundColor(Color.WHITE)
                textSize = 16f
                setPadding(32, 32, 32, 32)
            }
            setContentView(tv)
        }
    }

    private fun stepHeader(num: Int, title: String, onClick: () -> Unit): Pair<LinearLayout, Pair<TextView, TextView>> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener { onClick() }
        }
        row.addView(TextView(this).apply {
            text = "$num"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(Ui.GOLD))
            }
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(12) }
        })
        row.addView(TextView(this).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(Ui.GOLD))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val check = TextView(this).apply {
            text = "✓"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#2ED573"))
            visibility = View.GONE
            setPadding(dp(6), 0, dp(6), 0)
        }
        row.addView(check)
        val chevron = TextView(this).apply {
            text = "▼"
            textSize = 16f
            setTextColor(Color.parseColor(Ui.MUTED))
        }
        row.addView(chevron)
        return row to (check to chevron)
    }

    private fun toggleStep1(open: Boolean) {
        step1Open = open
        step1Body.visibility = if (open) View.VISIBLE else View.GONE
        step1Chevron.text = if (open) "▲" else "▼"
        if (open) Ui.animateChildren(step1Body)
    }

    private fun buildUi() {
        val frame = FrameLayout(this)

        // ---------- Translate tab (steps) ----------
        val root = Ui.screenRoot(this)
        root.setPadding(dp(18), dp(24), dp(18), dp(110))
        root.addView(buildBanner())

        // Step 1: screen setup guide
        val stepSettingsCard = Ui.card(this)
        val (hs, _) = stepHeader(1, Str.p("إعداد الشاشة", "Screen Setup")) { openGuide(0) }
        stepSettingsCard.addView(hs)
        stepSettingsCard.addView(Ui.bodyText(this, Str.p("شرح ربط الشاشة بالتليفون خطوة بخطوة", "Step-by-step guide to pair the screen with the phone"), 14f, Ui.MUTED))
        root.addView(stepSettingsCard)

        // Step 2: TV pairing
        val step1Card = Ui.card(this)
        val (h1, marks1) = stepHeader(2, Str.p("ربط الشاشة", "Screen Pairing")) {}
        step1Check = marks1.first
        step1Chevron = marks1.second
        step1Chevron.visibility = View.GONE
        step1Card.addView(h1)
        step1Body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        step1Body.addView(Ui.bodyText(this, Str.p("اكتب كود الربط اللي ظاهر على صفحة التلفزيون", "Enter the pairing code shown on the TV page")))
        codeInput = Ui.inputField(this, Str.p("مثال: 123456", "e.g. 123456"))
        codeInput.setText(Prefs.code(this))
        step1Body.addView(codeInput)
        codeInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (s.toString().trim() != Prefs.code(this@MainActivity)) {
                    pairSavedAt = 0
                    pairState = -1
                }
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        pairBtn = Ui.primaryButton(this, Str.p("حفظ الكود وربط", "Save Code & Connect")).apply {
            setOnClickListener {
                val c = codeInput.text.toString().trim()
                if (c.length < 4) {
                    Toast.makeText(this@MainActivity, Str.p("اكتب كود الربط الأول", "Enter the pairing code first"), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                Prefs.setCode(this@MainActivity, c)
                pairSavedAt = System.currentTimeMillis()
                pairState = -1
                MqttSender.disconnect()
                MqttSender.connect(this@MainActivity)
                Toast.makeText(this@MainActivity, Str.p("تم الحفظ، بيتصل بالتلفزيون...", "Saved, connecting to the TV..."), Toast.LENGTH_SHORT).show()
                showStep3()
            }
        }
        step1Body.addView(pairBtn)
        step1Body.addView(Ui.secondaryButton(this, Str.p("إلغاء الربط", "Unlink")).apply {
            setOnClickListener {
                Prefs.setCode(this@MainActivity, "")
                codeInput.setText("")
                pairSavedAt = 0
                pairState = -1
                MqttSender.disconnect()
                step1Check.visibility = View.GONE
                step1CollapsedByTv = false
                setPairBtn(0)
                statusText.text = Str.p("اتلغى الربط", "Unlinked")
                Toast.makeText(this@MainActivity, Str.p("اتلغى الربط ✓", "Unlinked ✓"), Toast.LENGTH_SHORT).show()
            }
        })
        statusText = Ui.bodyText(this, "", 14f, Ui.MUTED).apply {
            gravity = Gravity.CENTER
            text = if (Prefs.code(this@MainActivity).length < 4) Str.p("لسه مفيش كود ربط", "No pairing code yet") else Str.p("الكود محفوظ", "Code saved")
        }
        step1Body.addView(statusText)
        val ip = TvServer.localIp(this)
        step1Body.addView(Ui.bodyText(
            this,
            if (ip != null) Str.p("رقم الربط بتاعك: $ip:${TvServer.PORT}", "Your pairing address: $ip:${TvServer.PORT}")
            else Str.p("وصّل الموبايل بالواي فاي عشان يظهر رقم الربط", "Connect to Wi-Fi to show your pairing address"),
            15f, Ui.GOLD
        ).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
        step1Card.addView(step1Body)
        root.addView(step1Card)

        // Step 3: PS5 setup guide (appears after pairing)
        step3Card = Ui.card(this)
        val (h3, _) = stepHeader(3, Str.p("إعداد جهاز PS5", "PS5 Setup")) {
            openGuide(1)
            showStep4()
        }
        step3Card.addView(h3)
        step3Card.addView(Ui.bodyText(this, Str.p("شرح إعداد الـPS5 للترجمة", "How to set up the PS5 for translation"), 14f, Ui.MUTED))
        root.addView(step3Card)

        // Step 4: Remote Play (appears after PS5 setup step)
        step2Card = Ui.card(this)
        val (h2, _) = stepHeader(4, Str.p("الريموت بلاي", "Remote Play")) {}
        step2Card.addView(h2)
        step2Card.addView(Ui.primaryButton(this, Str.p("ترجمة عن طريق Remote Play", "Translate via Remote Play")).apply {
            setOnClickListener {
                suppressPip = true
                startActivity(Intent(this@MainActivity, RemotePlayActivity::class.java))
            }
        })
        root.addView(step2Card)

        TvServer.init(this)
        TvServer.start()

        root.addView(Ui.bodyText(this, Str.p("من قناة Merso Gaming", "From Merso Gaming channel"), 13f, Ui.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        })

        val ytRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(6))
            isClickable = true
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/@mersostore")))
                } catch (_: Exception) {}
            }
        }
        ytRow.addView(TextView(this).apply {
            text = "▶"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = Ui.roundedBg(this@MainActivity, "#FF0000", 8)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
        })
        ytRow.addView(Ui.bodyText(this, Str.p("تابعنا على اليوتيوب", "Follow us on YouTube"), 15f, Ui.TEXT))
        root.addView(ytRow)

        translateScroll = ScrollView(this).apply { addView(root) }
        frame.addView(translateScroll)

        // Initial step state — all 4 steps always visible
        showStep3(instant = true)
        showStep4(instant = true)
        toggleStep1(true)

        // ---------- Guide tab ----------
        val guideRoot = GuideUi.build(this)
        guideRoot.setPadding(dp(18), dp(24), dp(18), dp(110))
        guideScroll = ScrollView(this).apply {
            addView(guideRoot)
            visibility = View.GONE
        }
        frame.addView(guideScroll)

        // ---------- Settings tab ----------
        val settingsRoot = buildSettings()
        settingsScroll = ScrollView(this).apply {
            addView(settingsRoot)
            visibility = View.GONE
        }
        frame.addView(settingsScroll)

        // ---------- How to Use tab (YouTube tutorial) ----------
        val howtoRoot = Ui.screenRoot(this)
        howtoRoot.setPadding(dp(18), dp(24), dp(18), dp(110))
        val howtoCard = Ui.card(this)
        howtoCard.addView(Ui.cardTitle(this, Str.p("شرح الاستخدام", "How to Use")))
        howtoCard.addView(Ui.bodyText(this, Str.p("شاهد شرح طريقة استخدام التطبيق خطوة بخطوة في الفيديو ده:", "Watch the step-by-step tutorial for using the app in this video:")))
        val web = android.webkit.WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(210)
            ).apply { topMargin = dp(10); bottomMargin = dp(10) }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = android.webkit.WebViewClient()
            webChromeClient = android.webkit.WebChromeClient()
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        howtoWeb = web
        howtoCard.addView(web)
        howtoCard.addView(Ui.secondaryButton(this, Str.p("▶ افتح الفيديو في يوتيوب", "▶ Open the video on YouTube")).apply {
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://youtu.be/4Vu9-thSXhs")))
                } catch (_: Exception) {}
            }
        })
        howtoRoot.addView(howtoCard)
        howtoScroll = ScrollView(this).apply {
            addView(howtoRoot)
            visibility = View.GONE
        }
        frame.addView(howtoScroll)

        // ---------- Telegram tab ----------
        val tgRoot = Ui.screenRoot(this)
        tgRoot.setPadding(dp(18), dp(24), dp(18), dp(110))
        tgRoot.addView(Ui.header(this, Str.p("✈️ تليجرام", "✈️ Telegram"), Str.p("تابعنا واتكلم معانا", "Follow us and chat with us")))
        val tgCard = Ui.card(this)
        tgCard.addView(Ui.cardTitle(this, Str.p("روابط التليجرام", "Telegram Links")))
        tgCard.addView(Ui.primaryButton(this, Str.p("دخول قناة التليجرام", "Join Telegram Channel")).apply {
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/mersogaming1")))
                } catch (_: Exception) {}
            }
        })
        tgCard.addView(Ui.bodyText(this, Str.p("أخبار وتحديثات القناة والتطبيق أول بأول", "Channel and app news and updates as they happen"), 14f, Ui.MUTED))
        tgCard.addView(Ui.primaryButton(this, Str.p("دخول شات التليجرام", "Join Telegram Chat")).apply {
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/mersogamingchat")))
                } catch (_: Exception) {}
            }
        })
        tgCard.addView(Ui.bodyText(this, Str.p("نقاش ومساعدة مع باقي المتابعين", "Discussion and help with other followers"), 14f, Ui.MUTED))
        tgRoot.addView(tgCard)
        telegramScroll = ScrollView(this).apply {
            addView(tgRoot)
            visibility = View.GONE
        }
        frame.addView(telegramScroll)

        // ---------- Support tab ----------
        val supRoot = Ui.screenRoot(this)
        supRoot.setPadding(dp(18), dp(24), dp(18), dp(110))
        supRoot.addView(Ui.header(this, Str.p("❤ دعم المطوّر", "❤ Support the Developer"), Str.p("التطبيق مجاني وهيضل مجاني", "The app is free and will stay free")))
        val supCard = Ui.card(this)
        supCard.addView(Ui.cardTitle(this, Str.p("ادعم استمرار التطبيق", "Support the app")))
        supCard.addView(Ui.bodyText(this, Str.p(
            "Merso Translator مجاني بالكامل ومن غير اشتراك، وهيضل كده. لو التطبيق عجبك وساعدك تلعب بالعربي، دعمك بيساعدنا نكمل تطويره ونضيف مميزات جديدة. شكرًا ليك ❤",
            "Merso Translator is completely free with no subscription, and it will stay that way. If the app helped you play in Arabic, your support helps us keep developing it and adding new features. Thank you ❤"
        ), 15f, Ui.TEXT))
        supCard.addView(Ui.primaryButton(this, Str.p("❤ صفحة الدعم", "❤ Support Page")).apply {
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://creators.sa/mersogaming")))
                } catch (_: Exception) {}
            }
        })
        supCard.addView(Ui.bodyText(this, Str.p("ادعم بمبلغ بسيط من صفحة الدعم", "Support with a small amount via the support page"), 14f, Ui.MUTED))
        supCard.addView(Ui.primaryButton(this, Str.p("الاشتراك في القناة", "Subscribe to the Channel")).apply {
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/@mersostore?sub_confirmation=1")))
                } catch (_: Exception) {}
            }
        })
        supCard.addView(Ui.bodyText(this, Str.p("اشتراكك في القناة دعم كبير ومجاني", "Subscribing is a big, free way to support"), 14f, Ui.MUTED))
        supRoot.addView(supCard)
        supportScroll = ScrollView(this).apply {
            addView(supRoot)
            visibility = View.GONE
        }
        frame.addView(supportScroll)

        // ---------- Bottom tab bar ----------
        val bar = LinearLayout(this).apply {
            tabBar = this
            orientation = LinearLayout.HORIZONTAL
            background = Ui.roundedBg(this@MainActivity, "#1C1C1C", 40, "#333333", 1)
            setPadding(dp(6), dp(5), dp(6), dp(5))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply { setMargins(dp(24), 0, dp(24), dp(12)) }
            elevation = dp(8).toFloat()
        }
        val tabs = listOf(
            "💬" to Str.p("ترجمة", "Translate"),
            "📖" to Str.p("دليل", "Guide"),
            "⚙️" to Str.p("إعدادات", "Settings")
        )
        for ((i, t) in tabs.withIndex()) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(5), dp(4), dp(5))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { selectTab(i) }
            }
            val icon = TextView(this).apply {
                text = t.first
                textSize = 18f
                gravity = Gravity.CENTER
            }
            val label = TextView(this).apply {
                text = t.second
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            }
            item.addView(icon)
            item.addView(label)
            tabItems.add(item)
            tabIcons.add(icon)
            tabLabels.add(label)
            bar.addView(item)
        }
        val ytItem = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/@mersostore")))
                } catch (_: Exception) {}
            }
        }
        ytItem.addView(TextView(this).apply {
            text = "▶"
            textSize = 18f
            setTextColor(Color.parseColor("#FF2E2E"))
            gravity = Gravity.CENTER
        })
        ytItem.addView(TextView(this).apply {
            text = Str.p("يوتيوب", "YouTube")
            textSize = 11f
            setTextColor(Color.parseColor(Ui.SUB))
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        })
        bar.addView(ytItem)
        val howtoItem = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { selectTab(3) }
        }
        val howtoIcon = TextView(this).apply {
            text = "🎬"
            textSize = 18f
            gravity = Gravity.CENTER
        }
        val howtoLabel = TextView(this).apply {
            text = Str.p("شرح الاستخدام", "How to Use")
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        }
        howtoItem.addView(howtoIcon)
        howtoItem.addView(howtoLabel)
        tabItems.add(howtoItem)
        tabIcons.add(howtoIcon)
        tabLabels.add(howtoLabel)
        bar.addView(howtoItem)
        val tgItem = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { selectTab(4) }
        }
        val tgIcon = TextView(this).apply {
            text = "✈️"
            textSize = 18f
            gravity = Gravity.CENTER
        }
        val tgLabel = TextView(this).apply {
            text = Str.p("تليجرام", "Telegram")
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        }
        tgItem.addView(tgIcon)
        tgItem.addView(tgLabel)
        tabItems.add(tgItem)
        tabIcons.add(tgIcon)
        tabLabels.add(tgLabel)
        bar.addView(tgItem)
        val supItem = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { selectTab(5) }
        }
        val supIcon = TextView(this).apply {
            text = "❤"
            textSize = 18f
            setTextColor(Color.parseColor("#FF2E4D"))
            gravity = Gravity.CENTER
        }
        val supLabel = TextView(this).apply {
            text = Str.p("دعم", "Support")
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        }
        supItem.addView(supIcon)
        supItem.addView(supLabel)
        tabItems.add(supItem)
        tabIcons.add(supIcon)
        tabLabels.add(supLabel)
        bar.addView(supItem)
        frame.addView(bar)

        // Mini player: keeps the stream visible here after leaving Remote Play
        miniBox = FrameLayout(this).apply {
            visibility = View.GONE
            background = Ui.roundedBg(this@MainActivity, "#000000", 12, Ui.GOLD, 2)
            layoutParams = FrameLayout.LayoutParams(dp(168), dp(95), Gravity.BOTTOM or Gravity.END)
                .apply { bottomMargin = dp(92); marginEnd = dp(12) }
        }
        miniTexture = TextureView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            var downX = 0f
            var downY = 0f
            var startTx = 0f
            var startTy = 0f
            var moved = false
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX; downY = e.rawY
                        startTx = miniBox.translationX; startTy = miniBox.translationY
                        moved = false
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downX
                        val dy = e.rawY - downY
                        if (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12) moved = true
                        if (moved) {
                            val parent = miniBox.parent as? View
                            val pw = parent?.width ?: 0
                            val ph = parent?.height ?: 0
                            val minTx = -miniBox.left.toFloat()
                            val maxTx = (pw - miniBox.right).toFloat()
                            val minTy = -miniBox.top.toFloat()
                            val maxTy = (ph - miniBox.bottom).toFloat()
                            miniBox.translationX = (startTx + dx).coerceIn(minTx, maxOf(minTx, maxTx))
                            miniBox.translationY = (startTy + dy).coerceIn(minTy, maxOf(minTy, maxTy))
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            suppressPip = true
                            startActivity(Intent(this@MainActivity, RemotePlayActivity::class.java))
                        }
                        true
                    }
                    else -> true
                }
            }
        }
        miniBox.addView(miniTexture)
        miniClose = Button(this).apply {
            text = "✕"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(0, 0, 0, 0)
            layoutParams = FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP or Gravity.END)
            setOnClickListener {
                StreamHolder.controller?.stop()
                StreamHolder.controller = null
                miniBox.visibility = View.GONE
            }
        }
        miniBox.addView(miniClose)
        frame.addView(miniBox)

        setContentView(frame)
        selectTab(0)
        Ui.animateChildren(root)

        MqttSender.connect(this)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val updater = object : Runnable {
            override fun run() {
                val codeLen = Prefs.code(this@MainActivity).length
                val pairTimedOut = pairSavedAt > 0 && System.currentTimeMillis() - pairSavedAt > 20000
                when {
                    codeLen < 4 -> {
                        statusText.text = Str.p("لسه مفيش كود ربط", "No pairing code yet")
                        statusText.setTextColor(Color.parseColor(Ui.MUTED))
                        setPairBtn(0)
                    }
                    MqttSender.tvOnline -> {
                        statusText.text = Str.p("تم الاتصال بالتلفزيون ✓", "Connected to the TV ✓")
                        statusText.setTextColor(Color.parseColor("#2ED573"))
                        setPairBtn(1)
                    }
                    MqttSender.connected && pairTimedOut -> {
                        statusText.text = Str.p("الكود غلط؟ عيد الكود تاني واتأكد إنه نفس اللي ظاهر على التلفزيون", "Wrong code? Enter it again and make sure it matches the TV")
                        statusText.setTextColor(Color.parseColor("#FF2E2E"))
                        setPairBtn(2)
                    }
                    MqttSender.connected -> {
                        statusText.text = Str.p("متصل، مستني التلفزيون...", "Connected, waiting for the TV...")
                        statusText.setTextColor(Color.parseColor(Ui.MUTED))
                        setPairBtn(0)
                    }
                    MqttSender.lastError.isNotEmpty() -> {
                        statusText.text = Str.p("مش عارف يتصل: ${MqttSender.lastError}", "Can't connect: ${MqttSender.lastError}")
                        statusText.setTextColor(Color.parseColor("#FF2E2E"))
                        setPairBtn(0)
                    }
                    else -> {
                        statusText.text = Str.p("بيحاول يتصل...", "Trying to connect...")
                        statusText.setTextColor(Color.parseColor(Ui.MUTED))
                        setPairBtn(0)
                    }
                }
                if (Prefs.code(this@MainActivity).length >= 4) showStep3(instant = true)
                step1Check.visibility = if (MqttSender.tvOnline) View.VISIBLE else View.GONE
                handler.postDelayed(this, 1500)
            }
        }
        handler.post(updater)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
        }
    }

    override fun onResume() {
        super.onResume()
        suppressPip = false
        val c = StreamHolder.controller
        if (c != null && c.running && ::miniBox.isInitialized) {
            miniBox.visibility = View.VISIBLE
            c.onStatus = { }
            c.onCaption = { }
            c.onDebug = { }
            c.onLoginPin = { _, _ -> }
            c.attach(miniTexture)
            try { setPictureInPictureParams(pipParams()) } catch (_: Exception) {}
        } else if (::miniBox.isInitialized) {
            miniBox.visibility = View.GONE
        }
    }

    override fun onPause() {
        super.onPause()
        if (!inPip && !isInPictureInPictureMode) StreamHolder.controller?.detach()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!suppressPip && StreamHolder.controller?.running == true) enterPip()
    }

    override fun onStop() {
        super.onStop()
        // PiP window closed by the user: stop the stream.
        if (inPip) {
            inPip = false
            StreamHolder.controller?.stop()
            StreamHolder.controller = null
            if (::miniBox.isInitialized) miniBox.visibility = View.GONE
        }
    }

    private fun pipParams(): android.app.PictureInPictureParams {
        val builder = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(16, 9))
        if (android.os.Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(true)
        if (::miniTexture.isInitialized) {
            val rect = android.graphics.Rect()
            if (miniTexture.getGlobalVisibleRect(rect)) builder.setSourceRectHint(rect)
        }
        return builder.build()
    }

    private fun enterPip(): Boolean {
        inPip = true
        return try {
            val ok = enterPictureInPictureMode(pipParams())
            if (!ok) inPip = false
            ok
        } catch (_: Exception) {
            inPip = false
            false
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        try {
            if (isInPictureInPictureMode) {
                StreamHolder.controller?.attach(miniTexture)
                translateScroll.visibility = View.GONE
                guideScroll.visibility = View.GONE
                settingsScroll.visibility = View.GONE
                telegramScroll.visibility = View.GONE
                supportScroll.visibility = View.GONE
                tabBar.visibility = View.GONE
                miniClose.visibility = View.GONE
                miniBox.translationX = 0f
                miniBox.translationY = 0f
                miniBox.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                )
            } else {
                tabBar.visibility = View.VISIBLE
                miniClose.visibility = View.VISIBLE
                miniBox.layoutParams = FrameLayout.LayoutParams(dp(168), dp(95), Gravity.BOTTOM or Gravity.END)
                    .apply { bottomMargin = dp(92); marginEnd = dp(12) }
                selectTab(currentTab)
            }
        } catch (_: Exception) {}
    }

    private fun setPairBtn(state: Int) {
        if (!::pairBtn.isInitialized || state == pairState) return
        pairState = state
        when (state) {
            1 -> {
                pairBtn.text = Str.p("تم الاتصال ✓", "Connected ✓")
                pairBtn.background = Ui.gradientBg(this, intArrayOf(Color.parseColor("#2ED573"), Color.parseColor("#1E9E57")), 14)
            }
            2 -> {
                pairBtn.text = Str.p("عيد الكود تاني", "Enter the Code Again")
                pairBtn.background = Ui.gradientBg(this, intArrayOf(Color.parseColor("#FF2E2E"), Color.parseColor("#B00020")), 14)
            }
            else -> {
                pairBtn.text = Str.p("حفظ الكود وربط", "Save Code & Connect")
                pairBtn.background = Ui.gradientBg(this, intArrayOf(Color.parseColor("#E00000"), Color.parseColor("#8B0000")), 14)
            }
        }
        pairBtn.setTextColor(Color.WHITE)
    }

    private fun showStep3(instant: Boolean = false) {
        if (step3Shown) return
        step3Shown = true
        step3Card.visibility = View.VISIBLE
        if (!instant) {
            step3Card.alpha = 0f
            step3Card.translationY = 60f
            step3Card.animate().alpha(1f).translationY(0f).setDuration(450).start()
        }
    }

    private fun showStep4(instant: Boolean = false) {
        if (step2Shown) return
        step2Shown = true
        step2Card.visibility = View.VISIBLE
        if (!instant) {
            step2Card.alpha = 0f
            step2Card.translationY = 60f
            step2Card.animate().alpha(1f).translationY(0f).setDuration(450).start()
        }
    }

    private fun openGuide(section: Int) {
        guideScroll.removeAllViews()
        val g = GuideUi.build(this, section)
        g.setPadding(dp(18), dp(24), dp(18), dp(110))
        guideScroll.addView(g)
        selectTab(1)
    }

    // ---------- Settings ----------

    private fun segmented(options: List<String>, selectedIndex: Int, onPick: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.roundedBg(this@MainActivity, Ui.CARD2, 12, "#2E2E2E", 1)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6); bottomMargin = dp(6) }
        }
        val buttons = mutableListOf<Button>()
        var current = selectedIndex
        fun style() {
            for ((i, b) in buttons.withIndex()) {
                if (i == current) {
                    b.background = Ui.roundedBg(this@MainActivity, Ui.GOLD, 9)
                    b.setTextColor(Color.WHITE)
                } else {
                    b.background = null
                    b.setTextColor(Color.parseColor(Ui.SUB))
                }
            }
        }
        for ((i, opt) in options.withIndex()) {
            val b = Button(this).apply {
                text = opt
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(4), dp(9), dp(4), dp(9))
                setOnClickListener {
                    current = i
                    style()
                    onPick(i)
                }
            }
            buttons.add(b)
            row.addView(b)
        }
        // initial style uses the passed index
        for ((i, b) in buttons.withIndex()) {
            if (i == selectedIndex) {
                b.background = Ui.roundedBg(this, Ui.GOLD, 9)
                b.setTextColor(Color.WHITE)
            } else {
                b.background = null
                b.setTextColor(Color.parseColor(Ui.SUB))
            }
        }
        return row
    }

    private fun buildBanner(): FrameLayout {
        val banner = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#050505"),
                    Color.parseColor("#3D0606"),
                    Color.parseColor("#050505")
                )
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(132)
            ).apply { setMargins(-dp(18), -dp(24), -dp(18), dp(14)) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), 0, dp(22), 0)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val logo = ImageView(this).apply {
            val id = resources.getIdentifier("logo", "drawable", packageName)
            if (id != 0) setImageResource(id)
            layoutParams = LinearLayout.LayoutParams(dp(92), dp(92)).apply { marginEnd = dp(16) }
        }
        row.addView(logo)
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(this).apply {
            text = Str.p("مرسو للترجمة", "Merso Translator")
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        texts.addView(TextView(this).apply {
            text = Str.p("ترجمة إنجليزي لعربي على التلفزيون وانت بتلعب", "Translate your games on the TV while you play")
            textSize = 14f
            setTextColor(Color.parseColor(Ui.SUB))
            setPadding(0, dp(4), 0, 0)
        })
        row.addView(texts)
        banner.addView(row)
        return banner
    }

    private fun buildSettings(): LinearLayout {
        val sRoot = Ui.screenRoot(this)
        sRoot.setPadding(dp(18), dp(24), dp(18), dp(110))
        sRoot.addView(Ui.header(this, Str.p("⚙️ الإعدادات", "⚙️ Settings"), Str.p("ظبط البرنامج على مزاجك", "Make the app yours")))

        // Subtitle area
        val areaCard = Ui.card(this)
        areaCard.addView(Ui.cardTitle(this, Str.p("منطقة الترجمة", "Subtitle Area")))
        var selectedBox = 0
        val preview = CropPreviewView(this) { if (::miniTexture.isInitialized) miniTexture else null }
        preview.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(190)
        ).apply { topMargin = dp(6); bottomMargin = dp(6) }
        areaCard.addView(preview)

        var edge = 1 // 0=left 1=top 2=right 3=bottom
        val edges = listOf(
            Str.p("شمال", "Left"), Str.p("فوق", "Top"), Str.p("يمين", "Right"), Str.p("تحت", "Bottom")
        )
        val seek = SeekBar(this).apply { max = 100 }
        val pctLabel = Ui.bodyText(this, "", 15f, Ui.TEXT)
        var pauseBtn: Button? = null
        var boxSizeContainer: LinearLayout? = null
        fun refreshBoxSizeRow() {
            val c = boxSizeContainer ?: return
            c.removeAllViews()
            val cur = Prefs.boxSize(this@MainActivity, selectedBox)
            val idx = when (cur) { "S" -> 1; "M" -> 2; "L" -> 3; "H" -> 4; else -> 0 }
            c.addView(segmented(
                listOf(
                    Str.p("عام", "Global"), Str.p("صغير", "Small"), Str.p("عادي", "Normal"),
                    Str.p("كبير", "Large"), Str.p("ضخم", "Huge")
                ), idx
            ) { i ->
                Prefs.setBoxSize(this@MainActivity, selectedBox, listOf(null, "S", "M", "L", "H")[i])
            })
        }
        fun refreshSeek() {
            val boxes = Prefs.crops(this@MainActivity)
            selectedBox = selectedBox.coerceIn(0, boxes.lastIndex)
            preview.selectedIndex = selectedBox
            val c = boxes[selectedBox]
            val v = (c[edge] * 100).toInt()
            seek.progress = v
            pctLabel.text = "$v%"
            preview.invalidate()
            pauseBtn?.text = if (selectedBox in Prefs.pausedBoxes(this@MainActivity))
                Str.p("▶ تشغيل ترجمة المربع", "▶ Resume Box Translation")
            else
                Str.p("⏸ إيقاف ترجمة المربع", "⏸ Pause Box Translation")
            refreshBoxSizeRow()
        }
        areaCard.addView(segmented(edges, edge) { i ->
            edge = i
            refreshSeek()
        })
        val seekRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        seek.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        seekRow.addView(seek)
        pctLabel.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(10) }
        seekRow.addView(pctLabel)
        areaCard.addView(seekRow)
        seek.setOnTouchListener { v, e ->
            if (e.action == android.view.MotionEvent.ACTION_DOWN) {
                v.parent?.requestDisallowInterceptTouchEvent(true)
            }
            false
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val boxes = Prefs.crops(this@MainActivity).map { it.copyOf() }.toMutableList()
                selectedBox = selectedBox.coerceIn(0, boxes.lastIndex)
                val c = boxes[selectedBox]
                c[edge] = progress / 100f
                // keep the box sane
                if (c[0] > c[2] - 0.05f) c[0] = c[2] - 0.05f
                if (c[2] < c[0] + 0.05f) c[2] = c[0] + 0.05f
                if (c[1] > c[3] - 0.03f) c[1] = c[3] - 0.03f
                if (c[3] < c[1] + 0.03f) c[3] = c[1] + 0.03f
                Prefs.setCrops(this@MainActivity, boxes)
                pctLabel.text = "$progress%"
                preview.invalidate()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        val boxBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        boxBtnRow.addView(Ui.secondaryButton(this, Str.p("➕ إضافة مربع", "➕ Add Box")).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginEnd = dp(6) }
            setOnClickListener {
                val boxes = Prefs.crops(this@MainActivity).map { it.copyOf() }.toMutableList()
                if (boxes.size >= 4) {
                    Toast.makeText(this@MainActivity, Str.p("أقصى حاجة 4 مربعات", "Maximum 4 boxes"), Toast.LENGTH_SHORT).show()
                } else {
                    boxes.add(floatArrayOf(0.2f, 0.08f, 0.8f, 0.24f))
                    Prefs.setCrops(this@MainActivity, boxes)
                    selectedBox = boxes.lastIndex
                    refreshSeek()
                }
            }
        })
        boxBtnRow.addView(Ui.secondaryButton(this, Str.p("🗑 حذف المربع", "🗑 Delete Box")).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(6) }
            setOnClickListener {
                val boxes = Prefs.crops(this@MainActivity).map { it.copyOf() }.toMutableList()
                if (boxes.size <= 1) {
                    Toast.makeText(this@MainActivity, Str.p("لازم يفضل مربع واحد على الأقل", "At least one box must remain"), Toast.LENGTH_SHORT).show()
                } else {
                    selectedBox = selectedBox.coerceIn(0, boxes.lastIndex)
                    val deleted = selectedBox
                    boxes.removeAt(deleted)
                    Prefs.onBoxDeleted(this@MainActivity, deleted)
                    selectedBox = minOf(selectedBox, boxes.lastIndex)
                    Prefs.setCrops(this@MainActivity, boxes)
                    refreshSeek()
                }
            }
        })
        areaCard.addView(boxBtnRow)
        // Pause/resume translation for the selected box without deleting it
        pauseBtn = Ui.secondaryButton(this, Str.p("⏸ إيقاف ترجمة المربع", "⏸ Pause Box Translation")).apply {
            setOnClickListener {
                val boxesNow = Prefs.crops(this@MainActivity)
                selectedBox = selectedBox.coerceIn(0, boxesNow.lastIndex)
                val nowPaused = selectedBox !in Prefs.pausedBoxes(this@MainActivity)
                Prefs.setBoxPaused(this@MainActivity, selectedBox, nowPaused)
                if (nowPaused) {
                    // Take this box's caption off the TV right away
                    MqttSender.sendClearBox(this@MainActivity, selectedBox)
                    Toast.makeText(this@MainActivity, Str.p("اتوقفت ترجمة المربع ${selectedBox + 1} ⏸", "Box ${selectedBox + 1} translation paused ⏸"), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, Str.p("اشتغلت ترجمة المربع ${selectedBox + 1} ▶", "Box ${selectedBox + 1} translation resumed ▶"), Toast.LENGTH_SHORT).show()
                }
                refreshSeek()
            }
        }
        areaCard.addView(pauseBtn)
        // Per-box font size: applies to the selected box only
        areaCard.addView(Ui.bodyText(this, Str.p("حجم خط المربع المحدد", "Selected Box Font Size"), 15f, Ui.TEXT).apply {
            setPadding(0, dp(10), 0, 0)
        })
        boxSizeContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        areaCard.addView(boxSizeContainer)
        refreshBoxSizeRow()

        // Game profiles: named box layouts, switch per game
        val profileLabel = Ui.bodyText(this, "", 14f, Ui.GOLD)
        fun refreshProfileLabel() {
            val active = Prefs.activeProfile(this@MainActivity)
            profileLabel.text = if (active.isEmpty())
                Str.p("البروفايل الحالي: افتراضي", "Current profile: Default")
            else
                Str.p("البروفايل الحالي: $active", "Current profile: $active")
        }
        refreshProfileLabel()
        areaCard.addView(profileLabel)
        val profBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        profBtnRow.addView(Ui.secondaryButton(this, Str.p("📂 البروفايلات", "📂 Profiles")).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginEnd = dp(6) }
            setOnClickListener {
                val profs = Prefs.profiles(this@MainActivity)
                if (profs.isEmpty()) {
                    Toast.makeText(this@MainActivity, Str.p("لسه مفيش بروفايلات محفوظة", "No saved profiles yet"), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val list = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                }
                val dlg = AlertDialog.Builder(this@MainActivity)
                    .setTitle(Str.p("📂 اختار بروفايل", "📂 Choose a Profile"))
                    .setView(ScrollView(this@MainActivity).apply { addView(list) })
                    .setNegativeButton(Str.p("إلغاء", "Cancel"), null)
                    .create()
                for ((name, boxes) in profs) {
                    val row = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    row.addView(Ui.secondaryButton(this@MainActivity, name).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener {
                            Prefs.setCrops(this@MainActivity, boxes.map { it.copyOf() })
                            Prefs.clearPausedBoxes(this@MainActivity)
                            Prefs.clearBoxSizes(this@MainActivity)
                            Prefs.setActiveProfile(this@MainActivity, name)
                            selectedBox = 0
                            refreshSeek()
                            refreshProfileLabel()
                            dlg.dismiss()
                            Toast.makeText(this@MainActivity, Str.p("اتحمّل بروفايل $name ✓", "Profile $name loaded ✓"), Toast.LENGTH_SHORT).show()
                        }
                    })
                    row.addView(Ui.secondaryButton(this@MainActivity, "🗑").apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { marginStart = dp(6) }
                        setOnClickListener {
                            Prefs.deleteProfile(this@MainActivity, name)
                            refreshProfileLabel()
                            dlg.dismiss()
                            Toast.makeText(this@MainActivity, Str.p("اتمسح بروفايل $name", "Profile $name deleted"), Toast.LENGTH_SHORT).show()
                        }
                    })
                    list.addView(row)
                }
                dlg.show()
            }
        })
        profBtnRow.addView(Ui.secondaryButton(this, Str.p("💾 حفظ كبروفايل", "💾 Save as Profile")).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(6) }
            setOnClickListener {
                val input = Ui.inputField(this@MainActivity, Str.p("اسم اللعبة…", "Game name…"), number = false)
                input.setText(Prefs.activeProfile(this@MainActivity))
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(Str.p("💾 حفظ كبروفايل", "💾 Save as Profile"))
                    .setView(input)
                    .setPositiveButton(Str.p("حفظ", "Save")) { _, _ ->
                        val name = input.text.toString().trim()
                        if (name.isEmpty()) {
                            Toast.makeText(this@MainActivity, Str.p("اكتب اسم البروفايل الأول", "Enter a profile name first"), Toast.LENGTH_SHORT).show()
                        } else {
                            Prefs.saveProfile(this@MainActivity, name, Prefs.crops(this@MainActivity))
                            Prefs.setActiveProfile(this@MainActivity, name)
                            refreshProfileLabel()
                            Toast.makeText(this@MainActivity, Str.p("اتحفظ بروفايل $name ✓", "Profile $name saved ✓"), Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton(Str.p("إلغاء", "Cancel"), null)
                    .show()
            }
        })
        areaCard.addView(profBtnRow)
        areaCard.addView(Ui.secondaryButton(this, Str.p("رجوع للوضع الافتراضي", "Reset to Default")).apply {
            setOnClickListener {
                Prefs.setCrops(this@MainActivity, listOf(floatArrayOf(0.15f, 0.75f, 0.85f, 0.99f)))
                Prefs.setActiveProfile(this@MainActivity, "")
                Prefs.clearPausedBoxes(this@MainActivity)
                Prefs.clearBoxSizes(this@MainActivity)
                selectedBox = 0
                refreshSeek()
                refreshProfileLabel()
                preview.postInvalidate()
                Toast.makeText(this@MainActivity, Str.p("رجعنا للوضع الافتراضي ✓", "Reset to default ✓"), Toast.LENGTH_SHORT).show()
            }
        })
        areaCard.addView(Ui.bodyText(
            this,
            Str.p("حرّك الإطار الأحمر فوق مكان ترجمة اللعبة. لو الإطار ضيق، أول الجملة أو آخرها هيتقطع. تقدر تضيف أكتر من مربع، وكل مربع بيترجم المكان اللي هو فيه بس والترجمة بتظهر على التلفزيون في نفس مكان المربع — دوس على أي مربع عشان تختاره وتحرّكه.", "Move the red frame over the place where the game's subtitles appear. If the frame is too narrow, the start or end of a sentence gets cut off. You can add more than one box — each box translates only the area it covers, and its translation appears on the TV at that same spot. Tap a box to select and move it."),
            13f, Ui.MUTED
        ))
        preview.onCropChanged = { refreshSeek() }
        preview.onSelectionChanged = {
            selectedBox = preview.selectedIndex
            refreshSeek()
        }
        refreshSeek()
        sRoot.addView(areaCard)

        // Translation response time: how fast a new line is accepted
        val respCard = Ui.card(this)
        respCard.addView(Ui.cardTitle(this, Str.p("زمن استجابة الترجمة", "Translation Response Time")))
        val respIdx = when (Prefs.respMode(this)) { "F" -> 0; "A" -> 2; else -> 1 }
        respCard.addView(segmented(
            listOf(Str.p("سريع", "Fast"), Str.p("متوازن", "Balanced"), Str.p("دقيق", "Accurate")),
            respIdx
        ) { i ->
            Prefs.setRespMode(this, listOf("F", "B", "A")[i])
        })
        respCard.addView(Ui.bodyText(
            this,
            Str.p(
                "سريع: الترجمة تظهر من أول قراية — أسرع بس ممكن تهتز في الخطوط المزخرفة. متوازن: الوضع العادي. دقيق: أثبت حاجة وأبطأ شوية.",
                "Fast: the translation appears from the first read — quickest, but may wobble on stylized fonts. Balanced: the normal mode. Accurate: steadiest, slightly slower."
            ),
            13f, Ui.MUTED
        ))
        sRoot.addView(respCard)

        // Look on TV
        val lookCard = Ui.card(this)
        lookCard.addView(Ui.cardTitle(this, Str.p("شكل الترجمة على التلفزيون", "Look on the TV")))
        lookCard.addView(Ui.bodyText(this, Str.p("حجم الخط", "Text size"), 15f, Ui.TEXT))
        val sizes = listOf("S", "M", "L", "H")
        val sizeLabels = listOf(
            Str.p("صغير", "Small"), Str.p("متوسط", "Medium"), Str.p("كبير", "Large"), Str.p("ضخم", "Huge")
        )
        lookCard.addView(segmented(sizeLabels, sizes.indexOf(Prefs.tvSize(this)).coerceAtLeast(0)) { i ->
            Prefs.setTvSize(this, sizes[i])
            MqttSender.publishStyle(this)
        })
        lookCard.addView(Ui.bodyText(this, Str.p("لون الترجمة", "Caption color"), 15f, Ui.TEXT))
        val manualColorLabel = Ui.bodyText(this, Str.p("لون الخط", "Text color"), 15f, Ui.TEXT)
        val colors = listOf("white", "yellow")
        val colorLabels = listOf(Str.p("أبيض", "White"), Str.p("أصفر", "Yellow"))
        val manualColorSeg = segmented(colorLabels, colors.indexOf(Prefs.tvColor(this)).coerceAtLeast(0)) { i ->
            Prefs.setTvColor(this, colors[i])
            MqttSender.publishStyle(this)
        }
        // In auto mode the manual color row is locked (dimmed + disabled);
        // switching back to manual unlocks it.
        fun refreshColorMode() {
            val auto = Prefs.autoColor(this)
            manualColorSeg.alpha = if (auto) 0.35f else 1f
            manualColorLabel.alpha = if (auto) 0.35f else 1f
            for (j in 0 until manualColorSeg.childCount) {
                manualColorSeg.getChildAt(j).isEnabled = !auto
            }
        }
        val autoLabels = listOf(
            Str.p("تلقائي (لون اللعبة)", "Auto (game color)"), Str.p("يدوي", "Manual")
        )
        lookCard.addView(segmented(autoLabels, if (Prefs.autoColor(this)) 0 else 1) { i ->
            Prefs.setAutoColor(this, i == 0)
            refreshColorMode()
        })
        lookCard.addView(Ui.bodyText(
            this,
            Str.p("في الوضع التلقائي الترجمة بتظهر على التلفزيون بنفس لون نص اللعبة. واللون اليدوي اللي تحت بيُستخدم لو التلقائي مقدرش يحدد اللون.", "In auto mode the translation appears on the TV in the same color as the game's own text. The manual color below is used when auto can't detect a color."),
            13f, Ui.MUTED
        ))
        lookCard.addView(manualColorLabel)
        lookCard.addView(manualColorSeg)
        refreshColorMode()
        lookCard.addView(Ui.bodyText(this, Str.p("سواد الخلفية", "Background darkness"), 15f, Ui.TEXT))
        val bgRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val bgSeek = SeekBar(this).apply {
            max = 100
            progress = Prefs.tvBg(this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val bgPct = Ui.bodyText(this, "${Prefs.tvBg(this)}%", 15f, Ui.TEXT).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(10) }
        }
        bgSeek.setOnTouchListener { v, e ->
            if (e.action == android.view.MotionEvent.ACTION_DOWN) {
                v.parent?.requestDisallowInterceptTouchEvent(true)
            }
            false
        }
        bgSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                Prefs.setTvBg(this@MainActivity, progress)
                bgPct.text = "$progress%"
                MqttSender.publishStyle(this@MainActivity)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        bgRow.addView(bgSeek)
        bgRow.addView(bgPct)
        lookCard.addView(bgRow)
        lookCard.addView(Ui.bodyText(this, Str.p("مدة بقاء الترجمة", "Caption duration"), 15f, Ui.TEXT))
        val durs = listOf("S", "M", "L")
        val durLabels = listOf(Str.p("قصيرة", "Short"), Str.p("عادية", "Normal"), Str.p("طويلة", "Long"))
        lookCard.addView(segmented(durLabels, durs.indexOf(Prefs.tvDur(this)).coerceAtLeast(0)) { i ->
            Prefs.setTvDur(this, durs[i])
            MqttSender.publishStyle(this)
        })
        lookCard.addView(Ui.bodyText(this, Str.p("نطق الترجمة صوتيًا", "Speak translations aloud"), 15f, Ui.TEXT))
        lookCard.addView(segmented(
            listOf(Str.p("إيقاف", "Off"), Str.p("تشغيل", "On")),
            if (Prefs.ttsOn(this)) 1 else 0
        ) { i ->
            Prefs.setTtsOn(this, i == 1)
        })
        lookCard.addView(Ui.secondaryButton(this, Str.p("تجربة على الشاشة", "Test on TV")).apply {
            setOnClickListener {
                MqttSender.connect(this@MainActivity)
                MqttSender.publishStyle(this@MainActivity)
                MqttSender.sendCaption(
                    this@MainActivity,
                    "This is a test caption to preview the style",
                    Str.p("دي ترجمة تجريبية عشان تشوف الشكل على الشاشة", "This is a test caption to preview the style")
                )
                android.widget.Toast.makeText(
                    this@MainActivity,
                    Str.p("بعتنا التجربة للشاشة 📺", "Test sent to the TV 📺"),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        })
        sRoot.addView(lookCard)

        // Language
        val langCard = Ui.card(this)
        langCard.addView(Ui.cardTitle(this, Str.p("لغة التطبيق", "App Language")))
        val current = Prefs.lang(this)
        langCard.addView(langButton("العربية", current == "ar") { switchLanguage("ar") })
        langCard.addView(langButton("English", current == "en") { switchLanguage("en") })
        sRoot.addView(langCard)

        // About
        val aboutCard = Ui.card(this)
        aboutCard.addView(Ui.cardTitle(this, Str.p("عن البرنامج", "About")))
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
        val vRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        vRow.addView(Ui.bodyText(this, Str.p("رقم الإصدار", "Version"), 16f, Ui.TEXT).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        vRow.addView(Ui.bodyText(this, version, 16f, Ui.MUTED))
        aboutCard.addView(vRow)
        aboutCard.addView(Ui.primaryButton(this, Str.p("التحقق من التحديثات", "Check for Updates")).apply {
            setOnClickListener { Updater.checkManual(this@MainActivity) }
        })
        val ytRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(6))
            isClickable = true
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/@mersostore")))
                } catch (_: Exception) {}
            }
        }
        ytRow2.addView(TextView(this).apply {
            text = "▶"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = Ui.roundedBg(this@MainActivity, "#FF0000", 8)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
        })
        ytRow2.addView(Ui.bodyText(this, Str.p("تابعنا على اليوتيوب", "Follow us on YouTube"), 16f, Ui.TEXT))
        aboutCard.addView(ytRow2)
        aboutCard.addView(Ui.secondaryButton(this, Str.p("عرض المقدمة", "Show Introduction")).apply {
            setOnClickListener { startActivity(Intent(this@MainActivity, IntroActivity::class.java)) }
        })
        aboutCard.addView(Ui.secondaryButton(this, Str.p("حول / التراخيص", "About / Licenses")).apply {
            setOnClickListener { startActivity(Intent(this@MainActivity, LicensesActivity::class.java)) }
        })
        sRoot.addView(aboutCard)

        sRoot.addView(Ui.bodyText(this, Str.p("من قناة Merso Gaming", "From Merso Gaming channel"), 13f, Ui.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        })
        return sRoot
    }

    private fun langButton(text: String, selected: Boolean, onClick: () -> Unit): Button {
        val b = if (selected) Ui.primaryButton(this, text) else Ui.secondaryButton(this, text)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun switchLanguage(code: String) {
        if (Prefs.lang(this) == code) return
        Prefs.setLang(this, code)
        Str.lang = code
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun selectTab(index: Int) {
        currentTab = index
        translateScroll.visibility = if (index == 0) View.VISIBLE else View.GONE
        guideScroll.visibility = if (index == 1) View.VISIBLE else View.GONE
        settingsScroll.visibility = if (index == 2) View.VISIBLE else View.GONE
        howtoScroll.visibility = if (index == 3) View.VISIBLE else View.GONE
        telegramScroll.visibility = if (index == 4) View.VISIBLE else View.GONE
        supportScroll.visibility = if (index == 5) View.VISIBLE else View.GONE
        if (index == 3) {
            howtoWeb?.onResume()
            if (!howtoLoaded) {
                howtoLoaded = true
                // Load the embed inside an HTML wrapper with our own site as base URL so the
                // iframe request carries a valid non-YouTube Referer. A youtube.com base makes
                // YouTube block its own embed (Error 152); no base at all triggers Error 153.
                val html = "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                    "<style>html,body{margin:0;padding:0;background:#000;height:100%}iframe{border:0;width:100%;height:100%}</style></head>" +
                    "<body><iframe src=\"https://www.youtube.com/embed/4Vu9-thSXhs?rel=0&playsinline=1\" " +
                    "referrerpolicy=\"strict-origin-when-cross-origin\" " +
                    "allow=\"accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture\" allowfullscreen></iframe></body></html>"
                howtoWeb?.loadDataWithBaseURL("https://mersostore.github.io", html, "text/html", "UTF-8", null)
            }
        } else {
            howtoWeb?.onPause()
        }
        for ((i, item) in tabItems.withIndex()) {
            val sel = i == index
            item.background = if (sel) Ui.roundedBg(this, "#3A3A3A", 32) else null
            tabLabels[i].setTextColor(Color.parseColor(if (sel) Ui.GOLD else Ui.SUB))
            tabLabels[i].typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (sel) {
                item.animate().scaleX(1.06f).scaleY(1.06f).setDuration(180).start()
            } else {
                item.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
            }
        }
        val content = when (index) {
            1 -> guideScroll.getChildAt(0) as? LinearLayout
            2 -> settingsScroll.getChildAt(0) as? LinearLayout
            4 -> telegramScroll.getChildAt(0) as? LinearLayout
            5 -> supportScroll.getChildAt(0) as? LinearLayout
            else -> null
        }
        if (content != null) Ui.animateChildren(content)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tab = intent?.getIntExtra("tab", -1) ?: -1
        if (tab >= 0) selectTab(tab)
    }

    override fun onBackPressed() {
        if (currentTab != 0) {
            selectTab(0)
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(Str.p("خروج من البرنامج؟", "Exit the app?"))
            .setMessage(Str.p("متأكد إنك عايز تطلع من البرنامج؟", "Are you sure you want to exit?"))
            .setPositiveButton(Str.p("خروج", "Exit")) { _, _ -> finishAffinity() }
            .setNegativeButton(Str.p("إلغاء", "Cancel"), null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 11 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, CameraActivity::class.java))
        }
    }

    private class CropPreviewView(
        ctx: android.content.Context,
        private val textureProvider: () -> TextureView?
    ) : View(ctx) {
        var onCropChanged: (() -> Unit)? = null
        var onSelectionChanged: (() -> Unit)? = null
        var selectedIndex: Int = 0
        private var dragMode = 0 // 0 none, 1 move, 2 resize right, 3 resize bottom
        private var lastX = 0f
        private var lastY = 0f

        override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
            val boxes = Prefs.crops(context).map { it.copyOf() }.toMutableList()
            if (selectedIndex !in boxes.indices) selectedIndex = 0
            when (e.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Topmost box wins (last drawn is on top)
                    var hit = -1
                    var mode = 0
                    for (i in boxes.indices.reversed()) {
                        val c = boxes[i]
                        val l = c[0] * width
                        val t = c[1] * height
                        val r = c[2] * width
                        val b = c[3] * height
                        val m = when {
                            kotlin.math.abs(e.x - r) < 24f && e.y in t..b -> 2
                            kotlin.math.abs(e.y - b) < 24f && e.x in l..r -> 3
                            e.x in l..r && e.y in t..b -> 1
                            else -> 0
                        }
                        if (m != 0) {
                            hit = i
                            mode = m
                            break
                        }
                    }
                    if (hit < 0) return false
                    parent?.requestDisallowInterceptTouchEvent(true)
                    lastX = e.x
                    lastY = e.y
                    dragMode = mode
                    if (selectedIndex != hit) {
                        selectedIndex = hit
                        onSelectionChanged?.invoke()
                    }
                    invalidate()
                    return true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (dragMode == 0) return false
                    val c = boxes[selectedIndex]
                    val dx = (e.x - lastX) / width
                    val dy = (e.y - lastY) / height
                    lastX = e.x
                    lastY = e.y
                    when (dragMode) {
                        1 -> {
                            val w = c[2] - c[0]
                            val h = c[3] - c[1]
                            c[0] = (c[0] + dx).coerceIn(0f, 1f - w)
                            c[1] = (c[1] + dy).coerceIn(0f, 1f - h)
                            c[2] = c[0] + w
                            c[3] = c[1] + h
                        }
                        2 -> c[2] = (c[2] + dx).coerceIn(c[0] + 0.05f, 1f)
                        3 -> c[3] = (c[3] + dy).coerceIn(c[1] + 0.03f, 1f)
                    }
                    Prefs.setCrops(context, boxes)
                    invalidate()
                    onCropChanged?.invoke()
                    return true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    dragMode = 0
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return super.onTouchEvent(e)
        }

        private val bgPaint = Paint().apply { color = Color.BLACK }
        private val framePaint = Paint().apply {
            color = Color.parseColor(Ui.GOLD)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        private val selPaint = Paint().apply {
            color = Color.parseColor(Ui.GOLD)
            style = Paint.Style.STROKE
            strokeWidth = 7f
        }
        private val fillPaint = Paint().apply { color = Color.argb(40, 224, 0, 0) }
        private val selFillPaint = Paint().apply { color = Color.argb(80, 224, 0, 0) }
        private val numPaint = Paint().apply {
            color = Color.WHITE
            textSize = 36f
            typeface = Typeface.DEFAULT_BOLD
        }
        private val textPaint = Paint().apply {
            color = Color.parseColor(Ui.MUTED)
            textSize = 34f
        }

        private var frameBitmap: Bitmap? = null
        private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
        private val refreshRunnable = object : Runnable {
            override fun run() {
                val tv = textureProvider()
                val direct = if (tv != null && tv.isAvailable) {
                    try { tv.getBitmap(640, 360) } catch (_: Exception) { null }
                } else null
                val controller = StreamHolder.controller
                val snap = direct ?: controller?.grabFrame(640, 360) ?: controller?.snapshotFrame()
                if (snap != null) {
                    val old = frameBitmap
                    frameBitmap = snap
                    if (old != null && old !== snap) old.recycle()
                    invalidate()
                }
                refreshHandler.postDelayed(this, 100)
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            refreshHandler.post(refreshRunnable)
        }

        override fun onDetachedFromWindow() {
            refreshHandler.removeCallbacks(refreshRunnable)
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val frame = frameBitmap
            if (frame != null && !frame.isRecycled) {
                canvas.drawBitmap(frame, null, android.graphics.Rect(0, 0, width, height), bitmapPaint)
            } else {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
                canvas.drawText(
                    Str.p("صورة اللعبة بتظهر هنا والترجمة شغالة", "The game picture appears here while translation is on."),
                    24f, 56f, textPaint
                )
            }
            val boxes = Prefs.crops(context)
            val paused = Prefs.pausedBoxes(context)
            boxes.forEachIndexed { i, c ->
                val l = c[0] * width
                val t = c[1] * height
                val r = c[2] * width
                val b = c[3] * height
                val sel = i == selectedIndex
                canvas.drawRect(l, t, r, b, if (sel) selFillPaint else fillPaint)
                canvas.drawRect(l, t, r, b, if (sel) selPaint else framePaint)
                canvas.drawText(if (i in paused) "${i + 1} ⏸" else "${i + 1}", l + 10f, t + 42f, numPaint)
            }
        }
    }
}
