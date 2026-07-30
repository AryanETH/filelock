package com.aitoyz.mapplock.backend.shizuku

import com.aitoyz.mapplock.core.ForegroundDetector
import com.aitoyz.mapplock.model.ForegroundEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class ShizukuMonitor : ForegroundDetector {
    override fun start() {}
    override fun stop() {}
    override fun currentForeground(): String? = null
    override val events: Flow<ForegroundEvent> = emptyFlow()
}
