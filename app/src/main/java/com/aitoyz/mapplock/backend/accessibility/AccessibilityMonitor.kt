package com.aitoyz.mapplock.backend.accessibility

import com.aitoyz.mapplock.core.ForegroundEventBus
import com.aitoyz.mapplock.core.ForegroundMonitor
import com.aitoyz.mapplock.model.ForegroundEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

class AccessibilityMonitor : ForegroundMonitor {
    override fun start() {}
    override fun stop() {}
    override fun currentForeground(): String? = null
    
    override val events: Flow<ForegroundEvent> = ForegroundEventBus.events
        .filter { it.source == ForegroundEvent.Source.ACCESSIBILITY }
}
