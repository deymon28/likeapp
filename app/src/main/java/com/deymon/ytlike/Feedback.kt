package com.deymon.ytlike

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast

/**
 * Distinct buzzes so the result is clear with the phone in a pocket:
 * one short = done, two short = removed (undo like), one long = failed.
 */
object Feedback {
    fun show(context: Context, result: Like.Result) {
        val effect = when {
            !result.ok -> VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE)
            result.undo -> VibrationEffect.createWaveform(longArrayOf(0, 50, 120, 50), -1)
            else -> VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        val vibrator = context.getSystemService(Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= 33) {
            // Accessibility usage isn't muted by silent mode, unlike the default one.
            vibrator?.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ACCESSIBILITY))
        } else {
            vibrator?.vibrate(effect)
        }
        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
    }
}
