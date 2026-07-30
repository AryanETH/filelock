package com.aitoyz.mapplock.backend.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.aitoyz.mapplock.core.ForegroundDetector
import com.aitoyz.mapplock.model.ForegroundEvent
import com.aitoyz.mapplock.security.LockerLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Foreground monitor implementation using UsageStatsManager polling.
 * Optimized for low-latency detection of all MOVE_TO_FOREGROUND events.
 */
class UsageStatsMonitor(
    private val context: Context,
    private val scope: CoroutineScope
) : ForegroundDetector {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val _events = MutableSharedFlow<ForegroundEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<ForegroundEvent> = _events.asSharedFlow()

    private var pollingJob: Job? = null
    
    // TRACKING: Use timestamp to ensure no event is missed or duplicated
    private var lastEventTimestamp: Long = System.currentTimeMillis()
    private var lastEmittedPackage: String? = null

    override fun start() {
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "[REWRITE] UsageStatsMonitor starting (150ms polling)")
        lastEventTimestamp = System.currentTimeMillis() - 1000
        lastEmittedPackage = null
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.Default) {
            LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "[REWRITE] Polling loop active")
            var lastHeartbeat = 0L
            
            while (isActive) {
                try {
                    pollEvents()
                    
                    val now = System.currentTimeMillis()
                    if (now - lastHeartbeat > 10_000) {
                        LockerLogger.v(LockerLogger.Event.STATE_TRANSITION, "[REWRITE] Heartbeat: Monitor is alive")
                        lastHeartbeat = now
                    }
                } catch (e: Throwable) {
                    LockerLogger.e(LockerLogger.Event.ERROR, "[REWRITE] Polling CRASHED", e)
                }
                delay(150)
            }
        }
    }

    override fun stop() {
        pollingJob?.cancel()
    }

    private suspend fun pollEvents() {
        // LockerLogger.v(LockerLogger.Event.STATE_TRANSITION, "[REWRITE] Polling...")
        val endTime = System.currentTimeMillis()
        val startTime = lastEventTimestamp
        
        if (endTime <= startTime) return

        val events = usageStatsManager.queryEvents(startTime, endTime) ?: return
        
        if (!events.hasNextEvent()) return

        val event = UsageEvents.Event()
        var latestProcessedTime = startTime

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            
            if (event.timeStamp > startTime) {
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    val pkg = event.packageName
                    
                    if (pkg != lastEmittedPackage) {
                        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "[REWRITE] DETECTED: $pkg")
                        _events.emit(ForegroundEvent(
                            packageName = pkg,
                            source = ForegroundEvent.Source.USAGE_STATS
                        ))
                        lastEmittedPackage = pkg
                    }
                }
                if (event.timeStamp > latestProcessedTime) {
                    latestProcessedTime = event.timeStamp
                }
            }
        }
        
        lastEventTimestamp = latestProcessedTime
    }

    override fun currentForeground(): String? {
        return try {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 5000
            val events = usageStatsManager.queryEvents(startTime, endTime) ?: return null
            
            val event = UsageEvents.Event()
            var lastPkg: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastPkg = event.packageName
                }
            }
            lastPkg
        } catch (_: Exception) {
            null
        }
    }
}
