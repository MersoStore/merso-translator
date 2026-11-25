package com.merso.gametranslate

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.merso.gametranslate.Ui.dp

// Console-type selection shown after the TV setup guide:
// pick Online (official PSN) or Modded, see that console's setup
// explanation, then continue to the pairing steps.
class ConsoleTypeActivity : Activity() {

    private var fromSettings = false
    private var showingGuide = false
    private var selectedType = ""
    private var initialType = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fromSettings = intent.getBooleanExtra("from_settings", false)
        selectedType = Prefs.consoleType(this)
        initialType = selectedType
        showSelection()
    }

    private fun typeTitle(type: String): String =
        if (type == "modded") Str.p("جهاز معدل", "Modded Console") else Str.p("جهاز أونلاين", "Online Console")

    private fun showSelection() {
        showingGuide = false
        val root = Ui.screenRoot(this)
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(Ui.header(
            this,
            Str.p("نوع الجهاز", "Console Type"),
            Str.p("جهازك أونلاين ولا معدل؟", "Is your console online or modded?")
        ))
        root.addView(Ui.bodyText(
            this,
            Str.p("طريقة إعداد الريموت بلاي بتختلف حسب نوع الجهاز.", "Remote Play setup differs by console type."),
            14f, Ui.MUTED
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(10))
        })

        val types = listOf(
            Triple(
                "online", "🌐 " + Str.p("جهاز أونلاين", "Online Console"),
                Str.p("جهاز رسمي متصل بـPSN — الإعداد بحساب PSN ثانوي", "Official console on PSN — setup with a second PSN account")
            ),
            Triple(
                "modded", "🔓 " + Str.p("جهاز معدل", "Modded Console"),
                Str.p("جهاز معدل بجيلبريك — الإعداد بالتفعيل الوهمي من غير PSN", "Jailbroken console — setup with fake activation, no PSN")
            )
        )
        for ((type, title, desc) in types) {
            val card = Ui.card(this)
            if (type == selectedType) {
                card.background = Ui.roundedBg(this, Ui.CARD, 18, Ui.GOLD, 2)
            }
            card.addView(TextView(this).apply {
                text = title
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            card.addView(Ui.bodyText(this, desc, 14f, Ui.MUTED).apply {
                gravity = Gravity.CENTER
            })
            card.addView(Ui.primaryButton(this, Str.p("عرض الشرح", "Show Guide")).apply {
                setOnClickListener { choose(type) }
            })
            card.isClickable = true
            card.setOnClickListener { choose(type) }
            root.addView(card)
        }

        if (fromSettings) {
            root.addView(Ui.secondaryButton(this, Str.p("رجوع", "Back")).apply {
                setOnClickListener { finish() }
            })
        }

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        Ui.animateChildren(root)
    }

    private fun choose(type: String) {
        selectedType = type
        Prefs.setConsoleType(this, type)
        showGuide(type)
    }

    private fun showGuide(type: String) {
        showingGuide = true
        val root = Ui.screenRoot(this)
        root.addView(Ui.header(
            this,
            Str.p("شرح ${typeTitle(type)}", "${typeTitle(type)} Guide"),
            Str.p("اتبع الخطوات دي مرة واحدة بس", "Follow these steps once")
        ))
        root.addView(GuideUi.buildConsoleGuide(this, type))
        root.addView(Ui.primaryButton(
            this,
            if (fromSettings) Str.p("حفظ وبدء الربط", "Save & Start Pairing") else Str.p("كمل لطريقة الربط", "Continue to Pairing")
        ).apply {
            setOnClickListener { continueFlow() }
        })
        root.addView(Ui.secondaryButton(this, Str.p("تغيير نوع الجهاز", "Change Console Type")).apply {
            setOnClickListener { showSelection() }
        })

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        Ui.animateChildren(root)
    }

    private fun continueFlow() {
        Prefs.setConsoleSetupDone(this, true)
        if (fromSettings) {
            // Type changed from Settings: pair again from scratch.
            if (selectedType != initialType) {
                Prefs.clearPairing(this)
                Prefs.setAccountId(this, "")
                Prefs.setFakeActivated(this, false)
            }
            Toast.makeText(this, Str.p("اتحفظ نوع الجهاز ✓", "Console type saved ✓"), Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, SetupWizardActivity::class.java))
            finish()
            return
        }
        // Pairing wizard: screen pairing card -> PS5 pairing card -> stream.
        startActivity(Intent(this, SetupWizardActivity::class.java))
    }

    override fun onBackPressed() {
        if (showingGuide) {
            showSelection()
        } else {
            finish()
        }
    }
}
