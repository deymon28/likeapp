package com.deymon.ytlike

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast

/** Short buzz on success, double buzz on failure, plus a toast. */
object Feedback {
    fun show(context: Context, result: Like.Result) {
        val effect = if (result.ok) {
            VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 60), -1)
        }
        context.getSystemService(Vibrator::class.java)?.vibrate(effect)
        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
    }
}
