package com.geovault.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticHelper {
    fun vibrate(context: Context, strength: Int = 1) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = when(strength) {
                0 -> 50  // Subtle
                2 -> 255 // Strong
                else -> 150 // Medium
            }
            vibrator.vibrate(VibrationEffect.createOneShot(50, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }
}
