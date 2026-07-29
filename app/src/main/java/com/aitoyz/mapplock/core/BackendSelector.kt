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

    fun select(context: Context, scope: kotlinx.coroutines.CoroutineScope): ForegroundMonitor {
        val prefs = com.aitoyz.mapplock.security.SecureManager.getInstance(context).prefs
        val modeName = prefs.getString("monitoring_mode", Mode.AUTO.name) ?: Mode.AUTO.name
        val mode = try { Mode.valueOf(modeName) } catch (e: Exception) { Mode.AUTO }

        return when (mode) {
            Mode.USAGE_STATS -> UsageStatsMonitor(context)
            Mode.SHIZUKU -> ShizukuMonitor()
            Mode.ACCESSIBILITY -> AccessibilityMonitor()
            Mode.AUTO -> SmartMonitor(context, scope)
        }
    }
}
