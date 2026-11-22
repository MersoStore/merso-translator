package com.merso.gametranslate

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.concurrent.Executors

object MqttSender {
    private const val TAG = "MqttSender"
    private val BROKERS = listOf(
        "wss://broker.emqx.io:8084/mqtt",
        "tcp://broker.emqx.io:1883",
        "ssl://broker.emqx.io:8883",
        "tcp://broker.hivemq.com:1883",
        "ssl://broker.hivemq.com:8883"
    )
    private val exec = Executors.newSingleThreadExecutor()
    private var client: MqttClient? = null
    @Volatile var tvOnline: Boolean = false
    @Volatile var connected: Boolean = false
    @Volatile var lastError: String = ""

    fun topic(code: String, kind: String) = "merso/gametranslate/v1/$code/$kind"

    fun connect(ctx: Context) {
        val code = Prefs.code(ctx)
        if (code.length < 4) return
        exec.execute {
            if (client?.isConnected == true) return@execute
            for (broker in BROKERS) {
            try {
                val cid = "merso-phone-" + System.currentTimeMillis()
                val c = MqttClient(broker, cid, MemoryPersistence())
                c.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        connected = false
                        tvOnline = false
                        Log.w(TAG, "lost: $cause")
                    }
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        if (topic?.endsWith("/presence") == true) {
                            val p = message?.payload?.decodeToString() ?: ""
                            // Ignore our own phone presence echo on the same topic
                            if (p.contains("\"tv\"")) tvOnline = true
                        }
                    }
                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })
                val opts = MqttConnectOptions()
                opts.isCleanSession = true
                opts.connectionTimeout = 10
                opts.keepAliveInterval = 30
                opts.isAutomaticReconnect = true
                c.connect(opts)
                c.subscribe(topic(code, "presence"))
                c.publish(
                    topic(code, "presence"),
                    MqttMessage("""{"role":"phone"}""".toByteArray()).apply { qos = 0; isRetained = true }
                )
                client = c
                connected = true
                publishStyle(ctx)
                lastError = ""
                Log.i(TAG, "connected to $broker, code=$code")
                return@execute
            } catch (e: Exception) {
                connected = false
                lastError = e.message ?: e.javaClass.simpleName
                Log.e(TAG, "connect failed on $broker", e)
            }
            }
        }
    }

    fun publishStyle(ctx: Context) {
        try {
            val code = Prefs.code(ctx)
            if (code.length < 4) return
            val payload = """{"size":"${Prefs.tvSize(ctx)}","color":"${Prefs.tvColor(ctx)}","bg":${Prefs.tvBg(ctx)},"dur":"${Prefs.tvDur(ctx)}"}"""
            client?.publish(topic(code, "style"), MqttMessage(payload.toByteArray()).apply { qos = 0; isRetained = true })
        } catch (_: Exception) {}
    }

    fun sendCaption(ctx: Context, en: String, ar: String) {
        sendCaption(ctx, en, ar, -1, 0.5f, 0.9f, null)
    }

    // Tells the TV to hide every caption currently on screen.
    fun sendClear(ctx: Context) {
        val code = Prefs.code(ctx)
        if (code.length < 4) return
        exec.execute {
            try {
                if (client?.isConnected != true) connect(ctx)
                client?.publish(topic(code, "sub"), MqttMessage("""{"clear":1}""".toByteArray()).apply { qos = 0 })
            } catch (e: Exception) {
                Log.e(TAG, "clear publish failed", e)
            }
        }
    }

    // Tells the TV to hide one box group's captions (the box id itself and
    // its per-line ids group*10 .. group*10+9).
    fun sendClearBox(ctx: Context, group: Int) {
        val code = Prefs.code(ctx)
        if (code.length < 4) return
        exec.execute {
            try {
                if (client?.isConnected != true) connect(ctx)
                client?.publish(topic(code, "sub"), MqttMessage("""{"clear":1,"box":$group}""".toByteArray()).apply { qos = 0 })
            } catch (e: Exception) {
                Log.e(TAG, "clear box publish failed", e)
            }
        }
    }

    // Sends one box result: when per-line positions are known, each
    // translated line goes to its own spot inside the box; otherwise a
    // single caption at the box center. The group's old captions are
    // cleared first so stale lines never linger.
    fun sendLines(ctx: Context, group: Int, box: FloatArray, en: String, ar: String, color: String?, lines: List<FloatArray>) {
        val arLines = ar.split("\n")
        val enLines = en.split("\n")
        sendClearBox(ctx, group)
        if (lines.isNotEmpty() && lines.size == arLines.size) {
            for (j in lines.indices) {
                val fx = box[0] + lines[j][0] * (box[2] - box[0])
                val fy = box[1] + lines[j][1] * (box[3] - box[1])
                sendCaption(ctx, enLines.getOrElse(j) { en }, arLines[j], group * 10 + j, fx, fy, color)
            }
        } else {
            sendCaption(ctx, en, ar, group, (box[0] + box[2]) / 2f, (box[1] + box[3]) / 2f, color)
        }
    }

    // box >= 0: the TV shows this caption at the box's position (x, y are
    // the box center as fractions of the screen). box = -1 keeps the old
    // bottom-caption behavior for callers without boxes (camera mode).
    // color ("#RRGGBB") when non-null: the TV shows the caption in that
    // color (auto mode = the game's own text color sampled from the frame).
    fun sendCaption(ctx: Context, en: String, ar: String, box: Int, x: Float, y: Float, color: String?) {
        val code = Prefs.code(ctx)
        if (code.length < 4 || ar.isBlank()) return
        exec.execute {
            try {
                if (client?.isConnected != true) connect(ctx)
                val payload = JSONObject().apply {
                    put("en", en)
                    put("ar", ar)
                    put("t", System.currentTimeMillis())
                    if (box >= 0) {
                        put("box", box)
                        put("x", x.toDouble())
                        put("y", y.toDouble())
                    }
                    if (color != null) put("color", color)
                }.toString()
                client?.publish(topic(code, "sub"), MqttMessage(payload.toByteArray()).apply { qos = 0 })
            } catch (e: Exception) {
                Log.e(TAG, "publish failed", e)
            }
        }
    }

    fun disconnect() {
        exec.execute {
            try { client?.disconnect() } catch (_: Exception) {}
            client = null
            connected = false
            tvOnline = false
        }
    }
}
