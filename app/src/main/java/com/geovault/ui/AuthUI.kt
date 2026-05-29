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

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

@Composable
fun AuthUI(
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

    // Permission Launchers
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            IntruderManager.getInstance(context).startSession(lifecycleOwner)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            // Permission granted, re-check location
        }
    }

    // Start Intruder Session when this screen is active
    DisposableEffect(lifecycleOwner) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            IntruderManager.getInstance(context).startSession(lifecycleOwner)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        onDispose {
            IntruderManager.getInstance(context).stopSession()
        }
    }

    val captureIntruder = {
        failedAttempts++
        prefs.edit().putInt("temp_failed_attempts", failedAttempts).apply()
        
        if (failedAttempts >= 3) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            
            // Multiple captures for "clicking pictures"
            repeat(3) { i ->
                val delayMs = i * 500L
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
    
    if (relevantVaultId == null && (targetPackage.contains("packageinstaller"))) {
        relevantVaultId = allVaultIds.firstOrNull()
    }
    
    val lockTypeStr = relevantVaultId?.let { prefs.getString("vault_${it}_lock_type", "PIN") } ?: "PIN"
    val lockType = com.geovault.model.LockType.valueOf(lockTypeStr)
    val savedSecret = relevantVaultId?.let { prefs.getString("vault_${it}_secret", "") } ?: ""
    val vaultLat = relevantVaultId?.let { prefs.getFloat("vault_${it}_lat", 0f).toDouble() } ?: 0.0
    val vaultLon = relevantVaultId?.let { prefs.getFloat("vault_${it}_lon", 0f).toDouble() } ?: 0.0
    val radius = relevantVaultId?.let { prefs.getFloat("vault_${it}_radius", 0f) } ?: 0f

    val isSatelliteMode = remember { prefs.getBoolean("is_satellite_mode", false) }
    val isFingerprintEnabled = remember { prefs.getBoolean("fingerprint_enabled", true) }
    val isDarkMode = remember { prefs.getBoolean("dark_mode", false) }
    var biometricStatusMessage by remember { mutableStateOf<String?>(null) }
    
    var isWithinRadius by remember { mutableStateOf(radius <= 0f) }
    var hasAutoRequestedBiometric by remember { mutableStateOf(false) }
    
    val backgroundColor = if (isDarkMode) Color(0xFF0A0E14) else CreamWhite
    val textPrimary = if (isDarkMode) Color.White else Color.Black.copy(alpha = 0.8f)

    if (radius > 0) {
        LaunchedEffect(radius) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                
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
            } else {
                locationPermissionLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }

    LaunchedEffect(autoRequestBiometric, isWithinRadius) {
        if (autoRequestBiometric && !hasAutoRequestedBiometric && isWithinRadius) {
            hasAutoRequestedBiometric = true
            onBiometricRequested()
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
            Box(modifier = Modifier.fillMaxSize().background(backgroundColor), contentAlignment = Alignment.Center) {
                Text("Initializing Security...", color = textPrimary)
            }
            return
        }
        
        // Final fallback: if no vault, just unlock
        LaunchedEffect(Unit) {
            onAuthenticated()
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        val availableHeight = maxHeight
        val isSmallScreen = availableHeight < 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(if (isSmallScreen) 0.05f else 0.1f))

            // 1. App Logo
            appIcon?.let { icon ->
                Box(
                    modifier = Modifier.size(if (isSmallScreen) 80.dp else 110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = icon.toBitmap().asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(if (isSmallScreen) 60.dp else 80.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isSmallScreen) 16.dp else 32.dp))

            // 2. Instruction Text
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
                    style = if (isSmallScreen) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // 3. PIN / Pattern UI
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
                        Box(modifier = Modifier.height(if (isSmallScreen) 300.dp else 400.dp).fillMaxWidth().padding(8.dp)) {
                            MapLockScreen(
                                targetLocation = GeoPoint(vaultLat, vaultLon),
                                isSatelliteMode = isSatelliteMode,
                                isDarkMode = isDarkMode,
                                onSuccess = { 
                                    if (isWithinRadius) {
                                        failedAttempts = 0
                                        prefs.edit().putInt("temp_failed_attempts", 0).apply()
                                        onAuthenticated() 
                                    } else {
                                        captureIntruder()
                                    }
                                }
                            )
                        }
                    }
                    else -> {
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
                            isLightTheme = !isDarkMode
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.15f))

            // 4. Biometric Icon
            if (lockType != com.geovault.model.LockType.MAP && (isFingerprintEnabled || (isWithinRadius && radius > 0))) {
                Box(
                    modifier = Modifier
                        .size(if (isSmallScreen) 64.dp else 80.dp)
                        .clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            
                            if (radius > 0 && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                biometricStatusMessage = "Location permission required"
                                return@clickable
                            }

                            if (!isWithinRadius && radius > 0) {
                                biometricStatusMessage = "Outside location radius"
                                return@clickable
                            }
                            val biometricManager = BiometricManager.from(context)
                            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            
                            if (biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
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
                                    .setTitle("Identity Verification")
                                    .setSubtitle("Confirm identity to access $appLabel")
                                    .setAllowedAuthenticators(authenticators)
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
                        modifier = Modifier.size(if (isSmallScreen) 44.dp else 56.dp)
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

            Spacer(modifier = Modifier.height(if (isSmallScreen) 24.dp else 56.dp))
        }
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
