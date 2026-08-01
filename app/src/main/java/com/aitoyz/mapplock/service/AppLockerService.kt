package com.aitoyz.mapplock.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aitoyz.mapplock.R
import com.aitoyz.mapplock.backend.usage.UsageStatsMonitor
import com.aitoyz.mapplock.core.*
import com.aitoyz.mapplock.security.LockerLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Foreground service that owns the AppLockEngine and manages the lifecycle.
 * Acts as a fallback backend when Accessibility is not active.
 */
class AppLockerService : Service() {

    companion object {
        @Volatile
        private var instance: AppLockerService? = null
        fun isRunning(): Boolean = instance != null
        fun getInstance(): AppLockerService? = instance
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                SessionManager.clearAll()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        LockerLogger.i(LockerLogger.Event.SERVICE_RESTARTED, "AppLockerService Created")
        BackendCoordinator.setActiveBackend(BackendCoordinator.BackendType.USAGE_STATS)
        BackendCoordinator.resetRestartAttempts(this::class.java.simpleName)

        serviceScope.launch(Dispatchers.Default) {
            try {
                LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "[SERVICE] UsageStats backend active")
                
                // Pre-warm repositories on background thread to avoid jank on first lock
                launch(Dispatchers.IO) {
                    com.aitoyz.mapplock.security.LockerRepository.getInstance(this@AppLockerService)
                    com.aitoyz.mapplock.security.IntruderManager.getInstance(this@AppLockerService)
                }

                // Use the centralized CoreEngine brain
                val coreEngine = com.aitoyz.mapplock.core.CoreEngine.getInstance(this@AppLockerService)
                
                // Simplified detector loop - just feeds events to CoreEngine
                val detector = UsageStatsMonitor(this@AppLockerService, serviceScope)
                
                detector.events.onEach { event ->
                    coreEngine.onForegroundEvent(event)
                }.launchIn(serviceScope)

                detector.start()
                
                withContext(Dispatchers.Main) {
                    registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
                }
                
                // Initial check
                detector.currentForeground()?.let { pkg ->
                    coreEngine.onForegroundEvent(com.aitoyz.mapplock.model.ForegroundEvent(pkg, source = com.aitoyz.mapplock.model.ForegroundEvent.Source.SYSTEM))
                }
            } catch (e: Exception) {
                LockerLogger.e(LockerLogger.Event.ERROR, "[SERVICE] UsageStats Init failed", e)
            }
        }
    }

    private fun startForegroundSafe() {
        try {
            LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "[SERVICE] Attempting foreground promotion")
            val channelId = "security_monitoring"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "Security Monitor", NotificationManager.IMPORTANCE_LOW)
                getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }

            val intent = Intent(this, com.aitoyz.mapplock.MainActivity::class.java)
            val pendingIntent = android.app.PendingIntent.getActivity(
                this, 
                0, 
                intent, 
                android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Security monitoring is active")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .build()

            val type = determineForegroundServiceType()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1001, notification, type)
            } else {
                startForeground(1001, notification)
            }
            LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "[SERVICE] Successfully promoted to foreground")
        } catch (e: Exception) {
            // Android 12+ specific exception for background start restrictions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is android.app.ForegroundServiceStartNotAllowedException) {
                LockerLogger.e(LockerLogger.Event.ERROR, "[SERVICE] Foreground start DENIED by OS (Background restriction). Continuing in background.")
            } else {
                LockerLogger.e(LockerLogger.Event.ERROR, "[SERVICE] Foreground promotion failed: ${e.message}")
            }
            // CRITICAL: We do NOT finish/crash. We stay alive in background (degraded mode)
            // This prevents the OS from seeing a crash loop.
        }
    }

    private fun determineForegroundServiceType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            0 // Default
        } else 0
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground on every start request to ensure persistence
        startForegroundSafe()
        
        if (intent?.getBooleanExtra("refresh_locked_apps", false) == true) {
            LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "[SYNC] Refresh intent received")
            com.aitoyz.mapplock.core.CoreEngine.getInstance(this).refreshLockedApps()
        }
        return START_STICKY
    }

    fun notifyLockDismissed() {
        com.aitoyz.mapplock.core.CoreEngine.getInstance(this).getLauncher().notifyFinished()
    }
    
    fun notifyLockStarted(activity: com.aitoyz.mapplock.LockActivity) {
        com.aitoyz.mapplock.core.CoreEngine.getInstance(this).getLauncher().registerActivity(activity)
    }

    override fun onDestroy() {
        LockerLogger.w(LockerLogger.Event.SERVICE_RESTARTED, "AppLockerService Destroyed - Relying on Watchdog for recovery")
        unregisterReceiver(screenReceiver)
        serviceScope.cancel()
        instance = null
        super.onDestroy()
    }
}
