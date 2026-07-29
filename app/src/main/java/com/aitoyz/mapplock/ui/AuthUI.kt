package com.aitoyz.mapplock.ui

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
import com.aitoyz.mapplock.ui.theme.AppBlue
import com.aitoyz.mapplock.ui.theme.CreamWhite
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.graphics.drawable.toBitmap
import com.aitoyz.mapplock.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.ui.draw.clip
import com.aitoyz.mapplock.map.MapStyleHelper
import com.aitoyz.mapplock.security.IntruderManager
import com.aitoyz.mapplock.security.LockerLogger
import com.aitoyz.mapplock.model.FileCategory
import java.util.UUID

import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import com.aitoyz.mapplock.model.GeoPoint

import androidx.biometric.BiometricManager
import androidx.compose.ui.text.style.TextAlign

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import java.io.File
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Brush

@Composable
fun AuthUI(
    context: Context,
    targetPackage: String,
    titleOverride: String? = null,
    autoRequestBiometric: Boolean = false,
    isOverlay: Boolean = false,
    onAuthenticated: () -> Unit,
    onBiometricRequested: () -> Unit
) {
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val secureManager = remember { com.aitoyz.mapplock.security.SecureManager.getInstance(context) }
    val prefs = remember { secureManager.prefs }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    var failedAttempts by remember { mutableIntStateOf(0) }

    // Reset failed attempts for the current session
    LaunchedEffect(Unit) {
        prefs.edit().putInt("temp_failed_attempts", 0).apply()
    }

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
            // Permission granted
        }
    }

    // Start Intruder Session when this screen is active
    DisposableEffect(lifecycleOwner) {
        val isIntruderEnabled = prefs.getBoolean("intruder_capture_enabled", false)
        if (isIntruderEnabled) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                IntruderManager.getInstance(context).startSession(lifecycleOwner)
            } else if (!isOverlay) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        onDispose {
            IntruderManager.getInstance(context).stopSession()
        }
    }

    // Reactive vault lookup
    val allVaultIds = prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()
    
    val relevantVaultId = allVaultIds.find { id ->
        val apps = prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet()
        apps.contains(targetPackage)
    } ?: if (targetPackage.contains("packageinstaller")) allVaultIds.firstOrNull() else null

    val captureIntruder = {
        failedAttempts++
        prefs.edit().putInt("temp_failed_attempts", failedAttempts).apply()
        
        val isIntruderEnabled = prefs.getBoolean("intruder_capture_enabled", false)

        if (failedAttempts >= 1 && isIntruderEnabled) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            
            IntruderManager.getInstance(context).captureIntruder { uri, thumbPath ->
                val id = java.util.UUID.randomUUID().toString()
                secureManager.saveFileInfo(
                    id,
                    "Intruder_${System.currentTimeMillis()}.jpg",
                    uri.path ?: "",
                    com.aitoyz.mapplock.model.FileCategory.INTRUDER,
                    0L,
                    thumbPath,
                    null,
                    relevantVaultId
                )
            }
        }
    }
    
    val lockTypeStr = relevantVaultId?.let { prefs.getString("vault_${it}_lock_type", "PIN") } ?: "PIN"
    val lockType = com.aitoyz.mapplock.model.LockType.valueOf(lockTypeStr)
    val savedSecret = relevantVaultId?.let { prefs.getString("vault_${it}_secret", "") } ?: ""
    val vaultLat = relevantVaultId?.let { prefs.getFloat("vault_${it}_lat", 0f).toDouble() } ?: 0.0
    val vaultLon = relevantVaultId?.let { prefs.getFloat("vault_${it}_lon", 0f).toDouble() } ?: 0.0
    val radius = relevantVaultId?.let { prefs.getFloat("vault_${it}_radius", 0f) } ?: 0f

    val isSatelliteMode = remember { prefs.getBoolean("is_satellite_mode", false) }
    val isFingerprintEnabled = remember { prefs.getBoolean("fingerprint_enabled", true) }
    val isDarkMode = remember { prefs.getBoolean("is_dark_mode", false) }
    var biometricStatusMessage by remember { mutableStateOf<String?>(null) }
    
    var isWithinRadius by remember { mutableStateOf(radius <= 0f) }
    var hasAutoRequestedBiometric by remember { mutableStateOf(false) }
    
    val backgroundColor = if (isDarkMode) Color(0xFF0A0E14) else Color.White
    val customBgPath = remember { prefs.getString("lock_background_path", null) }
    val hasCustomBg = customBgPath != null && File(customBgPath).exists()
    val textPrimary = if (hasCustomBg) Color.White else (if (isDarkMode) Color.White else LightTextPrimary)
    val accentColor = CyberBlue

    if (radius > 0) {
        LaunchedEffect(radius, targetPackage) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                
                val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            isWithinRadius = com.aitoyz.mapplock.location.LocationHelper.isWithinRadius(
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
            } else if (!isOverlay) {
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
        LockerLogger.d(LockerLogger.Event.LOCK_SKIPPED, "No vault found for $targetPackage. Unlocking.")
        LaunchedEffect(targetPackage) {
            onAuthenticated()
        }
        Box(modifier = Modifier.fillMaxSize().background(backgroundColor))
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        // 0. Base Solid Layer (Never transparent)
        Box(modifier = Modifier.fillMaxSize().background(backgroundColor))

        if (hasCustomBg) {
            AsyncImage(
                model = File(customBgPath!!),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(if (lockType == com.aitoyz.mapplock.model.LockType.MAP) 10.dp else 40.dp)
                    .background(if (isDarkMode) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.2f))
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                            startY = 0.6f * constraints.maxHeight.toFloat()
                        )
                    )
            )
        }

        val availableHeight = maxHeight
        val isSmallScreen = availableHeight < 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                // Immersive: Draw behind system bars
                .padding(bottom = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(if (isSmallScreen) 0.05f else 0.1f))

            // 1. App Logo
            appIcon?.let { icon ->
                Box(
                    modifier = Modifier
                        .size(if (isSmallScreen) 80.dp else 110.dp)
                        .then(if (hasCustomBg) Modifier.shadow(12.dp, CircleShape) else Modifier)
                        .background(
                            if (hasCustomBg) Color.White.copy(alpha = 0.2f) else Color.Transparent, 
                            CircleShape
                        )
                        .border(
                            if (hasCustomBg) BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)) else BorderStroke(0.dp, Color.Transparent),
                            CircleShape
                        ),
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
            Text(
                text = if (!isWithinRadius && radius > 0) "Location Locked" else (titleOverride ?: when (lockType) {
                    com.aitoyz.mapplock.model.LockType.PIN -> "Enter PIN"
                    com.aitoyz.mapplock.model.LockType.PATTERN -> "Draw Pattern"
                    com.aitoyz.mapplock.model.LockType.MAP -> "Tap Target"
                    else -> "Verify"
                }),
                color = if (!isWithinRadius && radius > 0) Color.Red else textPrimary,
                style = (if (isSmallScreen) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall).copy(
                    shadow = if (hasCustomBg) Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 10f, offset = Offset(2f, 2f)) else null
                ),
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(0.1f))

            // 3. PIN / Pattern UI
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                when (lockType) {
                    com.aitoyz.mapplock.model.LockType.PIN -> {
                        CompactPinPad(
                            correctPin = savedSecret, 
                            onPinComplete = { 
                                if (isWithinRadius) {
                                    failedAttempts = 0
                                    onAuthenticated() 
                                } else {
                                    captureIntruder()
                                }
                            },
                            onError = captureIntruder,
                            isLightTheme = !isDarkMode && !hasCustomBg,
                            isFullPage = true,
                            isGlassMode = hasCustomBg
                        )
                    }
                    com.aitoyz.mapplock.model.LockType.PATTERN -> {
                        CompactPatternGrid(
                            correctPattern = savedSecret, 
                            onPatternComplete = { 
                                if (isWithinRadius) {
                                    failedAttempts = 0
                                    onAuthenticated() 
                                } else {
                                    captureIntruder()
                                }
                            },
                            onError = captureIntruder,
                            isLightTheme = !isDarkMode && !hasCustomBg,
                            isFullPage = true,
                            isGlassMode = hasCustomBg
                        )
                    }
                    com.aitoyz.mapplock.model.LockType.MAP -> {
                        Box(modifier = Modifier.height(if (isSmallScreen) 300.dp else 400.dp).fillMaxWidth().padding(8.dp)) {
                            MapLockScreen(
                                targetLocation = GeoPoint(vaultLat, vaultLon),
                                isSatelliteMode = isSatelliteMode,
                                isDarkMode = isDarkMode,
                                onSuccess = { 
                                    if (isWithinRadius) {
                                        failedAttempts = 0
                                        onAuthenticated() 
                                    } else {
                                        captureIntruder()
                                    }
                                }
                            )
                        }
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.weight(0.15f))

            // 4. Biometric Icon
            if (lockType != com.aitoyz.mapplock.model.LockType.MAP && (isFingerprintEnabled || (isWithinRadius && radius > 0))) {
                val glassSurfaceBrush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.1f)),
                    start = Offset.Zero, end = Offset.Infinite
                )
                val glassBorderBrush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f)),
                    start = Offset.Zero, end = Offset.Infinite
                )

                Box(
                    modifier = Modifier
                        .size(if (isSmallScreen) 64.dp else 80.dp)
                        .then(
                            if (hasCustomBg) Modifier
                                .background(glassSurfaceBrush, CircleShape)
                                .border(BorderStroke(1.dp, glassBorderBrush), CircleShape)
                            else Modifier
                        )
                        .clickable {
                            HapticHelper.vibrate(context, 1)
                            
                            if (radius > 0 && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                if (!isOverlay) {
                                    locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                }
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
                                biometricStatusMessage = "Biometric unavailable\n(Use PIN)"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Fingerprint, 
                        contentDescription = "Fingerprint", 
                        tint = if (hasCustomBg) Color.White else accentColor, 
                        modifier = Modifier.size(if (isSmallScreen) 44.dp else 56.dp)
                    )
                }
                
                biometricStatusMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = if (hasCustomBg) Color.White else Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
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
            MapStyleHelper.getSatelliteStyle(context, isHybrid = true)
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
                        map.setStyle(currentStyle) { style ->
                            MapStyleHelper.applyIndiaBoundaries(context, style)
                        }
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
                color = CyberBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}
