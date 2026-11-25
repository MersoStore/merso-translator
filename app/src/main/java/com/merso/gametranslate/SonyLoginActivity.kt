package com.merso.gametranslate

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.app.Activity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.Executors

class SonyLoginActivity : Activity() {

    companion object {
        // Same public Remote Play client used by open-source chiaki-ng
        private const val CLIENT_ID = "ba495a24-818c-472b-b12d-ff231c1b5745"
        private const val REDIRECT = "https://remoteplay.dl.playstation.net/remoteplay/redirect"
        private const val TOKEN_URL = "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/token"
        private const val SCOPE =
            "psn:clientapp referenceDataService:countryConfig.read pushNotification:webSocket.desktop.connect sessionManager:remotePlaySession.system.update"

        private fun duid(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return "0000000700410080" + bytes.joinToString("") { "%02x".format(it) }
        }

        private fun loginUrl(): String =
            "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/authorize" +
            "?service_entity=urn:service-entity:psn&response_type=code" +
            "&client_id=$CLIENT_ID&redirect_uri=$REDIRECT" +
            "&scope=${URLEncoder.encode(SCOPE, "UTF-8")}" +
            "&request_locale=en_US&ui=pr&service_logo=ps&layout_type=popup&smcid=remoteplay" +
            "&prompt=always&PlatformPrivacyWs1=minimal&duid=${duid()}&"

        private fun basicAuth(): String =
            "Basic " + Base64.getEncoder().encodeToString("$CLIENT_ID:${PsnSecret.CLIENT_SECRET}".toByteArray())
    }

    private val exec = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0E14"))
        }

        status = TextView(this).apply {
            text = Str.p("سجّل دخول بحسابك الثانوي في صفحة سوني الرسمية", "Sign in with your second account on Sony's official page")
            setTextColor(Color.parseColor(Ui.GOLD))
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            setPadding(32, 28, 32, 20)
        }
        root.addView(status)

        web = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url != null && url.startsWith(REDIRECT)) {
                        val code = android.net.Uri.parse(url).getQueryParameter("code")
                        if (!code.isNullOrEmpty()) {
                            onAuthCode(code)
                        } else {
                            status.text = Str.p("رجعنا من سوني من غير كود، جرب تاني", "Came back from Sony without a code, try again")
                        }
                        return true
                    }
                    return false
                }
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        root.addView(web)

        setContentView(root)
        web.loadUrl(loginUrl())
    }

    private fun onAuthCode(code: String) {
        status.text = Str.p("تم تسجيل الدخول، بيكمل الربط...", "Signed in, finishing linking...")
        exec.execute {
            val accountId = fetchAccountId(code)
            runOnUiThread {
                if (accountId != null) {
                    if (Prefs.accountId(this) != accountId) Prefs.clearPairing(this)
                    Prefs.setAccountId(this, accountId)
                    status.text = Str.p("تمام ✓ الحساب الثانوي اتربط", "Done ✓ second account linked")
                    Toast.makeText(this, Str.p("تم تسجيل دخول PSN الثانوي", "Second PSN signed in"), Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    status.text = Str.p("سجلت دخولك، بس رقم الحساب مجاش. اقفل الصفحة وجرب تاني", "Signed in, but the account ID didn't arrive. Close this page and try again")
                }
            }
        }
    }

    private fun fetchAccountId(code: String): String? {
        return try {
            val body = "grant_type=authorization_code&code=${URLEncoder.encode(code, "UTF-8")}" +
                "&scope=${URLEncoder.encode(SCOPE, "UTF-8")}&redirect_uri=$REDIRECT&"
            val conn = URL(TOKEN_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("Authorization", basicAuth())
            conn.outputStream.use { it.write(body.toByteArray()) }
            val resp = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
                .bufferedReader().readText()
            conn.disconnect()
            val j = JSONObject(resp)
            val token = j.optString("access_token")
            if (token.isEmpty()) return null
            Prefs.setPsnToken(this, token)

            val conn2 = URL("$TOKEN_URL/$token").openConnection() as HttpURLConnection
            conn2.connectTimeout = 15000
            conn2.readTimeout = 15000
            conn2.setRequestProperty("Authorization", basicAuth())
            conn2.setRequestProperty("Accept", "application/json")
            val resp2 = (if (conn2.responseCode in 200..299) conn2.inputStream else conn2.errorStream)
                .bufferedReader().readText()
            conn2.disconnect()
            val userId = JSONObject(resp2).optString("user_id")
            val num = userId.toLongOrNull() ?: return null
            val bytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(num).array()
            Base64.getEncoder().encodeToString(bytes)
        } catch (e: Exception) {
            null
        }
    }


    override fun onBackPressed() {
        // Return to whoever opened sign-in (wizard PIN card or Remote Play),
        // not the main interface.
        finish()
    }
}
