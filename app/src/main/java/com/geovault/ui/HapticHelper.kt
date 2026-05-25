package com.geovault.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticHelper {
    /**
     * Variable Haptic Feedback Strategy:
     * 0: Subtle (Light tap) - Keypads, sliders, small interactions.
     * 1: Medium (Firm tap) - Tab switches, confirmation toggles.
     * 2: Strong (Double/Heavy) - Errors, deletions, security alerts.
     */
    fun vibrate(context: Context, strength: Int = 1) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when(strength) {
                0 -> vibrator.vibrate(VibrationEffect.createOneShot(20, 40)) // Light
                2 -> {
                    // Double pulse for strong alerts
                    val timings = longArrayOf(0, 40, 60, 100)
                    val amplitudes = intArrayOf(0, 255, 0, 255)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                }
                else -> vibrator.vibrate(VibrationEffect.createOneShot(40, 150)) // Medium
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(strength.toLong() * 50 + 50)
        }
    }
}
