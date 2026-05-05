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
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var targetPackageState = mutableStateOf("")
    private var isOverlayAttached = false
    private var lastForegroundPackage = ""

    // Pre-calculated set for O(1) lookups
    private var lockedPackages = emptySet<String>()
    private var isUninstallProtectionEnabled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        prepareOverlay()
        refreshLockedPackages()
        startPolling()
        
        // Ensure service stays alive
        startForeground(1001, createNotification())
    }

    private fun createNotification(): android.app.Notification {
        val channelId = "app_locker_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, "Security Monitoring",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("Mapp Lock is Active")
            .setContentText("Protecting your privacy")
            .setSmallIcon(com.geovault.R.drawable.ic_calculator)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("refresh_locked_apps", false) == true) {
            refreshLockedPackages()
        }
        return START_STICKY
    }

    private fun prepareOverlay() {
        overlayView = ComposeView(this).apply {
            setContent {
                GeoVaultTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = CyberBlack) {
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
            val prefs = com.geovault.security.SecureManager.getInstance(this@AppLockerService).prefs
            val allVaultIds = prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()
            val apps = mutableSetOf<String>()
            allVaultIds.forEach { id ->
                apps.addAll(prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet())
            }
            lockedPackages = apps
            
            // Sync uninstall protection state
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val adminComponent = android.content.ComponentName(this@AppLockerService, com.geovault.security.UninstallProtectionReceiver::class.java)
            isUninstallProtectionEnabled = dpm.isAdminActive(adminComponent)
        }
    }

    private fun startPolling() {
        serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                checkForegroundApp()
                delay(150) // High-frequency polling (roughly 6.6 times per second)
            }
        }
    }

    private suspend fun checkForegroundApp() {
        val currentPackage = getForegroundPackage() ?: return
        
        if (currentPackage == lastForegroundPackage) return
        lastForegroundPackage = currentPackage

        // Ignore system and self
        if (currentPackage == this.packageName || currentPackage == "android" || 
            currentPackage == "com.android.systemui" || currentPackage == "com.android.launcher" ||
            currentPackage.contains("launcher")) {
            withContext(Dispatchers.Main) { hideOverlayImmediate() }
            return
        }

        val prefs = com.geovault.security.SecureManager.getInstance(this).prefs
        val bypassPackage = prefs.getString("bypass_package", null)

        // Protect Settings and Package Installer if uninstall protection is on
        val isSystemTarget = currentPackage == "com.android.settings" || 
                             currentPackage == "com.android.packageinstaller" || 
                             currentPackage == "com.google.android.packageinstaller" ||
                             currentPackage == "com.android.vending" // Also protect Play Store to prevent uninstall there

        if (lockedPackages.contains(currentPackage) || (isUninstallProtectionEnabled && isSystemTarget)) {
            if (currentPackage != bypassPackage) {
                withContext(Dispatchers.Main) {
                    targetPackageState.value = currentPackage
                    showOverlayImmediate()
                }
            } else {
                withContext(Dispatchers.Main) { hideOverlayImmediate() }
            }
        } else {
            if (bypassPackage != null && currentPackage != bypassPackage) {
                prefs.edit().remove("bypass_package").apply()
            }
            withContext(Dispatchers.Main) { hideOverlayImmediate() }
        }
    }

    private fun getForegroundPackage(): String? {
        val time = System.currentTimeMillis()
        val usageEvents = usageStatsManager?.queryEvents(time - 1000 * 60, time) ?: return null
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastPackage = event.packageName
            }
        }
        return lastPackage
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
                overlayView?.visibility = View.VISIBLE
                windowManager?.addView(overlayView, params)
                isOverlayAttached = true
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                
                // Ensure camera is ready
                serviceScope.launch {
                    delay(500) // Small delay to ensure view is attached
                    IntruderManager.getInstance(this@AppLockerService).startSession(this@AppLockerService)
                }
            } catch (e: Exception) {
                android.util.Log.e("AppLockerService", "Overlay Error", e)
            }
        } else if (overlayView?.visibility != View.VISIBLE) {
            overlayView?.visibility = View.VISIBLE
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            IntruderManager.getInstance(this).startSession(this)
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
        val context = this
        val isSystemLock = targetPackage == "com.android.settings" || 
                           targetPackage.contains("packageinstaller") ||
                           targetPackage == "com.android.vending"

        AuthSelectionScreen(
            context = context,
            targetPackage = targetPackage,
            titleOverride = if (isSystemLock) "UNINSTALL PROTECTION" else null,
            onAuthenticated = {
                val prefs = com.geovault.security.SecureManager.getInstance(context).prefs
                prefs.edit().putString("bypass_package", targetPackage).commit()
                hideOverlayImmediate()
            },
            onBiometricRequested = {
                val lockIntent = Intent(context, com.geovault.LockActivity::class.java)
                lockIntent.putExtra("target_package", targetPackage)
                lockIntent.putExtra("request_biometric", true)
                lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(lockIntent)
                hideOverlayImmediate()
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isOverlayAttached) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {}
        }
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
