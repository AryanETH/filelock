package com.aitoyz.mapplock.core

import com.aitoyz.mapplock.model.ForegroundEvent
import kotlinx.coroutines.flow.Flow

/**
 * Interface for components that monitor the foreground application.
 */
interface ForegroundMonitor {

    /**
     * Starts the monitoring process.
     */
    fun start()

    /**
     * Stops the monitoring process.
     */
    fun stop()

    /**
     * Returns the package name of the current foreground application, if available.
     */
    fun currentForeground(): String?

    /**
     * A flow of package names that are moved to the foreground.
     */
    val events: Flow<ForegroundEvent>
}
