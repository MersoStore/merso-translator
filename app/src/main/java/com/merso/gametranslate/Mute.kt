package com.merso.gametranslate

import android.content.Context
import android.media.AudioManager
import com.metallic.chiaki.lib.chiakiSetAudioMuted

object Mute {
    private var savedVolume = -1
    var muted = false
        private set
    private var nativeOk: Boolean? = null

    fun toggle(ctx: Context): Boolean {
        if (nativeOk == null) {
            nativeOk = try {
                chiakiSetAudioMuted(false)
                true
            } catch (_: UnsatisfiedLinkError) {
                false
            } catch (_: Exception) {
                false
            }
        }
        if (nativeOk == true) {
            muted = !muted
            try { chiakiSetAudioMuted(muted) } catch (_: Exception) {}
            return muted
        }
        // Fallback for ABIs without the patched native lib: phone media volume
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (!muted) {
            savedVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            muted = true
        } else {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, if (savedVolume >= 0) savedVolume else 5, 0)
            muted = false
        }
        return muted
    }

    fun unmute(ctx: Context) {
        if (muted) toggle(ctx)
    }
}
