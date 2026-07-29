package com.aitoyz.mapplock.core

import android.content.Context
import android.app.AppOpsManager
import android.os.Process
import com.aitoyz.mapplock.backend.usage.UsageStatsMonitor
import com.aitoyz.mapplock.backend.shizuku.ShizukuMonitor
import com.aitoyz.mapplock.backend.accessibility.AccessibilityMonitor

/**
 * Logic to select the appropriate foreground monitoring backend.
 */
object BackendSelector {

    enum class Mode {
        USAGE_STATS,
        SHIZUKU,
        ACCESSIBILITY,
        AUTO
    }

    fun select(context: Context): ForegroundMonitor {
        val prefs = com.aitoyz.mapplock.security.SecureManager.getInstance(context).prefs
        val modeName = prefs.getString("monitoring_mode", Mode.AUTO.name) ?: Mode.AUTO.name
        val mode = try { Mode.valueOf(modeName) } catch (e: Exception) { Mode.AUTO }

        return when (mode) {
            Mode.USAGE_STATS -> UsageStatsMonitor(context)
            Mode.SHIZUKU -> ShizukuMonitor()
            Mode.ACCESSIBILITY -> AccessibilityMonitor()
            Mode.AUTO -> autoSelect(context)
        }
    }

    private fun autoSelect(context: Context): ForegroundMonitor {
        return when {
            isUsageStatsPermissionGranted(context) -> UsageStatsMonitor(context)
            // Future: check Shizuku or Accessibility
            else -> UsageStatsMonitor(context) // Default fallback
        }
    }

    private fun isUsageStatsPermissionGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
