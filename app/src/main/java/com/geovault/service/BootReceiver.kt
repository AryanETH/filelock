package com.geovault.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val actions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_REBOOT,
            "com.geovault.WATCHDOG"
        )
        if (intent.action in actions) {
            // Android 14/15 Restriction: Do not start FGS directly from background at boot
            // unless necessary. AccessibilityService will start it upon first event.
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                android.util.Log.d("BootReceiver", "Skipping direct FGS start on Android 14+. Waiting for Accessibility or Manual start.")
                return
            }

            val serviceIntent = Intent(context, AppLockerService::class.java)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                android.util.Log.e("BootReceiver", "Failed to start service: ${e.message}")
            }
        }
    }
}
