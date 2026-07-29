package com.aitoyz.mapplock.core

import java.util.concurrent.ConcurrentHashMap

/**
 * Manages active unlock sessions for applications.
 */
object LockSessionManager {
    private val unlockedApps = ConcurrentHashMap<String, Long>()
    private var sessionTimeoutMillis: Long = 3600000L // Default 1 hour

    /**
     * Records that an application has been unlocked.
     */
    fun onAppUnlocked(packageName: String) {
        unlockedApps[packageName] = System.currentTimeMillis()
    }

    /**
     * Checks if an application is currently unlocked within the session timeout.
     */
    fun isUnlocked(packageName: String): Boolean {
        val unlockTime = unlockedApps[packageName] ?: return false
        val isExpired = System.currentTimeMillis() - unlockTime > sessionTimeoutMillis
        if (isExpired) {
            unlockedApps.remove(packageName)
            return false
        }
        return true
    }

    /**
     * Clears all active sessions.
     */
    fun clearAll() {
        unlockedApps.clear()
    }

    /**
     * Removes a specific application from the unlocked sessions.
     */
    fun lockApp(packageName: String) {
        unlockedApps.remove(packageName)
    }

    fun setSessionTimeout(timeoutMillis: Long) {
        sessionTimeoutMillis = timeoutMillis
    }
}
