package com.geovault.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.os.Build
import android.graphics.PixelFormat
import android.view.WindowManager
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.geovault.ui.theme.GeoVaultTheme
import com.geovault.ui.theme.CyberBlack
import com.geovault.ui.AuthSelectionScreen
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import kotlinx.coroutines.*

class AppLockerService : AccessibilityService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var overlayView: ComposeView? = null
    private var windowManager: WindowManager? = null
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var targetPackageState = mutableStateOf("")
    private var isOverlayAttached = false

    // Pre-calculated set for O(1) lookups
    private var lockedPackages = emptySet<String>()

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // 1. PRE-INFLATION: Create and prepare the view in memory immediately
        prepareOverlay()
        
        // 2. WARM UP: Load package list into memory immediately
        refreshLockedPackages()
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
        
        // Set initial state to GONE to prevent any accidental display
        overlayView?.visibility = View.GONE
    }

    private fun refreshLockedPackages() {
        serviceScope.launch(Dispatchers.IO) {
            val prefs = com.geovault.security.SecureManager.getInstance(this@AppLockerService).prefs
            val criticalPackages = setOf("com.android.vending", "com.android.settings", "com.google.android.permissioncontroller", "com.android.packageinstaller")
            val allVaultIds = prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()
            val apps = mutableSetOf<String>()
            allVaultIds.forEach { id ->
                apps.addAll(prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet())
            }
            apps.addAll(criticalPackages)
            lockedPackages = apps
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val startTime = System.currentTimeMillis()
        
        // Optimization: Fast-path for window changes
        val currentPackage = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> event.packageName?.toString()
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> getForegroundPackageFromWindows()
            else -> null
        } ?: return

        // Ignore system and self
        if (currentPackage == this.packageName || currentPackage == "android" || currentPackage == "com.android.systemui") {
            return
        }

        val prefs = com.geovault.security.SecureManager.getInstance(this).prefs
        val bypassPackage = prefs.getString("bypass_package", null)

        if (lockedPackages.contains(currentPackage)) {
            if (currentPackage != bypassPackage) {
                targetPackageState.value = currentPackage
                showOverlayImmediate()
                startLockActivity(currentPackage)
            } else {
                hideOverlayImmediate()
            }
        } else {
            if (bypassPackage != null && currentPackage != bypassPackage) {
                prefs.edit().remove("bypass_package").apply()
            }
            hideOverlayImmediate()
        }
        
        val duration = System.currentTimeMillis() - startTime
        if (duration > 10) {
            android.util.Log.w("AppLockerService", "Latency Warning: ${duration}ms")
        }
    }

    private fun getForegroundPackageFromWindows(): String? {
        val windows = windows
        return windows.find { it.isFocused }?.root?.packageName?.toString()
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
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                // Ensure it covers EVERYTHING including status/nav bars
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
            } catch (e: Exception) {
                android.util.Log.e("AppLockerService", "Overlay Error", e)
            }
        } else {
            overlayView?.visibility = View.VISIBLE
        }
    }

    private fun hideOverlayImmediate() {
        if (isOverlayAttached) {
            overlayView?.visibility = View.GONE
            // Keep attached but GONE for zero-latency next-show
        }
    }

    private fun startLockActivity(targetPackage: String) {
        val intent = Intent(this, com.geovault.LockActivity::class.java)
        intent.putExtra("target_package", targetPackage)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_SINGLE_TOP or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                       Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        startActivity(intent)
    }

    @androidx.compose.runtime.Composable
    private fun LockOverlayContent(targetPackage: String) {
        val context = this
        AuthSelectionScreen(
            context = context,
            targetPackage = targetPackage,
            onAuthenticated = {
                val prefs = com.geovault.security.SecureManager.getInstance(context).prefs
                prefs.edit().putString("bypass_package", targetPackage).apply()
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

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (isOverlayAttached) {
            windowManager?.removeView(overlayView)
        }
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
