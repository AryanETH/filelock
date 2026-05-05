package com.geovault.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import com.geovault.ui.theme.CyberDarkBlue
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onAuthenticated: () -> Unit,
    onBiometricRequested: () -> Unit
) {
    val secureManager = remember { com.geovault.security.SecureManager.getInstance(context) }
    val prefs = remember { secureManager.prefs }

    val captureIntruder = {
        IntruderManager.getInstance(context).captureIntruder { uri ->
            secureManager.saveFileInfo(
                UUID.randomUUID().toString(),
                "Intruder_${System.currentTimeMillis()}.jpg",
                uri.path ?: "",
                FileCategory.INTRUDER,
                0L
            )
        }
    }
    
    val allVaultIds = remember { prefs.getStringSet("vault_ids", emptySet()) ?: emptySet() }
    
    var relevantVaultId = allVaultIds.find { id ->
        val apps = prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet()
        apps.contains(targetPackage)
    }
    
    // Fallback for system targets like Settings/PackageInstaller
    if (relevantVaultId == null && (targetPackage == "com.android.settings" || targetPackage.contains("packageinstaller"))) {
        relevantVaultId = allVaultIds.firstOrNull()
    }
    
    val lockTypeStr = relevantVaultId?.let { prefs.getString("vault_${it}_lock_type", "PIN") } ?: "PIN"
    val lockType = com.geovault.model.LockType.valueOf(lockTypeStr)
    val savedSecret = relevantVaultId?.let { prefs.getString("vault_${it}_secret", "") } ?: ""
    val vaultLat = relevantVaultId?.let { prefs.getFloat("vault_${it}_lat", 0f).toDouble() } ?: 0.0
    val vaultLon = relevantVaultId?.let { prefs.getFloat("vault_${it}_lon", 0f).toDouble() } ?: 0.0

    val isDarkMode = remember { prefs.getBoolean("is_dark_mode", false) }
    val isSatelliteMode = remember { prefs.getBoolean("is_satellite_mode", false) }

    var biometricStatusMessage by remember { mutableStateOf<String?>(null) }

    android.util.Log.d("AuthSelection", "Target: $targetPackage, Vault: $relevantVaultId, Type: $lockType, SecretLength: ${savedSecret.length}")

    if (relevantVaultId == null) {
        // Only exit if there truly are NO vaults setup yet
        onAuthenticated()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            shadowElevation = 12.dp,
            modifier = Modifier.widthIn(max = 340.dp).wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    titleOverride ?: "VAULT ACCESS",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = if (titleOverride != null) 2.sp else 4.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    if (lockType == com.geovault.model.LockType.MAP) "LOCATE TARGET COORDINATES" else "VERIFY IDENTITY",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                
                Spacer(modifier = Modifier.height(40.dp))

                when (lockType) {
                    com.geovault.model.LockType.PIN -> {
                        CompactPinPad(
                            correctPin = savedSecret, 
                            onPinComplete = { onAuthenticated() },
                            onError = captureIntruder
                        )
                    }
                    com.geovault.model.LockType.PATTERN -> {
                        CompactPatternGrid(
                            correctPattern = savedSecret, 
                            onPatternComplete = { onAuthenticated() },
                            onError = captureIntruder
                        )
                    }
                    com.geovault.model.LockType.MAP -> {
                        Box(modifier = Modifier.height(280.dp).fillMaxWidth()) {
                            MapLockScreen(
                                targetLocation = GeoPoint(vaultLat, vaultLon),
                                isSatelliteMode = isSatelliteMode,
                                isDarkMode = isDarkMode,
                                onSuccess = onAuthenticated
                            )
                        }
                    }
                    else -> {
                        CompactPinPad(
                            correctPin = savedSecret,
                            onPinComplete = { onAuthenticated() },
                            onError = captureIntruder
                        )
                    }
                }
                
                if (lockType != com.geovault.model.LockType.MAP) {
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                val biometricManager = BiometricManager.from(context)
                                when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
                                    BiometricManager.BIOMETRIC_SUCCESS -> {
                                        biometricStatusMessage = null
                                        onBiometricRequested()
                                    }
                                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                                        biometricStatusMessage = "Fingerprint not supported on this device"
                                    }
                                    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                                        biometricStatusMessage = "Biometric hardware is currently unavailable"
                                    }
                                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                                        biometricStatusMessage = "No fingerprints enrolled"
                                    }
                                    else -> {
                                        biometricStatusMessage = "Biometric authentication is unavailable"
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.Fingerprint, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        }
                        
                        biometricStatusMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
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
