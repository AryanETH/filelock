package com.aitoyz.mapplock

import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.aitoyz.mapplock.ui.theme.CyberDarkBlue
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aitoyz.mapplock.ui.theme.MapplockTheme
import com.aitoyz.mapplock.ui.theme.CyberBlack
import com.aitoyz.mapplock.security.*
import com.aitoyz.mapplock.ui.AuthUI
import com.aitoyz.mapplock.core.SessionManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.WindowManager
import android.graphics.Color as AndroidColor
import androidx.appcompat.app.AppCompatActivity
import android.os.Build

import com.aitoyz.mapplock.security.LocaleManager
import com.aitoyz.mapplock.security.SecurityUtils
import android.widget.Toast
import android.util.Log

class LockActivity : AppCompatActivity() {

    private lateinit var repository: LockerRepository

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleManager.getLanguage(newBase)
        super.attachBaseContext(LocaleManager.getLocaleContext(newBase, lang))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Final fallback: Hide overlay as soon as activity window is attached
        com.aitoyz.mapplock.core.OverlayManager.hide()
    }

    private var isUnlocked = false
    private val targetPackageState = mutableStateOf("")
    private var snapshotMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            LockerLogger.i(LockerLogger.Event.LOCK_ACTIVITY_STARTED, "[LOCK_UI] onCreate entered")
            enableEdgeToEdge()
            super.onCreate(savedInstanceState)

            val initialTargetPackage = intent.getStringExtra("target_package") ?: ""
            targetPackageState.value = initialTargetPackage
            val requestBiometric = intent.getBooleanExtra("request_biometric", false)
            snapshotMode = intent.getBooleanExtra("is_snapshot", false)
            
            LockerLogger.d(LockerLogger.Event.LOCK_ACTIVITY_STARTED, "[LOCK_UI] Initializing Repository")
            repository = LockerRepository.getInstance(this)
            
            LockerLogger.d(LockerLogger.Event.LOCK_ACTIVITY_STARTED, "[LOCK_UI] Setting up Window")
            // 1. Immersive Full Screen Setup
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(AndroidColor.BLACK))
            window.statusBarColor = AndroidColor.TRANSPARENT
            window.navigationBarColor = AndroidColor.TRANSPARENT

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            // Professional Security: Prevent screenshots and recent app previews
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            
            // Root Detection
            if (SecurityUtils.isDeviceRooted()) {
                Toast.makeText(this, "Security Alert: Rooted device detected.", Toast.LENGTH_SHORT).show()
            }

            val prefs = SecureManager.getInstance(this).prefs
            
            LockerLogger.d(LockerLogger.Event.LOCK_ACTIVITY_STARTED, "[LOCK_UI] Checking Intruder Settings")
            val isIntruderEnabled = try { prefs.getBoolean("intruder_capture_enabled", false) } catch (e: Exception) { false }
            if (isIntruderEnabled) {
                LockerLogger.i(LockerLogger.Event.LOCK_ACTIVITY_STARTED, "[LOCK_UI] Starting Intruder Session")
                try {
                    IntruderManager.getInstance(this).startSession(this)
                } catch (e: Throwable) {
                    LockerLogger.e(LockerLogger.Event.ERROR, "[LOCK_UI] Intruder start FAILED", e)
                }
            }

            onBackPressedDispatcher.addCallback(this) {
                // Redirect to Home instead of showing the app behind
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
                finish()
            }

            LockerLogger.d(LockerLogger.Event.LOCK_ACTIVITY_STARTED, "[LOCK_UI] Setting Content")
            setContent {
                val isDark = remember { prefs.getBoolean("is_dark_mode", false) }
                val currentTargetPackage by targetPackageState
                val isSnapshot = snapshotMode
                
                // No additional state required for deterministic engine
                LaunchedEffect(currentTargetPackage) {
                    repository.updateState(LockerRepository.LockerState.LOCK_ACTIVITY_VISIBLE, currentTargetPackage)
                    // HIDE OVERLAY when UI is ready to show
                    com.aitoyz.mapplock.core.OverlayManager.hide()
                }

                MapplockTheme(darkTheme = isDark) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        // CRITICAL: Never use Transparent here as it exposes the app beneath during image loading.
                        color = if (isDark || isSnapshot) CyberBlack else Color.White,
                    ) {
                        if (!isSnapshot) {
                            AuthUI(
                                context = this@LockActivity,
                                targetPackage = currentTargetPackage,
                                autoRequestBiometric = requestBiometric,
                                onAuthenticated = {
                                    handleAuthenticationSuccess(currentTargetPackage)
                                },
                            ) {
                                showBiometricPrompt(currentTargetPackage)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LockerLogger.e(LockerLogger.Event.ERROR, "Crash in LockActivity onCreate", e)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (snapshotMode) {
            val currentPkg = targetPackageState.value
            if (SessionManager.isUnlocked(currentPkg)) {
                LockerLogger.d(LockerLogger.Event.SESSION_ACTIVE, "[SNAPSHOT] App resumed within grace period, finishing cover")
                finish()
                overridePendingTransition(0, 0)
            } else {
                LockerLogger.d(LockerLogger.Event.STATE_LOCKED, "[SNAPSHOT] Session expired, switching to Auth UI")
                snapshotMode = false
            }
        }
    }

    private fun handleAuthenticationSuccess(packageName: String) {
        lifecycleScope.launch {
            isUnlocked = true
            LockerLogger.i(LockerLogger.Event.AUTH_SUCCESS, "[AUTH_SUCCESS] $packageName")
            
            // 1. Mark session as authenticated
            SessionManager.unlock(packageName)
            
            // 2. Clear UI state
            repository.updateState(LockerRepository.LockerState.AUTHENTICATED, packageName)
            
            // 3. Return to the protected app
            finish()
            overridePendingTransition(0, 0)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newTarget = intent.getStringExtra("target_package") ?: ""
        if (newTarget.isNotEmpty()) {
            targetPackageState.value = newTarget
        }
    }

    override fun onPause() {
        super.onPause()
        com.aitoyz.mapplock.core.OverlayManager.hide()
    }

    override fun onDestroy() {
        super.onDestroy()
        IntruderManager.getInstance(this).stopSession()
        
        // Notify the active backend that the lock was dismissed
        com.aitoyz.mapplock.service.AppLockerService.getInstance()?.notifyLockDismissed()
        com.aitoyz.mapplock.backend.accessibility.AppLockAccessibilityService.getInstance()?.notifyLockDismissed()
    }

    private fun showBiometricPrompt(targetPackage: String) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    handleAuthenticationSuccess(targetPackage)
                }
            }
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Identity Verification")
            .setSubtitle("Confirm your Phone PIN/Pattern to unlock")
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )

        biometricPrompt.authenticate(builder.build())
    }
}
