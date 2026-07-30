package com.aitoyz.mapplock.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import kotlin.time.Duration.Companion.seconds

/**
 * Single source of truth for the App Locker state.
 * Refactored to use SessionManager for sessions and SystemAppFilter for package checks.
 */
class LockerRepository private constructor(context: Context) {

    private val applicationContext = context.applicationContext

    enum class LockerState {
        IDLE,                   // No locked app in foreground
        LOCK_ACTIVITY_VISIBLE,  // PIN/Pattern activity has confirmed it is drawn
        AUTHENTICATED           // User has provided correct credentials
    }

    private val _state = MutableStateFlow(LockerState.IDLE)
    val state: StateFlow<LockerState> = _state.asStateFlow()

    private val stateMutex = Mutex()
    private val repositoryScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    
    // Caches to minimize system calls
    private val lockedPackagesCache = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var isMasterStealthEnabled = false

    fun isDarkMode() = flow {
        val prefs = SecureManager.getInstance(applicationContext).prefs
        while (true) {
            emit(prefs.getBoolean("is_dark_mode", false))
            delay(2.seconds)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: LockerRepository? = null

        fun getInstance(context: Context): LockerRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LockerRepository(context.applicationContext).also { 
                    it.refreshLockedPackages()
                    INSTANCE = it 
                }
            }
        }
    }

    suspend fun updateState(newState: LockerState, packageName: String? = null) {
        stateMutex.withLock {
            val currentState = _state.value
            
            // ALLOW sideways transitions for different packages, or if resetting to IDLE
            if (currentState.ordinal > newState.ordinal && newState != LockerState.IDLE) {
                LockerLogger.d(LockerLogger.Event.STATE_TRANSITION, 
                    "Transition Ignored (Backward): $currentState -> $newState")
                return@withLock
            }

            if (currentState == newState) return@withLock
            
            LockerLogger.d(LockerLogger.Event.STATE_TRANSITION, 
                "Transition: $currentState -> $newState for package: ${packageName ?: "unknown"}")
            
            _state.value = newState
        }
    }

    fun resetState() {
        repositoryScope.launch {
            stateMutex.withLock {
                LockerLogger.d(LockerLogger.Event.STATE_TRANSITION, "Forcing state reset to IDLE")
                _state.value = LockerState.IDLE
            }
        }
    }

    fun refreshLockedPackages() {
        try {
            val prefs = SecureManager.getInstance(applicationContext).prefs
            val allVaultIds = prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()
            val apps = mutableSetOf<String>()
            allVaultIds.forEach { id ->
                apps.addAll(prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet())
            }
            lockedPackagesCache.clear()
            lockedPackagesCache.addAll(apps)
            isMasterStealthEnabled = prefs.getBoolean("master_stealth_enabled", false)
        } catch (e: Exception) {
            LockerLogger.e(LockerLogger.Event.ERROR, "Failed to refresh locked packages", e)
        }
    }
}
