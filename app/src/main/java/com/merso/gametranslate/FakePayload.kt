package com.merso.gametranslate

import android.content.Context
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

// Minimal client for the bundled PS5 Account Tools payload, used by the
// "re-confirm fake sign-in" button on the Remote Play page: re-sends the
// ELF if needed and re-runs /signin for the already-activated account.
// Needed when the console cleared the fake sign-in state (e.g. it reached
// PSN and realized the sign-in isn't real). Full activation flow lives in
// FakeSignInActivity.
object FakePayload {

    private const val ELF_ASSET = "ps5-account-tools.elf"
    private const val ELF_NAME = "ps5-account-tools.elf"
    private const val PAYLOAD_PORT = 6769
    private const val PLDMGR_PORT = 8084
    private const val ELFLOADER_PORT = 9021

    fun httpGet(urlStr: String, timeoutMs: Int = 8000): String? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText()
            conn.disconnect()
            if (code in 200..299) body else null
        } catch (_: Exception) {
            null
        }
    }

    fun isUp(ip: String): Boolean =
        httpGet("http://$ip:$PAYLOAD_PORT/api/accounts", 4000) != null

    /** Sends the ELF (Payload Manager 8084, fallback raw 9021) and waits
     *  until the payload API answers. Returns true when it is up. */
    fun ensureUp(ctx: Context, ip: String): Boolean {
        if (isUp(ip)) return true
        val elf = try {
            ctx.assets.open(ELF_ASSET).use { it.readBytes() }
        } catch (_: Exception) {
            return false
        }
        var sent = false
        try {
            uploadViaPldmgr(ip, elf)
            loadViaPldmgr(ip)
            sent = true
        } catch (_: Exception) {}
        if (!sent) {
            try {
                sendRawTcp(ip, ELFLOADER_PORT, elf)
                sent = true
            } catch (_: Exception) {}
        }
        if (!sent) return false
        for (i in 1..15) {
            Thread.sleep(2000)
            if (isUp(ip)) return true
        }
        return false
    }

    /** Re-runs NP fake sign-in for the given console user id.
     *  Returns (ok, message). */
    fun signin(ip: String, userId: Long): Pair<Boolean, String> {
        val r = httpGet("http://$ip:$PAYLOAD_PORT/api/accounts/signin?user=$userId")
            ?: return false to ""
        val j = try { JSONObject(r) } catch (_: Exception) { null }
            ?: return false to ""
        return j.optBoolean("ok", false) to j.optString("message", "")
    }

    fun exit(ip: String) {
        httpGet("http://$ip:$PAYLOAD_PORT/api/exit", 4000)
    }

    private fun uploadViaPldmgr(ip: String, elf: ByteArray) {
        val url = URL("http://$ip:$PLDMGR_PORT/manage:upload?filename=$ELF_NAME&mode=replace")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 8000
        conn.readTimeout = 30000
        conn.setRequestProperty("X-PLDMGR-Request", "1")
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setFixedLengthStreamingMode(elf.size)
        conn.outputStream.use { it.write(elf) }
        val code = conn.responseCode
        conn.disconnect()
        if (code !in 200..299) throw Exception("upload HTTP $code")
    }

    private fun loadViaPldmgr(ip: String) {
        val url = URL("http://$ip:$PLDMGR_PORT/loadpayload:$ELF_NAME")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 8000
        conn.readTimeout = 15000
        conn.setRequestProperty("X-PLDMGR-Request", "1")
        conn.setFixedLengthStreamingMode(0)
        conn.outputStream.use { }
        val code = conn.responseCode
        conn.disconnect()
        if (code !in 200..299) throw Exception("load HTTP $code")
    }

    private fun sendRawTcp(ip: String, port: Int, data: ByteArray) {
        Socket().use { s ->
            s.connect(InetSocketAddress(ip, port), 8000)
            s.soTimeout = 15000
            val out: OutputStream = s.getOutputStream()
            out.write(data)
            out.flush()
            s.shutdownOutput()
        }
    }
}
