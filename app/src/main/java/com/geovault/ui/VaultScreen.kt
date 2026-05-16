package com.geovault.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.Explore
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import com.geovault.R
import androidx.compose.ui.res.stringResource
import com.geovault.location.LocationHelper
import com.geovault.map.MapStyleHelper
import com.geovault.model.AppInfo
import com.geovault.model.GeoPoint
import com.geovault.model.LockType
import com.geovault.model.VaultState
import com.geovault.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.blur
import androidx.biometric.BiometricManager
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import com.geovault.security.IntruderManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.AnimationVector1D

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    state: VaultState,
    onUnlockAttempt: (Double, Double, String) -> Unit,
    onIntruderCaptured: (android.net.Uri, String?) -> Unit,
    onSaveConfig: (GeoPoint, String, Set<String>, LockType, Float) -> Unit,
    onLockClick: () -> Unit,
    onAppClick: (String) -> Unit,
    onRemoveApp: (String) -> Unit,
    onSimulateArrive: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenProtectedApps: () -> Unit,
    onToggleMasterStealth: () -> Unit,
    onAddFiles: (List<android.net.Uri>, com.geovault.model.FileCategory) -> Unit,
    onToggleAppLock: (String) -> Unit,
    onRemoveVault: (String) -> Unit,
    onClearAllVaults: () -> Unit,
    onGrantCamera: () -> Unit,
    onGrantStorage: () -> Unit,
    onGrantFullStorage: () -> Unit,
    onDeleteFile: (String) -> Unit,
    onRestoreFile: (String) -> Unit,
    onFetchGalleryItems: (com.geovault.model.FileCategory) -> Unit,
    onToggleDarkMode: () -> Unit,
    onToggleFingerprint: () -> Unit,
    onToggleSatellite: () -> Unit,
    onSetLanguage: (String) -> Unit,
    onCompleteTour: () -> Unit,
    onToggleScreenshotRestriction: () -> Unit,
    onCreateFolder: (String) -> Unit = {},
    onAddFilesToFolder: (List<android.net.Uri>, String) -> Unit = { _, _ -> },
    onStartAction: () -> Unit = {},
    onEndAction: () -> Unit = {}
) {
    val currentVaults by rememberUpdatedState(state.vaults)
    val currentInstalledApps by rememberUpdatedState(state.installedApps)
    
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    
    var showSetupDialog by remember { mutableStateOf(false) }
    var setupLatLng by remember { mutableStateOf<LatLng?>(null) }
    var isNativeEligible by remember { mutableStateOf(false) }
    val isDark = state.isDarkMode
    
    val currentStyleUrl = remember(state.isSatelliteMode) {
        if (state.isSatelliteMode) {
            MapStyleHelper.getSatelliteStyle(isHybrid = true)
        } else {
            // Force bright style even in dark mode for creamy feel
            MapStyleHelper.BRIGHT
        }
    }

    var mapBearing by remember { mutableFloatStateOf(0f) }
    var hideNetworkWarning by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    var searchQuery by remember { mutableStateOf("") }
    var searchSuggestions by remember { mutableStateOf<List<Pair<String, LatLng>>>(emptyList()) }
    var showOfflineDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val haptic = LocalHapticFeedback.current
    
    // Search suggestions logic
    LaunchedEffect(searchQuery) {
        if (searchQuery.length > 2) {
            delay(200) // Reduced debounce for faster live suggestions
            if (state.isNetworkAvailable) {
                searchSuggestions = getSearchSuggestions(searchQuery)
            } else {
                searchSuggestions = emptyList()
            }
        } else {
            searchSuggestions = emptyList()
        }
    }

    // Dynamic Gyro Rotation
    var isCenteredOnUser by remember { mutableStateOf(false) }
    
    val sensorManager = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager }
    val rotationSensor = remember { sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR) }
    
    LaunchedEffect(isCenteredOnUser) {
        mapLibreMap?.let { map ->
            if (isCenteredOnUser) {
                map.locationComponent.renderMode = org.maplibre.android.location.modes.RenderMode.COMPASS
                map.locationComponent.cameraMode = org.maplibre.android.location.modes.CameraMode.TRACKING_COMPASS
            } else {
                map.locationComponent.renderMode = org.maplibre.android.location.modes.RenderMode.NORMAL
                map.locationComponent.cameraMode = org.maplibre.android.location.modes.CameraMode.NONE
            }
        }
    }
    
    var selectedVaultForUnlock by remember { mutableStateOf<com.geovault.model.VaultConfig?>(null) }
    var showUnlockPrompt by remember { mutableStateOf(false) }
    
    // Ripple effect state
    var rippleOffset by remember { mutableStateOf<Offset?>(null) }
    val rippleScale = remember { androidx.compose.animation.core.Animatable(0f) }
    val rippleAlpha = remember { androidx.compose.animation.core.Animatable(0f) }

    // Tour Targets
    var fabColumnRect by remember { mutableStateOf(Rect.Zero) }
    
    val lifecycleOwner = LocalLifecycleOwner.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        onEndAction()
        if (permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            // Permission granted, will be reflected in state
        }
    }

    DisposableEffect(showUnlockPrompt) {
        if (showUnlockPrompt) {
            IntruderManager.getInstance(context).startSession(lifecycleOwner)
        }
        onDispose {
            if (showUnlockPrompt) {
                IntruderManager.getInstance(context).stopSession()
            }
        }
    }

    BackHandler(enabled = !state.isLocked) {
        onLockClick()
    }

    LaunchedEffect(Unit) {
        MapLibre.getInstance(context)
    }

    LaunchedEffect(currentStyleUrl) {
        android.util.Log.d("VaultScreen", "Applying map style. IsSatellite: ${state.isSatelliteMode}")
        mapLibreMap?.let { map ->
            map.setStyle(currentStyleUrl) { style ->
                android.util.Log.d("VaultScreen", "Style applied successfully")
            }
        }
    }

    val mapBlur by animateDpAsState(
        targetValue = if (showUnlockPrompt || showSetupDialog) 12.dp else 0.dp,
        animationSpec = tween(500),
        label = "MapBlur"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = state.isLocked,
            transitionSpec = {
                val duration = 600
                fadeIn(animationSpec = tween(duration)) togetherWith fadeOut(animationSpec = tween(duration))
            },
            label = "VaultTransition"
        ) { isLocked ->
            if (isLocked) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(mapBlur),
                        factory = { ctx ->
                            MapView(ctx).apply {
                                onCreate(null)
                                getMapAsync { map ->
                                    mapLibreMap = map
                                    map.uiSettings.isLogoEnabled = false
                                    map.uiSettings.isAttributionEnabled = false
                                    map.uiSettings.isCompassEnabled = false
                                    map.uiSettings.isDoubleTapGesturesEnabled = false // Reverted: Disable native double tap zoom
                                    map.uiSettings.isTiltGesturesEnabled = true // Enable 3D tilt gestures
                                    map.uiSettings.isRotateGesturesEnabled = true // Enable rotation gestures

                                    map.addOnCameraMoveListener {
                                        mapBearing = map.cameraPosition.bearing.toFloat()
                                    }

                                    // Custom Gesture Detector to prioritize vault actions over map engine
                                    val gestureDetector = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
                                        override fun onDown(e: android.view.MotionEvent): Boolean {
                                            return true // MUST return true to receive double tap events
                                        }

                                        override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                                            if (map.cameraPosition.zoom < 16.0) {
                                                android.widget.Toast.makeText(ctx, "Zoom in closer to unlock (100m scale)", android.widget.Toast.LENGTH_SHORT).show()
                                                return false
                                            }
                                            val point = map.projection.fromScreenLocation(android.graphics.PointF(e.x, e.y))
                                            val vault = currentVaults.find { v ->
                                                val dist = LocationHelper.calculateDistance(point.latitude, point.longitude, v.location.latitude, v.location.longitude)
                                                dist <= 100f
                                            }
                                            
                                            return if (vault != null) {
                                                selectedVaultForUnlock = vault
                                                showUnlockPrompt = true
                                                true // Consumed - Prioritized!
                                            } else {
                                                false
                                            }
                                        }

                                        override fun onLongPress(e: android.view.MotionEvent) {
                                            if (map.cameraPosition.zoom < 16.0) {
                                                android.widget.Toast.makeText(ctx, "Zoom in closer to set vault (100m scale)", android.widget.Toast.LENGTH_SHORT).show()
                                                return
                                            }
                                            val point = map.projection.fromScreenLocation(android.graphics.PointF(e.x, e.y))
                                            
                                            // Trigger ripple animation
                                            rippleOffset = Offset(e.x, e.y)
                                            scope.launch {
                                                rippleScale.snapTo(0f)
                                                rippleAlpha.snapTo(0.6f)
                                                launch { rippleScale.animateTo(3f, tween(400)) }
                                                launch { rippleAlpha.animateTo(0f, tween(400)) }
                                            }

                                            val existingVault = currentVaults.find { v ->
                                                LocationHelper.calculateDistance(point.latitude, point.longitude, v.location.latitude, v.location.longitude) <= 100f
                                            }
                                            
                                            if (existingVault == null) {
                                                setupLatLng = point
                                                
                                                // Check if eligible for "Native" option (within 1km of current location)
                                                isNativeEligible = state.currentLocation?.let { live ->
                                                    LocationHelper.calculateDistance(point.latitude, point.longitude, live.latitude, live.longitude) <= 1000f
                                                } ?: false
                                                
                                                showSetupDialog = true
                                            }
                                        }
                                    })

                                    // Intercept touches for custom logic but allow map to handle its own gestures (tilt/pan/zoom)
                                    setOnTouchListener { view, event ->
                                        val handled = gestureDetector.onTouchEvent(event)
                                        // If our gesture detector consumed it (e.g. double tap), we might want to block the map.
                                        // However, to ensure 3D tilt (multi-touch) works, we must return false 
                                        // unless we are absolutely sure we want to stop the map from seeing the event.
                                        if (event.pointerCount > 1) {
                                            false // Multi-touch always goes to the map (for 3D tilt)
                                        } else {
                                            // For single touches, we return whatever the detector says, 
                                            // but for long press/double tap to work alongside panning, 
                                            // we usually return false anyway.
                                            false
                                        }
                                    }

                                    map.setStyle(currentStyleUrl) { style ->
                                        try {
                                            val locationComponent = map.locationComponent
                                            locationComponent.activateLocationComponent(
                                                org.maplibre.android.location.LocationComponentActivationOptions.builder(ctx, style)
                                                    .locationComponentOptions(
                                                        org.maplibre.android.location.LocationComponentOptions.builder(ctx)
                                                            .compassAnimationEnabled(true)
                                                            .accuracyAlpha(0.2f)
                                                            .build()
                                                    )
                                                    .build()
                                            )
                                            locationComponent.isLocationComponentEnabled = true
                                            
                                            // Randomize initial view for maximum stealth - Start at a random city
                                            val cities = listOf(
                                                LatLng(48.8566, 2.3522),   // Paris
                                                LatLng(40.7128, -74.0060), // New York
                                                LatLng(35.6895, 139.6917), // Tokyo
                                                LatLng(51.5074, -0.1278),  // London
                                                LatLng(-33.8688, 151.2093),// Sydney
                                                LatLng(25.2048, 55.2708),  // Dubai
                                                LatLng(19.0760, 72.8777),  // Mumbai
                                                LatLng(30.0444, 31.2357),  // Cairo
                                                LatLng(-23.5505, -46.6333),// Sao Paulo
                                                LatLng(1.3521, 103.8198),  // Singapore
                                                LatLng(52.5200, 13.4050),  // Berlin
                                                LatLng(41.9028, 12.4964),  // Rome
                                                LatLng(34.0522, -118.2437) // Los Angeles
                                            )
                                            val randomCity = cities.random()
                                            val randomZoom = (Math.random() * 2) + 10 
                                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(randomCity, randomZoom))

                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                        }
                    )

                    // Map Search Bar
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp, start = 16.dp, end = 16.dp)
                            .alpha(if (showUnlockPrompt || showSetupDialog) 0.5f else 1f)
                    ) {
                        MapSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = { query ->
                                if (!state.isNetworkAvailable) {
                                    showOfflineDialog = true
                                } else {
                                    scope.launch {
                                        searchLocation(query)?.let { latLng ->
                                            mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15.0))
                                            searchQuery = ""
                                            searchSuggestions = emptyList()
                                        }
                                    }
                                }
                            },
                            isDark = isDark
                        )
                        
                        if (searchSuggestions.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = (if (isDark) CyberDarkBlue else MaterialTheme.colorScheme.surface).copy(alpha = 0.95f),
                                shadowElevation = 4.dp
                            ) {
                                Column {
                                    searchSuggestions.take(5).forEach { suggestion ->
                                        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                                        Text(
                                            text = suggestion.first,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(suggestion.second, 15.0))
                                                    searchQuery = ""
                                                    searchSuggestions = emptyList()
                                                    isCenteredOnUser = false
                                                    keyboardController?.hide()
                                                }
                                                .padding(16.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        HorizontalDivider(color = (if (isDark) Color.White else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.05f))
                                    }
                                }
                            }
                        }
                    }

                    if (showOfflineDialog) {
                        AlertDialog(
                            onDismissRequest = { showOfflineDialog = false },
                            title = { Text("Internet Connection Required") },
                            text = { Text("You need an active internet connection to search for locations.") },
                            confirmButton = {
                                Button(onClick = {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS)
                                    context.startActivity(intent)
                                    showOfflineDialog = false
                                }) {
                                    Text("Turn on Data")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showOfflineDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    // Ripple overlay
                    rippleOffset?.let { offset ->
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = CyberBlue.copy(alpha = rippleAlpha.value),
                                radius = 40.dp.toPx() * rippleScale.value,
                                center = offset
                            )
                        }
                    }

                    // Unified Controls Column (Bottom Right)
                    AnimatedVisibility(
                        visible = state.isLocked,
                        enter = fadeIn(tween(800, delayMillis = 400)) + slideInHorizontally(tween(800, delayMillis = 400), initialOffsetX = { it }),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(end = 16.dp, bottom = 64.dp)
                                .onGloballyPositioned { fabColumnRect = it.boundsInWindow() },
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            // Custom Aligned Compass
                            SmallMapFab(
                                icon = Icons.Default.Explore, 
                                active = false,
                                isDark = isDark,
                                modifier = Modifier.rotate(-mapBearing)
                            ) {
                                HapticHelper.vibrate(context, 1)
                                mapLibreMap?.animateCamera(CameraUpdateFactory.bearingTo(0.0))
                            }

                            // Zoom Controls
                            SmallMapFab(icon = Icons.Default.Add, active = false, isDark = isDark) {
                                HapticHelper.vibrate(context, 1)
                                mapLibreMap?.animateCamera(CameraUpdateFactory.zoomIn())
                            }

                            SmallMapFab(icon = Icons.Default.Remove, active = false, isDark = isDark) {
                                HapticHelper.vibrate(context, 1)
                                mapLibreMap?.animateCamera(CameraUpdateFactory.zoomOut())
                            }

                            SmallMapFab(icon = Icons.Default.MyLocation, active = isCenteredOnUser, isDark = isDark) {
                                HapticHelper.vibrate(context, 2)
                                if (state.hasLocationPermission) {
                                    isCenteredOnUser = true
                                    try {
                                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                            location?.let {
                                                mapLibreMap?.animateCamera(
                                                    CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 18.0),
                                                    600
                                                )
                                            }
                                        }
                                    } catch (e: SecurityException) {}
                                } else {
                                    onStartAction()
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            }
                        }
                    }
                    
                    // Reset centering if user moves map manually
                    LaunchedEffect(mapLibreMap) {
                        mapLibreMap?.addOnCameraMoveStartedListener { reason ->
                            if (reason == org.maplibre.android.maps.MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                isCenteredOnUser = false
                            }
                        }
                    }

                    if (!state.isNetworkAvailable && !state.isMapLoaded && !hideNetworkWarning) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 80.dp)
                                .padding(horizontal = 32.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.WifiOff, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Turn on the data to load the map",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.width(12.dp))
                                Icon(
                                    Icons.Default.Close,
                                    null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            hideNetworkWarning = true
                                        }
                                )
                            }
                        }
                    }

                    if (showUnlockPrompt && selectedVaultForUnlock != null) {
                        AnimatedVisibility(
                            visible = showUnlockPrompt,
                            enter = fadeIn() + scaleIn(initialScale = 0.9f),
                            exit = fadeOut() + scaleOut(targetScale = 0.9f)
                        ) {
                            VaultUnlockDialog(
                                vault = selectedVaultForUnlock!!,
                                isDark = isDark,
                                onDismiss = { showUnlockPrompt = false },
                                onConfirm = { secret ->
                                    onUnlockAttempt(selectedVaultForUnlock!!.location.latitude, selectedVaultForUnlock!!.location.longitude, secret)
                                    showUnlockPrompt = false
                                },
                                onIntruderCaptured = onIntruderCaptured
                            )
                        }
                    }

                    if (showSetupDialog && setupLatLng != null) {
                        AnimatedVisibility(
                            visible = showSetupDialog,
                            enter = fadeIn() + scaleIn(initialScale = 0.9f),
                            exit = fadeOut() + scaleOut(targetScale = 0.9f)
                        ) {
                            VaultSetupDialog(
                                apps = currentInstalledApps,
                                isNativeEligible = isNativeEligible,
                                isDark = isDark,
                                onDismiss = { showSetupDialog = false },
                                onConfirm = { secret, selectedApps, lockType, radius ->
                                    onSaveConfig(GeoPoint(setupLatLng!!.latitude, setupLatLng!!.longitude), secret, selectedApps, lockType, radius)
                                    showSetupDialog = false
                                }
                            )
                        }
                    }

                    if (state.showTour) {
                        AppTour(
                            steps = listOf(
                                TourStep(R.string.tour_welcome),
                                TourStep(R.string.tour_step1),
                                TourStep(R.string.tour_step2),
                                TourStep(R.string.tour_step3, fabColumnRect),
                                TourStep(R.string.tour_step4)
                            ),
                            onCompleted = onCompleteTour
                        )
                    }
                }
            } else {
                VaultContentScreen(
                    state = state,
                    onLockClick = onLockClick,
                    onAppClick = onAppClick,
                    onRemoveApp = onRemoveApp,
                    onOpenUsageSettings = onOpenUsageSettings,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onOpenProtectedApps = onOpenProtectedApps,
                    onToggleMasterStealth = onToggleMasterStealth,
                    onAddFiles = onAddFiles,
                    onToggleAppLock = onToggleAppLock,
                    onRemoveVault = onRemoveVault,
                    onClearAllVaults = onClearAllVaults,
                    onGrantCamera = onGrantCamera,
                    onGrantStorage = onGrantStorage,
                    onGrantFullStorage = onGrantFullStorage,
                    onDeleteFile = onDeleteFile,
                    onRestoreFile = onRestoreFile,
                    onToggleDarkMode = onToggleDarkMode,
                    onToggleFingerprint = onToggleFingerprint,
                    onSetLanguage = onSetLanguage,
                    onCompleteTour = onCompleteTour,
                    onToggleScreenshotRestriction = onToggleScreenshotRestriction,
                    onFetchGalleryItems = onFetchGalleryItems,
                    onCreateFolder = onCreateFolder,
                    onAddFilesToFolder = onAddFilesToFolder,
                    onStartAction = onStartAction,
                    onEndAction = onEndAction
                )
            }
        }
    }
}

@Composable
fun VaultUnlockDialog(
    vault: com.geovault.model.VaultConfig,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onIntruderCaptured: (android.net.Uri, String?) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var failedAttempts by remember { mutableIntStateOf(0) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = if (isDark) CyberDarkBlue else CreamWhite,
            border = null,
            shadowElevation = 12.dp,
            modifier = Modifier.width(320.dp).blur(if (failedAttempts > 0) 1.dp else 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (vault.lockType == LockType.PIN) "ENTER SECURE PIN" else "DRAW SECURE PATTERN",
                    color = if (isDark) CyberBlue else AppBlue,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    style = MaterialTheme.typography.titleSmall
                )
                
                Spacer(Modifier.height(24.dp))

                if (vault.lockType == LockType.PIN) {
                    CompactPinPad(
                        correctPin = vault.secret, 
                        isLightTheme = !isDark,
                        onPinComplete = {
                            failedAttempts = 0
                            onConfirm(it)
                        },
                        onError = {
                            failedAttempts++
                            if (failedAttempts >= 3) {
                                IntruderManager.getInstance(context).captureIntruder(onIntruderCaptured)
                            }
                        }
                    )
                } else {
                    CompactPatternGrid(
                        correctPattern = vault.secret, 
                        isLightTheme = !isDark,
                        onPatternComplete = {
                            failedAttempts = 0
                            onConfirm(it)
                        },
                        onError = {
                            failedAttempts++
                            if (failedAttempts >= 3) {
                                IntruderManager.getInstance(context).captureIntruder(onIntruderCaptured)
                            }
                        }
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(R.string.cancel), 
                        color = Color.Gray, 
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun VaultSetupDialog(
    apps: List<AppInfo>, 
    isNativeEligible: Boolean,
    isDark: Boolean,
    onDismiss: () -> Unit, 
    onConfirm: (String, Set<String>, LockType, Float) -> Unit
) {
    val context = LocalContext.current
    var secret by remember { mutableStateOf("") }
    var lockType by remember { mutableStateOf(LockType.PIN) }
    val selectedApps = remember { mutableStateOf(setOf<String>()) }
    var showApps by remember { mutableStateOf(false) }
    
    val haptic = LocalHapticFeedback.current
    var isNativeEnabled by remember { mutableStateOf(false) }
    var radius by remember { mutableStateOf(500f) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            shape = RoundedCornerShape(32.dp),
            color = if (isDark) CyberDarkBlue else CreamWhite,
            border = null,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (!showApps) "INITIALIZE VAULT" else "SECURE PACKAGES",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isDark) CyberBlue else AppBlue,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(20.dp))
                
                if (!showApps) {
                    if (isNativeEligible) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background((if (isDark) CyberBlue else AppBlue).copy(alpha = 0.08f))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Native Mode", 
                                style = MaterialTheme.typography.bodyLarge, 
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) CyberBlue else AppBlue,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = isNativeEnabled,
                                onCheckedChange = { isNativeEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = if (isDark) CyberBlue else AppBlue
                                )
                            )
                        }
                        
                        if (isNativeEnabled) {
                            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Lock Radius", 
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.LightGray else Color.Gray
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "${radius.toInt()}m", 
                                        color = if (isDark) CyberBlue else AppBlue,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Slider(
                                    value = radius,
                                    onValueChange = { 
                                        if (it.toInt() != radius.toInt()) {
                                            HapticHelper.vibrate(context, 0)
                                        }
                                        radius = it 
                                    },
                                    valueRange = 100f..2000f,
                                    steps = 19,
                                    colors = SliderDefaults.colors(
                                        thumbColor = if (isDark) CyberBlue else AppBlue,
                                        activeTrackColor = if (isDark) CyberBlue else AppBlue
                                    )
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        VaultLockTypeButton("PIN", lockType == LockType.PIN, isDark) { 
                            lockType = LockType.PIN 
                            secret = ""
                        }
                        VaultLockTypeButton("PATTERN", lockType == LockType.PATTERN, isDark) { 
                            lockType = LockType.PATTERN 
                            secret = ""
                        }
                    }
                    
                    Spacer(Modifier.height(24.dp))

                    if (lockType == LockType.PIN) {
                        CompactPinPad(
                            isLightTheme = !isDark,
                            onPinComplete = {
                                secret = it
                                showApps = true
                            }
                        )
                    } else {
                        Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            CompactPatternGrid(
                                isLightTheme = !isDark,
                                onPatternComplete = {
                                    secret = it
                                    showApps = true
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(apps) { app ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        if (selectedApps.value.contains(app.packageName)) selectedApps.value -= app.packageName
                                        else selectedApps.value += app.packageName
                                    }
                                    .padding(vertical = 12.dp, horizontal = 12.dp)
                            ) {
                                app.icon?.let { Image(it.toBitmap().asImageBitmap(), contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape)) }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    app.appName, 
                                    color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.8f), 
                                    modifier = Modifier.weight(1f), 
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Checkbox(
                                    checked = selectedApps.value.contains(app.packageName),
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = if (isDark) CyberBlue else AppBlue)
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(20.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showApps = false }) { 
                            Text(stringResource(R.string.back), color = Color.Gray) 
                        }
                        Button(
                            onClick = { 
                                val finalRadius = if (isNativeEnabled) radius else 0f
                                onConfirm(secret, selectedApps.value, lockType, finalRadius) 
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isDark) CyberBlue else AppBlue),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(stringResource(R.string.save_lock), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.VaultLockTypeButton(text: String, selected: Boolean, isDark: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) (if (isDark) CyberBlue else AppBlue).copy(alpha = 0.15f) else Color.Transparent,
            contentColor = if (selected) (if (isDark) CyberBlue else AppBlue) else Color.Gray
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) (if (isDark) CyberBlue else AppBlue) else (if (isDark) Color.White else Color.Black).copy(alpha = 0.1f)),
        modifier = Modifier.height(40.dp).weight(1f),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun SmallMapFab(icon: ImageVector, active: Boolean, isDark: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .size(44.dp) // Slightly larger for better touch target
            .clickable(
                interactionSource = interactionSource,
                indication = null // Removes the subtle faded black square shadow
            ) { onClick() },
        shape = CircleShape,
        color = if (active) (if (isDark) CyberBlue else AppBlue) else (if (isDark) CyberDarkBlue else CreamWhite).copy(alpha = 0.9f),
        contentColor = if (active) Color.White else (if (isDark) Color.White else Color.Black).copy(alpha = 0.7f),
        shadowElevation = 6.dp,
        border = null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

suspend fun searchLocation(query: String): LatLng? = getSearchSuggestions(query).firstOrNull()?.second

suspend fun getSearchSuggestions(query: String): List<Pair<String, LatLng>> = withContext(Dispatchers.IO) {
    try {
        // Optimized: Only fetch what we need and parse immediately
        val url = URL("https://nominatim.openstreetmap.org/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&format=json&limit=5&addressdetails=0")
        val connection = url.openConnection()
        connection.setRequestProperty("User-Agent", "GeoVault-App")
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        val response = connection.getInputStream().bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(response)
        val suggestions = mutableListOf<Pair<String, LatLng>>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val name = item.optString("display_name", "Unknown Location")
            val lat = item.optDouble("lat", 0.0)
            val lon = item.optDouble("lon", 0.0)
            if (lat != 0.0) suggestions.add(name to LatLng(lat, lon))
        }
        suggestions
    } catch (e: Exception) {
        emptyList()
    }
}

@Composable
fun MapSearchBar(query: String, onQueryChange: (String) -> Unit, onSearch: (String) -> Unit, isDark: Boolean, modifier: Modifier = Modifier) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Surface(
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(28.dp),
        color = (if (isDark) CyberDarkBlue else CreamWhite).copy(alpha = 0.9f),
        border = null,
        shadowElevation = 6.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = if (isDark) CyberBlue else AppBlue)
            Spacer(Modifier.width(12.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.8f), 
                    fontSize = 16.sp
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { 
                    onSearch(query)
                    keyboardController?.hide()
                }),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(if (isDark) CyberBlue else AppBlue),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.search_places),
                            color = Color.Gray, 
                            fontSize = 16.sp
                        )
                    }
                    innerTextField()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Close, 
                        contentDescription = null, 
                        tint = Color.Gray
                    )
                }
            }
        }
    }
}
