package com.aitoyz.mapplock.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.aitoyz.mapplock.security.LockerLogger

/**
 * A background worker that ensures the AppLockerService is running.
 * Provides a secondary watchdog mechanism in case the foreground service is killed.
 */
class LockerWatchdogWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            if (!AppLockerService.isRunning()) {
                LockerLogger.i(LockerLogger.Event.SERVICE_RESTARTED, "Watchdog: Service not running, restarting...")
                val intent = Intent(applicationContext, AppLockerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(intent)
                } else {
                    applicationContext.startService(intent)
                }
            } else {
                LockerLogger.d(LockerLogger.Event.SERVICE_RESTARTED, "Watchdog: Service is healthy")
            }
            Result.success()
        } catch (e: Exception) {
            LockerLogger.e(LockerLogger.Event.ERROR, "Watchdog failed to restart service", e)
            Result.retry()
        }
    }
}
