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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return
                
                // We emit everything to the bus; the Engine or DecisionEngine will filter it.
                val foregroundEvent = ForegroundEvent(
                    packageName = packageName,
                    activityName = event.className?.toString(),
                    source = ForegroundEvent.Source.ACCESSIBILITY
                )
                
                ForegroundEventBus.tryEmit(foregroundEvent)
                LockerLogger.v(LockerLogger.Event.ACCESSIBILITY_EVENT, "Accessibility event: $packageName")
            }
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
