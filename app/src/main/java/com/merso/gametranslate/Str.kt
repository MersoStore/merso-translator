package com.merso.gametranslate

object Str {
    @Volatile var lang: String = "ar"

    fun p(ar: String, en: String): String = if (lang == "en") en else ar
}
