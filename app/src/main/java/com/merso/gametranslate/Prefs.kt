package com.merso.gametranslate

import android.content.Context

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("merso", Context.MODE_PRIVATE)

    fun lang(ctx: Context): String = sp(ctx).getString("lang", "") ?: ""
    fun setLang(ctx: Context, v: String) = sp(ctx).edit().putString("lang", v).apply()

    fun introSeen(ctx: Context): Boolean = sp(ctx).getBoolean("intro_seen", false)
    fun setIntroSeen(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("intro_seen", v).apply()


    fun code(ctx: Context): String = sp(ctx).getString("code", "") ?: ""
    fun setCode(ctx: Context, v: String) = sp(ctx).edit().putString("code", v).apply()

    fun pin(ctx: Context): String = sp(ctx).getString("pin", "") ?: ""
    fun setPin(ctx: Context, v: String) = sp(ctx).edit().putString("pin", v).apply()

    fun pinVerified(ctx: Context): Boolean = sp(ctx).getBoolean("pin_verified", false)
    fun setPinVerified(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("pin_verified", v).apply()

    fun psnToken(ctx: Context): String = sp(ctx).getString("psn_token", "") ?: ""
    fun setPsnToken(ctx: Context, v: String) = sp(ctx).edit().putString("psn_token", v).apply()

    fun accountId(ctx: Context): String = sp(ctx).getString("account_id", "") ?: ""
    fun setAccountId(ctx: Context, v: String) = sp(ctx).edit().putString("account_id", v).apply()

    fun signedIn(ctx: Context): Boolean = psnToken(ctx).isNotEmpty() || accountId(ctx).isNotEmpty()

    // Fake sign-in / offline activation (PS5 Account Tools payload).
    // The account ID itself is stored via setAccountId() in the same
    // Base64 format as the Sony path; these flags only mark its origin.
    fun fakeActivated(ctx: Context): Boolean = sp(ctx).getBoolean("fake_activated", false)
    fun setFakeActivated(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("fake_activated", v).apply()

    fun fakeAccountName(ctx: Context): String = sp(ctx).getString("fake_account_name", "") ?: ""
    fun setFakeAccountName(ctx: Context, v: String) = sp(ctx).edit().putString("fake_account_name", v).apply()

    // Console-side user id of the fake-activated account, needed to re-run
    // the payload's /signin for the same account ("re-confirm" button).
    fun fakeUserId(ctx: Context): Long = sp(ctx).getLong("fake_user_id", 0L)
    fun setFakeUserId(ctx: Context, v: Long) = sp(ctx).edit().putLong("fake_user_id", v).apply()

    fun registKey(ctx: Context): String = sp(ctx).getString("regist_key", "") ?: ""
    fun setRegistKey(ctx: Context, v: String) = sp(ctx).edit().putString("regist_key", v).apply()

    // Drop all pairing state (regist key + morning key + PIN). Must be
    // called whenever the account ID changes (Sony sign-in with a
    // different account, or fake activation): keys paired under the old
    // account ID make the console reject the session request
    // ("Unknown Session Request Error").
    fun clearPairing(ctx: Context) = sp(ctx).edit()
        .putString("regist_key", "")
        .putString("morning", "")
        .putString("pin", "")
        .putBoolean("pin_verified", false)
        .apply()

    fun morning(ctx: Context): String = sp(ctx).getString("morning", "") ?: ""
    fun setMorning(ctx: Context, v: String) = sp(ctx).edit().putString("morning", v).apply()

    fun hostIp(ctx: Context): String = sp(ctx).getString("host_ip", "") ?: ""
    fun setHostIp(ctx: Context, v: String) = sp(ctx).edit().putString("host_ip", v).apply()

    // Subtitle crop region as fractions of the frame (left, top, right, bottom)
    fun crop(ctx: Context): FloatArray {
        val s = sp(ctx).getString("crop3", "0.15,0.75,0.85,0.99") ?: "0.15,0.75,0.85,0.99"
        val p = s.split(",").mapNotNull { it.toFloatOrNull() }
        return if (p.size == 4) floatArrayOf(p[0], p[1], p[2], p[3]) else floatArrayOf(0.15f, 0.75f, 0.85f, 0.99f)
    }
    fun tvSize(ctx: Context): String = sp(ctx).getString("tv_size", "M") ?: "M"
    fun setTvSize(ctx: Context, v: String) = sp(ctx).edit().putString("tv_size", v).apply()

    fun tvColor(ctx: Context): String = sp(ctx).getString("tv_color", "white") ?: "white"
    fun setTvColor(ctx: Context, v: String) = sp(ctx).edit().putString("tv_color", v).apply()

    fun tvBg(ctx: Context): Int = sp(ctx).getInt("tv_bg", 45)
    fun setTvBg(ctx: Context, v: Int) = sp(ctx).edit().putInt("tv_bg", v).apply()

    fun camCrop(ctx: Context): FloatArray {
        val s = sp(ctx).getString("cam_crop", "0.15,0.55,0.85,0.95") ?: "0.15,0.55,0.85,0.95"
        val p = s.split(",").mapNotNull { it.toFloatOrNull() }
        return if (p.size == 4) floatArrayOf(p[0], p[1], p[2], p[3])
        else floatArrayOf(0.15f, 0.55f, 0.85f, 0.95f)
    }
    fun setCamCrop(ctx: Context, l: Float, t: Float, r: Float, b: Float) =
        sp(ctx).edit().putString("cam_crop", "$l,$t,$r,$b").apply()

    fun setCrop(ctx: Context, l: Float, t: Float, r: Float, b: Float) =
        sp(ctx).edit().putString("crop3", "$l,$t,$r,$b").apply()
}
