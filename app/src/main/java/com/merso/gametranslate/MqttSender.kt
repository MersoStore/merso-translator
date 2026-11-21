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
            val payload = """{"size":"${Prefs.tvSize(ctx)}","color":"${Prefs.tvColor(ctx)}","bg":${Prefs.tvBg(ctx)}}"""
            client?.publish(topic(code, "style"), MqttMessage(payload.toByteArray()).apply { qos = 0; isRetained = true })
        } catch (_: Exception) {}
    }

    fun sendCaption(ctx: Context, en: String, ar: String) {
        val code = Prefs.code(ctx)
        if (code.length < 4 || ar.isBlank()) return
        exec.execute {
            try {
                if (client?.isConnected != true) connect(ctx)
                val payload = JSONObject().apply {
                    put("en", en)
                    put("ar", ar)
                    put("t", System.currentTimeMillis())
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
