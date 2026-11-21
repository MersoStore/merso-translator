package com.merso.gametranslate

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.merso.gametranslate.Ui.dp

class ModeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = Ui.screenRoot(this)
        root.gravity = Gravity.CENTER_HORIZONTAL

        val logo = ImageView(this).apply {
            val id = resources.getIdentifier("logo", "drawable", packageName)
            if (id != 0) setImageResource(id)
            layoutParams = LinearLayout.LayoutParams(dp(150), dp(150)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(30)
            }
        }
        root.addView(logo)

        root.addView(TextView(this).apply {
            text = Str.p("مرسو للترجمة", "Merso Translator")
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = Str.p("هترجم إزاي النهاردة؟", "How will you translate today?")
            textSize = 16f
            setTextColor(Color.parseColor(Ui.SUB))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(18))
        })

        val rpCard = Ui.card(this)
        rpCard.addView(TextView(this).apply {
            text = "🎮"
            textSize = 40f
            gravity = Gravity.CENTER
        })
        rpCard.addView(Ui.primaryButton(this, Str.p("ريموت بلاي", "Remote Play")).apply {
            setOnClickListener {
                startActivity(Intent(this@ModeActivity, MainActivity::class.java))
                finish()
            }
        })
        rpCard.addView(Ui.bodyText(this, Str.p("الترجمة توصل للتلفزيون وانت بتلعب من مكانك", "Translations reach the TV while you play from your couch"), 14f, Ui.MUTED).apply {
            gravity = Gravity.CENTER
        })
        root.addView(rpCard)

        val camCard = Ui.card(this)
        camCard.addView(TextView(this).apply {
            text = "📷"
            textSize = 40f
            gravity = Gravity.CENTER
        })
        camCard.addView(Ui.primaryButton(this, Str.p("ترجمة عن طريق كاميرا الفون", "Translate with Phone Camera")).apply {
            setOnClickListener { openCamera() }
        })
        camCard.addView(Ui.bodyText(this, Str.p("وجّه الكاميرا على الشاشة والترجمة هتظهر على تليفونك", "Point the camera at the screen and translations appear on your phone"), 14f, Ui.MUTED).apply {
            gravity = Gravity.CENTER
        })
        root.addView(camCard)

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        Ui.animateChildren(root)
    }

    private fun openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 21)
        } else {
            startActivity(Intent(this, CameraActivity::class.java))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 21 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, CameraActivity::class.java))
        }
    }

    override fun onBackPressed() {
        android.app.AlertDialog.Builder(this)
            .setTitle(Str.p("خروج من البرنامج؟", "Exit the app?"))
            .setMessage(Str.p("متأكد إنك عايز تطلع من البرنامج؟", "Are you sure you want to exit?"))
            .setPositiveButton(Str.p("خروج", "Exit")) { _, _ -> finishAffinity() }
            .setNegativeButton(Str.p("إلغاء", "Cancel"), null)
            .show()
    }
}
