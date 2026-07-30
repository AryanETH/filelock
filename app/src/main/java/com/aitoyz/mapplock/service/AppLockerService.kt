package com.aitoyz.mapplock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aitoyz.mapplock.R
import com.aitoyz.mapplock.core.*
import com.aitoyz.mapplock.repository.LockedAppsRepository
import com.aitoyz.mapplock.security.LockerLogger
import kotlinx.coroutines.*

/**
 * Foreground service that owns the AppLockEngine and manages the lifecycle.
 */
class AppLockerService : Service() {

    companion object {
        @Volatile
        private var instance: AppLockerService? = null
        fun isRunning(): Boolean = instance != null
        fun getInstance(): AppLockerService? = instance
    }
    
    fun notifyLockDismissed() {
        if (::engine.isInitialized) {
            engine.getLauncher().notifyFinished()
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var engine: AppLockEngine

    override fun onBind(intent: Intent?): IBinder? = null

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                SessionManager.clearAll()
            }
        }
    }

    override fun onCreate() {
        startForegroundSafe() // FIRST LINE
        super.onCreate()
        instance = this
        
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "[REWRITE] Service started and foregrounded")

        serviceScope.launch(Dispatchers.Default) {
            try {
                LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "[REWRITE] Async init starting")
                
                val lockedApps = LockedAppsRepository(this@AppLockerService)
                val detector = BackendSelector.select(this@AppLockerService, serviceScope)
                val decisionEngine = LockDecisionEngine(packageName, lockedApps)
                val launcher = LockLauncher(this@AppLockerService)
                val systemFilter = SystemAppFilter(this@AppLockerService)
                
                // Periodic cache refresh for system apps
                serviceScope.launch(Dispatchers.Default) {
                    while (isActive) {
                        delay(60_000) // Every 60 seconds
                        systemFilter.refreshCaches()
                    }
                }
                
                engine = AppLockEngine(
                    scope = serviceScope,
                    detector = detector,
                    decisionEngine = decisionEngine,
                    launcher = launcher,
                    systemAppFilter = systemFilter
                )

                withContext(Dispatchers.Main) {
                    registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
                    engine.start()
                    LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "[REWRITE] Engine active")
                }
            } catch (e: Exception) {
                LockerLogger.e(LockerLogger.Event.ERROR, "[REWRITE] Init failed", e)
            }
        }
    }

    private fun startForegroundSafe() {
        LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "[REWRITE] Setting up notification")
        val channelId = "security_monitoring"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Security Monitor", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Security monitoring is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1001, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        engine.stop()
        unregisterReceiver(screenReceiver)
        serviceScope.cancel()
        instance = null
        super.onDestroy()
    }
}
