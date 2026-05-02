package com.geovault

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.geovault.ui.AuthSelectionScreen
import android.content.Context
import android.content.Intent

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

import android.view.WindowManager
class LockActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val targetPackage = intent.getStringExtra("target_package") ?: ""
        val requestBiometric = intent.getBooleanExtra("request_biometric", false)
        
        if (requestBiometric) {
            showBiometricPrompt(targetPackage)
        }

        setContent {
            GeoVaultTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = CyberBlack) {
                    AuthSelectionScreen(
                        context = this,
                        targetPackage = targetPackage,
                        onAuthenticated = {
                            val prefs = com.geovault.security.SecureManager.getInstance(this).prefs
                            prefs.edit().putString("bypass_package", targetPackage).commit()
                            unlock(targetPackage)
                        },
                        onBiometricRequested = {
                            showBiometricPrompt(targetPackage)
                        }
                    )
                }
            }
        }
    }

    private fun showBiometricPrompt(targetPackage: String) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val prefs = com.geovault.security.SecureManager.getInstance(this@LockActivity).prefs
                    prefs.edit().putString("bypass_package", targetPackage).commit()
                    unlock(targetPackage)
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Identity Verification")
            .setSubtitle("Authorized personnel only")
            .setNegativeButtonText("Manual Auth")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun unlock(targetPackage: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
        launchIntent?.let { 
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it) 
        }
        finish()
    }

    private fun fail() {
        val homeIntent = Intent(Intent.ACTION_MAIN)
        homeIntent.addCategory(Intent.CATEGORY_HOME)
        homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(homeIntent)
        finish()
    }
}
