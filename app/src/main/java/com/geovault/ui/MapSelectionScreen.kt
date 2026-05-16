package com.geovault.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import com.geovault.ui.getDotIndexAt
import com.geovault.ui.getCenterForIndex
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.geovault.map.MapStyleHelper
import com.geovault.model.AppInfo
import com.geovault.model.GeoPoint
import com.geovault.model.LockType
import com.geovault.model.VaultState
import com.geovault.ui.theme.*
import com.google.android.gms.location.LocationServices
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSelectionScreen(
    state: VaultState,
    onSaveConfig: (GeoPoint, String, Set<String>, LockType) -> Unit,
    onUnlockAttempt: (String, String) -> Unit,
    onSimulateLocation: (Double, Double) -> Unit
) {
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var showSetupDialog by remember { mutableStateOf(false) }
    var selectedVaultId by remember { mutableStateOf<String?>(null) }
    var selectedLatLng by remember { mutableStateOf<LatLng?>(null) }
    
    val isDarkTheme = state.isDarkMode
    var isSatelliteMode by remember { mutableStateOf(state.isSatelliteMode) }
    
    val currentStyleUrl = remember(isSatelliteMode, isDarkTheme) {
        if (isSatelliteMode) {
            MapStyleHelper.getSatelliteStyle(isHybrid = true)
        } else {
            if (isDarkTheme) MapStyleHelper.DARK else MapStyleHelper.BRIGHT
        }
    }
    
    LaunchedEffect(currentStyleUrl) {
        mapLibreMap?.setStyle(currentStyleUrl)
    }

    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    
    var deviceAzimuth by remember { mutableStateOf(0f) }
    val animatedBearing by animateFloatAsState(
        targetValue = deviceAzimuth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "mapBearing"
    )

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    deviceAzimuth = -azimuth // Invert to align map to real world
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    LaunchedEffect(animatedBearing) {
        mapLibreMap?.moveCamera(CameraUpdateFactory.bearingTo(animatedBearing.toDouble()))
    }

    val mapView = remember { MapView(context) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = if (isDarkTheme) CyberBlack else Color.White,
        bottomBar = {
            if (state.vaults.isEmpty()) {
                CyberSetupOverlay()
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Map Layer
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    mapView.apply {
                        getMapAsync { map ->
                            mapLibreMap = map
                            map.setStyle(currentStyleUrl)
                            
                            map.addOnMapLongClickListener { point ->
                                if (map.cameraPosition.zoom < 16.0) {
                                    android.widget.Toast.makeText(context, "Zoom in closer to set vault (100m scale)", android.widget.Toast.LENGTH_SHORT).show()
                                    return@addOnMapLongClickListener true
                                }
                                selectedLatLng = point
                                map.clear()
                                // Removed: markers are no longer shown for stealth
                                showSetupDialog = true
                                true
                            }

                            map.addOnMapClickListener { point ->
                                state.vaults.forEach { vault ->
                                    val vaultLatLng = LatLng(vault.location.latitude, vault.location.longitude)
                                    if (point.distanceTo(vaultLatLng) < 500) {
                                        selectedVaultId = vault.id
                                    }
                                }
                                true
                            }
                            
                            map.clear()
                            // Removed: vault markers are hidden for "perfect hide"
                            
                            // Hide MapLibre UI for stealth/minimalism
                            map.uiSettings.isLogoEnabled = false
                            map.uiSettings.isAttributionEnabled = false
                        }
                    }
                }
            )

            // Radar/Grid Overlay (Subtle)
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().alpha(0.05f)) {
                val step = 100.dp.toPx()
                for (x in 0..size.width.toInt() step step.toInt()) {
                    drawLine(Color.Cyan, start = androidx.compose.ui.geometry.Offset(x.toFloat(), 0f), end = androidx.compose.ui.geometry.Offset(x.toFloat(), size.height))
                }
                for (y in 0..size.height.toInt() step step.toInt()) {
                    drawLine(Color.Cyan, start = androidx.compose.ui.geometry.Offset(0f, y.toFloat()), end = androidx.compose.ui.geometry.Offset(size.width, y.toFloat()))
                }
            }
            
            // Style Selector (Floating)
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StyleFab(icon = Icons.Default.Public, active = isSatelliteMode) { isSatelliteMode = !isSatelliteMode }
            }

            // Controls
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CyberActionButton(icon = Icons.Default.MyLocation, onClick = {
                    try {
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            location?.let {
                                val latLng = LatLng(it.latitude, it.longitude)
                                mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18.0), 1000, null)
                            }
                        }
                    } catch (e: SecurityException) {}
                })
            }
        }
    }

    if (showSetupDialog && selectedLatLng != null) {
        CyberSetupDialog(
            apps = state.installedApps,
            onDismiss = { showSetupDialog = false },
            onConfirm = { pin, selectedApps, lockType ->
                onSaveConfig(GeoPoint(selectedLatLng!!.latitude, selectedLatLng!!.longitude), pin, selectedApps, lockType)
                showSetupDialog = false
            }
        )
    }

    if (selectedVaultId != null) {
        val vault = state.vaults.find { it.id == selectedVaultId }
        if (vault != null) {
            CyberUnlockDialog(
                lockType = vault.lockType,
                onDismiss = { selectedVaultId = null },
                onConfirm = { secret ->
                    onUnlockAttempt(vault.id, secret)
                    selectedVaultId = null
                }
            )
        }
    }
}

@Composable
fun SecurityStatusPanel(isNear: Boolean, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "alpha"
    )

    Surface(
        modifier = modifier.padding(horizontal = 24.dp).fillMaxWidth(),
        color = CyberDarkBlue.copy(alpha = 0.8f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (isNear) CyberNeonGreen.copy(alpha = 0.5f) else CyberNeonRed.copy(alpha = 0.5f)),
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(12.dp).scale(if (isNear) 1f else 1.2f).alpha(alpha),
                    shape = CircleShape,
                    color = if (isNear) CyberNeonGreen else CyberNeonRed
                ) {}
                Surface(
                    modifier = Modifier.size(24.dp),
                    shape = CircleShape,
                    color = (if (isNear) CyberNeonGreen else CyberNeonRed).copy(alpha = 0.15f)
                ) {}
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    if (isNear) "ACCESS GRANTED" else "ZONE RESTRICTED",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = if (isNear) CyberNeonGreen else CyberNeonRed
                )
                Text(
                    if (isNear) "Double-tap radar to bypass security" else "Move to secure coordinates to unlock",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun StyleFab(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = if (active) CyberBlue else CyberDarkBlue.copy(alpha = 0.8f),
        contentColor = if (active) Color.Black else CyberBlue,
        shape = CircleShape
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun CyberActionButton(icon: ImageVector, color: Color = CyberBlue, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(56.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = CyberDarkBlue.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        shadowElevation = 8.dp
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(16.dp))
    }
}

@Composable
fun CyberSetupOverlay() {
    Surface(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        color = CyberBlack.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, Brush.verticalGradient(listOf(CyberBlue.copy(alpha = 0.3f), Color.Transparent)))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "SYSTEM INITIALIZATION",
                style = MaterialTheme.typography.labelLarge,
                color = CyberBlue,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            Text(
                "Long-press target area to establish vault zone",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun CyberSetupDialog(apps: List<AppInfo>, onDismiss: () -> Unit, onConfirm: (String, Set<String>, LockType) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var lockType by remember { mutableStateOf(LockType.PIN) }
    val selectedApps = remember { mutableStateOf(setOf<String>()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            shape = RoundedCornerShape(32.dp),
            color = CyberDarkBlue,
            border = BorderStroke(1.dp, CyberBlue.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("VAULT ENCRYPTION", style = MaterialTheme.typography.headlineSmall, color = CyberBlue, fontWeight = FontWeight.Black)
                
                Spacer(Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LockTypeButton("PIN", lockType == LockType.PIN) { 
                        lockType = LockType.PIN 
                        pin = "" 
                    }
                    LockTypeButton("PATTERN", lockType == LockType.PATTERN) { 
                        lockType = LockType.PATTERN 
                        pin = ""
                    }
                    LockTypeButton("BIO", lockType == LockType.FINGERPRINT) { lockType = LockType.FINGERPRINT }
                    LockTypeButton("MAP", lockType == LockType.MAP) { lockType = LockType.MAP }
                }

                Spacer(Modifier.height(16.dp))
                
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when (lockType) {
                        LockType.PIN -> {
                            CompactPinPad(onPinComplete = { pin = it })
                        }
                        LockType.PATTERN -> {
                            CompactPatternGrid(onPatternComplete = { pin = it })
                        }
                        LockType.FINGERPRINT -> {
                            Text("Biometric required on unlock", color = CyberBlue)
                            pin = "BIO"
                        }
                        LockType.MAP -> {
                            Text("Map location set by long-press", color = CyberBlue)
                            pin = "MAP"
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                Text("PACKAGE VISIBILITY", style = MaterialTheme.typography.titleSmall)
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(apps) { app ->
                        ListItem(
                            headlineContent = { Text(app.appName, color = Color.White) },
                            leadingContent = {
                                app.icon?.let {
                                    Image(it.toBitmap().asImageBitmap(), contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape))
                                }
                            },
                            trailingContent = {
                                Checkbox(
                                    checked = selectedApps.value.contains(app.packageName),
                                    onCheckedChange = { checked ->
                                        if (checked) selectedApps.value += app.packageName
                                        else selectedApps.value -= app.packageName
                                    }
                                )
                            }
                        )
                    }
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("ABORT") }
                    Button(
                        onClick = { onConfirm(pin, selectedApps.value, lockType) },
                        enabled = pin.isNotEmpty() && selectedApps.value.isNotEmpty()
                    ) { Text("INITIALIZE") }
                }
            }
        }
    }
}

@Composable
fun LockTypeButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) CyberBlue else Color.Transparent,
            contentColor = if (selected) Color.Black else CyberBlue
        ),
        border = BorderStroke(1.dp, CyberBlue),
        modifier = Modifier.height(40.dp)
    ) {
        Text(text, fontSize = 10.sp)
    }
}

@Composable
fun CyberUnlockDialog(lockType: LockType, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var secret by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(300.dp),
            shape = RoundedCornerShape(32.dp),
            color = CyberDarkBlue
        ) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BYPASS SECURITY", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(24.dp))
                
                when (lockType) {
                    LockType.PIN -> {
                        CompactPinPad(onPinComplete = { onConfirm(it) })
                    }
                    LockType.PATTERN -> {
                        CompactPatternGrid(onPatternComplete = { onConfirm(it) })
                    }
                    LockType.FINGERPRINT -> {
                        IconButton(
                            onClick = { onConfirm("BIO") },
                            modifier = Modifier.size(64.dp).background(CyberBlue.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(Icons.Default.Fingerprint, null, tint = CyberBlue, modifier = Modifier.size(32.dp))
                        }
                        Text("Use Fingerprint", color = CyberBlue, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    LockType.MAP -> {
                        Text("Tap target on map to unlock", color = Color.Gray, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { onConfirm("MAP") }) {
                            Text("OPEN MAP INTERFACE")
                        }
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = onDismiss) { Text("CANCEL", color = Color.Gray) }
            }
        }
    }
}


