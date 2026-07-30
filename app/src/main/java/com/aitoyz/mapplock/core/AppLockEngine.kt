package com.aitoyz.mapplock.core

import com.aitoyz.mapplock.model.ForegroundEvent
import com.aitoyz.mapplock.security.LockerLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Deterministic engine for coordinating package transitions and locking events.
 * Listens to all foreground events and manages session updates + locking decisions.
 */
class AppLockEngine(
    private val scope: CoroutineScope,
    private val detector: ForegroundDetector,
    private val decisionEngine: LockDecisionEngine,
    private val launcher: LockLauncher,
    private val systemAppFilter: SystemAppFilter
) {
    fun getLauncher() = launcher

    private var monitoringJob: Job? = null
    private var previousPackage: String? = null

    /**
     * Starts monitoring foreground events.
     */
    fun start() {
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "MAP_TEST: Engine starting")
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "AppLockEngine starting")
        
        detector.start()
        
        monitoringJob = detector.events
            .onEach { event ->
                handleEvent(event)
            }
            .launchIn(scope)

        // INITIAL CHECK: Capture the current app immediately on start
        detector.currentForeground()?.let { pkg ->
            handleEvent(ForegroundEvent(packageName = pkg, source = ForegroundEvent.Source.SYSTEM))
        }
    }

    /**
     * Stops monitoring.
     */
    fun stop() {
        monitoringJob?.cancel()
        detector.stop()
    }

    private fun handleEvent(event: ForegroundEvent) {
        val currentPackage = event.packageName
        
        try {
            LockerLogger.v(LockerLogger.Event.STATE_TRANSITION, "[EVENT] Processing: $currentPackage from ${event.source}")
            
            // 1. Identify and filter system states
            val isLauncher = systemAppFilter.isLauncher(currentPackage)
            val isKeyboard = systemAppFilter.isKeyboard(currentPackage)
            val isTransient = systemAppFilter.isTransientSystemOverlay(currentPackage)
            val isRecents = systemAppFilter.isRecents(currentPackage)
            val isSystemUI = systemAppFilter.isSystemUI(currentPackage)

            val enrichedEvent = event.copy(
                isLauncher = isLauncher,
                isKeyboard = isKeyboard,
                isSystem = isTransient || isRecents || isSystemUI
            )

            // 2. Transition Detection: Track EVERY package change to catch Recents -> App moves
            if (currentPackage != previousPackage) {
                LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "[TRANSITION] ${previousPackage ?: "None"} -> $currentPackage")
                
                // Only real apps affect the SessionManager's state logic
                val isCurrentIgnored = enrichedEvent.isSystem || enrichedEvent.isLauncher || enrichedEvent.isKeyboard
                val wasPreviousIgnored = previousPackage?.let { 
                    systemAppFilter.isLauncher(it) || systemAppFilter.isKeyboard(it) || systemAppFilter.isTransientSystemOverlay(it) || systemAppFilter.isRecents(it) || systemAppFilter.isSystemUI(it)
                } ?: true

                // GRANULAR RELOCK LOGIC:
                if (!wasPreviousIgnored && previousPackage != null) {
                    // EXEMPT: Never relock our own locker app to avoid noise
                    if (previousPackage != "com.aitoyz.mapplock") {
                        if (isLauncher) {
                            // 1. Home Button / Launcher: Immediate Lock
                            LockerLogger.d(LockerLogger.Event.STATE_LOCKED, "[RELOCK] Home detected, locking $previousPackage")
                            SessionManager.lockImmediately(previousPackage!!)
                        } else if (isRecents) {
                            // 2. Recents Button: Snapshot Blocker
                            LockerLogger.d(LockerLogger.Event.LOCK_DETECTED, "[RELOCK] Recents detected, covering $previousPackage")
                            launcher.launch(previousPackage!!, isSnapshot = true)
                        } else if (!isCurrentIgnored && currentPackage != previousPackage) {
                            // 3. Switching to another app: Immediate Lock previous
                            LockerLogger.d(LockerLogger.Event.STATE_LOCKED, "[RELOCK] Switch detected, locking $previousPackage")
                            SessionManager.lockImmediately(previousPackage!!)
                        }
                    }
                    
                    SessionManager.onBackground(previousPackage!!)
                }

                if (!isCurrentIgnored) {
                    SessionManager.onForeground(currentPackage)
                    // TRANSIENT TRANSPARENCY: Only update previousPackage for real apps or launcher
                    // This prevents system dialogs from disrupting the "underlying" app context
                    previousPackage = currentPackage
                }
            } else {
                // If it's the same package, only update foreground timestamp if it's a real app
                if (!enrichedEvent.isSystem && !enrichedEvent.isLauncher && !enrichedEvent.isKeyboard) {
                    SessionManager.onForeground(currentPackage)
                }
            }

            // 3. Locking Decision
            if (decisionEngine.shouldLock(enrichedEvent)) {
                LockerLogger.i(LockerLogger.Event.LOCK_DETECTED, "[LOCK] Launching lock activity for $currentPackage")
                launcher.launch(currentPackage)
            }
        } catch (e: Throwable) {
            LockerLogger.e(LockerLogger.Event.ERROR, "[CRASH] Engine handleEvent failed", e)
        }
    }
}
