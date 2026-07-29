package com.aitoyz.mapplock.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aitoyz.mapplock.security.LockerLogger
import com.aitoyz.mapplock.repository.SettingsRepository
import com.aitoyz.mapplock.ui.AuthUI
import com.aitoyz.mapplock.ui.theme.MapplockTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages the floating lock overlay.
 */
class OverlayService(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope
) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, ActivityResultRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    // Dummy ActivityResultRegistry for the Service environment
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            LockerLogger.w(LockerLogger.Event.ERROR, "ActivityResultRegistry.onLaunch called in Service overlay. This is not supported.")
        }
    }

    override val activityResultRegistry: ActivityResultRegistry get() = registry

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayComposeView: ComposeView? = null
    private var isOverlayAttached = false

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun show(packageName: String, onAuthenticated: () -> Unit, onBiometricRequested: () -> Unit) {
        scope.launch(Dispatchers.Main) {
            if (isOverlayAttached) return@launch

            prepareOverlay(packageName, onAuthenticated, onBiometricRequested)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT
            ).apply {
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            try {
                windowManager.addView(overlayComposeView, params)
                isOverlayAttached = true
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                LockerLogger.d(LockerLogger.Event.OVERLAY_ADDED, "Overlay added and resumed for $packageName")
            } catch (e: Exception) {
                LockerLogger.e(LockerLogger.Event.ERROR, "Failed to add overlay", e)
            }
        }
    }

    fun hide() {
        scope.launch(Dispatchers.Main) {
            if (!isOverlayAttached) return@launch
            try {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                windowManager.removeView(overlayComposeView)
                isOverlayAttached = false
                LockerLogger.d(LockerLogger.Event.OVERLAY_REMOVED, "Overlay paused and removed")
            } catch (e: Exception) {
                LockerLogger.e(LockerLogger.Event.ERROR, "Failed to remove overlay", e)
            }
        }
    }

    private fun prepareOverlay(packageName: String, onAuthenticated: () -> Unit, onBiometricRequested: () -> Unit) {
        overlayComposeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides this@OverlayService) {
                    val isDarkMode = settingsRepository.isDarkMode().collectAsState(initial = false).value
                    MapplockTheme(darkTheme = isDarkMode) {
                        AuthUI(
                            context = context,
                            targetPackage = packageName,
                            isOverlay = true,
                            onAuthenticated = {
                                onAuthenticated()
                                hide()
                            },
                            onBiometricRequested = {
                                onBiometricRequested()
                            }
                        )
                    }
                }
            }
        }
    }

    fun onDestroy() {
        hide()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
