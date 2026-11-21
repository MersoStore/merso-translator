package com.merso.gametranslate

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.TextureView
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView

class StreamActivity : Activity() {

    private lateinit var texture: TextureView
    private lateinit var status: TextView
    private lateinit var caption: TextView
    private lateinit var debug: TextView
    private lateinit var muteBtn: android.widget.Button
    private lateinit var pipBtn: android.widget.Button
    private var controller: StreamController? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        texture = TextureView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(texture)

        status = TextView(this).apply {
            text = Str.p("بيدور على الـPS5 على الشبكة...", "Searching for your PS5 on the network...")
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#99000000"))
            gravity = Gravity.CENTER
            setPadding(24, 14, 24, 14)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP
            )
        }
        root.addView(status)

        debug = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#8A93A3"))
            setBackgroundColor(Color.parseColor("#99000000"))
            gravity = Gravity.CENTER
            setPadding(16, 8, 16, 8)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP
            ).apply { topMargin = 110 }
        }
        root.addView(debug)

        caption = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.parseColor("#FFC400"))
            setBackgroundColor(Color.parseColor("#CC000000"))
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 16)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
            )
        }
        root.addView(caption)

        pipBtn = android.widget.Button(this).apply {
            text = Str.p("تصغير", "Minimize")
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            )
            setOnClickListener { enterPip() }
        }
        root.addView(pipBtn)

        muteBtn = android.widget.Button(this).apply {
            text = Str.p("🔇 كتم الصوت", "🔇 Mute")
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START
            )
            setOnClickListener {
                val m = Mute.toggle(this@StreamActivity)
                text = if (m) Str.p("🔊 تشغيل الصوت", "🔊 Unmute") else Str.p("🔇 كتم الصوت", "🔇 Mute")
            }
        }
        root.addView(muteBtn)
        setContentView(root)

        val existing = StreamHolder.controller
        if (existing != null && existing.running) {
            controller = existing
            existing.onStatus = { status.text = it }
            existing.onCaption = { caption.text = it }
            existing.onDebug = { debug.text = it }
            existing.onLoginPin = { incorrect, submit -> askLoginPin(incorrect, submit) }
            existing.attach(texture)
            StreamHolder.handedOff = false
            status.text = Str.p("متصل ✓ الترجمة شغالة", "Connected ✓ translation running")
        } else {
            controller = StreamController(
                this, texture,
                onStatus = { status.text = it },
                onCaption = { caption.text = it },
                onDebug = { debug.text = it },
                onLoginPin = { incorrect, submit -> askLoginPin(incorrect, submit) }
            )
            StreamHolder.controller = controller
            controller?.start()
        }

        if (intent.getBooleanExtra("autoPip", false)) {
            main.postDelayed({ enterPip() }, 1500)
        }
    }

    private fun askLoginPin(incorrect: Boolean, submit: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = Str.p("رقم PIN تسجيل دخول المستخدم على الجهاز", "User sign-in PIN on the console")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle(if (incorrect) Str.p("الرقم غلط، جرب تاني", "Wrong PIN, try again") else Str.p("الجهاز طالب رقم PIN", "The console is asking for a PIN"))
            .setView(input)
            .setPositiveButton(Str.p("تمام", "OK")) { _, _ -> submit(input.text.toString()) }
            .setNegativeButton(Str.p("إلغاء", "Cancel"), null)
            .show()
    }

    private fun enterPip(): Boolean {
        inPip = true
        return try {
            val params = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(16, 9))
                .build()
            val ok = enterPictureInPictureMode(params)
            if (!ok) inPip = false
            ok
        } catch (_: Exception) {
            inPip = false
            false
        }
    }

    private var inPip = false

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (controller?.running == true) enterPip()
    }

    override fun onStop() {
        super.onStop()
        // PiP window closed by the user: stop the stream.
        if (inPip) {
            inPip = false
            controller?.stop()
            if (StreamHolder.controller === controller) StreamHolder.controller = null
            controller = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Mute.unmute(this)
        if (isFinishing && !StreamHolder.handedOff) {
            controller?.stop()
            if (StreamHolder.controller === controller) StreamHolder.controller = null
        }
        controller = null
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) controller?.attach(texture)
        try {
            val vis = if (isInPictureInPictureMode) android.view.View.GONE else android.view.View.VISIBLE
            status.visibility = vis
            debug.visibility = vis
            caption.visibility = vis
            muteBtn.visibility = vis
            pipBtn.visibility = vis
        } catch (_: Exception) {}
    }

    override fun onBackPressed() {
        if (controller?.running == true) {
            StreamHolder.handedOff = true
            controller?.detach()
            controller = null
            startActivity(android.content.Intent(this, RemotePlayActivity::class.java))
            finish()
        } else {
            Nav.goHome(this)
        }
    }
}
