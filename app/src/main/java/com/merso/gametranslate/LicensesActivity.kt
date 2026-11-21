package com.merso.gametranslate

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import com.merso.gametranslate.Ui.dp

class LicensesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
        val root = Ui.screenRoot(this)
        root.addView(Ui.header(
            this,
            Str.p("حول / التراخيص", "About / Licenses"),
            Str.p("مرسو للترجمة — الإصدار $version", "Merso Translator — Version $version")
        ))

        // About the app
        val about = Ui.card(this)
        about.addView(Ui.cardTitle(this, Str.p("عن التطبيق", "About the app")))
        about.addView(Ui.bodyText(this, Str.p(
            "تطبيق مجاني من قناة Merso Gaming (@mersostore) لترجمة كلام الألعاب الإنجليزي للعربي على الشاشة أثناء اللعب.\n\nالتطبيق مش تابع لشركة Sony.",
            "A free app from the Merso Gaming channel (@mersostore) that translates English game text to Arabic on screen while you play.\n\nThis app is not affiliated with Sony."
        ), 15f, Ui.TEXT))
        about.addView(linkButton(Str.p("سورس التطبيق على GitHub", "App source on GitHub"), "https://github.com/MersoStore/merso-translator"))
        root.addView(about)

        // Chiaki
        val chiaki = Ui.card(this)
        chiaki.addView(Ui.cardTitle(this, Str.p("Chiaki — محرك الريموت بلاي", "Chiaki — Remote Play engine")))
        chiaki.addView(Ui.bodyText(this, Str.p(
            "بيستخدم التطبيق محرك Chiaki مفتوح المصدر لتشغيل الريموت بلاي.",
            "This app uses the open-source Chiaki engine for Remote Play."
        ), 15f, Ui.TEXT))
        chiaki.addView(Ui.bodyText(this, Str.p(
            "© مساهمو مشروع Chiaki\nالرخصة: GNU Affero General Public License v3 (AGPL-3.0)",
            "© Chiaki project contributors\nLicense: GNU Affero General Public License v3 (AGPL-3.0)"
        ), 14f, Ui.SUB))
        chiaki.addView(buttonRow(
            Str.p("نص الرخصة", "License text") to "https://www.gnu.org/licenses/agpl-3.0.html",
            Str.p("السورس", "Source") to "https://git.sr.ht/~thestr4ng3r/chiaki"
        ))
        root.addView(chiaki)

        // PS5 Account Tools
        val tools = Ui.card(this)
        tools.addView(Ui.cardTitle(this, Str.p("PS5 Account Tools — التفعيل بدون PSN", "PS5 Account Tools — no-PSN activation")))
        tools.addView(Ui.bodyText(this, Str.p(
            "بيستخدم التطبيق أداة PS5 Account Tools (الإصدار 1.2) المدمجة فيه لتفعيل الحسابات المحلية أوفلاين والتسجيل الوهمي.",
            "This app embeds PS5 Account Tools (version 1.2) for offline local-account activation and fake sign-in."
        ), 15f, Ui.TEXT))
        tools.addView(Ui.bodyText(this, Str.p(
            "© MangoScango ومساهموها\nالرخصة: GNU General Public License v3 (GPL-3.0)",
            "© MangoScango and contributors\nLicense: GNU General Public License v3 (GPL-3.0)"
        ), 14f, Ui.SUB))
        tools.addView(buttonRow(
            Str.p("نص الرخصة", "License text") to "https://www.gnu.org/licenses/gpl-3.0.html",
            Str.p("السورس", "Source") to "https://github.com/MangoScango/ps5-account-tools"
        ))
        root.addView(tools)

        // Notes
        val notes = Ui.card(this)
        notes.addView(Ui.cardTitle(this, Str.p("ملحوظات", "Notes")))
        notes.addView(Ui.bodyText(this, Str.p(
            "• تسجيل دخول سوني بيتم في صفحة سوني الرسمية جوه التطبيق، والتطبيق مش بيخزن كلمة السر.\n• الترجمة بتتم بخدمات ترجمة مجانية على النت، فممكن تبقى بطيئة أو تقف مؤقتًا لو الخدمة وصلت لحدها.",
            "• Sony sign-in happens on Sony's official page inside the app; the app does not store your password.\n• Translation uses free online services, so it may be slow or pause temporarily if a service hits its limit."
        ), 14f, Ui.SUB))
        root.addView(notes)

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        Ui.animateChildren(root)
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {}
    }

    private fun linkButton(text: String, url: String) = Ui.secondaryButton(this, text).apply {
        setOnClickListener { openUrl(url) }
    }

    private fun buttonRow(first: Pair<String, String>, second: Pair<String, String>): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        row.addView(Ui.smallButton(this, first.first).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
            setOnClickListener { openUrl(first.second) }
        })
        row.addView(Ui.smallButton(this, second.first).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { openUrl(second.second) }
        })
        return row
    }

    override fun onBackPressed() {
        Nav.goHome(this)
    }
}
