package com.merso.gametranslate.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var permBtn: Button
    private lateinit var toggleBtn: Button
    private val handler = Handler(Looper.getMainLooper())
    private val updater = object : Runnable {
        override fun run() {
            refreshUi()
            handler.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = TvPrefs.code(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(48), dp(20), dp(48), dp(20))
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(6) }
        })

        root.addView(TextView(this).apply {
            text = "Merso Translator TV"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "كود الربط — اكتبه في تطبيق الموبايل\nPairing code — enter it in the phone app"
            textSize = 15f
            setTextColor(Color.parseColor("#B0B7C3"))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(2))
        })

        root.addView(TextView(this).apply {
            text = code
            textSize = 48f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FFC400"))
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
        })

        statusText = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#8A93A3"))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(10))
        }
        root.addView(statusText)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        root.addView(btnRow)

        permBtn = bigButton("تفعيل العرض فوق التطبيقات\nAllow display over other apps") {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {}
        }
        btnRow.addView(permBtn)

        toggleBtn = bigButton("") {
            if (OverlayService.running) {
                stopService(Intent(this, OverlayService::class.java))
            } else {
                if (!Settings.canDrawOverlays(this)) {
                    try {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    } catch (_: Exception) {}
                } else {
                    val i = Intent(this, OverlayService::class.java)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                }
            }
            handler.postDelayed({ refreshUi() }, 600)
        }
        btnRow.addView(toggleBtn)

        val updateBtn = bigButton("التحقق من التحديثات\nCheck for updates") {
            TvUpdater.checkManual(this)
        }
        btnRow.addView(updateBtn)

        root.addView(TextView(this).apply {
            text = "الخطوات: ١) فعّل العرض فوق التطبيقات  ٢) اضغط تشغيل  ٣) حوّل الشاشة على مدخل HDMI بتاع الـPS5 — الترجمة هتظهر فوق اللعبة\n" +
                "Steps: 1) Allow display over other apps  2) Press Start  3) Switch the TV to the PS5 HDMI input — subtitles will appear over the game"
            textSize = 13f
            setTextColor(Color.parseColor("#8A93A3"))
            gravity = Gravity.CENTER
            setPadding(dp(60), dp(14), dp(60), 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })
        toggleBtn.requestFocus()
        refreshUi()
        TvUpdater.checkOnLaunch(this)
    }

    override fun onResume() {
        super.onResume()
        handler.post(updater)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updater)
    }

    private fun refreshUi() {
        val canOverlay = Settings.canDrawOverlays(this)
        permBtn.visibility = if (canOverlay) View.GONE else View.VISIBLE
        toggleBtn.text = if (OverlayService.running)
            "إيقاف الترجمة\nStop translation"
        else
            "تشغيل الترجمة\nStart translation"
        statusText.text = when {
            !canOverlay -> "لازم تفعّل العرض فوق التطبيقات الأول\nAllow display over other apps first"
            OverlayService.running && OverlayService.phoneOnline -> "الموبايل متصل ✓ — حوّل على مدخل HDMI بتاع الـPS5\nPhone connected ✓ — switch to the PS5 HDMI input"
            OverlayService.running && OverlayService.mqttConnected -> "مستني الموبايل… اكتب الكود في تطبيق الموبايل\nWaiting for the phone… enter the code in the phone app"
            OverlayService.running -> "بيتصل…\nConnecting…" + if (OverlayService.lastError.isNotEmpty()) "\n" + OverlayService.lastError else ""
            else -> "جاهز — اضغط تشغيل\nReady — press Start"
        }
    }

    private fun bigButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#C00000"))
            }
            setPadding(dp(28), dp(12), dp(28), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6); bottomMargin = dp(6); marginStart = dp(8); marginEnd = dp(8) }
            setOnClickListener { onClick() }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
