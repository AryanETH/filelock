package com.aitoyz.mapplock.core

import android.content.Context
import com.aitoyz.mapplock.backend.accessibility.AccessibilityMonitor
import com.aitoyz.mapplock.backend.usage.UsageStatsMonitor
import com.aitoyz.mapplock.model.ForegroundEvent
import com.aitoyz.mapplock.security.LockerLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * A composite monitor that manages UsageStats and Accessibility backends with automatic fallback.
 */
class SmartMonitor(
    private val context: Context,
    private val scope: CoroutineScope
) : ForegroundMonitor {

    private val usageMonitor = UsageStatsMonitor(context)
    private val accessibilityMonitor = AccessibilityMonitor()

    private val _events = MutableSharedFlow<ForegroundEvent>(extraBufferCapacity = 50)
    override val events: Flow<ForegroundEvent> = _events.asSharedFlow()

    private var activeMonitor: ForegroundMonitor? = null
    private var lastEventTime = System.currentTimeMillis()
    private var monitorJob: Job? = null
    private var eventCollectionJob: Job? = null

    override fun start() {
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "SmartMonitor starting")
        monitorJob?.cancel()
        monitorJob = scope.launch {
            // Initial selection
            val initial = if (isUsageStatsPermissionGranted()) usageMonitor else accessibilityMonitor
            switchMonitor(initial)

            // Dynamic fallback/recovery loop
            while (isActive) {
                delay(2000)
                val now = System.currentTimeMillis()
                
                // If using UsageStats but no events for 3 seconds, and app is likely being used
                if (activeMonitor == usageMonitor && (now - lastEventTime > 3000)) {
                    LockerLogger.w(LockerLogger.Event.STATE_TRANSITION, "UsageStats inactivity (3s). Fallback to Accessibility.")
                    switchMonitor(accessibilityMonitor)
                }
                
                // Recovery: If using Accessibility but UsageStats permission is granted, try switching back
                if (activeMonitor == accessibilityMonitor && isUsageStatsPermissionGranted()) {
                    LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "UsageStats permission detected. Attempting recovery.")
                    switchMonitor(usageMonitor)
                }
            }
        }
    }

    override fun stop() {
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "SmartMonitor stopping")
        monitorJob?.cancel()
        eventCollectionJob?.cancel()
        usageMonitor.stop()
        accessibilityMonitor.stop()
    }

    override fun currentForeground(): String? = activeMonitor?.currentForeground()

    private fun switchMonitor(newMonitor: ForegroundMonitor) {
        if (activeMonitor == newMonitor) return
        
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "Switching monitor to ${newMonitor::class.simpleName}")
        
        activeMonitor?.stop()
        eventCollectionJob?.cancel()
        
        activeMonitor = newMonitor
        activeMonitor?.start()

        eventCollectionJob = scope.launch {
            activeMonitor?.events?.collect { event ->
                lastEventTime = System.currentTimeMillis()
                _events.emit(event)
            }
        }
    }

    private fun isUsageStatsPermissionGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }
}
