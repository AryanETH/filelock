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
import com.geovault.ui.theme.CyberBlack
import com.geovault.ui.theme.CyberBlue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.geovault.security.IntruderManager
import com.geovault.model.FileCategory
import java.util.UUID

import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import com.geovault.model.GeoPoint

@Composable
fun AuthSelectionScreen(
    context: Context,
    targetPackage: String,
    onAuthenticated: () -> Unit,
    onBiometricRequested: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val secureManager = remember { com.geovault.security.SecureManager.getInstance(context) }
    val prefs = remember { secureManager.prefs }
    
    val allVaultIds = remember { prefs.getStringSet("vault_ids", emptySet()) ?: emptySet() }
    
    val relevantVaultId = allVaultIds.find { id ->
        val apps = prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet()
        apps.contains(targetPackage)
    }
    
    val lockTypeStr = relevantVaultId?.let { prefs.getString("vault_${it}_lock_type", "PIN") } ?: "PIN"
    val lockType = com.geovault.model.LockType.valueOf(lockTypeStr)
    val savedSecret = relevantVaultId?.let { prefs.getString("vault_${it}_secret", "") } ?: ""
    val vaultLat = relevantVaultId?.let { prefs.getFloat("vault_${it}_lat", 0f).toDouble() } ?: 0.0
    val vaultLon = relevantVaultId?.let { prefs.getFloat("vault_${it}_lon", 0f).toDouble() } ?: 0.0

    android.util.Log.d("AuthSelection", "Target: $targetPackage, Vault: $relevantVaultId, Type: $lockType, SecretLength: ${savedSecret.length}")

    Column(
        modifier = Modifier.fillMaxSize().background(CyberBlack),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = CyberDarkBlue.copy(alpha = 0.98f),
            border = BorderStroke(1.dp, CyberBlue.copy(alpha = 0.4f)),
            modifier = Modifier.widthIn(max = 340.dp).wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "VAULT ACCESS",
                    color = CyberBlue,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                )
                Text(
                    if (lockType == com.geovault.model.LockType.MAP) "LOCATE TARGET COORDINATES" else "VERIFY IDENTITY",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
                
                Spacer(modifier = Modifier.height(40.dp))

                when (lockType) {
                    com.geovault.model.LockType.PIN -> {
                        CompactPinPad(
                            correctPin = savedSecret, 
                            onPinComplete = { onAuthenticated() },
                            onError = {
                                IntruderManager.getInstance(context).captureIntruder(lifecycleOwner) { uri ->
                                    secureManager.saveFileInfo(
                                        UUID.randomUUID().toString(),
                                        "Intruder_${System.currentTimeMillis()}.jpg",
                                        uri.path ?: "",
                                        FileCategory.INTRUDER,
                                        0L
                                    )
                                }
                            }
                        )
                    }
                    com.geovault.model.LockType.PATTERN -> {
                        CompactPatternGrid(
                            correctPattern = savedSecret, 
                            onPatternComplete = { onAuthenticated() },
                            onError = {
                                IntruderManager.getInstance(context).captureIntruder(lifecycleOwner) { uri ->
                                    secureManager.saveFileInfo(
                                        UUID.randomUUID().toString(),
                                        "Intruder_${System.currentTimeMillis()}.jpg",
                                        uri.path ?: "",
                                        FileCategory.INTRUDER,
                                        0L
                                    )
                                }
                            }
                        )
                    }
                    com.geovault.model.LockType.MAP -> {
                        Box(modifier = Modifier.height(280.dp).fillMaxWidth()) {
                            MapLockScreen(
                                targetLocation = GeoPoint(vaultLat, vaultLon),
                                onSuccess = onAuthenticated
                            )
                        }
                    }
                    else -> {
                        CompactPinPad(correctPin = savedSecret, onPinComplete = {
                            onAuthenticated()
                        })
                    }
                }
                
                if (lockType != com.geovault.model.LockType.MAP) {
                    Spacer(modifier = Modifier.height(32.dp))
                    IconButton(
                        onClick = onBiometricRequested,
                        modifier = Modifier
                            .size(64.dp)
                            .background(CyberBlue.copy(alpha = 0.05f), CircleShape)
                            .border(1.dp, CyberBlue.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Default.Fingerprint, null, tint = CyberBlue, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MapLockScreen(targetLocation: GeoPoint, onSuccess: () -> Unit) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    
    Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        map.setStyle("https://tiles.openfreemap.org/styles/dark")
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false
                        
                        map.addOnMapClickListener { point ->
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
