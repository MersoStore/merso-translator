package com.merso.gametranslate

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView

/**
 * Shows a single setup guide card (screen or console, per the type the
 * user already chose) and returns to the caller on Back — used by the
 * pairing wizard's guide buttons.
 */
class GuideCardActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = Ui.screenRoot(this)
        when (intent.getStringExtra("guide")) {
            "console" -> {
                root.addView(Ui.header(
                    this,
                    Str.p("شرح ربط الـPS5", "PS5 Pairing Guide"),
                    Str.p("الشرح الخاص بنوع جهازك", "Guide for your console type")
                ))
                root.addView(GuideUi.buildConsoleGuide(this, Prefs.consoleType(this)))
            }
            else -> {
                root.addView(Ui.header(
                    this,
                    Str.p("شرح ربط الشاشة", "Screen Pairing Guide"),
                    Str.p("الشرح الخاص بنوع شاشتك", "Guide for your screen type")
                ))
                root.addView(GuideUi.buildScreenGuide(this, Prefs.screenType(this)))
            }
        }
        root.addView(Ui.secondaryButton(this, Str.p("رجوع", "Back")).apply {
            setOnClickListener { finish() }
        })

        setContentView(ScrollView(this).apply { addView(root) })
        Ui.animateChildren(root)
    }
    // Default Back finishes this screen and returns to the pairing card.
}
