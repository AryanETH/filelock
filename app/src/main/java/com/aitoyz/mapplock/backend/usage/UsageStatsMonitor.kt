package com.aitoyz.mapplock.backend.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.aitoyz.mapplock.core.ForegroundMonitor
import com.aitoyz.mapplock.model.ForegroundEvent
import com.aitoyz.mapplock.security.LockerLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Foreground monitor implementation using UsageStatsManager polling.
 */
class UsageStatsMonitor(private val context: Context) : ForegroundMonitor {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val _events = MutableSharedFlow<ForegroundEvent>(extraBufferCapacity = 10)
    override val events: SharedFlow<ForegroundEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null
    private var lastPackageName: String? = null

    override fun start() {
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "UsageStatsMonitor starting polling")
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    val currentPkg = currentForeground()
                    if (currentPkg != null && currentPkg != lastPackageName) {
                        LockerLogger.d(LockerLogger.Event.STATE_TRANSITION, "UsageStats detected package change: $currentPkg")
                        lastPackageName = currentPkg
                        val event = ForegroundEvent(
                            packageName = currentPkg,
                            source = ForegroundEvent.Source.USAGE_STATS
                        )
                        _events.emit(event)
                    }
                } catch (e: Exception) {
                    LockerLogger.e(LockerLogger.Event.ERROR, "Error in UsageStats polling loop", e)
                }
                delay(100) // Faster polling interval (100ms)
            }
        }
    }

    override fun stop() {
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "UsageStatsMonitor stopping polling")
        pollingJob?.cancel()
    }

    override fun currentForeground(): String? {
        return try {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 10000 // 10 second window
            val events = usageStatsManager.queryEvents(startTime, endTime) ?: run {
                LockerLogger.e(LockerLogger.Event.ERROR, "UsageStats queryEvents returned null. Check permission.")
                return null
            }
            
            val event = UsageEvents.Event()
            var lastPkg: String? = null
            var eventCount = 0
            
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                eventCount++
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastPkg = event.packageName
                }
            }
            
            if (lastPkg == null && eventCount == 0) {
                // Potential issue: queryEvents returning no events even if apps are running
                // Log this occasionally or once
            }
            
            lastPkg
        } catch (e: Exception) {
            LockerLogger.e(LockerLogger.Event.ERROR, "Failed to query usage events", e)
            null
        }
    }
}
