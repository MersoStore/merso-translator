package com.merso.gametranslate.tv

import android.content.Context
import kotlin.random.Random

object TvPrefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("merso_tv", Context.MODE_PRIVATE)

    fun code(ctx: Context): String {
        val p = sp(ctx)
        var c = p.getString("code", "") ?: ""
        if (c.length < 6) {
            c = (100000 + Random.nextInt(900000)).toString()
            p.edit().putString("code", c).apply()
        }
        return c
    }

    fun styleSize(ctx: Context): String = sp(ctx).getString("style_size", "M") ?: "M"
    fun styleColor(ctx: Context): String = sp(ctx).getString("style_color", "white") ?: "white"
    fun styleBg(ctx: Context): Int = sp(ctx).getInt("style_bg", 45)

    fun saveStyle(ctx: Context, size: String?, color: String?, bg: Int?) {
        val e = sp(ctx).edit()
        if (size != null) e.putString("style_size", size)
        if (color != null) e.putString("style_color", color)
        if (bg != null) e.putInt("style_bg", bg)
        e.apply()
    }
}
