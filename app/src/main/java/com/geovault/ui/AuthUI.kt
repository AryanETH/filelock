package com.geovault.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import com.geovault.ui.theme.AppBlue
import com.geovault.ui.theme.CreamWhite
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.graphics.drawable.toBitmap
import com.geovault.ui.theme.CyberBlue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.ui.draw.clip
import com.geovault.map.MapStyleHelper
import com.geovault.security.IntruderManager
import com.geovault.model.FileCategory
import java.util.UUID

import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import com.geovault.model.GeoPoint

import androidx.biometric.BiometricManager
import androidx.compose.ui.text.style.TextAlign

@Composable
fun AuthSelectionScreen(
    context: Context,
    targetPackage: String,
    titleOverride: String? = null,
    autoRequestBiometric: Boolean = false,
    onAuthenticated: () -> Unit,
    onBiometricRequested: () -> Unit
) {
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val secureManager = remember { com.geovault.security.SecureManager.getInstance(context) }
    val prefs = remember { secureManager.prefs }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    var failedAttempts by remember { mutableIntStateOf(prefs.getInt("temp_failed_attempts", 0)) }

    // Start Intruder Session when this screen is active
    DisposableEffect(lifecycleOwner) {
        IntruderManager.getInstance(context).startSession(lifecycleOwner)
        onDispose {
            IntruderManager.getInstance(context).stopSession()
            // Reset temp attempts on success (this is only called on dispose, 
            // but we might want to keep it until success)
        }
    }

    val captureIntruder = {
        failedAttempts++
        prefs.edit().putInt("temp_failed_attempts", failedAttempts).apply()
        
        if (failedAttempts >= 3) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            
            // Multiple captures for "clicking pictures"
            repeat(2) { i ->
                val delayMs = i * 700L
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    IntruderManager.getInstance(context).captureIntruder { uri, thumbPath ->
                        val id = java.util.UUID.randomUUID().toString()
                        secureManager.saveFileInfo(
                            id,
                            "Intruder_${System.currentTimeMillis()}.jpg",
                            uri.path ?: "",
                            com.geovault.model.FileCategory.INTRUDER,
                            0L,
                            thumbPath,
                            null
                        )
                    }
                }, delayMs)
            }
        }
    }
    
    val allVaultIds = remember { prefs.getStringSet("vault_ids", emptySet()) ?: emptySet() }
    
    var relevantVaultId = allVaultIds.find { id ->
        val apps = prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet()
        apps.contains(targetPackage)
    }
    
    if (relevantVaultId == null && (targetPackage == "com.android.settings" || targetPackage.contains("packageinstaller"))) {
        relevantVaultId = allVaultIds.firstOrNull()
    }
    
    val lockTypeStr = relevantVaultId?.let { prefs.getString("vault_${it}_lock_type", "PIN") } ?: "PIN"
    val lockType = com.geovault.model.LockType.valueOf(lockTypeStr)
    val savedSecret = relevantVaultId?.let { prefs.getString("vault_${it}_secret", "") } ?: ""
    val vaultLat = relevantVaultId?.let { prefs.getFloat("vault_${it}_lat", 0f).toDouble() } ?: 0.0
    val vaultLon = relevantVaultId?.let { prefs.getFloat("vault_${it}_lon", 0f).toDouble() } ?: 0.0
    val radius = relevantVaultId?.let { prefs.getFloat("vault_${it}_radius", 0f) } ?: 0f

    val isSatelliteMode = remember { prefs.getBoolean("is_satellite_mode", false) }
    val isFingerprintEnabled = remember { prefs.getBoolean("fingerprint_enabled", false) }
    val isDarkMode = remember { prefs.getBoolean("dark_mode", false) }
    var biometricStatusMessage by remember { mutableStateOf<String?>(null) }
    
    var isWithinRadius by remember { mutableStateOf(radius <= 0f) }
    var hasAutoRequestedBiometric by remember { mutableStateOf(false) }
    
    val backgroundColor = if (isDarkMode) Color(0xFF0A0E14) else CreamWhite
    val textPrimary = if (isDarkMode) Color.White else Color.Black.copy(alpha = 0.8f)
    val cardColor = if (isDarkMode) Color(0xFF101720) else CreamWhite.copy(alpha = 0.95f)

    if (radius > 0) {
        LaunchedEffect(Unit) {
            val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        isWithinRadius = com.geovault.location.LocationHelper.isWithinRadius(
                            location.latitude, location.longitude,
                            vaultLat, vaultLon,
                            radius
                        )
                    } else {
                        isWithinRadius = false
                    }
                }
            } catch (e: Exception) {
                isWithinRadius = false
            }
        }
    }

    if (autoRequestBiometric && !hasAutoRequestedBiometric && isWithinRadius) {
        LaunchedEffect(isWithinRadius) {
            onBiometricRequested()
            hasAutoRequestedBiometric = true
        }
    }

    // Fetch Target App Icon and Name
    val pm = context.packageManager
    val appIcon = remember(targetPackage) {
        try { pm.getApplicationIcon(targetPackage) } catch (e: Exception) { null }
    }
    val appLabel = remember(targetPackage) {
        try { pm.getApplicationLabel(pm.getApplicationInfo(targetPackage, 0)).toString() } catch (e: Exception) { "" }
    }

    if (relevantVaultId == null) {
        // If it's a known protected system app, wait a bit or show generic
        if (targetPackage.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CyberBlue)
            }
            return
        }
        
        // Final fallback: if no vault, just unlock
        LaunchedEffect(Unit) {
            onAuthenticated()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor) 
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.1f))

        // 1. App Logo (Top)
        appIcon?.let { icon ->
            Box(
                modifier = Modifier
                    .size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = icon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 2. Instruction Text + NATIVE TAG
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (!isWithinRadius && radius > 0) {
                    "Location Locked"
                } else {
                    titleOverride ?: when (lockType) {
                        com.geovault.model.LockType.PIN -> "Enter PIN"
                        com.geovault.model.LockType.PATTERN -> "Draw Pattern"
                        com.geovault.model.LockType.MAP -> "Tap Target"
                        else -> "Verify"
                    }
                },
                color = if (!isWithinRadius && radius > 0) Color.Red else textPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            
            if (isWithinRadius && radius > 0) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = AppBlue,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Text(
                        "SAFE ZONE",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.15f)) // More breathing space

        // 3. PIN / Pattern UI (Center - Expanded)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (lockType) {
                com.geovault.model.LockType.PIN -> {
                    CompactPinPad(
                        correctPin = savedSecret, 
                        onPinComplete = { 
                            if (isWithinRadius) {
                                failedAttempts = 0
                                prefs.edit().putInt("temp_failed_attempts", 0).apply()
                                onAuthenticated() 
                            } else {
                                captureIntruder()
                            }
                        },
                        onError = captureIntruder,
                        isLightTheme = !isDarkMode,
                        isFullPage = true
                    )
                }
                com.geovault.model.LockType.PATTERN -> {
                    CompactPatternGrid(
                        correctPattern = savedSecret, 
                        onPatternComplete = { 
                            if (isWithinRadius) {
                                failedAttempts = 0
                                prefs.edit().putInt("temp_failed_attempts", 0).apply()
                                onAuthenticated() 
                            } else {
                                captureIntruder()
                            }
                        },
                        onError = captureIntruder,
                        isLightTheme = !isDarkMode,
                        isFullPage = true
                    )
                }
                com.geovault.model.LockType.MAP -> {
                    Box(modifier = Modifier.height(380.dp).fillMaxWidth().padding(8.dp)) {
                        MapLockScreen(
                            targetLocation = GeoPoint(vaultLat, vaultLon),
                            isSatelliteMode = isSatelliteMode,
                            isDarkMode = isDarkMode,
                            onSuccess = { if (isWithinRadius) onAuthenticated() }
                        )
                    }
                }
                else -> {
                    CompactPinPad(
                        correctPin = savedSecret,
                        onPinComplete = { if (isWithinRadius) onAuthenticated() else captureIntruder() },
                        onError = captureIntruder,
                        isLightTheme = !isDarkMode
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.2f))

        // 4. Biometric Icon (Bottom)
        if (lockType != com.geovault.model.LockType.MAP && isFingerprintEnabled) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clickable {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        if (!isWithinRadius && radius > 0) {
                            biometricStatusMessage = "Outside location radius"
                            return@clickable
                        }
                        val biometricManager = BiometricManager.from(context)
                        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
                            biometricStatusMessage = null
                            val biometricPrompt = androidx.biometric.BiometricPrompt(
                                context as androidx.fragment.app.FragmentActivity,
                                androidx.core.content.ContextCompat.getMainExecutor(context),
                                object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                        super.onAuthenticationError(errorCode, errString)
                                        if (errorCode == androidx.biometric.BiometricPrompt.ERROR_LOCKOUT || errorCode == androidx.biometric.BiometricPrompt.ERROR_LOCKOUT_PERMANENT) {
                                            captureIntruder()
                                        }
                                    }
                                    override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                        super.onAuthenticationSucceeded(result)
                                        failedAttempts = 0
                                        prefs.edit().putInt("temp_failed_attempts", 0).apply()
                                        onAuthenticated()
                                    }
                                    override fun onAuthenticationFailed() {
                                        super.onAuthenticationFailed()
                                        captureIntruder()
                                    }
                                }
                            )
                            
                            val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                .setTitle("Biometric Unlock")
                                .setSubtitle("Verify identity to access $appLabel")
                                .setNegativeButtonText("Use PIN/Pattern")
                                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                                .build()
                                
                            biometricPrompt.authenticate(promptInfo)
                        } else {
                            biometricStatusMessage = "Biometric unavailable"
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Fingerprint, 
                    contentDescription = "Fingerprint", 
                    tint = AppBlue, 
                    modifier = Modifier.size(56.dp)
                )
            }
            
            biometricStatusMessage?.let { msg ->
                Text(
                    text = msg,
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(56.dp))
    }
}

@Composable
fun MapLockScreen(targetLocation: GeoPoint, isSatelliteMode: Boolean, isDarkMode: Boolean, onSuccess: () -> Unit) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    
    val currentStyle = remember(isSatelliteMode, isDarkMode) {
        if (isSatelliteMode) {
            MapStyleHelper.getSatelliteStyle(isHybrid = true)
        } else {
            if (isDarkMode) MapStyleHelper.DARK else MapStyleHelper.BRIGHT
        }
    }

    Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        map.setStyle(currentStyle)
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false
                        
                        map.addOnMapClickListener { point ->
                            if (map.cameraPosition.zoom < 16.0) {
                                android.widget.Toast.makeText(context, "Zoom in closer to target", android.widget.Toast.LENGTH_SHORT).show()
                                return@addOnMapClickListener true
                            }
                            val targetLatLng = org.maplibre.android.geometry.LatLng(targetLocation.latitude, targetLocation.longitude)
                            if (point.distanceTo(targetLatLng) < 500) {
                                onSuccess()
                            }
                            true
                        }
                    }
                }
            }
        )
        
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            color = (if (isDarkMode) Color(0xFF101720) else CreamWhite).copy(alpha = 0.9f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "TAP TARGET COORDINATES",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = if (isDarkMode) CyberBlue else AppBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}
