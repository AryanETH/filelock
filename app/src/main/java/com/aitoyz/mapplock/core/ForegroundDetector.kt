package com.aitoyz.mapplock.core

import com.aitoyz.mapplock.model.ForegroundEvent
import kotlinx.coroutines.flow.Flow

/**
 * Interface for components that detect foreground application changes.
 */
interface ForegroundDetector {
    val events: Flow<ForegroundEvent>
    fun start()
    fun stop()
    fun currentForeground(): String?
}
