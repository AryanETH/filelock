package com.aitoyz.mapplock.core

import android.content.Context
import com.aitoyz.mapplock.model.ForegroundEvent
import com.aitoyz.mapplock.repository.LockedAppsRepository
import com.aitoyz.mapplock.security.LockerLogger
import com.aitoyz.mapplock.security.LockerRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.minutes

/**
 * The central brain of Mapplock. Single source of truth for app detection and locking coordination.
 * Deduplicates events from multiple sources (Accessibility, UsageStats) and manages the state machine.
 */
class CoreEngine private constructor(private val applicationContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val systemAppFilter = SystemAppFilter(applicationContext)
    private val repository = LockedAppsRepository(applicationContext)
    private val decisionEngine = LockDecisionEngine(applicationContext.packageName, repository)
    private val launcher = LockLauncher(applicationContext)
    
    private val _events = MutableSharedFlow<ForegroundEvent>(extraBufferCapacity = 32)
    private var previousPackage: String? = null
    
    // Event deduplication: ignore identical package events within 100ms
    private var lastEventTime = 0L
    private var lastEventPkg: String? = null

    init {
        _events.onEach { handleEvent(it) }.launchIn(scope)
        
        // Periodic cache refresh for system apps
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1.minutes)
                systemAppFilter.refreshCaches()
            }
        }
    }

    /**
     * Injects a foreground event into the engine.
     * Automatically handles deduplication and cross-source synchronization.
     */
    fun onForegroundEvent(event: ForegroundEvent) {
        val now = System.currentTimeMillis()
        if (event.packageName == lastEventPkg && now - lastEventTime < 100) {
            // Ignore rapid duplicate events for the same package
            return
        }
        
        lastEventPkg = event.packageName
        lastEventTime = now
        _events.tryEmit(event)
    }

    private fun handleEvent(event: ForegroundEvent) {
        val currentPackage = event.packageName
        
        try {
            val lockerRepo = LockerRepository.getInstance(applicationContext)
            
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

            // 2. Transition Detection
            if (currentPackage != previousPackage) {
                LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "[TRANSITION] ${previousPackage ?: "None"} -> $currentPackage")
                
                val isCurrentIgnored = enrichedEvent.isSystem || enrichedEvent.isLauncher || enrichedEvent.isKeyboard
                val wasPreviousIgnored = previousPackage?.let { 
                    systemAppFilter.isLauncher(it) || systemAppFilter.isKeyboard(it) || systemAppFilter.isTransientSystemOverlay(it) || systemAppFilter.isRecents(it) || systemAppFilter.isSystemUI(it)
                } ?: true

                // SECURITY: If we transition to Launcher or Recents, FORCE RESET the state machine
                if (isLauncher || isRecents) {
                    lockerRepo.forceReset()
                }

                if (!wasPreviousIgnored && previousPackage != null && previousPackage != contextPackageName) {
                    if (isLauncher) {
                        LockerLogger.d(LockerLogger.Event.STATE_LOCKED, "[RELOCK] Home detected, locking $previousPackage")
                        SessionManager.lockImmediately(previousPackage!!)
                    } else if (isRecents) {
                        LockerLogger.d(LockerLogger.Event.LOCK_DETECTED, "[RELOCK] Recents detected, locking $previousPackage")
                        SessionManager.lockImmediately(previousPackage!!)
                        launcher.launch(previousPackage!!, isSnapshot = true)
                    } else if (!isCurrentIgnored && currentPackage != previousPackage && currentPackage != contextPackageName) {
                        LockerLogger.d(LockerLogger.Event.STATE_LOCKED, "[RELOCK] Switch detected, locking $previousPackage")
                        SessionManager.lockImmediately(previousPackage!!)
                    }
                    SessionManager.onBackground(previousPackage!!)
                }

                if (!isCurrentIgnored) {
                    SessionManager.onForeground(currentPackage)
                    previousPackage = currentPackage
                }
            } else {
                if (!enrichedEvent.isSystem && !enrichedEvent.isLauncher && !enrichedEvent.isKeyboard) {
                    SessionManager.onForeground(currentPackage)
                }
            }

            // 3. Locking Decision
            val currentState = lockerRepo.state.value
            
            if (decisionEngine.shouldLock(enrichedEvent)) {
                // FILTER: Do not request lock if we are already in a locking transition for this package
                if ((currentState == LockerRepository.LockerState.LOCKING || 
                     currentState == LockerRepository.LockerState.LOCK_ACTIVITY_VISIBLE)) {
                    LockerLogger.v(LockerLogger.Event.LOCK_SKIPPED, "[LOCK] Already locking/visible, skip request for $currentPackage")
                    return
                }
                
                LockerLogger.i(LockerLogger.Event.LOCK_DETECTED, "[LOCK] Requesting lock for $currentPackage")
                launcher.launch(currentPackage)
            }
        } catch (e: Throwable) {
            LockerLogger.e(LockerLogger.Event.ERROR, "[CRASH] CoreEngine failure", e)
        }
    }
    
    private val contextPackageName = applicationContext.packageName

    companion object {
        @Volatile
        private var INSTANCE: CoreEngine? = null

        fun getInstance(context: Context): CoreEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CoreEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    fun getLauncher() = launcher

    fun refreshLockedApps() {
        repository.refreshCache()
    }
}
