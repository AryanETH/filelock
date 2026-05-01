package com.geovault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.Explore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.geovault.ui.theme.CyberBlue
import com.geovault.ui.theme.CyberDarkBlue
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
    onClearAllVaults: () -> Unit
) {
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var pin by remember { mutableStateOf("") }
    var showPinPrompt by remember { mutableStateOf(false) }
    var lastTapLocation by remember { mutableStateOf<LatLng?>(null) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    
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
                                // The image mentions "double taps or tap on the location where he earlier tap to hide"
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastTapTime < 500) { 
                                    lastTapLocation = point
                                    showPinPrompt = true
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

            if (showPinPrompt) {
                PinEntryDialog(
                    title = "ENTER PIN",
                    onDismiss = { showPinPrompt = false },
                    onConfirm = { enteredPin ->
                        lastTapLocation?.let { onUnlockAttempt(it.latitude, it.longitude, enteredPin) }
                        showPinPrompt = false
                    }
                )
            }

            if (showSetupDialog && setupLatLng != null) {
                VaultSetupDialog(
                    apps = state.installedApps,
                    onDismiss = { showSetupDialog = false },
                    onConfirm = { pinSecret, selectedApps, lockType ->
                        onSaveConfig(GeoPoint(setupLatLng!!.latitude, setupLatLng!!.longitude), pinSecret, selectedApps, lockType)
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
                onClearAllVaults = onClearAllVaults
            )
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

@Composable
fun PinEntryDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    
    // Auto-confirm when 5 digits are reached
    LaunchedEffect(pin) {
        if (pin.length == 5) {
            // Give user time to see the 5th digit box filled
            delay(300)
            onConfirm(pin)
            // Reset for next time if it fails or returns
            pin = ""
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CyberDarkBlue,
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBlue.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = CyberBlue, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(5) { index ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (index < pin.length) CyberBlue else Color.Gray.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (index < pin.length) {
                                Text(pin[index].toString(), color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                // Simple numeric keypad
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { key ->
                                TextButton(
                                    onClick = {
                                        when (key) {
                                            "C" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                            "OK" -> {
                                                // Keep manual OK just in case, but LaunchedEffect handles auto
                                                if (pin.length == 5) onConfirm(pin)
                                            }
                                            else -> if (pin.length < 5) pin += key
                                        }
                                    },
                                    modifier = Modifier.size(56.dp),
                                    colors = ButtonDefaults.textButtonColors(containerColor = Color.White.copy(alpha = 0.05f))
                                ) {
                                    Text(key, color = Color.White, fontSize = 18.sp)
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
fun VaultSetupDialog(apps: List<AppInfo>, onDismiss: () -> Unit, onConfirm: (String, Set<String>, LockType) -> Unit) {
    var pin by remember { mutableStateOf("") }
    val selectedApps = remember { mutableStateOf(setOf<String>()) }
    var showApps by remember { mutableStateOf(false) }

    // Auto-advance when 5 digits are reached in setup
    LaunchedEffect(pin) {
        if (pin.length == 5 && !showApps) {
            delay(300)
            showApps = true
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = CyberDarkBlue,
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBlue.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("VAULT INITIALIZATION", style = MaterialTheme.typography.titleLarge, color = CyberBlue, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(16.dp))
                
                if (!showApps) {
                    Text("SET 5-DIGIT PIN", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) { index ->
                            Box(
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(if (index < pin.length) CyberBlue else Color.Gray.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (index < pin.length) Text(pin[index].toString(), color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "->")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        keys.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { key ->
                                    TextButton(onClick = {
                                        when(key) {
                                            "C" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                            "->" -> if (pin.length == 5) showApps = true
                                            else -> if (pin.length < 5) pin += key
                                        }
                                    }, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.textButtonColors(containerColor = Color.White.copy(alpha = 0.05f))) {
                                        Text(key, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text("SELECT STEALTH APPS", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(apps) { app ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                                if (selectedApps.value.contains(app.packageName)) selectedApps.value -= app.packageName
                                else selectedApps.value += app.packageName
                            }.padding(vertical = 8.dp)) {
                                app.icon?.let { Image(it.toBitmap().asImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape)) }
                                Spacer(Modifier.width(12.dp))
                                Text(app.appName, color = Color.White, modifier = Modifier.weight(1f))
                                Checkbox(checked = selectedApps.value.contains(app.packageName), onCheckedChange = null)
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showApps = false }) { Text("BACK") }
                        Button(onClick = { onConfirm(pin, selectedApps.value, LockType.PIN) }) { Text("CREATE VAULT") }
                    }
                }
            }
        }
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
