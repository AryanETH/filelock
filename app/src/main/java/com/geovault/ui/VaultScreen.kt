package com.geovault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.Explore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.geovault.location.LocationHelper
import com.geovault.model.AppInfo
import com.geovault.model.GeoPoint
import com.geovault.model.LockType
import com.geovault.model.VaultState
import com.geovault.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.net.URL
import java.util.Locale
import org.json.JSONArray
import com.google.android.gms.location.LocationServices
import org.maplibre.android.location.LocationComponentActivationOptions
import androidx.compose.foundation.gestures.detectDragGestures

@Composable
fun VaultScreen(
    state: VaultState,
    onUnlockAttempt: (Double, Double, String) -> Unit,
    onSaveConfig: (GeoPoint, String, Set<String>, LockType) -> Unit,
    onLockClick: () -> Unit,
    onAppClick: (String) -> Unit,
    onRemoveApp: (String) -> Unit,
    onSimulateArrive: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenProtectedApps: () -> Unit,
    onToggleMasterStealth: () -> Unit,
    onAddFile: (android.net.Uri, com.geovault.model.FileCategory) -> Unit,
    onToggleAppLock: (String) -> Unit,
    onRemoveVault: (String) -> Unit,
    onClearAllVaults: () -> Unit,
    onGrantCamera: () -> Unit,
    onGrantStorage: () -> Unit
) {
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    
    var showSetupDialog by remember { mutableStateOf(false) }
    var setupLatLng by remember { mutableStateOf<LatLng?>(null) }
    
    val brightStyle = "https://tiles.openfreemap.org/styles/bright"
    val darkStyle = "https://tiles.openfreemap.org/styles/dark"
    val satelliteStyle = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}" // ESRI Satellite Tiles
    var currentStyleUrl by remember { mutableStateOf(darkStyle) }
    var mapBearing by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    var selectedVaultForUnlock by remember { mutableStateOf<com.geovault.model.VaultConfig?>(null) }
    var showUnlockPrompt by remember { mutableStateOf(false) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    BackHandler(enabled = !state.isLocked) {
        onLockClick()
    }

    LaunchedEffect(Unit) {
        MapLibre.getInstance(context)
    }

    LaunchedEffect(currentStyleUrl) {
        mapLibreMap?.setStyle(currentStyleUrl)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLocked) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        onCreate(null)
                        getMapAsync { map ->
                            mapLibreMap = map
                            map.uiSettings.isLogoEnabled = false
                            map.uiSettings.isAttributionEnabled = false

                            // Disable native compass to use our custom aligned one
                            map.uiSettings.isCompassEnabled = false
                            
                            map.addOnCameraMoveListener {
                                mapBearing = map.cameraPosition.bearing.toFloat()
                            }

                            val styleBuilder = if (currentStyleUrl.contains("World_Imagery")) {
                                // Create a basic Style JSON for ESRI Satellite if using tile URL directly
                                "{\"version\": 8, \"sources\": {\"satellite\": {\"type\": \"raster\", \"tiles\": [\"$currentStyleUrl\"], \"tileSize\": 256}}, \"layers\": [{\"id\": \"satellite\", \"type\": \"raster\", \"source\": \"satellite\"}]}"
                            } else {
                                currentStyleUrl
                            }

                            map.setStyle(styleBuilder) { style ->
                                try {
                                    val locationComponent = map.locationComponent
                                    locationComponent.activateLocationComponent(
                                        org.maplibre.android.location.LocationComponentActivationOptions.builder(ctx, style).build()
                                    )
                                    locationComponent.isLocationComponentEnabled = true
                                    
                                    // Randomize initial view for maximum stealth
                                    val randomLat = (Math.random() * 130) - 60 // -60 to 70
                                    val randomLon = (Math.random() * 360) - 180 // -180 to 180
                                    val randomZoom = (Math.random() * 2) + 2 // 2.0 to 4.0
                                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(randomLat, randomLon), randomZoom))

                                } catch (e: SecurityException) {
                                    e.printStackTrace()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            
                            map.addOnMapClickListener { point ->
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastTapTime < 500) { 
                                    val vault = state.vaults.find { v ->
                                        LocationHelper.isWithinRadius(point.latitude, point.longitude, v.location.latitude, v.location.longitude, 500f)
                                    }
                                    if (vault != null) {
                                        selectedVaultForUnlock = vault
                                        showUnlockPrompt = true
                                    }
                                }
                                lastTapTime = currentTime
                                true
                            }

                            map.addOnMapLongClickListener { point ->
                                // The image mentions "long press on the map pin screen" (setup)
                                setupLatLng = point
                                showSetupDialog = true
                                true
                            }
                        }
                    }
                }
            )

            Canvas(modifier = Modifier.fillMaxSize().alpha(0.08f)) {
                val step = 100.dp.toPx()
                for (x in 0..size.width.toInt() step step.toInt()) {
                    drawLine(Color.Cyan, start = Offset(x.toFloat(), 0f), end = Offset(x.toFloat(), size.height))
                }
                for (y in 0..size.height.toInt() step step.toInt()) {
                    drawLine(Color.Cyan, start = Offset(0f, y.toFloat()), end = Offset(size.width, y.toFloat()))
                }
            }

            // Unified Controls Column (Bottom Right)
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Custom Aligned Compass
                SmallMapFab(
                    icon = Icons.Default.Explore, 
                    active = false,
                    modifier = Modifier.rotate(-mapBearing)
                ) {
                    mapLibreMap?.animateCamera(CameraUpdateFactory.bearingTo(0.0))
                }

                // Style Selector (Cycle through Bright, Dark, Satellite)
                SmallMapFab(
                    icon = when(currentStyleUrl) {
                        brightStyle -> Icons.Default.LightMode
                        darkStyle -> Icons.Default.DarkMode
                        else -> Icons.Default.Public // Satellite icon
                    }, 
                    active = false
                ) { 
                    currentStyleUrl = when (currentStyleUrl) {
                        brightStyle -> darkStyle
                        darkStyle -> satelliteStyle
                        else -> brightStyle
                    }
                }

                SmallMapFab(icon = Icons.Default.MyLocation, active = false) {
                    try {
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            location?.let {
                                mapLibreMap?.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15.0)
                                )
                            }
                        }
                    } catch (e: SecurityException) {
                        // Handle permission not granted
                    }
                }
            }

            if (showUnlockPrompt && selectedVaultForUnlock != null) {
                VaultUnlockDialog(
                    vault = selectedVaultForUnlock!!,
                    onDismiss = { showUnlockPrompt = false },
                    onConfirm = { secret ->
                        onUnlockAttempt(selectedVaultForUnlock!!.location.latitude, selectedVaultForUnlock!!.location.longitude, secret)
                        showUnlockPrompt = false
                    }
                )
            }

            if (showSetupDialog && setupLatLng != null) {
                VaultSetupDialog(
                    apps = state.installedApps,
                    onDismiss = { showSetupDialog = false },
                    onConfirm = { secret, selectedApps, lockType ->
                        onSaveConfig(GeoPoint(setupLatLng!!.latitude, setupLatLng!!.longitude), secret, selectedApps, lockType)
                        showSetupDialog = false
                    }
                )
            }
        } else {
            VaultContentScreen(
                state = state,
                onLockClick = onLockClick,
                onAppClick = onAppClick,
                onRemoveApp = onRemoveApp,
                onOpenUsageSettings = onOpenUsageSettings,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenProtectedApps = onOpenProtectedApps,
                onToggleMasterStealth = onToggleMasterStealth,
                onAddFile = onAddFile,
                onToggleAppLock = onToggleAppLock,
                onRemoveVault = onRemoveVault,
                onClearAllVaults = onClearAllVaults,
                onGrantCamera = onGrantCamera,
                onGrantStorage = onGrantStorage
            )
        }
    }
}

@Composable
fun VaultUnlockDialog(
    vault: com.geovault.model.VaultConfig,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = CyberDarkBlue,
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBlue.copy(alpha = 0.4f)),
            modifier = Modifier.width(320.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (vault.lockType == LockType.PIN) "VERIFY PIN" else "VERIFY PATTERN",
                    color = CyberBlue,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(Modifier.height(24.dp))

                if (vault.lockType == LockType.PIN) {
                    CompactPinPad(correctPin = vault.secret, onPinComplete = onConfirm)
                } else {
                    CompactPatternGrid(correctPattern = vault.secret, onPatternComplete = onConfirm)
                }
                
                Spacer(Modifier.height(16.dp))
                
                TextButton(onClick = onDismiss) {
                    Text("ABORT", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun VaultSetupDialog(apps: List<AppInfo>, onDismiss: () -> Unit, onConfirm: (String, Set<String>, LockType) -> Unit) {
    var secret by remember { mutableStateOf("") }
    var lockType by remember { mutableStateOf(LockType.PIN) }
    val selectedApps = remember { mutableStateOf(setOf<String>()) }
    var showApps by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = CyberDarkBlue,
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBlue.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    if (!showApps) "SECURE VAULT" else "STEALTH APPS",
                    style = MaterialTheme.typography.titleLarge,
                    color = CyberBlue,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(20.dp))
                
                if (!showApps) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        VaultLockTypeButton("PIN", lockType == LockType.PIN) { 
                            lockType = LockType.PIN 
                            secret = ""
                        }
                        VaultLockTypeButton("PATTERN", lockType == LockType.PATTERN) { 
                            lockType = LockType.PATTERN 
                            secret = ""
                        }
                    }
                    
                    Spacer(Modifier.height(24.dp))

                    if (lockType == LockType.PIN) {
                        CompactPinPad(onPinComplete = {
                            secret = it
                            showApps = true
                        })
                    } else {
                        Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            CompactPatternGrid(onPatternComplete = {
                                secret = it
                                showApps = true
                            })
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(apps) { app ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (selectedApps.value.contains(app.packageName)) selectedApps.value -= app.packageName
                                        else selectedApps.value += app.packageName
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp)
                            ) {
                                app.icon?.let { Image(it.toBitmap().asImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape)) }
                                Spacer(Modifier.width(12.dp))
                                Text(app.appName, color = Color.White, modifier = Modifier.weight(1f), fontSize = 15.sp)
                                Checkbox(
                                    checked = selectedApps.value.contains(app.packageName),
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = CyberBlue)
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(20.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showApps = false }) { Text("BACK", color = Color.Gray) }
                        Button(
                            onClick = { onConfirm(secret, selectedApps.value, lockType) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("INITIALIZE", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.VaultLockTypeButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) CyberBlue.copy(alpha = 0.15f) else Color.Transparent,
            contentColor = if (selected) CyberBlue else Color.Gray
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) CyberBlue else Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.height(40.dp).weight(1f),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun SmallMapFab(icon: ImageVector, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.size(40.dp).clickable { onClick() },
        shape = CircleShape,
        color = if (active) CyberBlue else CyberDarkBlue.copy(alpha = 0.7f),
        contentColor = if (active) Color.Black else Color.White,
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

suspend fun searchLocation(query: String): LatLng? = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://nominatim.openstreetmap.org/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&format=json&limit=1")
        val connection = url.openConnection()
        connection.setRequestProperty("User-Agent", "GeoVault-App")
        val response = connection.getInputStream().bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(response)
        if (jsonArray.length() > 0) {
            val first = jsonArray.getJSONObject(0)
            val lat = first.getDouble("lat")
            val lon = first.getDouble("lon")
            LatLng(lat, lon)
        } else null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun MapSearchBar(query: String, onQueryChange: (String) -> Unit, onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(28.dp),
        color = CyberDarkBlue.copy(alpha = 0.9f),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBlue.copy(alpha = 0.5f)),
        shadowElevation = 8.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = CyberBlue)
            Spacer(Modifier.width(12.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch(query) }),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(CyberBlue),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text("Search places...", color = Color.Gray, fontSize = 16.sp)
                    }
                    innerTextField()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                }
            }
        }
    }
}
