package com.aitoyz.mapplock.backend.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.aitoyz.mapplock.core.ForegroundEventBus
import com.aitoyz.mapplock.model.ForegroundEvent
import com.aitoyz.mapplock.security.LockerLogger

/**
 * A real AccessibilityService implementation for high-performance app detection.
 */
class AppLockAccessibilityService : AccessibilityService() {

    private var lastPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    val packageName = event.packageName?.toString() ?: return
                    
                    if (packageName != lastPackage) {
                        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "[REWRITE] DETECTED (Accessibility): $packageName")
                        
                        val foregroundEvent = ForegroundEvent(
                            packageName = packageName,
                            activityName = event.className?.toString(),
                            source = ForegroundEvent.Source.ACCESSIBILITY
                        )
                        
                        ForegroundEventBus.tryEmit(foregroundEvent)
                        lastPackage = packageName
                    }
                }
            }
        } catch (e: Throwable) {
            LockerLogger.e(LockerLogger.Event.ERROR, "[REWRITE] Accessibility Processing FAILED", e)
        }
    }

    override fun onInterrupt() {
        LockerLogger.w(LockerLogger.Event.ACCESSIBILITY_EVENT, "Accessibility Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        LockerLogger.i(LockerLogger.Event.ACCESSIBILITY_EVENT, "Accessibility Service Connected")
    }
}
