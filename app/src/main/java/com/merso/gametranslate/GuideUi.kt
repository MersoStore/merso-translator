package com.merso.gametranslate

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.merso.gametranslate.Ui.dp

object GuideUi {

    fun build(ctx: Context, startSection: Int = 0): LinearLayout {
        val root = Ui.screenRoot(ctx)
        root.addView(Ui.header(ctx, Str.p("طريقة الربط", "Setup Guide"), Str.p("اتبع الخطوات دي مرة واحدة بس", "Follow these steps once")))

        lateinit var tabTv: Button
        lateinit var tabPs5: Button
        val sectionTv = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val sectionPs5 = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        fun styleTab(b: Button, selected: Boolean) {
            if (selected) {
                b.background = Ui.roundedBg(ctx, Ui.GOLD, 10)
                b.setTextColor(Color.parseColor(Ui.DARK_TEXT))
            } else {
                b.background = Ui.roundedBg(ctx, Ui.CARD, 10)
                b.setTextColor(Color.parseColor(Ui.SUB))
            }
        }

        fun selectTab(tv: Boolean) {
            sectionTv.visibility = if (tv) View.VISIBLE else View.GONE
            sectionPs5.visibility = if (tv) View.GONE else View.VISIBLE
            styleTab(tabTv, tv)
            styleTab(tabPs5, !tv)
            Ui.animateChildren(if (tv) sectionTv else sectionPs5)
        }

        fun tabButton(text: String): Button = Button(ctx).apply {
            this.text = text
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(ctx.dp(8), ctx.dp(10), ctx.dp(8), ctx.dp(10))
        }

        val tabRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.roundedBg(ctx, Ui.CARD, 14, "#2E2E2E", 1)
            setPadding(ctx.dp(6), ctx.dp(6), ctx.dp(6), ctx.dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = ctx.dp(8) }
        }
        tabTv = tabButton(Str.p("إعدادات ربط الشاشة بالتليفون", "TV–Phone Pairing Setup"))
        tabPs5 = tabButton(Str.p("إعدادات جهاز PS5", "PS5 Setup"))
        tabRow.addView(tabTv)
        tabRow.addView(tabPs5)
        root.addView(tabRow)
        root.addView(sectionTv)
        root.addView(sectionPs5)

        tabTv.setOnClickListener { selectTab(true) }
        tabPs5.setOnClickListener { selectTab(false) }

        buildTvSection(ctx, sectionTv)
        buildPs5Section(ctx, sectionPs5)
        selectTab(startSection == 0)

        return root
    }

    private fun stepRow(ctx: Context, num: Int, text: String): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.roundedBg(ctx, Ui.CARD, 16, "#2E2E2E", 1)
            setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = ctx.dp(6); bottomMargin = ctx.dp(6) }
        }
        row.addView(TextView(ctx).apply {
            this.text = "$num"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(Ui.DARK_TEXT))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(Ui.GOLD))
            }
            layoutParams = LinearLayout.LayoutParams(ctx.dp(34), ctx.dp(34)).apply { marginEnd = ctx.dp(12) }
        })
        row.addView(Ui.bodyText(ctx, text, 15f, Ui.TEXT).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        return row
    }

    private fun buildTvSection(ctx: Context, sectionTv: LinearLayout) {
        val ip = TvServer.localIp(ctx)
        val address = if (ip != null) "$ip:${TvServer.PORT}" else Str.p("الرقم اللي ظاهر في الصفحة الرئيسية", "the address shown on the home screen")
        val steps = listOf(
            Str.p("نوع الشاشة LG أو Android TV أو Google TV — كلهم نفس الخطوات", "TV type: LG, Android TV, or Google TV — same steps for all"),
            Str.p("حمّل تطبيق Media Station X على التلفزيون", "Install the Media Station X app on the TV"),
            Str.p("ادخل على الإعدادات Settings جوه التطبيق", "Open Settings inside the app"),
            Str.p("اختار Start Parameter", "Choose Start Parameter"),
            Str.p("اختار Setup وبعدين Continue", "Choose Setup, then Continue"),
            Str.p("اكتب الرقم ده: $address\nوبعدين اضغط على علامة صح ✓ وبعدين اضغط Yes", "Enter this address: $address\nThen tap the check mark ✓, then tap Yes"),
            Str.p("اضغط على «افتح صفحة الترجمة» وبعدين Continue", "Tap 'Open Translation Page', then Continue"),
            Str.p("اكتب الكود اللي هيظهر على التلفزيون في التطبيق على الموبايل ودوس «حفظ الكود وربط»", "Enter the code shown on the TV into the phone app and tap 'Save Code & Connect'")
        )
        for ((i, step) in steps.withIndex()) sectionTv.addView(stepRow(ctx, i + 1, step))
        sectionTv.addView(Ui.bodyText(ctx, Str.p("ملحوظة: خلي الموبايل والتلفزيون على نفس شبكة الواي فاي.", "Note: keep the phone and TV on the same Wi-Fi network."), 13f, Ui.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, ctx.dp(12), 0, 0)
        })
    }

    private fun buildPs5Section(ctx: Context, sectionPs5: LinearLayout) {
        fun sectionTitle(t: String) = TextView(ctx).apply {
            text = t
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(Ui.GOLD))
            setPadding(0, ctx.dp(16), 0, ctx.dp(4))
        }

        sectionPs5.addView(sectionTitle(Str.p("١) إضافة مستخدم ثانٍ", "1) Add a Second User")))
        sectionPs5.addView(Ui.bodyText(
            ctx,
            Str.p("الـPS5 بيعامل الجهاز المتصل عن طريق Remote Play كأنه دراع المستخدم ده. لو الموبايل اتصل بحسابك الأساسي، الدراع اللي في إيدك هيفصل من التلفزيون. عشان كده الموبايل بيتصل كمستخدم منفصل، وانت بتكمل لعب بحسابك الأساسي عادي.", "The PS5 treats a device connected over Remote Play as that user's controller. If the phone connects with your main account, the controller in your hands gets disconnected from the TV. So the phone connects as a separate user, and you keep playing with your own account."),
            14f, Ui.SUB
        ))
        sectionPs5.addView(stepRow(ctx, 1, Str.p("ضيف مستخدم جديد على الـPS5 (من صورة البروفايل في الشاشة الرئيسية: تبديل المستخدم ← إضافة مستخدم)", "Add a new user on the PS5 (from your profile picture on the home screen: Switch User → Add User)")))
        sectionPs5.addView(stepRow(ctx, 2, Str.p("سجّل دخول للمستخدم ده بحساب PSN. لو معندكش حساب إضافي، اعمله مجانًا؛ مش محتاج PS Plus", "Sign this user in to a PSN account. If you don't have a spare one, create it for free; PS Plus is not needed")))
        sectionPs5.addView(stepRow(ctx, 3, Str.p("هتستخدم الحساب الثانوي ده في تسجيل دخول PSN على الموبايل كمان", "You'll use this second account for the PSN sign-in on the phone too")))

        sectionPs5.addView(sectionTitle(Str.p("٢) تشغيل Remote Play", "2) Turn On Remote Play")))
        sectionPs5.addView(stepRow(ctx, 1, Str.p("بدّل للمستخدم الثانوي على الـPS5", "Switch to the second user on the PS5")))
        sectionPs5.addView(stepRow(ctx, 2, Str.p("روح على الإعدادات ← النظام ← Remote Play", "Go to Settings → System → Remote Play")))
        sectionPs5.addView(stepRow(ctx, 3, Str.p("شغّل «تمكين Remote Play»", "Turn on 'Enable Remote Play'")))

        sectionPs5.addView(sectionTitle(Str.p("٣) الإيقاظ من وضع الراحة (اختياري)", "3) Waking from Rest Mode (Optional)")))
        sectionPs5.addView(Ui.bodyText(
            ctx,
            Str.p("عشان الموبايل يقدر يصحي الـPS5 لما تبدأ الترجمة وهو في وضع الراحة:", "To let the phone wake the PS5 when you start translation while it is in rest mode:"),
            14f, Ui.SUB
        ))
        sectionPs5.addView(stepRow(ctx, 1, Str.p("روح على الإعدادات ← النظام ← توفير الطاقة ← الميزات المتاحة في وضع الراحة", "Go to Settings → System → Power Saving → Features Available in Rest Mode")))
        sectionPs5.addView(stepRow(ctx, 2, Str.p("فعّل «البقاء متصلًا بالإنترنت» و«تمكين تشغيل PS5 من الشبكة»", "Enable 'Stay Connected to the Internet' and 'Enable Turning On PS5 from Network'")))
    }
}
