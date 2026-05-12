package com.geovault

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
import com.geovault.ui.theme.CyberDarkBlue
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geovault.ui.theme.GeoVaultTheme
import com.geovault.ui.theme.CyberBlack
import com.geovault.ui.AuthUI
import com.geovault.security.IntruderManager
import android.content.Context
import android.content.Intent

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

class LockActivity : AppCompatActivity() {

    private var isUnlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        val targetPackage = intent.getStringExtra("target_package") ?: ""
        val isSilentCover = intent.getBooleanExtra("is_silent_cover", false)
        val requestBiometric = intent.getBooleanExtra("request_biometric", false)

        // Mark lock as active for Accessibility interception
        val prefs = com.geovault.security.SecureManager.getInstance(this).prefs
        prefs.edit().putBoolean("lock_active_right_now", true).apply()

        // Fullscreen Immersive
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        IntruderManager.getInstance(this).startSession(this)

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
            val isRestricted = remember { prefs.getBoolean("screenshot_restriction", true) }
            
            GeoVaultTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White,
                ) {
                    // Apply FLAG_SECURE dynamically
                    SideEffect {
                        if (isRestricted) {
                            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }

                    AuthUI(
                        context = this,
                        targetPackage = targetPackage,
                        autoRequestBiometric = requestBiometric,
                        onAuthenticated = {
                            isUnlocked = true
                            val authPrefs = com.geovault.security.SecureManager.getInstance(this).prefs
                            authPrefs.edit().putString("bypass_package", targetPackage).apply()
                            unlock(targetPackage)
                        },
                    ) {
                        showBiometricPrompt(targetPackage)
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // We let the user go home.
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        val targetPackage = intent.getStringExtra("target_package") ?: ""
        val requestBiometric = intent.getBooleanExtra("request_biometric", false)
        
        if (requestBiometric) {
            showBiometricPrompt(targetPackage)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.geovault.security.SecureManager.getInstance(this).prefs.edit()
            .putBoolean("lock_active_right_now", false).apply()
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
                    isUnlocked = true
                    val prefs = com.geovault.security.SecureManager.getInstance(this@LockActivity).prefs
                    prefs.edit().putString("bypass_package", targetPackage).apply()
                    unlock(targetPackage)
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

    private fun unlock(targetPackage: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
        launchIntent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it)
        }
        finish()
    }
}
