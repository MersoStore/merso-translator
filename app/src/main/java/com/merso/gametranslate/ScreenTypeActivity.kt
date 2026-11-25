package com.merso.gametranslate

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.merso.gametranslate.Ui.dp

// Screen-type selection shown after the user picks Remote Play:
// pick the TV type, then see that TV's own setup guide card.
class ScreenTypeActivity : Activity() {

    private var fromSettings = false
    private var showingGuide = false
    private var selectedType = ""
    private var initialType = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fromSettings = intent.getBooleanExtra("from_settings", false)
        selectedType = Prefs.screenType(this)
        initialType = selectedType
        showSelection()
    }

    private fun typeTitle(type: String): String = when (type) {
        "android_tv" -> "Android TV / Google TV"
        else -> "LG"
    }

    private fun showSelection() {
        showingGuide = false
        val root = Ui.screenRoot(this)
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(Ui.header(
            this,
            Str.p("نوع الشاشة", "TV Type"),
            Str.p("اختار نوع تلفزيونك عشان نعرض لك شرح التثبيت الصح", "Choose your TV type so we show the right setup steps")
        ))
        root.addView(Ui.bodyText(
            this,
            Str.p("الترجمة بتظهر على التلفزيون، وطريقة الربط بتختلف حسب نوع الشاشة.", "Subtitles appear on the TV, and pairing differs by TV type."),
            14f, Ui.MUTED
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(10))
        })

        val types = listOf(
            Triple(
                "lg", "📺 LG",
                Str.p("شاشات LG بنظام webOS — الربط عن طريق Media Station X", "LG webOS TVs — pairing via Media Station X")
            ),
            Triple(
                "android_tv", "📺 Android TV / Google TV",
                Str.p("شاشات Android TV وGoogle TV — الربط عن طريق تطبيق الشاشة", "Android TV & Google TV — pairing via the TV app")
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
            card.addView(Ui.primaryButton(this, Str.p("عرض شرح التثبيت", "Show Setup Guide")).apply {
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
        Prefs.setScreenType(this, type)
        showGuide(type)
    }

    private fun showGuide(type: String) {
        showingGuide = true
        val root = Ui.screenRoot(this)
        root.addView(Ui.header(
            this,
            Str.p("تثبيت شاشة ${typeTitle(type)}", "${typeTitle(type)} TV Setup"),
            Str.p("اتبع الخطوات دي مرة واحدة بس", "Follow these steps once")
        ))
        root.addView(GuideUi.buildScreenGuide(this, type))
        root.addView(Ui.primaryButton(
            this,
            if (fromSettings) Str.p("حفظ وبدء الربط", "Save & Start Pairing") else Str.p("تمام، كمل", "Done, Continue")
        ).apply {
            setOnClickListener { continueFlow() }
        })
        root.addView(Ui.secondaryButton(this, Str.p("تغيير نوع الشاشة", "Change TV Type")).apply {
            setOnClickListener { showSelection() }
        })

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        Ui.animateChildren(root)
    }

    private fun continueFlow() {
        Prefs.setScreenSetupDone(this, true)
        if (fromSettings) {
            // Type changed from Settings: pair again from scratch.
            if (selectedType != initialType) {
                Prefs.setCode(this, "")
                MqttSender.disconnect()
            }
            Toast.makeText(this, Str.p("اتحفظ نوع الشاشة ✓", "TV type saved ✓"), Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, SetupWizardActivity::class.java))
            finish()
            return
        }
        // Next onboarding step: console type (online / modded), unless already done.
        if (Prefs.consoleSetupDone(this)) {
            startActivity(Intent(this, SetupWizardActivity::class.java))
        } else {
            startActivity(Intent(this, ConsoleTypeActivity::class.java))
        }
        finish()
    }

    override fun onBackPressed() {
        if (showingGuide) {
            showSelection()
        } else {
            finish()
        }
    }
}
