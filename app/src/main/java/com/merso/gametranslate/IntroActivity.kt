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

class IntroActivity : Activity() {

    private var page = 0
    private lateinit var iconText: TextView
    private lateinit var titleText: TextView
    private lateinit var bodyText: TextView
    private lateinit var nextBtn: Button
    private lateinit var dotsRow: LinearLayout
    private lateinit var contentBox: LinearLayout

    private fun slides() = listOf(
        Triple(
            "🎮",
            Str.p("أهلاً بيك في مرسو للترجمة", "Welcome to Merso Translator"),
            Str.p("البرنامج بيترجم كلام الألعاب الإنجليزي لعربي ويعرضه على التلفزيون وانت بتلعب، من غير ما توقف اللعبة.", "The app translates English game text into Arabic and shows it on your TV while you play, without pausing the game.")
        ),
        Triple(
            "📺",
            Str.p("اربط التلفزيون مرة واحدة", "Pair Your TV Once"),
            Str.p("على شاشات LG افتح Media Station X وحط رقم الربط اللي هتلاقيه في البرنامج. وعلى شاشات Android TV ثبّت تطبيق Merso Translator TV على التلفزيون. وبعدين اكتب الكود اللي هيظهر على التلفزيون هنا في الموبايل.", "On LG TVs, open Media Station X and enter the pairing address from this app. On Android TV, install the Merso Translator TV app on the TV. Then type the code shown on the TV into the phone.")
        ),
        Triple(
            "🕹️",
            Str.p("طريقتين للترجمة", "Two Ways to Translate"),
            Str.p("ترجم عن طريق Remote Play وانت قاعد مرتاح، أو وجّه كاميرا الموبايل على الشاشة. اختر اللي يناسبك من تبويب «ترجمة».", "Translate via Remote Play from your couch, or point your phone camera at the screen. Pick what suits you from the Translate tab.")
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#050505"), Color.parseColor("#200505"), Color.parseColor("#0A0A0A"))
            )
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val skip = TextView(this).apply {
            text = Str.p("تخطي", "Skip")
            textSize = 16f
            setTextColor(Color.parseColor(Ui.MUTED))
            gravity = Gravity.END
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { finishIntro() }
        }
        root.addView(skip)

        contentBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        iconText = TextView(this).apply {
            textSize = 84f
            gravity = Gravity.CENTER
            setPadding(0, dp(40), 0, dp(10))
        }
        contentBox.addView(iconText)

        titleText = TextView(this).apply {
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(Ui.GOLD))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(8))
        }
        contentBox.addView(titleText)

        bodyText = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.parseColor(Ui.SUB))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.35f)
            setPadding(dp(8), 0, dp(8), 0)
        }
        contentBox.addView(bodyText)
        root.addView(contentBox)

        dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(16))
        }
        root.addView(dotsRow)

        nextBtn = Ui.primaryButton(this, "").apply {
            setOnClickListener {
                if (page < slides().size - 1) {
                    page++
                    showPage()
                } else {
                    finishIntro()
                }
            }
        }
        root.addView(nextBtn)

        setContentView(root)
        showPage()
    }

    private fun showPage() {
        val s = slides()[page]
        contentBox.animate().alpha(0f).translationX(-40f).setDuration(150).withEndAction {
            iconText.text = s.first
            titleText.text = s.second
            bodyText.text = s.third
            contentBox.translationX = 40f
            contentBox.animate().alpha(1f).translationX(0f).setDuration(250).start()
        }.start()

        dotsRow.removeAllViews()
        for (i in slides().indices) {
            dotsRow.addView(TextView(this).apply {
                text = "●"
                textSize = if (i == page) 16f else 12f
                setTextColor(Color.parseColor(if (i == page) Ui.GOLD else Ui.MUTED))
                setPadding(dp(4), 0, dp(4), 0)
            })
        }
        nextBtn.text = if (page == slides().size - 1) Str.p("ابدأ", "Get Started") else Str.p("التالي", "Next")
    }

    private fun finishIntro() {
        Prefs.setIntroSeen(this, true)
        startActivity(Intent(this, ModeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    override fun onBackPressed() {
        finishIntro()
    }
}
