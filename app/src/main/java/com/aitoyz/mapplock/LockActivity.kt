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

    private var isUnlocked = false
    private val targetPackageState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            enableEdgeToEdge()
            super.onCreate(savedInstanceState)
            repository = LockerRepository.getInstance(this)
            
            LockerLogger.d(LockerLogger.Event.LOCK_ACTIVITY_STARTED, "LockActivity Created")
            
            // 1. CRITICAL: Set solid black window background to prevent ANY transparency flicker
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))

            // Professional Security: Prevent screenshots and recent app previews
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

            // Root Detection
            if (SecurityUtils.isDeviceRooted()) {
                Toast.makeText(this, "Security Alert: Rooted device detected.", Toast.LENGTH_SHORT).show()
            }
            
            val initialTargetPackage = intent.getStringExtra("target_package") ?: ""
            targetPackageState.value = initialTargetPackage
            val requestBiometric = intent.getBooleanExtra("request_biometric", false)

            val prefs = SecureManager.getInstance(this).prefs
            
            // Fullscreen Immersive
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            
            val isIntruderEnabled = prefs.getBoolean("intruder_capture_enabled", false)
            if (isIntruderEnabled) {
                IntruderManager.getInstance(this).startSession(this)
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

            setContent {
                val isDark = remember { prefs.getBoolean("is_dark_mode", false) }
                val currentTargetPackage by targetPackageState
                
                // CRITICAL: Notify repository that UI is drawn so overlay can be removed
                LaunchedEffect(currentTargetPackage) {
                    // Increased delay to ensure Compose UI is fully rendered before revealing
                    delay(500)
                    repository.updateState(LockerRepository.LockerState.LOCK_ACTIVITY_VISIBLE, currentTargetPackage)
                }

                MapplockTheme(darkTheme = isDark) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        // CRITICAL: Never use Transparent here as it exposes the app beneath during image loading.
                        color = if (isDark) CyberBlack else Color.White,
                    ) {
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
        } catch (e: Exception) {
            LockerLogger.e(LockerLogger.Event.ERROR, "Crash in LockActivity onCreate", e)
            finish()
        }
    }

    private fun handleAuthenticationSuccess(packageName: String) {
        lifecycleScope.launch {
            isUnlocked = true
            LockerLogger.i(LockerLogger.Event.AUTH_SUCCESS, "Authenticated for $packageName")
            repository.startSession(packageName)
            repository.updateState(LockerRepository.LockerState.AUTHENTICATED, packageName)
            
            // Small delay to allow state to propagate before finishing
            delay(50)
            repository.updateState(LockerRepository.LockerState.UNLOCKED_SESSION, packageName)
            finish()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newTarget = intent.getStringExtra("target_package") ?: ""
        if (newTarget.isNotEmpty()) {
            targetPackageState.value = newTarget
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        IntruderManager.getInstance(this).stopSession()
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
