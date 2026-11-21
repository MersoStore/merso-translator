package com.merso.gametranslate

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.merso.gametranslate.Ui.dp

class LanguageActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#0B1026"),
                    Color.parseColor("#1B1040"),
                    Color.parseColor("#0B0E14")
                )
            )
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        val logo = ImageView(this).apply {
            val id = resources.getIdentifier("logo", "drawable", packageName)
            if (id != 0) setImageResource(id)
            layoutParams = LinearLayout.LayoutParams(dp(180), dp(180))
        }
        box.addView(logo)

        box.addView(TextView(this).apply {
            text = "اختر اللغة  •  Choose Language"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(Ui.GOLD))
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(14))
        })

        box.addView(Ui.primaryButton(this, "العربية").apply {
            setOnClickListener { choose("ar") }
        })
        box.addView(Ui.secondaryButton(this, "English").apply {
            setOnClickListener { choose("en") }
        })

        root.addView(box)
        setContentView(root)
    }

    private fun choose(lang: String) {
        Prefs.setLang(this, lang)
        Str.lang = lang
        val i = Intent(this, if (Prefs.introSeen(this)) ModeActivity::class.java else IntroActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(i)
        finish()
    }

    override fun onBackPressed() {
        Nav.goHome(this)
    }
}
