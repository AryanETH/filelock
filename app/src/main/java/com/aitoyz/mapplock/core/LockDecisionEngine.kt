package com.aitoyz.mapplock.core

import com.aitoyz.mapplock.model.ForegroundEvent
import com.aitoyz.mapplock.repository.LockedAppsRepository
import com.aitoyz.mapplock.security.LockerLogger

/**
 * Pure logic engine for determining if a package should be locked.
 */
class LockDecisionEngine(
    private val ownPackageName: String,
    private val lockedApps: LockedAppsRepository
) {
    /**
     * Returns true if the package requires a lock screen.
     */
    fun shouldLock(event: ForegroundEvent): Boolean {
        val packageName = event.packageName
        
        // 1. Is it our own app? -> Ignore
        if (packageName == ownPackageName) {
            LockerLogger.v(LockerLogger.Event.LOCK_SKIPPED, "[DECISION] Own app $packageName, skip")
            return false
        }

        // 2. Is launcher/system? -> Ignore
        if (event.isLauncher || event.isKeyboard || event.isSystem) {
            LockerLogger.v(LockerLogger.Event.LOCK_SKIPPED, "[DECISION] System/Launcher/Keyboard $packageName, skip")
            return false
        }

        // 3. Is app protected? -> If not, Ignore
        if (!lockedApps.isLocked(packageName)) {
            LockerLogger.i(LockerLogger.Event.LOCK_SKIPPED, "[DECISION] App NOT PROTECTED: $packageName, skip")
            return false
        }
        
        // 4. Session unlocked? -> If yes, Ignore
        if (SessionManager.isUnlocked(packageName)) {
            LockerLogger.i(LockerLogger.Event.SESSION_ACTIVE, "[DECISION] Session active for $packageName, skip")
            return false
        }
        
        // 5. Ignore if screen is off
        if (event.isScreenOff) {
            LockerLogger.v(LockerLogger.Event.LOCK_SKIPPED, "[DECISION] Screen off, skip")
            return false
        }

        // Otherwise -> Lock
        LockerLogger.i(LockerLogger.Event.LOCK_DETECTED, "[DECISION] LOCK REQUIRED for $packageName")
        return true
    }
}
