package com.merso.gametranslate.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.concurrent.Executors

class OverlayService : Service() {

    companion object {
        private const val TAG = "MersoTvOverlay"
        private val BROKERS = listOf(
            "wss://broker.emqx.io:8084/mqtt",
            "tcp://broker.emqx.io:1883",
            "ssl://broker.emqx.io:8883",
            "tcp://broker.hivemq.com:1883",
            "ssl://broker.hivemq.com:8883"
        )
        @Volatile var running = false
        @Volatile var mqttConnected = false
        @Volatile var phoneOnline = false
        @Volatile var lastCaptionAt = 0L
        @Volatile var lastError = ""
        @Volatile var currentBroker = ""
    }

    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private var wm: WindowManager? = null
    private var container: android.widget.FrameLayout? = null
    private val captionViews = mutableMapOf<Int, TextView>()
    private val hideRunnables = mutableMapOf<Int, Runnable>()
    @Volatile private var client: MqttClient? = null
    private var code = ""
    @Volatile private var destroyed = false

    private val watchdog = object : Runnable {
        override fun run() {
            if (destroyed) return
            if (client?.isConnected != true) {
                mqttConnected = false
                connectMqtt()
            } else {
                // heartbeat: refresh retained presence so the phone always sees us
                exec.execute {
                    try {
                        client?.publish(
                            topic("presence"),
                            MqttMessage("""{"role":"tv"}""".toByteArray()).apply { qos = 0; isRetained = true }
                        )
                    } catch (_: Exception) {}
                }
            }
            main.postDelayed(this, 15000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        code = TvPrefs.code(this)
        startForegroundWithNotification()
        showOverlay()
        connectMqtt()
        main.postDelayed(watchdog, 15000)
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        destroyed = true
        running = false
        mqttConnected = false
        phoneOnline = false
        main.removeCallbacks(watchdog)
        try { container?.let { wm?.removeView(it) } } catch (_: Exception) {}
        container = null
        captionViews.clear()
        exec.execute {
            try { client?.disconnect() } catch (_: Exception) {}
            try { client?.close() } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("merso_tv", "Merso Translator TV", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n = Notification.Builder(this, "merso_tv")
            .setContentTitle("Merso Translator TV")
            .setContentText("الترجمة شغالة / Translation is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(7, n)
    }

    // ---------- Overlay ----------

    private fun showOverlay() {
        wm = getSystemService(WindowManager::class.java)
        val root = android.widget.FrameLayout(this)
        container = root
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm?.addView(root, params)
        } catch (e: Exception) {
            Log.e(TAG, "overlay add failed", e)
            lastError = "overlay: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    // Cairo (Black weight) bundled in assets — the caption font.
    private var cachedTypeface: Typeface? = null
    private fun captionTypeface(): Typeface {
        cachedTypeface?.let { return it }
        val tf = try {
            Typeface.createFromAsset(assets, "cairo.ttf")
        } catch (_: Exception) {
            Typeface.DEFAULT_BOLD
        }
        cachedTypeface = tf
        return tf
    }

    private fun getCaptionView(box: Int): TextView {
        captionViews[box]?.let { return it }
        val screenW = resources.displayMetrics.widthPixels
        val tv = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = captionTypeface()
            setShadowLayer(8f, 0f, 0f, Color.BLACK)
            setPadding(dp(28), dp(14), dp(28), dp(14))
            maxWidth = (screenW * 0.8f).toInt()
            visibility = View.GONE
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        applyStyleTo(tv)
        container?.addView(tv)
        captionViews[box] = tv
        return tv
    }

    private fun applyStyle() {
        captionViews.values.forEach { applyStyleTo(it) }
    }

    private fun applyStyleTo(tv: TextView) {
        val sizeSp = when (TvPrefs.styleSize(this)) {
            "S" -> 17f
            "L" -> 33f
            "H" -> 42f
            else -> 25f
        }
        tv.textSize = sizeSp
        if (tv.tag != "custom") {
            tv.setTextColor(if (TvPrefs.styleColor(this) == "yellow") Color.parseColor("#FFD400") else Color.WHITE)
        }
        val alpha = (TvPrefs.styleBg(this).coerceIn(0, 100) * 255 / 100)
        tv.background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(Color.argb(alpha, 0, 0, 0))
        }
    }

    private fun showCaption(ar: String, box: Int, x: Float, y: Float, colorHex: String?) {
        main.post {
            val tv = getCaptionView(box)
            tv.text = ar
            if (colorHex != null) {
                try {
                    tv.setTextColor(Color.parseColor(colorHex))
                    tv.tag = "custom"
                } catch (_: Exception) {}
            } else {
                tv.tag = null
                applyStyleTo(tv)
            }
            tv.visibility = View.VISIBLE
            val screenW = resources.displayMetrics.widthPixels
            val screenH = resources.displayMetrics.heightPixels
            tv.post {
                tv.x = (screenW * x - tv.width / 2f).coerceIn(0f, maxOf(0f, (screenW - tv.width).toFloat()))
                tv.y = (screenH * y - tv.height / 2f).coerceIn(0f, maxOf(0f, (screenH - tv.height).toFloat()))
            }
            hideRunnables[box]?.let { main.removeCallbacks(it) }
            val readMs = when (TvPrefs.styleDur(this)) {
                "S" -> maxOf(2500L, minOf(7000L, 1800L + ar.length * 55L))
                "L" -> maxOf(5000L, minOf(15000L, 3500L + ar.length * 110L))
                else -> maxOf(3500L, minOf(10000L, 2500L + ar.length * 80L))
            }
            val r = Runnable { tv.visibility = View.GONE }
            hideRunnables[box] = r
            main.postDelayed(r, readMs)
        }
    }

    // ---------- MQTT ----------

    private fun topic(kind: String) = "merso/gametranslate/v1/$code/$kind"

    private fun connectMqtt() {
        exec.execute {
            if (destroyed) return@execute
            if (client?.isConnected == true) return@execute
            try { client?.close() } catch (_: Exception) {}
            client = null
            for (broker in BROKERS) {
                if (destroyed) return@execute
                try {
                    currentBroker = broker
                    val cid = "merso-tv-" + System.currentTimeMillis()
                    val c = MqttClient(broker, cid, MemoryPersistence())
                    c.setCallback(object : MqttCallback {
                        override fun connectionLost(cause: Throwable?) {
                            mqttConnected = false
                            lastError = "lost: " + (cause?.message ?: "")
                            Log.w(TAG, "lost: $cause")
                        }
                        override fun messageArrived(topic: String?, message: MqttMessage?) {
                            val payload = message?.payload?.decodeToString() ?: return
                            try {
                                val msg = JSONObject(payload)
                                when {
                                    topic?.endsWith("/style") == true -> {
                                        TvPrefs.saveStyle(
                                            this@OverlayService,
                                            if (msg.has("size")) msg.getString("size") else null,
                                            if (msg.has("color")) msg.getString("color") else null,
                                            if (msg.has("bg")) msg.getInt("bg") else null,
                                            if (msg.has("dur")) msg.getString("dur") else null
                                        )
                                        main.post { applyStyle() }
                                    }
                                    topic?.endsWith("/presence") == true -> {
                                        if (msg.optString("role") == "phone") phoneOnline = true
                                    }
                                    topic?.endsWith("/sub") == true -> {
                                        if (msg.optInt("clear") == 1) {
                                            val g = if (msg.has("box")) msg.getInt("box") else -1
                                            main.post {
                                                for ((k, v) in captionViews) {
                                                    if (g < 0 || k == g || (k >= g * 10 && k < g * 10 + 10)) {
                                                        v.visibility = View.GONE
                                                        hideRunnables[k]?.let { main.removeCallbacks(it) }
                                                        hideRunnables.remove(k)
                                                    }
                                                }
                                            }
                                        } else {
                                            val ar = msg.optString("ar")
                                            if (ar.isNotBlank()) {
                                                phoneOnline = true
                                                lastCaptionAt = System.currentTimeMillis()
                                                val box = if (msg.has("box")) msg.getInt("box") else 0
                                                val x = if (msg.has("x")) msg.getDouble("x").toFloat() else 0.5f
                                                val y = if (msg.has("y")) msg.getDouble("y").toFloat() else 0.9f
                                                val color = msg.optString("color").ifBlank { null }
                                                showCaption(ar, box, x, y, color)
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                    })
                    val opts = MqttConnectOptions()
                    opts.isCleanSession = true
                    opts.connectionTimeout = 10
                    opts.keepAliveInterval = 30
                    opts.isAutomaticReconnect = true
                    c.connect(opts)
                    c.subscribe(topic("sub"))
                    c.subscribe(topic("style"))
                    c.subscribe(topic("presence"))
                    c.publish(
                        topic("presence"),
                        MqttMessage("""{"role":"tv"}""".toByteArray()).apply { qos = 0; isRetained = true }
                    )
                    client = c
                    mqttConnected = true
                    lastError = ""
                    Log.i(TAG, "connected to $broker, code=$code")
                    return@execute
                } catch (e: Exception) {
                    mqttConnected = false
                    lastError = broker.substringAfter("://").substringBefore(":") + ": " +
                        (e.message ?: e.javaClass.simpleName)
                    Log.e(TAG, "connect failed on $broker", e)
                }
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
