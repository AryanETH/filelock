package com.aitoyz.mapplock.core

import com.aitoyz.mapplock.model.ForegroundEvent
import com.aitoyz.mapplock.security.LockerLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The central coordinator for app locking logic.
 * Deterministic event-driven engine.
 */
class AppLockEngine(
    private val scope: CoroutineScope,
    private val monitor: ForegroundMonitor,
    private val decisionEngine: LockDecisionEngine,
    private val systemAppFilter: SystemAppFilter,
    private val onTriggerOverlay: (String) -> Unit
) {
    private var monitoringJob: Job? = null
    
    // Transition tracking
    private var lastPackage: String? = null

    /**
     * Starts the locking engine.
     */
    fun start() {
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "Engine starting monitoring")
        
        // Listen to global events
        monitoringJob = ForegroundEventBus.events
            .onEach { event ->
                handleForegroundEvent(event)
            }
            .launchIn(scope)

        monitor.start()
        
        // Connect monitor to the global bus
        scope.launchInMonitor(monitor)
    }

    private fun CoroutineScope.launchInMonitor(monitor: ForegroundMonitor) {
        monitor.events
            .onEach { event ->
                ForegroundEventBus.tryEmit(event)
            }
            .launchIn(this)
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
            ForegroundEventBus.tryEmit(ForegroundEvent(
                packageName = packageName,
                source = ForegroundEvent.Source.SYSTEM
            ))
        }
    }

    /**
     * Called when a lock screen is dismissed.
     */
    fun onLockDismissed() {
        // No-op in new deterministic model as state is managed by sessions
    }

    private fun handleForegroundEvent(event: ForegroundEvent) {
        val packageName = event.packageName
        
        // 1. Identify our own LockActivity - Never lock it
        val isOurLock = packageName == "com.aitoyz.mapplock" && event.activityName?.contains("LockActivity") == true
        if (isOurLock) return

        // 2. Ignore system packages
        val isLauncher = systemAppFilter.isLauncher(packageName)
        val isKeyboard = systemAppFilter.isKeyboard(packageName)
        val isTransient = systemAppFilter.isTransientSystemOverlay(packageName)
        val isRecents = systemAppFilter.isRecents(packageName)
        val isSystemUI = systemAppFilter.isSystemUI(packageName)
        val isOurApp = packageName == "com.aitoyz.mapplock"

        val isIgnored = isLauncher || isKeyboard || isTransient || isRecents || isSystemUI || isOurApp

        if (isIgnored) {
            LockerLogger.v(LockerLogger.Event.LOCK_SKIPPED, "Ignoring system event for $packageName")
            return
        }

        // 3. Transition Logic
        val oldPkg = lastPackage
        if (packageName != oldPkg) {
            LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "[TRANSITION] ${oldPkg ?: "None"} -> $packageName")
            
            // Mark the previous app as backgrounded
            oldPkg?.let { UnlockSessionManager.onBackground(it) }
            
            // Mark the new app as foreground
            UnlockSessionManager.onForeground(packageName)
            
            lastPackage = packageName
        }

        // 4. Enrich the event with flags
        val enrichedEvent = event.copy(
            isLauncher = isLauncher,
            isKeyboard = isKeyboard,
            isSystem = isTransient || isRecents || isSystemUI
        )

        // 5. Lock Decision
        if (decisionEngine.shouldLock(enrichedEvent)) {
            LockerLogger.i(LockerLogger.Event.LOCK_DETECTED, "[AUTH_REQUIRED] $packageName")
            onTriggerOverlay(packageName)
        }
    }
}
