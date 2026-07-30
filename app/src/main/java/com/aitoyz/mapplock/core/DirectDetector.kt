package com.aitoyz.mapplock.core

import com.aitoyz.mapplock.model.ForegroundEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A detector that receives events directly from an external source (like AccessibilityService).
 */
class DirectDetector : ForegroundDetector {
    private val _events = MutableSharedFlow<ForegroundEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<ForegroundEvent> = _events.asSharedFlow()

    private var currentPkg: String? = null

    override fun start() {
        // No-op
    }

    override fun stop() {
        // No-op
    }

    override fun currentForeground(): String? = currentPkg

    fun onPackageChanged(packageName: String, source: ForegroundEvent.Source) {
        currentPkg = packageName
        _events.tryEmit(ForegroundEvent(packageName = packageName, source = source))
    }
}
