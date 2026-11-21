package com.merso.gametranslate

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import com.merso.gametranslate.Ui.dp
import com.metallic.chiaki.lib.DiscoveryService
import com.metallic.chiaki.lib.DiscoveryServiceOptions
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors

// Offline activation + NP fake sign-in via the bundled "PS5 Account Tools"
// payload (GPL-3.0), so Remote Play works on local accounts that were never
// linked to PSN. The payload is sent to the console (Payload Manager on
// port 8084, or raw TCP to an ELF loader on 9021) and then driven over its
// HTTP API on port 6769. The resulting Account ID is stored in Prefs in the
// exact same format as the Sony sign-in path, so chiaki registration works
// unchanged.
class FakeSignInActivity : Activity() {

    private data class Acct(
        val userId: Long,
        val name: String,
        val slot: Int,
        val activated: Boolean,
        val signedIn: Boolean,
        val accountIdHex: String,
        val accountIdB64: String
    )

    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var ipInput: EditText
    private lateinit var status: TextView
    private lateinit var sendBtn: Button
    private lateinit var accountsBox: LinearLayout
    private lateinit var doneBox: LinearLayout
    private var busy = false
    private var discovery: DiscoveryService? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    companion object {
        private const val ELF_ASSET = "ps5-account-tools.elf"
        private const val ELF_NAME = "ps5-account-tools.elf"
        private const val PAYLOAD_PORT = 6769
        private const val PLDMGR_PORT = 8084
        private const val ELFLOADER_PORT = 9021
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = Ui.screenRoot(this)
        root.addView(Ui.header(this, Str.p("تفعيل بدون PSN", "Activation without PSN"), Str.p("تسجيل وهمي لتشغيل الريموت بلاي على الحسابات المحلية", "Fake sign-in to enable Remote Play on local accounts")))

        // What + safety
        val c0 = Ui.card(this)
        c0.addView(Ui.cardTitle(this, Str.p("إيه ده؟", "What is this?")))
        c0.addView(Ui.bodyText(this, Str.p(
            "لو حسابك على الـPS5 المعدل محلي وعمره ما اتربط بـPSN، الجهاز هيطلب تسجيل دخول ومش هيشغل الريموت بلاي. الأداة دي بتفعّل الحساب أوفلاين وتعمل تسجيل دخول وهمي، فالريموت بلاي يشتغل من غير PSN خالص، وكله من جوه التطبيق.",
            "If your account on the jailbroken PS5 is local and was never linked to PSN, the console demands a sign-in and Remote Play won't work. This tool activates the account offline and performs a fake sign-in, so Remote Play works with no PSN at all — all from inside the app."
        )))
        c0.addView(Ui.bodyText(this, Str.p(
            "⚠️ مهم قبل ما تبدأ:\n• اعمل نسخة احتياطية من حفظات اللعب (Saves) الأول — تغيير رقم الحساب ممكن يفصل الحفظات عن الحساب.\n• متفعّلش الحساب الأساسي (Slot 1) لو هو حساب PSN حقيقي متفعل أصلًا.\n• الأحسن تعمل مستخدم محلي جديد على الجهاز وتفعّله هو.",
            "⚠️ Important before you start:\n• Back up your game saves first — changing the account ID can disassociate saves from the account.\n• Do not activate the main account (Slot 1) if it is already a legit PSN-activated account.\n• Best practice: create a new local user on the console and activate that one."
        ), 14f, "#FFC400"))
        root.addView(c0)

        // Step 1: IP + send payload
        val c1 = Ui.card(this)
        c1.addView(Ui.cardTitle(this, Str.p("الخطوة ١ — إرسال أداة التفعيل للجهاز", "Step 1 — Send the activation tool to the console")))
        c1.addView(Ui.bodyText(this, Str.p(
            "اتأكد إن الـPS5 شغال والجيلبريك شغال وPayload Manager مفتوح، وإن التليفون على نفس الشبكة. اكتب عنوان IP بتاع الجهاز (هتلاقيه في Payload Manager) أو دوس بحث.",
            "Make sure the PS5 is on, the jailbreak is running and Payload Manager is open, and the phone is on the same network. Enter the console IP address (shown in Payload Manager) or tap Search."
        )))
        ipInput = Ui.inputField(this, Str.p("عنوان IP بتاع الـPS5", "PS5 IP address"), number = false)
        ipInput.inputType = android.text.InputType.TYPE_CLASS_TEXT
        ipInput.setText(Prefs.hostIp(this))
        c1.addView(ipInput)
        c1.addView(Ui.secondaryButton(this, Str.p("🔎 بحث عن الجهاز على الشبكة", "🔎 Search for the console")).apply {
            setOnClickListener { discoverIp() }
        })
        sendBtn = Ui.primaryButton(this, Str.p("إرسال أداة التفعيل للجهاز", "Send activation tool to console")).apply {
            setOnClickListener { sendPayload() }
        }
        c1.addView(sendBtn)
        status = Ui.bodyText(this, "", 14f, Ui.TEXT).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }
        c1.addView(status)
        root.addView(c1)

        // Step 2: accounts
        val c2 = Ui.card(this)
        c2.addView(Ui.cardTitle(this, Str.p("الخطوة ٢ — اختار الحساب اللي هيتفعّل", "Step 2 — Pick the account to activate")))
        c2.addView(Ui.bodyText(this, Str.p(
            "الحسابات اللي داخلة على الجهاز دلوقتي هتظهر هنا. دوس على الحساب عشان يتفعّل ويتسجل وهمي.",
            "Accounts currently logged in on the console will appear here. Tap an account to activate it and fake sign it in."
        )))
        accountsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c2.addView(accountsBox)
        c2.addView(Ui.secondaryButton(this, Str.p("↻ تحديث قايمة الحسابات", "↻ Refresh account list")).apply {
            setOnClickListener { if (!busy) loadAccounts(currentIp()) }
        })
        root.addView(c2)

        // Step 3: after success
        doneBox = Ui.card(this)
        doneBox.addView(Ui.cardTitle(this, Str.p("الخطوة ٣ — بعد التفعيل", "Step 3 — After activation")))
        doneBox.addView(Ui.bodyText(this, Str.p(
            "١) اعمل إعادة تشغيل (Restart) للـPS5 — خطوة إجبارية.\n٢) بعد الريستارت، سجّل دخول على الجهاز بنفس الحساب اللي فعّلته («${Prefs.fakeAccountName(this)}») وخليه هو الحساب اللي شغال قدامك على الشاشة.\n٣) ⚠️ الأهم: على الجهاز روح الإعدادات ← النظام ← Remote Play وشغّل «Enable Remote Play» (لازم تبقى ON) وانت داخل بنفس الحساب ده. من سوفت 10.00 وطالع الإعداد ده لكل حساب لوحده، ولو مقفول البث هيترفض حتى لو الـPIN اتحفظ.\n٤) بعدها افتح ربط جهاز وخد رقم PIN جديد (الرقم القديم مش هيشتغل).\n٥) ارجع لصفحة الريموت بلاي في التطبيق وحط رقم الـPIN الجديد في الخطوة ٢ ودوس حفظ، وبعدين دوس بدء البث.\n\nملحوظة: خلي الجهاز أوفلاين أو احجب PSN (زي nanoDNS) عشان الجهاز ميكتشفش إنه مش مسجل بجد ويلغي التسجيل الوهمي. لو إعدادات الريموت بلاي اتقفلت أو الزرار بقى رمادي، احجب PSN واعمل إعادة تشغيل. مش هتحتاج تعيد التفعيل تاني.",
            "1) Restart the PS5 — this step is mandatory.\n2) After the restart, log in on the console with the same account you activated (“${Prefs.fakeAccountName(this)}”) and keep it as the account on screen.\n3) ⚠️ Most important: on the console go to Settings → System → Remote Play and turn ON “Enable Remote Play” while signed in with this same account. Since firmware 10.00 this setting is per-user; if it is OFF the stream will be refused even if the PIN saved fine.\n4) Then open Link Device and get a NEW PIN (the old PIN won't work).\n5) Go back to the Remote Play page in the app, enter the NEW PIN in Step 2 and tap Save, then tap Start Stream.\n\nNote: keep the console offline or block PSN (e.g. nanoDNS) so the console doesn't realize it isn't really signed in and clear the fake sign-in. If the Remote Play settings lock or the toggle is greyed out, block PSN and restart. You won't need to activate again."
        )))
        doneBox.addView(Ui.secondaryButton(this, Str.p("إيقاف أداة التفعيل على الجهاز", "Stop the activation tool on the console")).apply {
            setOnClickListener { exitPayload() }
        })
        doneBox.visibility = View.GONE
        root.addView(doneBox)

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        Ui.animateChildren(root)
    }

    private fun currentIp(): String = ipInput.text.toString().trim()

    private fun setStatus(msg: String, color: String = Ui.TEXT) {
        main.post {
            status.text = msg
            status.setTextColor(Color.parseColor(color))
        }
    }

    private fun setBusy(b: Boolean) {
        busy = b
        main.post { sendBtn.isEnabled = !b }
    }

    // ---------- discovery ----------

    private fun discoverIp() {
        if (busy) return
        setBusy(true)
        setStatus(Str.p("بيدور على الـPS5 على الشبكة...", "Searching for your PS5 on the network..."))
        var done = false
        val timeout = Runnable {
            if (!done) {
                done = true
                cleanupDiscovery()
                setBusy(false)
                setStatus(Str.p("مش لاقي الجهاز. اكتب الـIP يدوي من Payload Manager", "Can't find the console. Enter the IP manually from Payload Manager"), "#FF2E2E")
            }
        }
        main.postDelayed(timeout, 9000)
        try {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("merso-fake").apply {
                setReferenceCounted(false)
                acquire()
            }
            discovery = DiscoveryService(
                DiscoveryServiceOptions(16U, 3U, 500U, InetSocketAddress("255.255.255.255", 987))
            ) { hosts ->
                val ps5 = hosts.firstOrNull { it.isPS5 && it.hostAddr != null }
                if (ps5 != null && !done) {
                    done = true
                    main.removeCallbacks(timeout)
                    val ip = ps5.hostAddr!!
                    main.post {
                        ipInput.setText(ip)
                        cleanupDiscovery()
                        setBusy(false)
                        setStatus(Str.p("لقيت الجهاز: $ip ✓ دوس إرسال أداة التفعيل", "Found the console: $ip ✓ Tap Send activation tool"), "#2ED573")
                    }
                }
            }
        } catch (_: Exception) {
            main.removeCallbacks(timeout)
            cleanupDiscovery()
            setBusy(false)
            setStatus(Str.p("البحث فشل. اكتب الـIP يدوي", "Search failed. Enter the IP manually"), "#FF2E2E")
        }
    }

    private fun cleanupDiscovery() {
        try { discovery?.dispose() } catch (_: Exception) {}
        try { multicastLock?.release() } catch (_: Exception) {}
        discovery = null
        multicastLock = null
    }

    // ---------- payload delivery ----------

    private fun sendPayload() {
        val ip = currentIp()
        if (ip.isEmpty()) {
            setStatus(Str.p("اكتب عنوان IP بتاع الجهاز الأول", "Enter the console IP address first"), "#FF2E2E")
            return
        }
        if (busy) return
        setBusy(true)
        Prefs.setHostIp(this, ip)
        exec.execute {
            // Already running?
            if (fetchAccounts(ip) != null) {
                setStatus(Str.p("أداة التفعيل شغالة على الجهاز أصلًا ✓", "The activation tool is already running on the console ✓"), "#2ED573")
                setBusy(false)
                loadAccounts(ip)
                return@execute
            }
            val elf = try {
                assets.open(ELF_ASSET).use { it.readBytes() }
            } catch (_: Exception) {
                setStatus(Str.p("ملف الأداة مش موجود في التطبيق", "Tool file missing inside the app"), "#FF2E2E")
                setBusy(false)
                return@execute
            }
            var sent = false
            var lastErr = ""
            // Path A: Payload Manager (port 8084) upload + load
            setStatus(Str.p("بيبعت الأداة عن طريق Payload Manager...", "Sending the tool via Payload Manager..."))
            try {
                uploadViaPldmgr(ip, elf)
                loadViaPldmgr(ip)
                sent = true
            } catch (e: Exception) {
                lastErr = e.message ?: "pldmgr"
            }
            // Path B: raw TCP to ELF loader (port 9021)
            if (!sent) {
                setStatus(Str.p("Payload Manager مردش، بيجرب الإرسال المباشر (بورت 9021)...", "Payload Manager didn't answer, trying direct send (port 9021)..."))
                try {
                    sendRawTcp(ip, ELFLOADER_PORT, elf)
                    sent = true
                } catch (e: Exception) {
                    lastErr = e.message ?: "tcp"
                }
            }
            if (!sent) {
                setBusy(false)
                setStatus(Str.p(
                    "الإرسال فشل ($lastErr). اتأكد إن الجيلبريك شغال وPayload Manager مفتوح على الجهاز والتليفون على نفس الشبكة، وجرب تاني",
                    "Sending failed ($lastErr). Make sure the jailbreak is running, Payload Manager is open on the console, and the phone is on the same network, then try again"
                ), "#FF2E2E")
                return@execute
            }
            setStatus(Str.p("اتبعتت ✓ بيستنى الأداة تشتغل على الجهاز...", "Sent ✓ waiting for the tool to start on the console..."))
            var up = false
            for (i in 1..15) {
                Thread.sleep(2000)
                if (fetchAccounts(ip) != null) { up = true; break }
            }
            setBusy(false)
            if (up) {
                setStatus(Str.p("الأداة اشتغلت ✓ اختار الحساب من تحت", "Tool is running ✓ pick an account below"), "#2ED573")
                loadAccounts(ip)
            } else {
                setStatus(Str.p(
                    "الأداة اتبعتت بس مش راضية تشتغل. جرب تدوس إرسال تاني، أو افتح Payload Manager على الجهاز وشغلها منه يدوي",
                    "The tool was sent but won't start. Tap Send again, or open Payload Manager on the console and launch it manually"
                ), "#FF2E2E")
            }
        }
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

    // ---------- payload API (port 6769) ----------

    private fun httpGet(urlStr: String, timeoutMs: Int = 8000): String? {
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

    private fun parseAccounts(json: String): List<Acct> {
        val out = mutableListOf<Acct>()
        try {
            val arr = JSONObject(json).optJSONArray("accounts") ?: return out
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(Acct(
                    userId = o.optLong("userId", -1),
                    name = o.optString("name", ""),
                    slot = o.optInt("slot", 0),
                    activated = o.optBoolean("activated", false),
                    signedIn = o.optBoolean("signedIn", false),
                    accountIdHex = o.optString("accountId", ""),
                    accountIdB64 = o.optString("accountIdB64", "")
                ))
            }
        } catch (_: Exception) {}
        return out
    }

    private fun fetchAccounts(ip: String): List<Acct>? {
        val body = httpGet("http://$ip:$PAYLOAD_PORT/api/accounts", 4000) ?: return null
        return parseAccounts(body)
    }

    private fun loadAccounts(ip: String) {
        if (ip.isEmpty()) {
            setStatus(Str.p("اكتب عنوان IP بتاع الجهاز الأول", "Enter the console IP address first"), "#FF2E2E")
            return
        }
        exec.execute {
            val list = fetchAccounts(ip)
            main.post {
                if (list == null) {
                    setStatus(Str.p("أداة التفعيل مش شغالة على الجهاز. دوس إرسال أداة التفعيل الأول", "The activation tool is not running on the console. Tap Send activation tool first"), "#FF2E2E")
                } else if (list.isEmpty()) {
                    setStatus(Str.p("مفيش حسابات داخلة على الجهاز دلوقتي. سجل دخول بحساب على الجهاز وجرب تاني", "No accounts are logged in on the console right now. Log in with an account on the console and try again"), "#FFC400")
                    renderAccounts(emptyList())
                } else {
                    renderAccounts(list)
                }
            }
        }
    }

    private fun renderAccounts(list: List<Acct>) {
        accountsBox.removeAllViews()
        for (a in list) {
            val ready = a.activated && a.signedIn && a.accountIdB64.isNotEmpty()
            val state = when {
                ready -> Str.p("متفعل ومتسجل ✓ جاهز للريموت بلاي", "Activated & signed in ✓ ready for Remote Play")
                a.activated -> Str.p("متفعل، لسه محتاج تسجيل وهمي", "Activated, still needs fake sign-in")
                else -> Str.p("مش متفعل", "Not activated")
            }
            val row = Ui.secondaryButton(this, "${a.name}  —  $state").apply {
                if (ready) {
                    background = Ui.gradientBg(this@FakeSignInActivity, intArrayOf(Color.parseColor("#2ED573"), Color.parseColor("#1E9E57")), 14)
                }
                setOnClickListener { confirmAccount(a) }
            }
            accountsBox.addView(row)
        }
    }

    private fun confirmAccount(a: Acct) {
        val ready = a.activated && a.signedIn && a.accountIdB64.isNotEmpty()
        val slotWarn = if (a.slot == 1) Str.p(
            "\n\n⚠️ ده الحساب الأساسي (Slot 1). لو هو حساب PSN حقيقي متفعل أصلًا، متفعّلوش تاني عشان حفظاتك متضيعش.",
            "\n\n⚠️ This is the main account (Slot 1). If it is already a legit PSN-activated account, do not activate it again or your saves may be lost."
        ) else ""
        val msg = if (ready) {
            Str.p(
                "الحساب «${a.name}» متفعل ومتسجل أصلًا. تستخدمه للريموت بلاي؟ هيتحفظ رقم حسابه في التطبيق.",
                "Account “${a.name}” is already activated and signed in. Use it for Remote Play? Its account ID will be saved in the app."
            )
        } else {
            Str.p(
                "هتفعّل الحساب «${a.name}» أوفلاين وتعمل له تسجيل دخول وهمي.\n\n⚠️ اعمل نسخة احتياطية من حفظات اللعب الأول — تغيير رقم الحساب ممكن يفصل الحفظات عن الحساب.$slotWarn",
                "This will activate account “${a.name}” offline and fake sign it in.\n\n⚠️ Back up your game saves first — changing the account ID can disassociate saves from the account.$slotWarn"
            )
        }
        AlertDialog.Builder(this)
            .setTitle(Str.p("تأكيد التفعيل", "Confirm activation"))
            .setMessage(msg)
            .setPositiveButton(Str.p("فعّل", "Activate")) { _, _ ->
                if (ready) saveAndFinish(a) else activateAccount(a)
            }
            .setNegativeButton(Str.p("إلغاء", "Cancel"), null)
            .show()
    }

    private fun activateAccount(a: Acct) {
        val ip = currentIp()
        if (busy) return
        setBusy(true)
        setStatus(Str.p("بيفعّل الحساب «${a.name}»...", "Activating account “${a.name}”..."))
        exec.execute {
            var ok = true
            var failMsg = ""
            if (!a.activated) {
                val r = httpGet("http://$ip:$PAYLOAD_PORT/api/accounts/activate?user=${a.userId}")
                val j = r?.let { try { JSONObject(it) } catch (_: Exception) { null } }
                if (j == null || !j.optBoolean("ok", false)) {
                    ok = false
                    failMsg = j?.optString("message", "") ?: ""
                }
            }
            if (ok) {
                setStatus(Str.p("التفعيل تمام ✓ بيعمل تسجيل وهمي...", "Activation done ✓ fake signing in..."))
                val r2 = httpGet("http://$ip:$PAYLOAD_PORT/api/accounts/signin?user=${a.userId}")
                val j2 = r2?.let { try { JSONObject(it) } catch (_: Exception) { null } }
                if (j2 == null || !j2.optBoolean("ok", false)) {
                    ok = false
                    failMsg = j2?.optString("message", "") ?: ""
                }
            }
            setBusy(false)
            if (!ok) {
                setStatus(Str.p(
                    "التفعيل فشل${if (failMsg.isNotEmpty()) ": $failMsg" else ""}. اتأكد إن الحساب ده هو اللي داخل على الجهاز دلوقتي وجرب تاني",
                    "Activation failed${if (failMsg.isNotEmpty()) ": $failMsg" else ""}. Make sure this account is the one logged in on the console and try again"
                ), "#FF2E2E")
                loadAccounts(ip)
                return@execute
            }
            val fresh = fetchAccounts(ip)?.firstOrNull { it.userId == a.userId }
            main.post {
                if (fresh != null && fresh.accountIdB64.isNotEmpty()) {
                    saveAndFinish(fresh)
                } else {
                    setStatus(Str.p("التفعيل خلص بس رقم الحساب مرجعش. دوس تحديث القايمة ودوس على الحساب تاني", "Activation finished but the account ID didn't come back. Refresh the list and tap the account again"), "#FFC400")
                    loadAccounts(ip)
                }
            }
        }
    }

    private fun saveAndFinish(a: Acct) {
        val bytes = try {
            android.util.Base64.decode(a.accountIdB64, android.util.Base64.DEFAULT)
        } catch (_: Exception) {
            null
        }
        if (bytes == null || bytes.size != 8) {
            setStatus(Str.p("رقم الحساب اللي رجع مش مظبوط، جرب تاني", "The returned account ID is invalid, try again"), "#FF2E2E")
            return
        }
        // Same storage format as the Sony sign-in path, so chiaki
        // registration (PIN verify + stream) works with no Sony login.
        // Pairing keys from any previous account are invalid for this
        // account ID — clear them so a fresh Regist with a fresh console
        // PIN is forced (reusing old keys = "Unknown Session Request Error").
        if (Prefs.accountId(this) != a.accountIdB64) Prefs.clearPairing(this)
        Prefs.setAccountId(this, a.accountIdB64)
        Prefs.setFakeActivated(this, true)
        Prefs.setFakeAccountName(this, a.name)
        Prefs.setFakeUserId(this, a.userId)
        Prefs.setHostIp(this, currentIp())
        Prefs.setPinVerified(this, false)
        setStatus(Str.p(
            "تمام ✓ الحساب «${a.name}» جاهز للريموت بلاي من غير PSN. اعمل إعادة تشغيل للجهاز وكمل من الخطوة ٣ تحت",
            "Done ✓ account “${a.name}” is ready for Remote Play without PSN. Restart the console and continue from Step 3 below"
        ), "#2ED573")
        doneBox.visibility = View.VISIBLE
        AlertDialog.Builder(this)
            .setTitle(Str.p("⚠️ خطوات إجبارية قبل البث", "⚠️ Mandatory steps before streaming"))
            .setMessage(Str.p(
                "١) اعمل إعادة تشغيل (Restart) للـPS5.\n٢) سجّل دخول على الجهاز بنفس الحساب «${a.name}» وخليه هو الحساب اللي شغال قدامك.\n٣) ⚠️ روح الإعدادات ← النظام ← Remote Play وشغّل «Enable Remote Play» (ON) للحساب ده — من غيرها البث هيترفض حتى لو الـPIN اتحفظ.\n٤) افتح ربط جهاز (Link Device) وخد رقم PIN جديد.\n٥) في التطبيق: الخطوة ٢ حط الـPIN الجديد ودوس حفظ، وبعدين بدء البث.\n\nالربط القديم اتمسح، فالـPIN القديم مش هيشتغل. وخلي الجهاز أوفلاين أو احجب PSN (nanoDNS) عشان التسجيل الوهمي ميتلغيش.",
                "1) Restart the PS5.\n2) Log in on the console with the same account “${a.name}” and keep it on screen.\n3) ⚠️ Go to Settings → System → Remote Play and turn ON “Enable Remote Play” for this account — without it the stream will be refused even if the PIN saved fine.\n4) Open Link Device and get a NEW PIN.\n5) In the app: enter the NEW PIN in Step 2, tap Save, then Start Stream.\n\nThe old pairing was cleared, so the old PIN won't work. Keep the console offline or block PSN (nanoDNS) so the fake sign-in isn't cleared."
            ))
            .setPositiveButton(Str.p("تمام", "OK"), null)
            .show()
        // Best effort: stop the payload server on the console.
        val ip = currentIp()
        exec.execute { httpGet("http://$ip:$PAYLOAD_PORT/api/exit", 4000) }
    }

    private fun exitPayload() {
        val ip = currentIp()
        if (ip.isEmpty()) return
        exec.execute {
            httpGet("http://$ip:$PAYLOAD_PORT/api/exit", 4000)
            setStatus(Str.p("اتقفلت أداة التفعيل على الجهاز", "Activation tool stopped on the console"))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupDiscovery()
        exec.shutdown()
    }

    override fun onBackPressed() {
        Nav.goHome(this)
    }
}
