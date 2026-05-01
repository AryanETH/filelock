package com.geovault.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import com.geovault.ui.theme.CyberBlack
import com.geovault.ui.theme.CyberBlue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint

import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.camera.CameraUpdateFactory
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
    val prefs = remember { com.geovault.security.SecureManager.getInstance(context).prefs }
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

    when (lockType) {
        com.geovault.model.LockType.PIN -> {
            PinLockScreen(
                savedPin = savedSecret,
                onSuccess = onAuthenticated,
                onBiometricClick = onBiometricRequested
            )
        }
        com.geovault.model.LockType.PATTERN -> {
            PatternLockScreen(
                savedPattern = savedSecret,
                onSuccess = onAuthenticated,
                onBiometricClick = onBiometricRequested
            )
        }
        com.geovault.model.LockType.MAP -> {
            MapLockScreen(
                targetLocation = GeoPoint(vaultLat, vaultLon),
                onSuccess = onAuthenticated
            )
        }
        else -> {
            PinLockScreen(savedPin = savedSecret, onSuccess = onAuthenticated, onBiometricClick = onBiometricRequested)
        }
    }
}

@Composable
fun PinLockScreen(savedPin: String, onSuccess: () -> Unit, onBiometricClick: () -> Unit) {
    var input by remember { mutableStateOf("") }

    // Auto-validate when 5th digit is entered
    LaunchedEffect(input) {
        if (input.length == 5) {
            // Short delay so user can actually see the 5th box fill up
            delay(300)
            android.util.Log.d("PinLockScreen", "Input: $input, Saved: $savedPin")
            if (input == savedPin) {
                onSuccess()
            } else {
                input = ""
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "ENCRYPTED ACCESS",
            color = CyberBlue,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp
        )
        Text("ENTER SECURITY PIN", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        
        Spacer(modifier = Modifier.height(48.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(5) { i ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            if (i < input.length) CyberBlue else Color.Transparent,
                            CircleShape
                        )
                        .border(1.dp, CyberBlue, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(64.dp))

        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "BIO", "0", "DEL")
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { key ->
                        KeypadButton(key) {
                            when (key) {
                                "BIO" -> onBiometricClick()
                                "DEL" -> if (input.isNotEmpty()) input = input.dropLast(1)
                                else -> {
                                    if (input.length < 5) {
                                        input += key
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(72.dp).clickable { onClick() },
        shape = CircleShape,
        color = CyberDarkBlue.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, CyberBlue.copy(alpha = 0.3f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (text == "BIO") {
                Icon(Icons.Default.Fingerprint, null, tint = CyberBlue, modifier = Modifier.size(32.dp))
            } else if (text == "DEL") {
                Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = CyberBlue, modifier = Modifier.size(24.dp))
            } else {
                Text(text, color = CyberBlue, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PatternLockScreen(savedPattern: String, onSuccess: () -> Unit, onBiometricClick: () -> Unit) {
    var points by remember { mutableStateOf(emptyList<Int>()) }
    var currentTouchPoint by remember { mutableStateOf<Offset?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "NEURAL INTERFACE",
            color = CyberBlue,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp
        )
        Text("TRACE SECURITY PATTERN", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        
        Spacer(modifier = Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .size(320.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            points = emptyList()
                            currentTouchPoint = offset
                        },
                        onDrag = { change, _ ->
                            currentTouchPoint = change.position
                            val dotIndex = getDotIndexAt(change.position, size.width.toFloat())
                            if (dotIndex != -1 && dotIndex !in points) {
                                points = points + dotIndex
                            }
                        },
                        onDragEnd = {
                            if (points.isNotEmpty()) {
                                if (points.joinToString("") == savedPattern) onSuccess()
                                else points = emptyList()
                            }
                            currentTouchPoint = null
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dotRadius = 12.dp.toPx()
                val spacing = size.width / 3
                val startOffset = spacing / 2

                for (i in 0..2) {
                    for (j in 0..2) {
                        val center = Offset(startOffset + j * spacing, startOffset + i * spacing)
                        drawCircle(
                            color = if ((i * 3 + j) in points) CyberBlue else CyberBlue.copy(alpha = 0.1f),
                            radius = dotRadius,
                            center = center
                        )
                        drawCircle(
                            color = CyberBlue.copy(alpha = 0.3f),
                            radius = dotRadius + 4.dp.toPx(),
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
                        )
                    }
                }

                if (points.isNotEmpty()) {
                    for (i in 0 until points.size - 1) {
                        val p1 = getCenterForIndex(points[i], spacing, startOffset)
                        val p2 = getCenterForIndex(points[i + 1], spacing, startOffset)
                        drawLine(
                            color = CyberBlue,
                            start = p1,
                            end = p2,
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    
                    currentTouchPoint?.let { touch ->
                        val lastPoint = getCenterForIndex(points.last(), spacing, startOffset)
                        drawLine(
                            color = CyberBlue,
                            start = lastPoint,
                            end = touch,
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        IconButton(
            onClick = onBiometricClick,
            modifier = Modifier
                .size(64.dp)
                .background(CyberBlue.copy(alpha = 0.1f), CircleShape)
                .border(1.dp, CyberBlue.copy(alpha = 0.4f), CircleShape)
        ) {
            Icon(
                Icons.Default.Fingerprint,
                contentDescription = "Biometric Unlock",
                tint = CyberBlue,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun MapLockScreen(targetLocation: GeoPoint, onSuccess: () -> Unit) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
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
            
            // Subtle instruction overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp),
                color = CyberDarkBlue.copy(alpha = 0.8f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CyberBlue.copy(alpha = 0.5f))
            ) {
                Text(
                    "IDENTIFY SECURE COORDINATES",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = CyberBlue,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

fun getDotIndexAt(offset: Offset, size: Float): Int {
    val spacing = size / 3
    val startOffset = spacing / 2
    val threshold = spacing / 3

    for (i in 0..2) {
        for (j in 0..2) {
            val center = Offset(startOffset + j * spacing, startOffset + i * spacing)
            val distance = (offset - center).getDistance()
            if (distance < threshold) {
                return i * 3 + j
            }
        }
    }
    return -1
}

fun getCenterForIndex(index: Int, spacing: Float, startOffset: Float): Offset {
    val row = index / 3
    val col = index % 3
    return Offset(startOffset + col * spacing, startOffset + row * spacing)
}
