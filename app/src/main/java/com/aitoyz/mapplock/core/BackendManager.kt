package com.aitoyz.mapplock.core

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import com.aitoyz.mapplock.backend.accessibility.AccessibilityMonitor
import com.aitoyz.mapplock.backend.usage.UsageStatsMonitor
import com.aitoyz.mapplock.model.ForegroundEvent
import com.aitoyz.mapplock.security.LockerLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Orchestrates the active foreground monitoring backend.
 * Uses UsageStats as primary and falls back to Accessibility if needed.
 */
class BackendManager(
    private val context: Context,
    private val scope: CoroutineScope
) : ForegroundDetector {

    private val usageMonitor = UsageStatsMonitor(context, scope)
    private val accessibilityMonitor = AccessibilityMonitor()

    private val _events = MutableSharedFlow<ForegroundEvent>(extraBufferCapacity = 32)
    override val events: Flow<ForegroundEvent> = _events.asSharedFlow()

    private var activeMonitor: ForegroundDetector? = null
    private var lastEventTime = System.currentTimeMillis()
    private var healthCheckJob: Job? = null
    private var collectionJob: Job? = null

    override fun start() {
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "BackendManager starting")
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            // Initial selection
            val initial = if (isUsageStatsPermissionGranted()) usageMonitor else accessibilityMonitor
            switchMonitor(initial)

            // Dynamic fallback/recovery loop
            while (isActive) {
                delay(5000) // Check every 5 seconds
                
                // Recovery/Switch logic:
                // 1. If we have UsageStats permission but aren't using it, switch back to primary.
                if (activeMonitor != usageMonitor && isUsageStatsPermissionGranted()) {
                    LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "UsageStats healthy. Recovering primary monitor.")
                    switchMonitor(usageMonitor)
                }
                
                // 2. If we are on UsageStats but lost permission (unlikely without restart, but safe), switch to Accessibility.
                if (activeMonitor == usageMonitor && !isUsageStatsPermissionGranted()) {
                    LockerLogger.w(LockerLogger.Event.STATE_TRANSITION, "UsageStats permission lost. Falling back to Accessibility.")
                    switchMonitor(accessibilityMonitor)
                }
            }
        }
    }

    override fun stop() {
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "BackendManager stopping")
        healthCheckJob?.cancel()
        collectionJob?.cancel()
        activeMonitor?.stop()
    }

    override fun currentForeground(): String? = activeMonitor?.currentForeground()

    private fun switchMonitor(newMonitor: ForegroundDetector) {
        if (activeMonitor == newMonitor) return
        
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "Switching backend to ${newMonitor::class.simpleName}")
        
        activeMonitor?.stop()
        collectionJob?.cancel()
        
        activeMonitor = newMonitor
        activeMonitor?.start()

        collectionJob = scope.launch {
            activeMonitor?.events?.collect { event ->
                lastEventTime = System.currentTimeMillis()
                _events.emit(event)
            }
        }
    }

    private fun isUsageStatsPermissionGranted(): Boolean {
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
