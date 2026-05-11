package com.geovault.service

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.geovault.security.IntruderManager
import com.geovault.ui.AuthSelectionScreen
import com.geovault.ui.theme.CyberBlack
import com.geovault.ui.theme.GeoVaultTheme
import kotlinx.coroutines.*

class AppLockerService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var overlayView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var usageStatsManager: UsageStatsManager? = null
    private lateinit var prefs: android.content.SharedPreferences
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var targetPackageState = mutableStateOf("")
    private var isOverlayAttached = false
    private var lastForegroundPackage = ""
    private var lastResumeTime = 0L

    // Pre-calculated set for O(1) lookups
    private var lockedPackages = emptySet<String>()
    private var isMasterStealthEnabled = false

    override fun onBind(intent: Intent?): IBinder? = null

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            serviceScope.launch(Dispatchers.Main) {
                val prefs = com.geovault.security.SecureManager.getInstance(this@AppLockerService).prefs
                prefs.edit().remove("bypass_package").commit()
                hideOverlayImmediate()
            }
        }
    }

    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "bypass_package") {
            val prefs = com.geovault.security.SecureManager.getInstance(this).prefs
            val bypass = prefs.getString("bypass_package", null)
            if (bypass != null && (bypass == lastForegroundPackage || bypass == targetPackageState.value)) {
                serviceScope.launch(Dispatchers.Main) {
                    hideOverlayImmediate()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = com.geovault.security.SecureManager.getInstance(this).prefs
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        
        // Register screen receiver
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_SCREEN_OFF)
            addAction(android.content.Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
        
        // 5. High-Frequency Stability: Set thread priority to maximum
        Thread.currentThread().priority = Thread.MAX_PRIORITY

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        prepareOverlay()
        refreshLockedPackages()
        startPolling()
        scheduleWatchdog()
        
        // Ensure service stays alive with max priority
        if (Build.VERSION.SDK_INT >= 34) { // FOREGROUND_SERVICE_TYPE_SPECIAL_USE requires API 34
            startForeground(
                1001, 
                createNotification(), 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1001, createNotification())
        }
    }

    private fun scheduleWatchdog() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, BootReceiver::class.java).apply {
            action = "com.geovault.WATCHDOG"
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.setRepeating(
            android.app.AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 60000,
            60000,
            pendingIntent
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Restart the service if swiped away
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        val restartServicePendingIntent = android.app.PendingIntent.getService(
            applicationContext, 1, restartServiceIntent,
            android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmService.set(
            android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
    }

    private fun createNotification(): android.app.Notification {
        val channelId = "app_locker_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, "Security Monitoring",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Keeps the app lock active in the background"
            }
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("Mapp Lock is Active")
            .setContentText("Protecting your privacy")
            .setSmallIcon(com.geovault.R.drawable.ic_calculator)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("refresh_locked_apps", false) == true) {
            refreshLockedPackages()
        }
        
        // Handle Accessibility Events for 0ms latency
        val packageName = intent?.getStringExtra("event_package_name")
        if (packageName != null && intent.getBooleanExtra("is_accessibility_event", false)) {
            // OS: Process & Thread Management
            // Offload to Default dispatcher to keep the main thread fluid
            serviceScope.launch {
                handlePackageChange(packageName, isEventDriven = true)
            }
        }
        
        return START_STICKY
    }

    private fun prepareOverlay() {
        overlayView = ComposeView(this).apply {
            setContent {
                GeoVaultTheme(darkTheme = false) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                        if (targetPackageState.value.isNotEmpty()) {
                            LockOverlayContent(targetPackage = targetPackageState.value)
                        }
                    }
                }
            }
        }
        overlayView?.setViewTreeLifecycleOwner(this)
        overlayView?.setViewTreeViewModelStoreOwner(this)
        overlayView?.setViewTreeSavedStateRegistryOwner(this)
        overlayView?.visibility = View.GONE
    }

    private fun refreshLockedPackages() {
        serviceScope.launch(Dispatchers.IO) {
            val allVaultIds = prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()
            val apps = mutableSetOf<String>()
            allVaultIds.forEach { id ->
                apps.addAll(prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet())
            }
            lockedPackages = apps
            
            isMasterStealthEnabled = prefs.getBoolean("master_stealth_enabled", false)
        }
    }

    private data class ForegroundInfo(val packageName: String, val isNewEvent: Boolean)

    private fun getForegroundPackageInfo(): ForegroundInfo? {
        val time = System.currentTimeMillis()
        val usageEvents = usageStatsManager?.queryEvents(time - 1000, time) ?: return null
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        var lastTime = 0L
        
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED || 
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == 26) {
                lastPackage = event.packageName
                lastTime = event.timeStamp
            }
        }
        
        if (lastPackage == null) return null
        
        val isNew = lastTime > lastResumeTime
        if (isNew) {
            lastResumeTime = lastTime
        }
        
        return ForegroundInfo(lastPackage, isNew)
    }

    private fun startPolling() {
        serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                val info = getForegroundPackageInfo()
                if (info != null) {
                    handlePackageChange(info.packageName, info.isNewEvent)
                }
                
                // DAA: Greedy strategy for battery optimization
                // Poll faster when a high-risk (locked) app is likely to be open
                // or if we just detected a change.
                val delayMs = when {
                    lockedPackages.contains(lastForegroundPackage) -> 100L // Fast for locked apps
                    lastForegroundPackage == "com.android.systemui" -> 50L  // Ultra-fast for Recents
                    else -> 300L // Slower for idle/safe apps
                }
                delay(delayMs)
            }
        }
    }

    private suspend fun handlePackageChange(currentPackage: String, isEventDriven: Boolean) {
        val prefs = com.geovault.security.SecureManager.getInstance(this).prefs
        
        // Clear bypass when entering the locker app itself
        if (currentPackage == this.packageName) {
            withContext(Dispatchers.Main) {
                prefs.edit().remove("bypass_package").apply()
            }
            return
        }

        val bypassPackage = prefs.getString("bypass_package", null)

        // 1. If we are in SystemUI or a different app, ensure bypass is cleared
        if (bypassPackage != null && currentPackage != bypassPackage) {
            withContext(Dispatchers.Main) {
                prefs.edit().remove("bypass_package").apply()
            }
        }

        val isNewLaunch = currentPackage != lastForegroundPackage || isEventDriven
        
        if (isNewLaunch) {
            lastForegroundPackage = currentPackage

            // Refresh bypass after potential clear
            val updatedBypass = prefs.getString("bypass_package", null)

            val isSystemTarget = currentPackage == "com.android.settings" || 
                                 currentPackage == "com.android.packageinstaller" || 
                                 currentPackage == "com.google.android.packageinstaller" ||
                                 currentPackage == "com.android.vending"

            val shouldLock = lockedPackages.contains(currentPackage) || 
                             (isMasterStealthEnabled && isSystemTarget)

            if (shouldLock && currentPackage != updatedBypass) {
                withContext(Dispatchers.Main) {
                    targetPackageState.value = currentPackage
                    showOverlayImmediate()
                }
            } else {
                withContext(Dispatchers.Main) {
                    hideOverlayImmediate()
                }
            }
        }
    }

    private fun showOverlayImmediate() {
        if (!isOverlayAttached) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                else 
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
                @Suppress("DEPRECATION") WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            try {
                // IMPORTANT: The overlay is now OPAQUE WHITE to cover app content instantly.
                overlayView?.alpha = 1f 
                overlayView?.visibility = View.VISIBLE
                windowManager?.addView(overlayView, params)
                isOverlayAttached = true
                
                // Launch LockActivity to provide the actual PIN UI
                val isFingerprintEnabled = prefs.getBoolean("fingerprint_enabled", false)
                val lockIntent = Intent(this, com.geovault.LockActivity::class.java).apply {
                    putExtra("target_package", targetPackageState.value)
                    putExtra("request_biometric", isFingerprintEnabled)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                startActivity(lockIntent)
            } catch (e: Exception) {}
        } else {
            // Even if attached, if we are calling showOverlayImmediate, it means a lock is required.
            // Re-trigger LockActivity to be safe (handles the case where user cleared it from Recents).
            val isFingerprintEnabled = prefs.getBoolean("fingerprint_enabled", false)
            val lockIntent = Intent(this, com.geovault.LockActivity::class.java).apply {
                putExtra("target_package", targetPackageState.value)
                putExtra("request_biometric", isFingerprintEnabled)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(lockIntent)
        }
    }

    private fun hideOverlayImmediate() {
        if (isOverlayAttached && overlayView?.visibility == View.VISIBLE) {
            overlayView?.visibility = View.GONE
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            IntruderManager.getInstance(this).stopSession()
        }
    }

    @androidx.compose.runtime.Composable
    private fun LockOverlayContent(targetPackage: String) {
        // Pure White Cover for background privacy. 
        // We keep it empty so the "Initialization" screen is invisible.
        Box(modifier = Modifier.fillMaxSize().background(Color.White))
    }

    override fun onDestroy() {
        super.onDestroy()
        val prefs = com.geovault.security.SecureManager.getInstance(this).prefs
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)

        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {}

        if (isOverlayAttached) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {}
        }
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
