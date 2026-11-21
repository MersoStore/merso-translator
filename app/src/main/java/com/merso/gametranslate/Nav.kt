package com.merso.gametranslate

import android.app.Activity
import android.content.Intent

object Nav {
    fun goHome(act: Activity) {
        act.startActivity(Intent(act, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        act.finish()
    }

    fun goMode(act: Activity) {
        act.startActivity(Intent(act, ModeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        act.finish()
    }

    fun goHomeKeep(act: Activity) {
        act.startActivity(Intent(act, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
    }
}
