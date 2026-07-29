package com.aitoyz.mapplock.core

import com.aitoyz.mapplock.model.ForegroundEvent
import com.aitoyz.mapplock.repository.LockedAppsRepository
import com.aitoyz.mapplock.security.LockerLogger

/**
 * Central engine for making locking decisions based on session state.
 */
class LockDecisionEngine(
    private val lockedApps: LockedAppsRepository,
    private val sessions: UnlockSessionManager
) {
    /**
     * Determines whether the given foreground event should trigger a lock screen.
     */
    fun shouldLock(event: ForegroundEvent): Boolean {
        val packageName = event.packageName
        
        // 1. Is it our own app? (Don't lock the locker)
        if (packageName == "com.aitoyz.mapplock") {
            return false
        }

        // 2. Is the app in the locked list?
        if (!lockedApps.isLocked(packageName)) {
            LockerLogger.v(LockerLogger.Event.LOCK_SKIPPED, "$packageName is not in locked list")
            return false
        }
        
        // 3. Is it a special package that should be ignored?
        if (event.isLauncher || event.isKeyboard || event.isSystem) {
            return false
        }
        
        // 4. Session Check - Is authentication required?
        if (!sessions.isAuthenticationRequired(packageName)) {
            LockerLogger.v(LockerLogger.Event.SESSION_ACTIVE, "[SESSION_VALID] $packageName")
            return false
        }
        
        // 5. Is the screen off?
        if (event.isScreenOff) {
            return false
        }

        return true
    }
}
