package com.merso.gametranslate

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    const val BG = "#0B0E14"
    const val CARD = "#161616"
    const val CARD2 = "#212121"
    const val GOLD = "#FF2E2E"
    const val TEXT = "#FFFFFF"
    const val SUB = "#B0B7C3"
    const val MUTED = "#8A93A3"
    const val DARK_TEXT = "#FFFFFF"

    fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    fun roundedBg(ctx: Context, color: String, radiusDp: Int, strokeColor: String? = null, strokeDp: Int = 1): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = ctx.dp(radiusDp).toFloat()
            setColor(Color.parseColor(color))
            if (strokeColor != null) setStroke(ctx.dp(strokeDp), Color.parseColor(strokeColor))
        }
    }

    fun gradientBg(ctx: Context, colors: IntArray, radiusDp: Int, orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.LEFT_RIGHT): GradientDrawable {
        return GradientDrawable(orientation, colors).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = ctx.dp(radiusDp).toFloat()
        }
    }

    fun primaryButton(ctx: Context, text: String): Button = Button(ctx).apply {
        this.text = text
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor(DARK_TEXT))
        background = gradientBg(ctx, intArrayOf(Color.parseColor("#E00000"), Color.parseColor("#8B0000")), 14)
        setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ctx.dp(8); bottomMargin = ctx.dp(8) }
    }

    fun secondaryButton(ctx: Context, text: String): Button = Button(ctx).apply {
        this.text = text
        textSize = 16f
        setTextColor(Color.parseColor(TEXT))
        background = roundedBg(ctx, CARD2, 14, GOLD, 1)
        setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(16), ctx.dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ctx.dp(8); bottomMargin = ctx.dp(8) }
    }

    fun smallButton(ctx: Context, text: String): Button = Button(ctx).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.parseColor(TEXT))
        background = roundedBg(ctx, CARD2, 10, "#3A3A3A", 1)
        setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(8))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = ctx.dp(4); marginEnd = ctx.dp(4); topMargin = ctx.dp(6) }
    }

    fun card(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBg(ctx, CARD, 18, "#2E2E2E", 1)
        setPadding(ctx.dp(20), ctx.dp(18), ctx.dp(20), ctx.dp(18))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ctx.dp(10); bottomMargin = ctx.dp(10) }
    }

    fun cardTitle(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor(GOLD))
        setPadding(0, 0, 0, ctx.dp(6))
    }

    fun bodyText(ctx: Context, text: String, size: Float = 15f, color: String = SUB): TextView = TextView(ctx).apply {
        this.text = text
        textSize = size
        setTextColor(Color.parseColor(color))
        setLineSpacing(0f, 1.25f)
    }

    fun inputField(ctx: Context, hint: String, number: Boolean = true): EditText = EditText(ctx).apply {
        this.hint = hint
        if (number) inputType = InputType.TYPE_CLASS_NUMBER
        setTextColor(Color.parseColor(TEXT))
        setHintTextColor(Color.parseColor(MUTED))
        textSize = 19f
        gravity = Gravity.CENTER
        background = roundedBg(ctx, CARD2, 12, "#3A3A3A", 1)
        setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(16), ctx.dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ctx.dp(6); bottomMargin = ctx.dp(6) }
    }

    fun animateIn(v: android.view.View, delayMs: Long) {
        v.alpha = 0f
        v.translationY = 50f
        v.animate().alpha(1f).translationY(0f)
            .setDuration(450)
            .setStartDelay(delayMs)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    fun animateChildren(container: LinearLayout) {
        for (i in 0 until container.childCount) {
            animateIn(container.getChildAt(i), i * 90L)
        }
    }

    fun screenRoot(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#050505"),
                Color.parseColor("#200505"),
                Color.parseColor("#0A0A0A")
            )
        )
        setPadding(ctx.dp(18), ctx.dp(24), ctx.dp(18), ctx.dp(24))
    }

    fun header(ctx: Context, title: String, subtitle: String): LinearLayout {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, ctx.dp(6), 0, ctx.dp(10))
        }
        box.addView(TextView(ctx).apply {
            text = title
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(GOLD))
            gravity = Gravity.CENTER
        })
        box.addView(TextView(ctx).apply {
            text = subtitle
            textSize = 14f
            setTextColor(Color.parseColor(SUB))
            gravity = Gravity.CENTER
            setPadding(0, ctx.dp(4), 0, 0)
        })
        return box
    }
}
