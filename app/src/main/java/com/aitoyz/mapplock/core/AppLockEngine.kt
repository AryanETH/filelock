package com.aitoyz.mapplock.core

import com.aitoyz.mapplock.repository.LockedAppsRepository
import com.aitoyz.mapplock.security.LockerLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The central coordinator for app locking logic.
 */
class AppLockEngine(
    private val scope: CoroutineScope,
    private val monitor: ForegroundMonitor,
    private val lockedApps: LockedAppsRepository,
    private val sessions: LockSessionManager,
    private val onTriggerOverlay: (String) -> Unit
) {
    private var monitoringJob: Job? = null

    /**
     * Starts the locking engine.
     */
    fun start() {
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "Engine starting monitoring")
        
        // Subscribe BEFORE starting monitor to avoid race condition
        monitoringJob = monitor.events
            .onEach { packageName ->
                handleForegroundChanged(packageName)
            }
            .launchIn(scope)

        monitor.start()
    }

    /**
     * Stops the locking engine.
     */
    fun stop() {
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "Engine stopping monitoring")
        monitoringJob?.cancel()
        monitor.stop()
    }

    /**
     * Manually triggers a check for the current foreground application.
     */
    fun recheck() {
        monitor.currentForeground()?.let { packageName ->
            LockerLogger.d(LockerLogger.Event.STATE_TRANSITION, "Engine manual recheck: $packageName")
            handleForegroundChanged(packageName)
        }
    }

    private fun handleForegroundChanged(packageName: String) {
        LockerLogger.d(LockerLogger.Event.STATE_TRANSITION, "Processing package change: $packageName")
        
        if (!lockedApps.isLocked(packageName)) {
            LockerLogger.d(LockerLogger.Event.LOCK_SKIPPED, "$packageName is not in locked list")
            return
        }
        
        if (sessions.isUnlocked(packageName)) {
            LockerLogger.d(LockerLogger.Event.SESSION_ACTIVE, "Session active for $packageName, skipping lock")
            return
        }

        LockerLogger.i(LockerLogger.Event.LOCK_DETECTED, "Triggering lock for $packageName")
        onTriggerOverlay(packageName)
    }
}
