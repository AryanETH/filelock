package com.aitoyz.mapplock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val actions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_REBOOT,
            "com.aitoyz.mapplock.WATCHDOG"
        )
        if (intent.action in actions) {
            android.util.Log.d("BootReceiver", "[STABILITY] onReceive: ${intent.action}")
            
            val serviceIntent = Intent(context, AppLockerService::class.java)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                android.util.Log.d("BootReceiver", "[STABILITY] Service start requested")
            } catch (e: Exception) {
                android.util.Log.e("BootReceiver", "[STABILITY] Failed to start service: ${e.message}")
            }
        }
    }
}
