package com.merso.gametranslate

import android.app.Activity
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.ScrollView
import android.widget.TextView
import com.merso.gametranslate.Ui.dp

class LicensesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.1"
        } catch (_: Exception) {
            "1.1"
        }
        val root = Ui.screenRoot(this)
        root.addView(Ui.header(
            this,
            Str.p("حول / التراخيص", "About / Licenses"),
            Str.p("مرسو للترجمة — الإصدار $version", "Merso Translator — Version $version")
        ))
        val card = Ui.card(this)
        val body = TextView(this).apply {
            text = Str.p(AR_TEXT, EN_TEXT)
            textSize = 15f
            setTextColor(android.graphics.Color.parseColor(Ui.TEXT))
            setLineSpacing(0f, 1.25f)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        Linkify.addLinks(body, Linkify.WEB_URLS)
        body.movementMethod = LinkMovementMethod.getInstance()
        card.addView(body)
        root.addView(card)
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        Ui.animateChildren(root)
    }

    override fun onBackPressed() {
        Nav.goHome(this)
    }

    companion object {
        private const val AR_TEXT = """حول التطبيق
تطبيق مجاني من قناة Merso Gaming (@mersostore) لترجمة كلام الألعاب الإنجليزي للعربي على الشاشة أثناء اللعب.
التطبيق مش تابع لشركة Sony ولا لتطبيق سرد.

التراخيص
بيستخدم التطبيق محرك Chiaki مفتوح المصدر لتشغيل الريموت بلاي.
Chiaki © مساهمو مشروع Chiaki، ومرخّص تحت رخصة GNU Affero General Public License v3 (AGPL-3.0).
نص الرخصة الكامل: https://www.gnu.org/licenses/agpl-3.0.html
سورس Chiaki: https://git.sr.ht/~thestr4ng3r/chiaki

بيستخدم التطبيق كمان أداة PS5 Account Tools (الإصدار 1.2) المدمجة في التطبيق لتفعيل الحسابات المحلية أوفلاين والتسجيل الوهمي (خاصية التفعيل بدون PSN).
PS5 Account Tools © MangoScango ومساهموها، ومرخّصة تحت رخصة GNU General Public License v3 (GPL-3.0).
نص الرخصة الكامل: https://www.gnu.org/licenses/gpl-3.0.html
سورس الأداة: https://github.com/MangoScango/ps5-account-tools

تسجيل دخول سوني بيتم في صفحة سوني الرسمية جوا التطبيق، والتطبيق مش بيخزن كلمة السر.
الترجمة بتتم بخدمات ترجمة مجانية على النت، فممكن تبقى بطيئة أو تقف مؤقتًا لو الخدمة وصلت لحدها."""

        private const val EN_TEXT = """About
A free app from the Merso Gaming channel (@mersostore) that translates English game text to Arabic on screen while you play.
This app is not affiliated with Sony or with the SARD app.

Licenses
This app uses the open-source Chiaki engine for Remote Play.
Chiaki © Chiaki project contributors, licensed under the GNU Affero General Public License v3 (AGPL-3.0).
Full license text: https://www.gnu.org/licenses/agpl-3.0.html
Chiaki source: https://git.sr.ht/~thestr4ng3r/chiaki

This app also embeds PS5 Account Tools (version 1.2) for offline local-account activation and fake sign-in (the no-PSN activation feature).
PS5 Account Tools © MangoScango and contributors, licensed under the GNU General Public License v3 (GPL-3.0).
Full license text: https://www.gnu.org/licenses/gpl-3.0.html
Tool source: https://github.com/MangoScango/ps5-account-tools

Sony sign-in happens on Sony's official page inside the app; the app does not store your password.
Translation uses free online services, so it may be slow or pause temporarily if a service hits its limit."""
    }
}
