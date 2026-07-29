package com.aitoyz.mapplock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aitoyz.mapplock.R
import com.aitoyz.mapplock.core.*
import com.aitoyz.mapplock.repository.LockedAppsRepository
import com.aitoyz.mapplock.repository.SettingsRepository
import com.aitoyz.mapplock.security.LockerLogger
import com.aitoyz.mapplock.security.LockerRepository
import com.aitoyz.mapplock.LockActivity
import kotlinx.coroutines.*

/**
 * The high-level service that manages the app locking lifecycle.
 * Now refactored to use the Clean Backend Architecture.
 */
class AppLockerService : Service() {

    companion object {
        @Volatile
        private var instance: AppLockerService? = null
        fun isRunning(): Boolean = instance != null
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    private lateinit var engine: AppLockEngine
    private lateinit var overlayService: OverlayService
    private lateinit var lockedAppsRepository: LockedAppsRepository
    private lateinit var sessionManager: LockSessionManager

    override fun onBind(intent: Intent?): IBinder? = null

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    LockSessionManager.clearAll()
                    LockerLogger.i(LockerLogger.Event.ACCESSIBILITY_EVENT, "Screen off, clearing sessions")
                }
                Intent.ACTION_SCREEN_ON -> {
                    LockerLogger.i(LockerLogger.Event.STATE_TRANSITION, "Screen on, triggering engine recheck")
                    engine.recheck()
                }
            }
        }
    }

    override fun onCreate() {
        startForegroundSafe()
        super.onCreate()
        instance = this
        
        LockerLogger.i(LockerLogger.Event.SERVICE_RESTARTED, "Monitor Service Starting")
        
        lockedAppsRepository = LockedAppsRepository(this)
        val settingsRepository = SettingsRepository(this)
        sessionManager = LockSessionManager
        
        val monitor = BackendSelector.select(this)
        
        overlayService = OverlayService(this, settingsRepository, serviceScope)
        
        engine = AppLockEngine(
            scope = serviceScope,
            monitor = monitor,
            lockedApps = lockedAppsRepository,
            sessions = sessionManager,
            onTriggerOverlay = { packageName ->
                overlayService.show(
                    packageName = packageName,
                    onAuthenticated = {
                        sessionManager.onAppUnlocked(packageName)
                    },
                    onBiometricRequested = {
                        triggerLockActivity(packageName, requestBiometric = true)
                    }
                )
            }
        )

        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }

        engine.start()
        
        LockerLogger.i(LockerLogger.Event.SERVICE_RESTARTED, "Monitor Service Ready")
    }

    private fun startForegroundSafe() {
        try {
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            LockerLogger.e(LockerLogger.Event.ERROR, "Foreground start failed", e)
        }
    }

    private fun triggerLockActivity(targetPackage: String, requestBiometric: Boolean = false) {
        try {
            val lockIntent = Intent(this, LockActivity::class.java).apply {
                putExtra("target_package", targetPackage)
                putExtra("request_biometric", requestBiometric)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                         Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or 
                         Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            
            try {
                startActivity(lockIntent)
                LockerLogger.d(LockerLogger.Event.LOCK_ACTIVITY_STARTED, "startActivity for $targetPackage")
            } catch (e: Exception) {
                val pendingIntent = PendingIntent.getActivity(this, 0, lockIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                pendingIntent.send()
                LockerLogger.d(LockerLogger.Event.LOCK_ACTIVITY_STARTED, "PendingIntent for $targetPackage")
            }
        } catch (e: Exception) {
            LockerLogger.e(LockerLogger.Event.ERROR, "Failed to trigger LockActivity", e)
        }
    }

    private fun createNotification(): Notification {
        val channelId = "security_monitoring_channel"
        val channelName = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps the app lock active in the background"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(channelName)
            .setContentText(getString(R.string.bg_active_desc))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("refresh_locked_apps", false) == true) {
            lockedAppsRepository.refreshCache()
        }
        startForegroundSafe()
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            engine.stop()
            overlayService.onDestroy()
            unregisterReceiver(screenReceiver)
            serviceScope.cancel()
            instance = null
        } catch (e: Exception) {}
        super.onDestroy()
    }
}
