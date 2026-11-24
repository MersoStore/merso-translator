package com.merso.gametranslate

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.animation.OvershootInterpolator
import android.widget.*
import com.merso.gametranslate.Ui.dp
import kotlin.random.Random

class SplashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SignatureCheck.ok(this)) {
            Toast.makeText(this, Str.p("نسخة غير أصلية — حمّل التطبيق من قناة Merso Gaming", "Unofficial copy — get the app from the Merso Gaming channel"), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        // Anonymous daily-active-device ping (random UUID, no personal data)
        Stats.pingDaily(this)

        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#050505"),
                    Color.parseColor("#260606"),
                    Color.parseColor("#0A0A0A")
                )
            )
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        val logo = ImageView(this).apply {
            val id = resources.getIdentifier("logo", "drawable", packageName)
            if (id != 0) setImageResource(id)
            layoutParams = LinearLayout.LayoutParams(dp(260), dp(260))
            alpha = 0f
            scaleX = 0.6f
            scaleY = 0.6f
        }
        box.addView(logo)

        val title = TextView(this).apply {
            text = "Merso Translator"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(Ui.GOLD))
            gravity = Gravity.CENTER
            alpha = 0f
            setPadding(0, dp(18), 0, 0)
        }
        box.addView(title)

        val sub = TextView(this).apply {
            text = "ترجمة ألعابك على التلفزيون وانت بتلعب 🎮"
            textSize = 15f
            setTextColor(Color.parseColor(Ui.SUB))
            gravity = Gravity.CENTER
            alpha = 0f
            setPadding(0, dp(6), 0, 0)
        }
        box.addView(sub)

        root.addView(box)
        setContentView(root)

        // Floating PlayStation symbols behind the logo
        root.post {
            val symbols = listOf("🎮", "△", "○", "✕", "□", "🎯", "⚡", "🎮", "△", "○", "✕", "□", "🏆", "🎮", "△", "○", "✕", "□", "🎮", "🎯", "⚡", "△", "○", "🎮")
            val colors = listOf("#FF2E2E", "#FFFFFF", "#4D7CFE", "#FF6B6B", "#E0E0E0")
            val w = root.width
            val h = root.height
            for ((i, sym) in symbols.withIndex()) {
                val tv = TextView(this).apply {
                    text = sym
                    textSize = Random.nextInt(28, 58).toFloat()
                    setTextColor(Color.parseColor(colors[i % colors.size]))
                    alpha = 0f
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                    x = Random.nextInt(0, (w - dp(50)).coerceAtLeast(1)).toFloat()
                    y = h.toFloat() + dp(20)
                }
                root.addView(tv, 0)
                val dur = Random.nextLong(3000, 5200)
                val delay = Random.nextLong(0, 1200)
                tv.animate()
                    .translationY(-(h + dp(120)).toFloat())
                    .alpha(1f)
                    .setDuration(dur)
                    .setStartDelay(delay)
                    .withEndAction { tv.alpha = 0f }
                    .start()
            }
        }

        logo.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(900)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()
        title.animate().alpha(1f).setDuration(600).setStartDelay(500).start()
        sub.animate().alpha(1f).setDuration(600).setStartDelay(800).start()

        Handler(Looper.getMainLooper()).postDelayed({
            box.animate().alpha(0f).setDuration(350).withEndAction {
                val next = when {
                    Prefs.lang(this).isEmpty() -> LanguageActivity::class.java
                    !Prefs.introSeen(this) -> IntroActivity::class.java
                    else -> ModeActivity::class.java
                }
                startActivity(Intent(this, next))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }.start()
        }, 4200)
    }
}
