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
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.geovault.security.IntruderManager
import com.geovault.security.UnlockSessionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppLockerService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var nativeOverlayView: android.view.View? = null
    private var windowManager: WindowManager? = null
    private var usageStatsManager: UsageStatsManager? = null
    private lateinit var prefs: android.content.SharedPreferences
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val detectionMutex = Mutex()
    private var isOverlayAttached = false
    private var lastForegroundPackage = ""
    private var lastResumeTime = 0L

    // Pre-calculated set for O(1) lookups
    private var lockedPackages = emptySet<String>()
    private var isMasterStealthEnabled = false

    override fun onBind(intent: Intent?): IBinder? = null

    private var screenStateFlow = MutableStateFlow(true)

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                android.content.Intent.ACTION_SCREEN_ON -> {
                    screenStateFlow.value = true
                    lastForegroundPackage = ""
                }
                android.content.Intent.ACTION_SCREEN_OFF -> {
                    screenStateFlow.value = false
                    serviceScope.launch {
                        UnlockSessionManager.getInstance(this@AppLockerService).clearAll()
                        hideOverlayImmediate()
                    }
                }
                "com.geovault.HIDE_OVERLAY" -> {
                    serviceScope.launch {
                        hideOverlayImmediate()
                    }
                }
                android.content.Intent.ACTION_USER_PRESENT -> {
                    screenStateFlow.value = true
                }
            }
        }
    }

    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "bypass_package") {
            val bypass = prefs.getString("bypass_package", null)
            if (bypass != null && (bypass == lastForegroundPackage)) {
                serviceScope.launch {
                    hideOverlayImmediate()
                }
            }
        }
    }

    companion object {
        private var instance: AppLockerService? = null
        
        fun isRunning(): Boolean = instance != null

        fun onPackageDetected(packageName: String) {
            instance?.let { service ->
                service.serviceScope.launch {
                    service.handlePackageChange(packageName, isEventDriven = true)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundSafe()

        // 2. High-Priority Setup
        Thread.currentThread().priority = Thread.MAX_PRIORITY
        
        prefs = com.geovault.security.SecureManager.getInstance(this).prefs
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_SCREEN_ON)
            addAction(android.content.Intent.ACTION_SCREEN_OFF)
            addAction(android.content.Intent.ACTION_USER_PRESENT)
            addAction("com.geovault.HIDE_OVERLAY")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        prepareNativeOverlay()
        refreshLockedPackages()
        startPolling()
        scheduleWatchdog()
    }

    private fun startForegroundSafe() {
        try {
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                // Android 14/15 requires explicit foreground service type
                startForeground(
                    1001, 
                    notification, 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(1001, notification)
            }
            android.util.Log.d("AppLockerService", "Foreground service started successfully")
        } catch (e: Exception) {
            android.util.Log.e("AppLockerService", "Foreground start failed: ${e.message}")
            // Fallback: If it's a background restriction, we might need to wait for a valid transition
            if (Build.VERSION.SDK_INT >= 31 && e is android.app.ForegroundServiceStartNotAllowedException) {
                android.util.Log.e("AppLockerService", "FGS start not allowed from background")
            }
        }
    }

    private fun prepareNativeOverlay() {
        // High-speed native black view for 0ms blocking
        nativeOverlayView = android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }
    }

    private fun scheduleWatchdog() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, BootReceiver::class.java).apply { action = "com.geovault.WATCHDOG" }
        val pendingIntent = android.app.PendingIntent.getBroadcast(this, 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setRepeating(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60000, 60000, pendingIntent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartServiceIntent = Intent(applicationContext, this.javaClass).apply { setPackage(packageName) }
        val restartServicePendingIntent = android.app.PendingIntent.getService(applicationContext, 1, restartServiceIntent, android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmService.set(android.app.AlarmManager.ELAPSED_REALTIME, android.os.SystemClock.elapsedRealtime() + 1000, restartServicePendingIntent)
    }

    private fun createNotification(): android.app.Notification {
        val channelId = "security_monitoring_channel"
        val channelName = getString(com.geovault.R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, channelName, android.app.NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Keeps the app lock active in the background"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(channel)
        }

        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle(channelName)
            .setContentText(getString(com.geovault.R.string.bg_active_desc))
            .setSmallIcon(com.geovault.R.mipmap.ic_launcher)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("refresh_locked_apps", false) == true) {
            refreshLockedPackages()
        }
        
        val packageName = intent?.getStringExtra("event_package_name")
        if (packageName != null && intent.getBooleanExtra("is_accessibility_event", false)) {
            serviceScope.launch {
                handlePackageChange(packageName, isEventDriven = true)
            }
        }
        
        if (pollingJob == null || !pollingJob!!.isActive) {
            startPolling()
        }

        return START_STICKY
    }

    private fun refreshLockedPackages() {
        val allVaultIds = prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()
        val apps = mutableSetOf<String>()
        allVaultIds.forEach { id ->
            val vaultApps = prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet()
            apps.addAll(vaultApps)
        }
        lockedPackages = apps
        isMasterStealthEnabled = prefs.getBoolean("master_stealth_enabled", false)
    }

    private data class ForegroundInfo(val packageName: String, val isNewEvent: Boolean)

    private fun getForegroundPackageInfo(): ForegroundInfo? {
        val time = System.currentTimeMillis()
        val usageEvents = usageStatsManager?.queryEvents(time - 5000, time)
        if (usageEvents != null) {
            val event = UsageEvents.Event()
            var lastPkg: String? = null
            var lastTime = 0L
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (event.timeStamp > lastTime) {
                        lastPkg = event.packageName
                        lastTime = event.timeStamp
                    }
                }
            }
            if (lastPkg != null && lastTime > lastResumeTime) {
                lastResumeTime = lastTime
                return ForegroundInfo(lastPkg, true)
            }
        }
        
        val stats = usageStatsManager?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 10000, time)
        if (!stats.isNullOrEmpty()) {
            val latest = stats.maxByOrNull { it.lastTimeUsed }
            if (latest != null && latest.packageName != lastForegroundPackage) {
                return ForegroundInfo(latest.packageName, false)
            }
        }
        return null
    }

    private var pollingJob: Job? = null

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch(Dispatchers.Default) {
            // Wait a bit to let Accessibility Service take lead
            delay(1000L)
            while (isActive) {
                try {
                    if (screenStateFlow.value) {
                        val info = getForegroundPackageInfo()
                        if (info != null) {
                            // Only handle if it's a new launch or we missed an event
                            handlePackageChange(info.packageName, info.isNewEvent)
                        }
                        delay(500L) // Slower polling fallback to save battery
                    } else {
                        withTimeoutOrNull(2000L) { screenStateFlow.filter { it }.first() }
                    }
                } catch (e: Exception) {
                    delay(2000L)
                }
            }
        }
    }

    private fun isLauncherApp(packageName: String): Boolean {
        if (packageName == "android" || packageName == "com.android.systemui") return true
        if (packageName.contains("launcher", ignoreCase = true) || packageName.contains("home", ignoreCase = true)) return true
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private suspend fun handlePackageChange(currentPackage: String, isEventDriven: Boolean) {
        detectionMutex.withLock {
            val myPackage = this.packageName
            val sessionManager = UnlockSessionManager.getInstance(this)

            if (currentPackage == myPackage) {
                hideOverlayImmediate()
                return@withLock
            }

            val isLauncher = isLauncherApp(currentPackage)
            if (isLauncher) {
                // Proactively clear sessions for dead apps
                serviceScope.launch(Dispatchers.IO) {
                    sessionManager.pruneSessions(this@AppLockerService)
                }
                
                // For "Strict" behavior: Clear the current bypass when leaving to Launcher.
                // The grace period in UnlockSessionManager handles the transition loop.
                val currentBypass = prefs.getString("bypass_package", null)
                if (currentBypass != null) {
                    sessionManager.clear(currentBypass)
                }

                lastForegroundPackage = ""
                hideOverlayImmediate()
                return@withLock
            }

            val isNewLaunch = currentPackage != lastForegroundPackage || isEventDriven
            
            if (isNewLaunch) {
                lastForegroundPackage = currentPackage
                
                val isSystemTarget = currentPackage == "com.android.packageinstaller" || currentPackage == "com.google.android.packageinstaller"
                val isRestrictedSystemApp = currentPackage == "com.android.settings" || currentPackage == "com.android.vending" || currentPackage == "com.google.android.vending"

                val shouldLock = !isRestrictedSystemApp && (lockedPackages.contains(currentPackage) || (isMasterStealthEnabled && isSystemTarget))

                if (shouldLock) {
                    // Double check with Recents list to be absolutely strict
                    sessionManager.pruneSessions(this)
                    
                    if (!sessionManager.isUnlocked(currentPackage)) {
                        withContext(Dispatchers.Main) {
                            showOverlayImmediate(currentPackage)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            hideOverlayImmediate()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        hideOverlayImmediate()
                    }
                }
            }
        }
    }

    private var lastLockedPackage: String? = null

    private fun showOverlayImmediate(targetPackage: String) {
        if (isOverlayAttached && lastLockedPackage == targetPackage) return
        
        lastLockedPackage = targetPackage
        if (!isOverlayAttached) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            try {
                windowManager?.addView(nativeOverlayView, params)
                isOverlayAttached = true
            } catch (e: Exception) {}
        }
        triggerLockActivity(targetPackage)
    }

    private fun triggerLockActivity(targetPackage: String) {
        val isFingerprintEnabled = prefs.getBoolean("fingerprint_enabled", true)
        val lockIntent = Intent(this, com.geovault.LockActivity::class.java).apply {
            putExtra("target_package", targetPackage)
            putExtra("request_biometric", isFingerprintEnabled)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        startActivity(lockIntent)
    }

    private fun hideOverlayImmediate() {
        if (isOverlayAttached) {
            try {
                windowManager?.removeView(nativeOverlayView)
            } catch (e: Exception) {}
            isOverlayAttached = false
            lastLockedPackage = null
            IntruderManager.getInstance(this).stopSession()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}
        if (isOverlayAttached) { try { windowManager?.removeView(nativeOverlayView) } catch (e: Exception) {} }
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
