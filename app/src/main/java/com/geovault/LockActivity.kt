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

import com.geovault.security.LocaleManager
import com.geovault.security.SecurityUtils
import android.widget.Toast

class LockActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleManager.getLanguage(newBase)
        super.attachBaseContext(LocaleManager.getLocaleContext(newBase, lang))
    }

    private var isUnlocked = false
    private val targetPackageState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        android.util.Log.e("LOCK_DEBUG", "LOCK ACTIVITY CREATED")
        
        // Professional Security: Prevent screenshots and recent app previews
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        // Root Detection
        if (SecurityUtils.isDeviceRooted()) {
            Toast.makeText(this, "Security Alert: Rooted device detected.", Toast.LENGTH_SHORT).show()
        }
        
        val initialTargetPackage = intent.getStringExtra("target_package") ?: ""
        targetPackageState.value = initialTargetPackage
        val requestBiometric = intent.getBooleanExtra("request_biometric", false)

        // Mark lock as active for Accessibility interception
        val prefs = com.geovault.security.SecureManager.getInstance(this).prefs
        prefs.edit().putBoolean("lock_active_right_now", true).commit()

        // Fullscreen Immersive - DISABLED to bring back system bars
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
            val isRestricted = remember { prefs.getBoolean("screenshot_restriction", false) }
            val isDark = remember { prefs.getBoolean("is_dark_mode", false) }
            val currentTargetPackage by targetPackageState
            
            // Notify service to hide white overlay once UI is ready
            LaunchedEffect(currentTargetPackage) {
                kotlinx.coroutines.delay(300)
                sendBroadcast(Intent("com.geovault.HIDE_OVERLAY"))
            }

            GeoVaultTheme(darkTheme = isDark) {
                val customBgPath = remember { prefs.getString("lock_background_path", null) }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (customBgPath != null) Color.Transparent else (if (isDark) CyberBlack else Color.White),
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
                        targetPackage = currentTargetPackage,
                        autoRequestBiometric = requestBiometric,
                        onAuthenticated = {
                            isUnlocked = true
                            val authPrefs = com.geovault.security.SecureManager.getInstance(this).prefs
                            authPrefs.edit().putString("bypass_package", currentTargetPackage).commit()
                            unlock(currentTargetPackage)
                        },
                    ) {
                        showBiometricPrompt(currentTargetPackage)
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
        val newTarget = intent.getStringExtra("target_package") ?: ""
        if (newTarget.isNotEmpty()) {
            targetPackageState.value = newTarget
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.geovault.security.SecureManager.getInstance(this).prefs.edit()
            .putBoolean("lock_active_right_now", false).commit()
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
                    prefs.edit()
                        .putString("bypass_package", targetPackage)
                        .commit()
                    finish()
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
