package com.aitoyz.mapplock.security

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for the App Locker state and sessions.
 * Manages the state machine and coordinates between Accessibility and Foreground services.
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
    private val unlockedApps = ConcurrentHashMap<String, Long>()
    
    // Caches to minimize system calls
    private val launcherPackages = ConcurrentHashMap.newKeySet<String>()
    private val systemPackages = ConcurrentHashMap.newKeySet<String>()
    private val lockedPackagesCache = ConcurrentHashMap.newKeySet<String>()
    private var isMasterStealthEnabled = false
    private var lastCacheUpdate = 0L

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
            
            // VALIDATION: Prevent backward transitions that cause UI stuck states
            // (e.g., Don't go back to OVERLAY_SHOWING if we are already LOCK_ACTIVITY_VISIBLE)
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
        
        // Master Stealth logic for package installers
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
        
        val unlockTime = unlockedApps[packageName] ?: return false
        // 1 hour session timeout
        val isActive = System.currentTimeMillis() - unlockTime < 3600000L
        if (!isActive) {
            unlockedApps.remove(packageName)
            SecureManager.getInstance(context).saveSessions(unlockedApps)
        }
        return isActive
    }

    fun startSession(packageName: String) {
        unlockedApps[packageName] = System.currentTimeMillis()
        SecureManager.getInstance(context).saveSessions(unlockedApps)
    }

    fun endSession(packageName: String) {
        unlockedApps.remove(packageName)
        SecureManager.getInstance(context).saveSessions(unlockedApps)
    }

    fun clearAllSessions() {
        unlockedApps.clear()
        SecureManager.getInstance(context).saveSessions(unlockedApps)
    }

    fun loadSessions() {
        val savedSessions = SecureManager.getInstance(context).getSessions()
        val now = System.currentTimeMillis()
        // Only load sessions that haven't expired
        val validSessions = savedSessions.filter { now - it.value < 3600000L }
        unlockedApps.putAll(validSessions)
        if (validSessions.size != savedSessions.size) {
            SecureManager.getInstance(context).saveSessions(unlockedApps)
        }
    }

    fun isLauncher(packageName: String): Boolean {
        refreshCachesIfNeeded()
        return launcherPackages.contains(packageName) || systemPackages.contains(packageName)
    }

    private fun refreshCachesIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastCacheUpdate < 60000L && launcherPackages.isNotEmpty()) return
        
        try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            launcherPackages.clear()
            resolveInfos.forEach { launcherPackages.add(it.activityInfo.packageName) }
            
            systemPackages.clear()
            systemPackages.addAll(listOf("android", "com.android.systemui", "com.android.settings", "com.google.android.settings", context.packageName))
            
            lastCacheUpdate = now
        } catch (e: Exception) {
            LockerLogger.e(LockerLogger.Event.ERROR, "Failed to refresh package caches", e)
        }
    }
}
