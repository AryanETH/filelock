package com.aitoyz.mapplock

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.aitoyz.mapplock.core.SessionManager
import com.aitoyz.mapplock.security.*
import com.aitoyz.mapplock.ui.AuthUI
import com.aitoyz.mapplock.ui.theme.CyberBlack
import com.aitoyz.mapplock.ui.theme.MapplockTheme
import kotlinx.coroutines.launch

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
            
            // Match CyberBlack for perfect transition
            val cyberBlackInt = AndroidColor.parseColor("#0A0E14")
            window.setBackgroundDrawable(cyberBlackInt.toDrawable())
            
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
                } catch (_: Throwable) {
                    LockerLogger.e(LockerLogger.Event.ERROR, "[LOCK_UI] Intruder start FAILED")
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
        // Safety check to hide overlay if it somehow persisted
        com.aitoyz.mapplock.core.OverlayManager.hide()

        if (snapshotMode) {
            val currentPkg = targetPackageState.value
            if (SessionManager.isUnlocked(currentPkg)) {
                LockerLogger.d(LockerLogger.Event.SESSION_ACTIVE, "[SNAPSHOT] App resumed within grace period, finishing cover")
                finish()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
                } else {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
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
