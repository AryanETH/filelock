package com.aitoyz.mapplock.backend.accessibility

import com.aitoyz.mapplock.core.ForegroundDetector
import com.aitoyz.mapplock.core.ForegroundEventBus
import com.aitoyz.mapplock.model.ForegroundEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

/**
 * Foreground monitor implementation using AccessibilityService.
 * Filters global events coming from the AccessibilityService.
 */
class AccessibilityMonitor : ForegroundDetector {
    override val events: Flow<ForegroundEvent> = ForegroundEventBus.events
        .filter { it.source == ForegroundEvent.Source.ACCESSIBILITY }

    override fun start() {
        // AccessibilityService starts automatically when enabled in system settings.
    }

    override fun stop() {
        // We don't stop the system service, just stop listening to events in the engine.
    }

    override fun currentForeground(): String? = null
}
