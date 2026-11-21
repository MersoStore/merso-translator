package com.merso.gametranslate

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView

class HelpActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = GuideUi.build(this)
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        Ui.animateChildren(root)
    }

    override fun onBackPressed() {
        Nav.goHome(this)
    }
}
