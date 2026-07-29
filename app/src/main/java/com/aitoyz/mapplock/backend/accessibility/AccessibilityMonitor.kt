package com.aitoyz.mapplock.backend.accessibility

import com.aitoyz.mapplock.core.ForegroundMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class AccessibilityMonitor : ForegroundMonitor {
    override fun start() {}
    override fun stop() {}
    override fun currentForeground(): String? = null
    override val events: Flow<String> = emptyFlow()
}
