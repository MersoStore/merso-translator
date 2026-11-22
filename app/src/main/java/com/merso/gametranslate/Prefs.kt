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

    // Auto caption color: match the game's own text color (sampled from the frame)
    fun autoColor(ctx: Context): Boolean = sp(ctx).getBoolean("auto_color", true)
    fun setAutoColor(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("auto_color", v).apply()

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

    // Multiple subtitle boxes (each translated on its own area), stored as
    // "l,t,r,b;l,t,r,b;...". Falls back to the legacy single crop3 box.
    fun crops(ctx: Context): List<FloatArray> {
        val s = sp(ctx).getString("crops", "") ?: ""
        if (s.isNotBlank()) {
            val boxes = s.split(";").mapNotNull { part ->
                val p = part.split(",").mapNotNull { it.toFloatOrNull() }
                if (p.size == 4) floatArrayOf(p[0], p[1], p[2], p[3]) else null
            }
            if (boxes.isNotEmpty()) return boxes
        }
        return listOf(crop(ctx))
    }

    fun setCrops(ctx: Context, boxes: List<FloatArray>) {
        val s = boxes.joinToString(";") { "${it[0]},${it[1]},${it[2]},${it[3]}" }
        val e = sp(ctx).edit().putString("crops", s)
        if (boxes.isNotEmpty()) {
            e.putString("crop3", "${boxes[0][0]},${boxes[0][1]},${boxes[0][2]},${boxes[0][3]}")
        }
        e.apply()
        // Keep the active game profile in sync with the live boxes
        val active = activeProfile(ctx)
        if (active.isNotEmpty()) writeProfile(ctx, active, s)
    }

    // Caption display duration on TV: S short / M normal / L long
    fun tvDur(ctx: Context): String = sp(ctx).getString("tv_dur", "M") ?: "M"
    fun setTvDur(ctx: Context, v: String) = sp(ctx).edit().putString("tv_dur", v).apply()

    // Speak translations aloud on the phone (TTS)
    fun ttsOn(ctx: Context): Boolean = sp(ctx).getBoolean("tts_on", false)
    fun setTtsOn(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("tts_on", v).apply()

    // ---------- Game profiles (named box layouts) ----------
    // Stored as JSON object: {"<name>": "l,t,r,b;l,t,r,b", ...}
    fun profiles(ctx: Context): LinkedHashMap<String, List<FloatArray>> {
        val out = LinkedHashMap<String, List<FloatArray>>()
        try {
            val raw = sp(ctx).getString("profiles", "") ?: ""
            if (raw.isBlank()) return out
            val obj = org.json.JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val boxes = obj.optString(name).split(";").mapNotNull { part ->
                    val p = part.split(",").mapNotNull { it.toFloatOrNull() }
                    if (p.size == 4) floatArrayOf(p[0], p[1], p[2], p[3]) else null
                }
                if (boxes.isNotEmpty()) out[name] = boxes
            }
        } catch (_: Exception) {}
        return out
    }

    private fun writeProfile(ctx: Context, name: String, boxesStr: String) {
        try {
            val raw = sp(ctx).getString("profiles", "") ?: ""
            val obj = if (raw.isBlank()) org.json.JSONObject() else org.json.JSONObject(raw)
            obj.put(name, boxesStr)
            sp(ctx).edit().putString("profiles", obj.toString()).apply()
        } catch (_: Exception) {}
    }

    fun saveProfile(ctx: Context, name: String, boxes: List<FloatArray>) {
        val s = boxes.joinToString(";") { "${it[0]},${it[1]},${it[2]},${it[3]}" }
        writeProfile(ctx, name, s)
    }

    fun deleteProfile(ctx: Context, name: String) {
        try {
            val raw = sp(ctx).getString("profiles", "") ?: ""
            if (raw.isBlank()) return
            val obj = org.json.JSONObject(raw)
            obj.remove(name)
            sp(ctx).edit().putString("profiles", obj.toString()).apply()
            if (activeProfile(ctx) == name) setActiveProfile(ctx, "")
        } catch (_: Exception) {}
    }

    fun activeProfile(ctx: Context): String = sp(ctx).getString("active_profile", "") ?: ""
    fun setActiveProfile(ctx: Context, v: String) = sp(ctx).edit().putString("active_profile", v).apply()
}
