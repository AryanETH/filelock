package com.geovault.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import com.geovault.ui.theme.CyberDarkBlue
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

    // Start Intruder Session when this screen is active
    DisposableEffect(lifecycleOwner) {
        IntruderManager.getInstance(context).startSession(lifecycleOwner)
        onDispose {
            IntruderManager.getInstance(context).stopSession()
        }
    }

    val captureIntruder = {
        IntruderManager.getInstance(context).captureIntruder { uri, thumbPath ->
            android.util.Log.d("AuthSelection", "Intruder captured at $uri")
            
            secureManager.saveFileInfo(
                java.util.UUID.randomUUID().toString(),
                "Intruder_${System.currentTimeMillis()}.jpg",
                uri.path ?: "",
                com.geovault.model.FileCategory.INTRUDER,
                0L,
                thumbPath
            )
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
    var biometricStatusMessage by remember { mutableStateOf<String?>(null) }
    
    var isWithinRadius by remember { mutableStateOf(radius <= 0f) }
    var hasAutoRequestedBiometric by remember { mutableStateOf(false) }
    
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
        onAuthenticated()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.1f))

        // 1. App Logo (Top)
        appIcon?.let { icon ->
            Image(
                bitmap = icon.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp) // Slightly larger
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.03f))
                    .padding(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 2. Instruction Text
        Text(
            text = if (!isWithinRadius && radius > 0) {
                "Location Locked: Stay within ${radius.toInt()}m"
            } else {
                titleOverride ?: when (lockType) {
                    com.geovault.model.LockType.PIN -> "Enter your PIN"
                    com.geovault.model.LockType.PATTERN -> "Draw your pattern"
                    com.geovault.model.LockType.MAP -> "Tap target coordinates"
                    else -> "Verify identity"
                }
            },
            color = if (!isWithinRadius && radius > 0) Color.Red else Color.Black,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

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
                        onPinComplete = { if (isWithinRadius) onAuthenticated() else captureIntruder() },
                        onError = captureIntruder,
                        isLightTheme = true,
                        isFullPage = true
                    )
                }
                com.geovault.model.LockType.PATTERN -> {
                    CompactPatternGrid(
                        correctPattern = savedSecret, 
                        onPatternComplete = { if (isWithinRadius) onAuthenticated() else captureIntruder() },
                        onError = captureIntruder,
                        isLightTheme = true,
                        isFullPage = true
                    )
                }
                com.geovault.model.LockType.MAP -> {
                    Box(modifier = Modifier.height(360.dp).fillMaxWidth()) {
                        MapLockScreen(
                            targetLocation = GeoPoint(vaultLat, vaultLon),
                            isSatelliteMode = isSatelliteMode,
                            isDarkMode = false,
                            onSuccess = { if (isWithinRadius) onAuthenticated() }
                        )
                    }
                }
                else -> {
                    CompactPinPad(
                        correctPin = savedSecret,
                        onPinComplete = { if (isWithinRadius) onAuthenticated() else captureIntruder() },
                        onError = captureIntruder,
                        isLightTheme = true
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.2f))

        // 4. Biometric Icon (Bottom)
        if (lockType != com.geovault.model.LockType.MAP && isFingerprintEnabled) {
            IconButton(
                onClick = {
                    if (!isWithinRadius && radius > 0) {
                        biometricStatusMessage = "Outside location radius"
                        return@IconButton
                    }
                    val biometricManager = BiometricManager.from(context)
                    if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
                        biometricStatusMessage = null
                        onBiometricRequested()
                    } else {
                        biometricStatusMessage = "Biometric unavailable"
                    }
                },
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.04f))
            ) {
                Icon(
                    Icons.Default.Fingerprint, 
                    contentDescription = "Fingerprint", 
                    tint = Color.Black, 
                    modifier = Modifier.size(44.dp)
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

    Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))) {
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
                                android.widget.Toast.makeText(context, "Zoom in closer to target (100m scale)", android.widget.Toast.LENGTH_SHORT).show()
                                return@addOnMapClickListener true
                            }
                            val targetLatLng = LatLng(targetLocation.latitude, targetLocation.longitude)
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
            color = CyberDarkBlue.copy(alpha = 0.7f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                "TAP TARGET COORDINATES",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = CyberBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
