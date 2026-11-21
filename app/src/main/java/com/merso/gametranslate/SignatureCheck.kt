package com.merso.gametranslate

import android.content.Context
import android.content.pm.PackageManager

// Verifies the app was signed with Merso's own release key. A copy that was
// modified and re-signed by someone else fails this check and won't run.
object SignatureCheck {
    private const val EXPECTED_REVERSED = "8853c1e517481d519395586600085a47d1aeeaed569486885f87aae8c1457538"

    fun ok(ctx: Context): Boolean {
        return try {
            val pm = ctx.packageManager
            val sigs = if (android.os.Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val cert = sigs?.firstOrNull()?.toByteArray() ?: return false
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val hex = md.digest(cert).joinToString("") { "%02x".format(it) }
            hex == EXPECTED_REVERSED.reversed()
        } catch (_: Exception) {
            false
        }
    }
}
