package com.aitoyz.mapplock.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.aitoyz.mapplock.R
import com.aitoyz.mapplock.security.LockerLogger

/**
 * A background worker that ensures the AppLockerService is running.
 * Provides a secondary watchdog mechanism in case the foreground service is killed.
 */
class LockerWatchdogWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    private val NOTIFICATION_ID = 1002

    override fun doWork(): Result {
        return try {
            LockerLogger.d(LockerLogger.Event.SERVICE_RESTARTED, "Watchdog: Cycle started")
            
            // On Android 12+, we can use setForeground to gain FGS launch exemption
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val foregroundInfo = createForegroundInfo()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        setForegroundAsync(ForegroundInfo(NOTIFICATION_ID, foregroundInfo.notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)).get()
                    } else {
                        setForegroundAsync(foregroundInfo).get()
                    }
                }
            } catch (e: Exception) {
                LockerLogger.e(LockerLogger.Event.ERROR, "Watchdog: Could not elevate to foreground", e)
            }

            if (!AppLockerService.isRunning()) {
                LockerLogger.i(LockerLogger.Event.SERVICE_RESTARTED, "Watchdog: Service not running, restarting...")
                val intent = Intent(applicationContext, AppLockerService::class.java)
                
                // DYNAMIC START: 
                // We use startService first. The service will promote itself in onStartCommand.
                // This avoids BackgroundStartRestriction crashes.
                applicationContext.startService(intent)
            } else {
                LockerLogger.d(LockerLogger.Event.SERVICE_RESTARTED, "Watchdog: Service is healthy")
                // Still send an intent to trigger startForegroundSafe in onStartCommand
                applicationContext.startService(Intent(applicationContext, AppLockerService::class.java))
            }
            Result.success()
        } catch (e: Exception) {
            LockerLogger.e(LockerLogger.Event.ERROR, "Watchdog failed", e)
            Result.retry()
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val channelId = "security_watchdog"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Security Guard", NotificationManager.IMPORTANCE_MIN)
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, com.aitoyz.mapplock.MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            applicationContext, 
            0, 
            intent, 
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Security Guard")
            .setContentText("Ensuring protection is active...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingIntent)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
}
