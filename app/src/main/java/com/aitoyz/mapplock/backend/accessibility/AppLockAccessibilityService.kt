package com.aitoyz.mapplock.backend.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.aitoyz.mapplock.core.*
import com.aitoyz.mapplock.model.ForegroundEvent
import com.aitoyz.mapplock.repository.LockedAppsRepository
import com.aitoyz.mapplock.security.LockerLogger
import kotlinx.coroutines.cancel

/**
 * A real AccessibilityService implementation for high-performance app detection.
 * System-bound service that acts as the primary app-lock backend.
 */
class AppLockAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: AppLockAccessibilityService? = null
        fun getInstance(): AppLockAccessibilityService? = instance

        var lastForegroundPackage: String? = null
            private set
    }

    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    private var engine: AppLockEngine? = null
    private val directDetector = DirectDetector()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LockerLogger.i(LockerLogger.Event.ACCESSIBILITY_EVENT, "Accessibility Service Connected")
        
        // Stop fallback backend if running
        BackendCoordinator.stopAllOtherBackends(this, BackendCoordinator.BackendType.ACCESSIBILITY)
        BackendCoordinator.setActiveBackend(BackendCoordinator.BackendType.ACCESSIBILITY)
        BackendCoordinator.resetRestartAttempts("AccessibilityRecovery")
        
        val lockedApps = LockedAppsRepository(this)
        val decisionEngine = LockDecisionEngine(packageName, lockedApps)
        val launcher = LockLauncher(this)
        val systemAppFilter = SystemAppFilter(this)
        
        engine = AppLockEngine(
            scope = serviceScope,
            detector = directDetector,
            decisionEngine = decisionEngine,
            launcher = launcher,
            systemAppFilter = systemAppFilter
        )
        
        engine?.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    val packageName = event.packageName?.toString() ?: return
                    lastForegroundPackage = packageName
                    
                    // Route to engine via direct detector
                    directDetector.onPackageChanged(packageName, ForegroundEvent.Source.ACCESSIBILITY)
                }
            }
        } catch (e: Throwable) {
            LockerLogger.e(LockerLogger.Event.ERROR, "[REWRITE] Accessibility Processing FAILED", e)
        }
    }

    override fun onInterrupt() {
        LockerLogger.w(LockerLogger.Event.ACCESSIBILITY_EVENT, "Accessibility Service Interrupted")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        LockerLogger.w(LockerLogger.Event.ACCESSIBILITY_EVENT, "Accessibility Service Unbound")
        BackendCoordinator.setActiveBackend(BackendCoordinator.BackendType.NONE)
        engine?.stop()
        serviceScope.cancel()
        attemptFallbackStart()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        LockerLogger.w(LockerLogger.Event.ACCESSIBILITY_EVENT, "Accessibility Service Destroyed")
        BackendCoordinator.setActiveBackend(BackendCoordinator.BackendType.NONE)
        engine?.stop()
        serviceScope.cancel()
        attemptFallbackStart()
        instance = null
        super.onDestroy()
    }

    fun notifyLockDismissed() {
        engine?.getLauncher()?.notifyFinished()
    }

    private fun attemptFallbackStart() {
        val serviceName = "AccessibilityRecovery"
        if (BackendCoordinator.shouldAttemptRestart(serviceName)) {
            BackendCoordinator.recordRestartAttempt(serviceName)
            LockerLogger.i(LockerLogger.Event.SERVICE_RESTARTED, "Starting fallback AppLockerService...")
            val intent = android.content.Intent(this, com.aitoyz.mapplock.service.AppLockerService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }
}
