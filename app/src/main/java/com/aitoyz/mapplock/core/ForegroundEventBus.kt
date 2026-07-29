package com.aitoyz.mapplock.core

import com.aitoyz.mapplock.model.ForegroundEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A central bus for broadcasting foreground events across the app.
 */
object ForegroundEventBus {
    private val _events = MutableSharedFlow<ForegroundEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ForegroundEvent> = _events.asSharedFlow()

    /**
     * Emits a new foreground event to all subscribers.
     */
    suspend fun emit(event: ForegroundEvent) {
        _events.emit(event)
    }

    /**
     * Non-suspending version of emit.
     */
    fun tryEmit(event: ForegroundEvent): Boolean {
        return _events.tryEmit(event)
    }
}
