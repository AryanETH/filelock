package com.aitoyz.mapplock.security

import android.content.Context
import com.aitoyz.mapplock.core.UnlockSessionManager
import com.aitoyz.mapplock.core.SystemAppFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for the App Locker state.
 * Refactored to use UnlockSessionManager for sessions and SystemAppFilter for package checks.
 */
class LockerRepository private constructor(private val context: Context) {

    enum class LockerState {
        IDLE,                   // No locked app in foreground
        DETECTED,               // Locked app detected, preparing overlay
        OVERLAY_SHOWING,        // Black overlay is visible to user
        LOCK_ACTIVITY_VISIBLE,  // PIN/Pattern activity has confirmed it is drawn
        AUTHENTICATED,          // User has provided correct credentials
        RETURNING,              // Closing lock activity and returning to app
        UNLOCKED_SESSION        // User is currently using the app happily
    }

    private val _state = MutableStateFlow(LockerState.IDLE)
    val state: StateFlow<LockerState> = _state.asStateFlow()

    private val _currentTarget = MutableStateFlow<String?>(null)
    val currentTarget: StateFlow<String?> = _currentTarget.asStateFlow()

    private val stateMutex = Mutex()
    private val systemAppFilter = SystemAppFilter(context)
    
    // Caches to minimize system calls
    private val lockedPackagesCache = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var isMasterStealthEnabled = false

    fun isDarkMode() = flow {
        val prefs = SecureManager.getInstance(context).prefs
        while (true) {
            emit(prefs.getBoolean("is_dark_mode", false))
            delay(2000)
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
            
            if (currentState.ordinal > newState.ordinal && newState != LockerState.IDLE) {
                LockerLogger.d(LockerLogger.Event.STATE_TRANSITION, 
                    "Transition Ignored (Backward): $currentState -> $newState")
                return@withLock
            }

            if (currentState == newState && (packageName == null || _currentTarget.value == packageName)) return@withLock
            
            LockerLogger.d(LockerLogger.Event.STATE_TRANSITION, 
                "Transition: $currentState -> $newState for package: ${packageName ?: _currentTarget.value}")
            
            _state.value = newState
            packageName?.let { _currentTarget.value = it }
            
            if (newState == LockerState.IDLE) {
                _currentTarget.value = null
            }
        }
    }

    fun refreshLockedPackages() {
        try {
            val prefs = SecureManager.getInstance(context).prefs
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

    fun isAppLocked(packageName: String): Boolean {
        if (lockedPackagesCache.contains(packageName)) return true
        
        if (isMasterStealthEnabled) {
            if (packageName == "com.android.packageinstaller" || packageName == "com.google.android.packageinstaller") {
                return true
            }
        }
        
        return false
    }

    fun isSessionActive(packageName: String): Boolean {
        if (_state.value == LockerState.UNLOCKED_SESSION && _currentTarget.value == packageName) {
            return true
        }
        return UnlockSessionManager.isUnlocked(packageName)
    }

    fun startSession(packageName: String) {
        UnlockSessionManager.markAuthenticated(packageName)
    }

    fun endSession(packageName: String) {
        UnlockSessionManager.lockApp(packageName)
    }

    fun clearAllSessions() {
        UnlockSessionManager.clearAll()
    }

    fun loadSessions() {
        // UnlockSessionManager doesn't currently support loading sessions from disk,
        // but we could add it if needed.
    }

    fun isLauncher(packageName: String): Boolean {
        return systemAppFilter.isLauncher(packageName) || systemAppFilter.isSystemUI(packageName)
    }
}
