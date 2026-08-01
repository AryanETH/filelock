package com.aitoyz.mapplock.core

import android.os.SystemClock
import com.aitoyz.mapplock.security.LockerLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages authenticated app sessions using a deterministic model.
 * Replaces UnlockSessionManager.
 */
object SessionManager {

    data class AppSession(
        val packageName: String,
        var isUnlocked: Boolean = false,
        var unlockTimestamp: Long = 0L,
        var backgroundTimestamp: Long = 0L,
        var lastForegroundTimestamp: Long = 0L
    )

    private val sessions = ConcurrentHashMap<String, AppSession>()
    private val GRACE_PERIOD_MS = 5_000L // Reduced from 15s to 5s for higher production security

    // Clock provider for testing
    var clock: () -> Long = { SystemClock.elapsedRealtime() }

    fun unlock(packageName: String) {
        val session = getOrCreate(packageName)
        session.isUnlocked = true
        session.unlockTimestamp = clock()
        // CRITICAL: Reset background timer on successful unlock.
        // This prevents "Dual Lock" if the user stays on the PIN screen longer than the grace period.
        session.backgroundTimestamp = 0
        session.lastForegroundTimestamp = clock()
        LockerLogger.i(LockerLogger.Event.SESSION_ACTIVE, "[SESSION] $packageName unlocked")
    }

    fun lock(packageName: String) {
        val session = sessions[packageName] ?: return
        session.isUnlocked = false
        LockerLogger.i(LockerLogger.Event.STATE_LOCKED, "[SESSION] $packageName locked")
    }

    fun lockImmediately(packageName: String) {
        val session = sessions[packageName] ?: return
        session.isUnlocked = false
        session.backgroundTimestamp = 0 // Clear background time to force relock
        LockerLogger.i(LockerLogger.Event.STATE_LOCKED, "[SESSION] $packageName locked immediately (Home/Back/Switch)")
    }

    fun isUnlocked(packageName: String): Boolean {
        return sessions[packageName]?.isUnlocked ?: false
    }

    fun onForeground(packageName: String) {
        val session = getOrCreate(packageName)
        
        // Expiration check: If we were in background, check if we stayed too long
        if (session.isUnlocked && session.backgroundTimestamp > 0) {
            val timeInBackground = clock() - session.backgroundTimestamp
            if (timeInBackground > GRACE_PERIOD_MS) {
                session.isUnlocked = false
                LockerLogger.d(LockerLogger.Event.STATE_LOCKED, "[SESSION] $packageName expired after ${timeInBackground}ms")
            } else {
                LockerLogger.v(LockerLogger.Event.SESSION_ACTIVE, "[SESSION] $packageName recovered within grace period")
            }
        }

        session.lastForegroundTimestamp = clock()
        session.backgroundTimestamp = 0
        // Reduced verbosity: Only log foreground transition if it was in background
        // LockerLogger.v(LockerLogger.Event.STATE_TRANSITION, "[SESSION] $packageName in foreground")
    }

    fun onBackground(packageName: String) {
        val session = sessions[packageName] ?: return
        session.backgroundTimestamp = clock()
        LockerLogger.v(LockerLogger.Event.STATE_TRANSITION, "[SESSION] $packageName in background")
    }

    fun clearAll() {
        sessions.clear()
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "[SESSION] All sessions cleared")
    }

    private fun getOrCreate(packageName: String): AppSession {
        return sessions.getOrPut(packageName) { AppSession(packageName) }
    }
}
