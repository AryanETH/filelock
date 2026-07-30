package com.aitoyz.mapplock.core

import android.content.Context
import android.content.Intent
import android.os.Build
import com.aitoyz.mapplock.security.LockerLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates which backend is currently active and manages process survival logic.
 * Ported from AppLock-master patterns.
 */
object BackendCoordinator {

    enum class BackendType {
        ACCESSIBILITY,
        USAGE_STATS,
        NONE
    }

    private var currentActiveBackend = BackendType.NONE
    private val restartAttempts = ConcurrentHashMap<String, Int>()
    private val lastRestartTime = ConcurrentHashMap<String, Long>()

    const val MAX_RESTART_ATTEMPTS = 3
    const val RESTART_COOLDOWN_MS = 30_000L
    const val RESTART_INTERVAL_MS = 5_000L

    fun setActiveBackend(type: BackendType) {
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "Active backend set to: $type")
        currentActiveBackend = type
    }

    fun getActiveBackend(): BackendType = currentActiveBackend

    fun shouldAttemptRestart(serviceName: String): Boolean {
        val attempts = restartAttempts.getOrDefault(serviceName, 0)
        val lastTime = lastRestartTime.getOrDefault(serviceName, 0L)
        val now = System.currentTimeMillis()

        if (attempts >= MAX_RESTART_ATTEMPTS) {
            if (now - lastTime > RESTART_COOLDOWN_MS) {
                resetRestartAttempts(serviceName)
                return true
            }
            return false
        }

        return now - lastTime > RESTART_INTERVAL_MS
    }

    fun recordRestartAttempt(serviceName: String) {
        val attempts = restartAttempts.getOrDefault(serviceName, 0)
        restartAttempts[serviceName] = attempts + 1
        lastRestartTime[serviceName] = System.currentTimeMillis()
    }

    fun resetRestartAttempts(serviceName: String) {
        restartAttempts[serviceName] = 0
    }

    fun stopAllOtherBackends(context: Context, except: BackendType) {
        if (except != BackendType.USAGE_STATS) {
            LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "Stopping fallback AppLockerService")
            context.stopService(Intent(context, com.aitoyz.mapplock.service.AppLockerService::class.java))
        }
    }
}
