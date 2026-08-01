package com.aitoyz.mapplock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aitoyz.mapplock.security.LockerLogger

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        // SECURITY GUARD: Ensure we only process allowed system actions.
        val allowedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "com.aitoyz.mapplock.WATCHDOG"
        )
        
        if (action !in allowedActions) return

        LockerLogger.d(LockerLogger.Event.SERVICE_RESTARTED, "[BOOT] onReceive: $action")
        
        val serviceIntent = Intent(context, AppLockerService::class.java)
        if (action == Intent.ACTION_USER_UNLOCKED) {
            serviceIntent.putExtra("refresh_locked_apps", true)
        }
            
            try {
                // Determine if we should start the fallback service
                // AccessibilityService starts itself via OS if enabled.
                // We always try to start AppLockerService as a fallback/guard.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                LockerLogger.d(LockerLogger.Event.SERVICE_RESTARTED, "[BOOT] Service start requested")
            } catch (e: Exception) {
                LockerLogger.e(LockerLogger.Event.ERROR, "[BOOT] Failed to start service: ${e.message}")
            }
    }
}
