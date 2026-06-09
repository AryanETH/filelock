package com.geovault.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.res.painterResource
import com.airbnb.lottie.compose.*
import com.geovault.R
import androidx.compose.ui.res.stringResource
import com.geovault.location.LocationHelper
import com.geovault.map.MapStyleHelper
import com.geovault.model.AppInfo
import com.geovault.model.GeoPoint
import com.geovault.model.LockType
import com.geovault.model.FileCategory
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
import org.json.JSONObject
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import com.google.android.gms.location.LocationServices
import org.maplibre.android.location.LocationComponentActivationOptions
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.draw.blur
import androidx.biometric.BiometricManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import com.geovault.security.IntruderManager
import androidx.lifecycle.compose.LocalLifecycleOwner as LifecycleOwnerCompose
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.AnimationVector1D
import androidx.media3.common.util.UnstableApi
import android.content.Intent

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    state: VaultState,
    onUnlockAttempt: (Double, Double, String) -> Unit,
    onIntruderCaptured: (android.net.Uri, String?) -> Unit,
    onSaveConfig: (GeoPoint, String, Set<String>, LockType, Float) -> Unit,
    onLockClick: () -> Unit,
    onAppClick: (String) -> Unit,
    onOpenUsageSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenProtectedApps: () -> Unit,
    onToggleMasterStealth: () -> Unit,
    onAddFiles: (List<android.net.Uri>, FileCategory) -> Unit,
    onToggleAppLock: (String) -> Unit,
    onRemoveVault: (String) -> Unit,
    onClearAllVaults: () -> Unit,
    onGrantCamera: () -> Unit,
    onGrantStorage: () -> Unit,
    onGrantFullStorage: () -> Unit,
    onGrantBackgroundPopups: () -> Unit,
    onDeleteFile: (String) -> Unit,
    onRestoreFile: (String) -> Unit,
    onFetchGalleryItems: (FileCategory) -> Unit,
    onRemoveAppFromVault: (String, String) -> Unit = { _, _ -> },
    onToggleDarkMode: () -> Unit,
    onToggleFingerprint: () -> Unit,
    onSetLanguage: (String) -> Unit,
    onCompleteTour: () -> Unit,
    onToggleScreenshotRestriction: () -> Unit,
    onToggleIntruderCapture: (Boolean) -> Unit = {},
    onToggleUninstallShield: (Boolean) -> Unit = {},
    onRestoreAndUninstall: () -> Unit = {},
    onCreateFolder: (String) -> Unit = {},
    onDeleteFolder: (String, Boolean) -> Unit = { _, _ -> },
    onBulkDelete: (Set<String>, Boolean) -> Unit = { _, _ -> },
    onBulkRestore: (Set<String>) -> Unit = {},
    onAddFilesToFolder: (List<android.net.Uri>, String) -> Unit = { _, _ -> },
    onSetCustomBackground: (String?) -> Unit = {},
    onFetchWeather: (Double, Double) -> Unit = { _, _ -> },
    onStartAction: () -> Unit = {},
    onEndAction: () -> Unit = {},
    onRequestGps: (() -> Unit) -> Unit = { it() }
) {
    val currentVaults by rememberUpdatedState(state.vaults)
    
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentMapZoom by remember { mutableDoubleStateOf(0.0) }
    var currentMapLat by remember { mutableDoubleStateOf(0.0) }
    
    var showSetupDialog by remember { mutableStateOf(false) }
    var setupLatLng by remember { mutableStateOf<LatLng?>(null) }
    var isNativeEligible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isDark = state.isDarkMode
    
    val currentStyleUrl = remember(state.isSatelliteMode) {
        if (state.isSatelliteMode) {
            MapStyleHelper.getSatelliteStyle(context, isHybrid = true)
        } else {
            MapStyleHelper.BRIGHT
        }
    }

    var mapBearing by remember { mutableFloatStateOf(0f) }
    var hideNetworkWarning by remember { mutableStateOf(false) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    var isCenteredOnUser by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchSuggestions by remember { mutableStateOf<List<Pair<String, LatLng>>>(emptyList()) }
    var showOfflineDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val haptic = LocalHapticFeedback.current
    var showWeatherPanel by remember { mutableStateOf(false) }
    
    LaunchedEffect(searchQuery) {
        searchSuggestions = if (searchQuery.length > 2) {
            delay(200)
            if (state.isNetworkAvailable) {
                getSearchSuggestions(searchQuery)
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

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
    
    var rippleOffset by remember { mutableStateOf<Offset?>(null) }
    val rippleScale = remember { Animatable(0f) }
    val rippleAlpha = remember { Animatable(0f) }

    var fromLocation by remember { mutableStateOf<Pair<String, LatLng>?>(null) }
    var toLocation by remember { mutableStateOf<Pair<String, LatLng>?>(null) }
    var showDirectionsPanel by remember { mutableStateOf(false) }
    var directionsDistance by remember { mutableStateOf<Double?>(null) }
    var lastZoomToastTime by remember { mutableLongStateOf(0L) }
    var isUserScaling by remember { mutableStateOf(false) }


    
    val lifecycleOwner = LifecycleOwnerCompose.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        onEndAction()
    }

    DisposableEffect(showUnlockPrompt) {
        if (showUnlockPrompt && state.isIntruderCaptureEnabled) {
            IntruderManager.getInstance(context).startSession(lifecycleOwner)
        }
        onDispose {
            if (showUnlockPrompt) {
                IntruderManager.getInstance(context).stopSession()
            }
        }
    }

    BackHandler(enabled = state.isLocked && (showDirectionsPanel || searchQuery.isNotEmpty() || showUnlockPrompt || showSetupDialog || showWeatherPanel)) {
        when {
            showUnlockPrompt -> showUnlockPrompt = false
            showSetupDialog -> showSetupDialog = false
            showWeatherPanel -> showWeatherPanel = false
            showDirectionsPanel -> {
                showDirectionsPanel = false
                fromLocation = null
                toLocation = null
            }
            searchQuery.isNotEmpty() -> {
                searchQuery = ""
                searchSuggestions = emptyList()
            }
        }
    }

    LaunchedEffect(Unit) {
        MapLibre.getInstance(context)
    }

    LaunchedEffect(currentStyleUrl) {
        mapLibreMap?.let { map ->
            map.setStyle(currentStyleUrl)
        }
    }

    val mapBlur by animateDpAsState(
        targetValue = 0.dp,
        animationSpec = tween(500),
        label = "MapBlur"
    )

    // Highlight line logic
    LaunchedEffect(fromLocation, toLocation, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (fromLocation != null && toLocation != null) {
            val from = fromLocation!!.second
            val to = toLocation!!.second

            val (routeLineString, distance) = getRouteData(from, to)
            
            // Draw Line
            val lineId = "directions-line"
            val sourceId = "directions-source"
            
            map.getStyle { style ->
                try {
                    val existingSource = style.getSource(sourceId) as? org.maplibre.android.style.sources.GeoJsonSource
                    if (existingSource == null) {
                        val newSource = if (routeLineString != null) {
                            org.maplibre.android.style.sources.GeoJsonSource(sourceId, routeLineString)
                        } else {
                            // Fallback if routing fails - create simple line from start to end
                            val points = listOf(Point.fromLngLat(from.longitude, from.latitude), Point.fromLngLat(to.longitude, to.latitude))
                            org.maplibre.android.style.sources.GeoJsonSource(sourceId, LineString.fromLngLats(points))
                        }
                        style.addSource(newSource)
                        style.addLayer(org.maplibre.android.style.layers.LineLayer(lineId, sourceId).apply {
                            setProperties(
                                org.maplibre.android.style.layers.PropertyFactory.lineColor(android.graphics.Color.rgb(0, 245, 255)), // Neon Cyan #00F5FF
                                org.maplibre.android.style.layers.PropertyFactory.lineWidth(6f),
                                org.maplibre.android.style.layers.PropertyFactory.lineCap(org.maplibre.android.style.layers.Property.LINE_CAP_ROUND),
                                org.maplibre.android.style.layers.PropertyFactory.lineJoin(org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND)
                            )
                        })
                    } else {
                        if (routeLineString != null) {
                            existingSource.setGeoJson(routeLineString)
                        } else {
                            val points = listOf(Point.fromLngLat(from.longitude, from.latitude), Point.fromLngLat(to.longitude, to.latitude))
                            existingSource.setGeoJson(LineString.fromLngLats(points))
                        }
                    }

                    if (from != to) {
                        val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                            .include(from)
                            .include(to)
                            .build()
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
                    } else {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(from, 15.0))
                    }
                    
                    directionsDistance = distance
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            map.getStyle { style ->
                if (style.getLayer("directions-line") != null) style.removeLayer("directions-line")
                if (style.getSource("directions-source") != null) style.removeSource("directions-source")
                directionsDistance = null
            }
        }
    }

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
                    if (mapLibreMap == null) {
                        MapSkeleton()
                    }
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
                                    map.uiSettings.isDoubleTapGesturesEnabled = false
                                    map.uiSettings.isTiltGesturesEnabled = true
                                    map.uiSettings.isRotateGesturesEnabled = true

                                    map.addOnCameraMoveListener {
                                        mapBearing = map.cameraPosition.bearing.toFloat()
                                        currentMapZoom = map.cameraPosition.zoom
                                        map.cameraPosition.target?.let { currentMapLat = it.latitude }

                                        // PREVENT EMPTY SPACE: Calculate min zoom dynamically based on screen height
                                        val viewHeight = this@apply.height.toDouble()
                                        if (viewHeight > 0) {
                                            val pitch = map.cameraPosition.tilt
                                            val baseMinZoom = Math.log(viewHeight / 256.0) / Math.log(2.0)
                                            val dynamicMinZoom = if (pitch > 10.0) baseMinZoom + (pitch / 45.0) else baseMinZoom
                                            
                                            // Use setMinZoomPreference to avoid fighting with animations (fixes location button glitch)
                                            if (map.minZoomLevel != dynamicMinZoom) {
                                                map.setMinZoomPreference(dynamicMinZoom)
                                            }

                                            // Show "Highest peak" message when user hits the limit manually with two-finger zoom
                                            if (isUserScaling && map.cameraPosition.zoom <= dynamicMinZoom + 0.1 && !isCenteredOnUser) {
                                                val now = System.currentTimeMillis()
                                                // Debounce toast to every 3 seconds
                                                if (now - lastZoomToastTime > 3000) {
                                                    android.widget.Toast.makeText(ctx, "Wooho! Highest peak", android.widget.Toast.LENGTH_SHORT).show()
                                                    lastZoomToastTime = now
                                                }
                                            }
                                        }
                                    }

                                    map.addOnScaleListener(object : org.maplibre.android.maps.MapLibreMap.OnScaleListener {
                                        override fun onScaleBegin(detector: org.maplibre.android.gestures.StandardScaleGestureDetector) {
                                            isUserScaling = true
                                        }
                                        override fun onScale(detector: org.maplibre.android.gestures.StandardScaleGestureDetector) {
                                        }
                                        override fun onScaleEnd(detector: org.maplibre.android.gestures.StandardScaleGestureDetector) {
                                            isUserScaling = false
                                        }
                                    })

                                    map.addOnCameraIdleListener {
                                        if (map.cameraPosition.zoom >= 14.0) { // Approx 2km scale
                                            map.cameraPosition.target?.let { target ->
                                                onFetchWeather(target.latitude, target.longitude)
                                            }
                                        }
                                    }

                                    val gestureDetector = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
                                        override fun onDown(e: android.view.MotionEvent): Boolean {
                                            return true
                                        }

                                        override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                                            val point = map.projection.fromScreenLocation(android.graphics.PointF(e.x, e.y))
                                            val vault = currentVaults.find { v ->
                                                val dist = LocationHelper.calculateDistance(point.latitude, point.longitude, v.location.latitude, v.location.longitude)
                                                dist <= 1000f // 1km radius as requested
                                            }
                                            if (vault != null) {
                                                selectedVaultForUnlock = vault
                                                showUnlockPrompt = true
                                                return true
                                            } else {
                                                if (map.cameraPosition.zoom < 16.0) {
                                                    android.widget.Toast.makeText(ctx, "Zoom in closer (100m scale)", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                                return false
                                            }
                                        }

                                        override fun onLongPress(e: android.view.MotionEvent) {
                                            if (currentVaults.size >= 2) {
                                                android.widget.Toast.makeText(ctx, "Maximum 2 zones allowed", android.widget.Toast.LENGTH_SHORT).show()
                                                return
                                            }
                                            if (map.cameraPosition.zoom < 16.0) {
                                                android.widget.Toast.makeText(ctx, "Zoom in closer to set safe zone", android.widget.Toast.LENGTH_SHORT).show()
                                                return
                                            }
                                            val point = map.projection.fromScreenLocation(android.graphics.PointF(e.x, e.y))
                                            
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
                                                isNativeEligible = state.currentLocation?.let { live ->
                                                    LocationHelper.calculateDistance(point.latitude, point.longitude, live.latitude, live.longitude) <= 1000f
                                                } ?: false
                                                showSetupDialog = true
                                            }
                                        }
                                    })

                                    setOnTouchListener { v, event ->
                                        gestureDetector.onTouchEvent(event)
                                        if (event.action == android.view.MotionEvent.ACTION_UP) {
                                            v.performClick()
                                        }
                                        false
                                    }

                                    map.setStyle(currentStyleUrl) { style ->
                                        // Always apply official boundaries for compliance and consistency
                                        MapStyleHelper.applyIndiaBoundaries(ctx, style)

                                        try {
                                            val locationComponent = map.locationComponent
                                            locationComponent.activateLocationComponent(
                                                LocationComponentActivationOptions.builder(ctx, style)
                                                    .locationComponentOptions(
                                                        org.maplibre.android.location.LocationComponentOptions.builder(ctx)
                                                            .compassAnimationEnabled(true)
                                                            .accuracyAlpha(0.2f)
                                                            .build()
                                                    )
                                                    .build()
                                            )
                                            if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                locationComponent.isLocationComponentEnabled = true
                                            }
                                            
                                            val cities = listOf(
                                                LatLng(19.0760, 72.8777),  // Mumbai
                                                LatLng(28.6139, 77.2090),  // Delhi
                                                LatLng(12.9716, 77.5946),  // Bangalore
                                                LatLng(48.8566, 2.3522),   // Paris
                                                LatLng(40.7128, -74.0060)  // New York
                                            )
                                            val randomCity = cities.random()
                                            // Start at a comfortable global level that fills most screens
                                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(randomCity, 4.0))
                                            
                                            // Quickly "warm up" surrounding tiles by adjusting camera slightly
                                            map.animateCamera(CameraUpdateFactory.zoomTo(5.0), 1500)
                                        } catch (e: Exception) {}
                                    }
                                }
                            }
                        }
                    )

                    if (state.isLocked && currentMapZoom > 0) {
                        MapScaleBar(
                            zoom = currentMapZoom,
                            latitude = currentMapLat,
                            modifier = Modifier.align(Alignment.BottomStart)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 24.dp, start = 16.dp, end = 16.dp)
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
                            isDark = false,
                            mapBearing = mapBearing,
                            onCompassClick = {
                                HapticHelper.vibrate(context, 1)
                                if (isCenteredOnUser) {
                                    mapLibreMap?.animateCamera(CameraUpdateFactory.bearingTo(0.0))
                                } else {
                                    isCenteredOnUser = true
                                    mapLibreMap?.locationComponent?.cameraMode = org.maplibre.android.location.modes.CameraMode.TRACKING_COMPASS
                                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                        location?.let {
                                            mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 18.0), 1000)
                                        }
                                    }
                                }
                            }
                        )
                        
                        if (searchSuggestions.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDark) CyberDarkBlue else Color.White,
                                shadowElevation = 8.dp
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
                                            color = if (isDark) Color.White else LightTextPrimary
                                        )
                                        HorizontalDivider(color = (if (isDark) Color.White else CyberBlue).copy(alpha = 0.05f))
                                    }
                                }
                            }
                        }
                    }

                    if (showOfflineDialog) {
                        AlertDialog(
                            onDismissRequest = { showOfflineDialog = false },
                            title = { Text(stringResource(R.string.support_links), fontWeight = FontWeight.Black) },
                            text = { Text("Turn on data to search.", fontWeight = FontWeight.Bold) },
                            confirmButton = {
                                Button(onClick = {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS))
                                    showOfflineDialog = false
                                }) { Text("Settings") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showOfflineDialog = false }) { Text(stringResource(R.string.cancel)) }
                            }
                        )
                    }

                    rippleOffset?.let { offset ->
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = CyberBlue.copy(alpha = rippleAlpha.value),
                                radius = 40.dp.toPx() * rippleScale.value,
                                center = offset
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = state.isLocked,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(end = 16.dp, bottom = 30.dp)
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            SmallMapFab(icon = Icons.Default.Add, active = false) {
                                HapticHelper.vibrate(context, 1)
                                mapLibreMap?.animateCamera(CameraUpdateFactory.zoomIn())
                            }

                            SmallMapFab(icon = Icons.Default.Remove, active = false) {
                                HapticHelper.vibrate(context, 1)
                                mapLibreMap?.animateCamera(CameraUpdateFactory.zoomOut())
                            }

                            SmallMapFab(
                                icon = Icons.Default.MyLocation,
                                active = isCenteredOnUser,
                                activeColor = Color(0xFF0368E8)
                            ) {
                                if (!isCenteredOnUser) {
                                    HapticHelper.vibrate(context, 1)
                                    isCenteredOnUser = true
                                    mapLibreMap?.locationComponent?.cameraMode = org.maplibre.android.location.modes.CameraMode.TRACKING_COMPASS
                                }
                                if (state.hasLocationPermission) {
                                    onRequestGps {
                                        try {
                                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                                location?.let {
                                                    mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 18.0), 1500)
                                                }
                                            }
                                        } catch (e: SecurityException) {}
                                    }
                                } else {
                                    onStartAction()
                                    locationPermissionLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                                }
                            }

                            SmallMapFab(icon = Icons.Default.MoreVert, active = showWeatherPanel) {
                                HapticHelper.vibrate(context, 1)
                                showWeatherPanel = true
                            }
                        }
                    }
                    
                    if (showUnlockPrompt && selectedVaultForUnlock != null) {
                        VaultUnlockDialog(
                            vault = selectedVaultForUnlock!!,
                            isDark = isDark,
                            isIntruderCaptureEnabled = state.isIntruderCaptureEnabled,
                            onDismiss = { showUnlockPrompt = false },
                            onConfirm = { secret ->
                                onUnlockAttempt(selectedVaultForUnlock!!.location.latitude, selectedVaultForUnlock!!.location.longitude, secret)
                                showUnlockPrompt = false
                            },
                            onIntruderCaptured = onIntruderCaptured
                        )
                    }

                    if (showSetupDialog && setupLatLng != null) {
                        VaultSetupDialog(
                            isNativeEligible = isNativeEligible,
                            isDark = isDark,
                            onDismiss = { showSetupDialog = false },
                            onConfirm = { secret, selectedApps, lockType, radius ->
                                onSaveConfig(GeoPoint(setupLatLng!!.latitude, setupLatLng!!.longitude), secret, selectedApps, lockType, radius)
                                showSetupDialog = false
                            }
                        )
                    }


                    // Directions Panel (Slide up)
                    AnimatedVisibility(
                        visible = showDirectionsPanel,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        DirectionsPanel(
                            from = fromLocation,
                            to = toLocation,
                            distance = directionsDistance,
                            isDark = isDark,
                            onFromChange = { fromLocation = it },
                            onToChange = { toLocation = it },
                            onSwap = {
                                val temp = fromLocation
                                fromLocation = toLocation
                                toLocation = temp
                            },
                            onClose = { showDirectionsPanel = false }
                        )
                    }

                    // Weather Panel (Slide up)
                    AnimatedVisibility(
                        visible = showWeatherPanel,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        WeatherPanel(
                            weatherInfo = state.weatherInfo,
                            isDark = isDark,
                            onDismiss = { showWeatherPanel = false }
                        )
                    }
                }
            } else {
                VaultContentScreen(
                    state = state,
                    onLockClick = onLockClick,
                    onAppClick = onAppClick,
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
                    onGrantBackgroundPopups = onGrantBackgroundPopups,
                    onDeleteFile = onDeleteFile,
                    onRestoreFile = onRestoreFile,
                    onRemoveAppFromVault = onRemoveAppFromVault,
                    onToggleDarkMode = onToggleDarkMode,
                    onToggleFingerprint = onToggleFingerprint,
                    onSetLanguage = onSetLanguage,
                    onCompleteTour = onCompleteTour,
                    onToggleScreenshotRestriction = onToggleScreenshotRestriction,
                    onToggleIntruderCapture = onToggleIntruderCapture,
                    onToggleUninstallShield = onToggleUninstallShield,
                    onRestoreAndUninstall = onRestoreAndUninstall,
                    onFetchGalleryItems = onFetchGalleryItems,
                    onCreateFolder = onCreateFolder,
                    onDeleteFolder = onDeleteFolder,
                    onBulkDelete = onBulkDelete,
                    onBulkRestore = onBulkRestore,
                    onAddFilesToFolder = onAddFilesToFolder,
                    onSetCustomBackground = onSetCustomBackground,
                    onStartAction = onStartAction,
                    onEndAction = onEndAction
                )
            }
        }

        if (state.showBackgroundPopupGuide) {
            BackgroundPopupGuideDialog(onDismiss = { onGrantBackgroundPopups() })
        }
    }
}

@Composable
fun MapSkeleton() {
    val transition = rememberInfiniteTransition(label = "map_skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFEEEEEE))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 60.dp.toPx()
            for (x in 0..size.width.toInt() step gridSpacing.toInt()) {
                drawLine(Color.LightGray.copy(alpha = alpha), Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), strokeWidth = 1.dp.toPx())
            }
            for (y in 0..size.height.toInt() step gridSpacing.toInt()) {
                drawLine(Color.LightGray.copy(alpha = alpha), Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), strokeWidth = 1.dp.toPx())
            }
        }
    }
}

@Composable
fun VaultUnlockDialog(
    vault: com.geovault.model.VaultConfig,
    isDark: Boolean,
    isIntruderCaptureEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onIntruderCaptured: (android.net.Uri, String?) -> Unit
) {
    val context = LocalContext.current
    var failedAttempts by remember { mutableIntStateOf(0) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CreamWhite, // Always light theme for the unlock prompt
            shadowElevation = 8.dp,
            modifier = Modifier.width(300.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (vault.lockType == LockType.PIN) stringResource(R.string.enter_pin) else stringResource(R.string.draw_pattern),
                    color = CyberBlue,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(20.dp))
                if (vault.lockType == LockType.PIN) {
                    CompactPinPad(
                        correctPin = vault.secret, 
                        isLightTheme = true, // Always light theme
                        onPinComplete = {
                            failedAttempts = 0
                            onConfirm(it)
                        },
                        onError = {
                            failedAttempts++
                            if (failedAttempts >= 3 && isIntruderCaptureEnabled) {
                                IntruderManager.getInstance(context).captureIntruder(onIntruderCaptured)
                            }
                        }
                    )
                } else {
                    CompactPatternGrid(
                        correctPattern = vault.secret, 
                        isLightTheme = true, // Always light theme
                        onPatternComplete = {
                            failedAttempts = 0
                            onConfirm(it)
                        },
                        onError = {
                            failedAttempts++
                            if (failedAttempts >= 3 && isIntruderCaptureEnabled) {
                                IntruderManager.getInstance(context).captureIntruder(onIntruderCaptured)
                            }
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Color.Gray) }
            }
        }
    }
}

@Composable
fun VaultSetupDialog(
    isNativeEligible: Boolean,
    isDark: Boolean,
    onDismiss: () -> Unit, 
    onConfirm: (String, Set<String>, LockType, Float) -> Unit
) {
    var lockType by remember { mutableStateOf(LockType.PIN) }
    var isNativeEnabled by remember { mutableStateOf(false) }
    var radius by remember { mutableFloatStateOf(500f) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = CreamWhite, // Always light theme for setup
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.setup_lock), style = MaterialTheme.typography.titleLarge, color = CyberBlue, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                if (isNativeEligible) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CyberBlue.copy(alpha = 0.05f)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Native Mode", fontWeight = FontWeight.Bold, color = CyberBlue, modifier = Modifier.weight(1f))
                        Switch(checked = isNativeEnabled, onCheckedChange = { isNativeEnabled = it })
                    }
                    if (isNativeEnabled) {
                        Slider(value = radius, onValueChange = { radius = it }, valueRange = 100f..2000f, steps = 19)
                        Text("${radius.toInt()}m", color = CyberBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultLockTypeButton(stringResource(R.string.lock_type_pin), lockType == LockType.PIN, false) { lockType = LockType.PIN }
                    VaultLockTypeButton(stringResource(R.string.lock_type_pattern), lockType == LockType.PATTERN, false) { lockType = LockType.PATTERN }
                }
                Spacer(Modifier.height(20.dp))
                if (lockType == LockType.PIN) {
                    CompactPinPad(isLightTheme = true, autoConfirm = false, onPinComplete = { onConfirm(it, emptySet(), lockType, if (isNativeEnabled) radius else 0f) })
                } else {
                    CompactPatternGrid(isLightTheme = true, showConfirmButton = true, onPatternComplete = { onConfirm(it, emptySet(), lockType, if (isNativeEnabled) radius else 0f) })
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Color.Gray) }
            }
        }
    }
}

@Composable
fun RowScope.VaultLockTypeButton(text: String, selected: Boolean, isDark: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) CyberBlue.copy(alpha = 0.15f) else Color.Transparent, contentColor = if (selected) CyberBlue else Color.Gray),
        modifier = Modifier.height(40.dp).weight(1f),
        shape = RoundedCornerShape(12.dp),
        elevation = null // Fix square shadow
    ) { Text(text, fontSize = 12.sp, fontWeight = FontWeight.Black) }
}

@Composable
fun SmallMapFab(
    icon: ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    isDark: Boolean = false,
    activeColor: Color = Color.Black,
    onClick: () -> Unit
) {
    val containerColor = if (active) activeColor else (if (isDark) CyberDarkBlue else Color.White)
    val contentColor = if (containerColor == Color.White) Color.Black else Color.White
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 6.dp
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(20.dp)) }
    }
}

suspend fun getRouteData(from: LatLng, to: LatLng): Pair<LineString?, Double> = withContext(Dispatchers.IO) {
    try {
        // OSRM wants Longitude first: Lng,Lat
        val url = URL("https://router.project-osrm.org/route/v1/driving/${from.longitude},${from.latitude};${to.longitude},${to.latitude}?overview=full&geometries=geojson")
        val connection = url.openConnection()
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        val response = connection.getInputStream().bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        val routes = json.getJSONArray("routes")
        
        if (routes.length() > 0) {
            val route = routes.getJSONObject(0)
            val distance = route.getDouble("distance")
            val geometry = route.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")
            
            val pointsList = mutableListOf<Point>()
            for (i in 0 until coordinates.length()) {
                val coordPair = coordinates.getJSONArray(i)
                val lng = coordPair.getDouble(0)
                val lat = coordPair.getDouble(1)
                pointsList.add(Point.fromLngLat(lng, lat))
            }
            
            LineString.fromLngLats(pointsList) to distance
        } else null to LocationHelper.calculateDistance(from.latitude, from.longitude, to.latitude, to.longitude).toDouble()
    } catch (e: Exception) {
        e.printStackTrace()
        null to LocationHelper.calculateDistance(from.latitude, from.longitude, to.latitude, to.longitude).toDouble()
    }
}

suspend fun searchLocation(query: String): LatLng? = getSearchSuggestions(query).firstOrNull()?.second

suspend fun getSearchSuggestions(query: String): List<Pair<String, LatLng>> = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://nominatim.openstreetmap.org/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&format=json&limit=5&addressdetails=0")
        val connection = url.openConnection()
        connection.setRequestProperty("User-Agent", "MapLock-App")
        val response = connection.getInputStream().bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(response)
        val suggestions = mutableListOf<Pair<String, LatLng>>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            suggestions.add(item.optString("display_name") to LatLng(item.optDouble("lat"), item.optDouble("lon")))
        }
        suggestions
    } catch (e: Exception) { emptyList() }
}

@Composable
fun DirectionsPanel(
    from: Pair<String, LatLng>?,
    to: Pair<String, LatLng>?,
    distance: Double?,
    isDark: Boolean,
    onFromChange: (Pair<String, LatLng>?) -> Unit,
    onToChange: (Pair<String, LatLng>?) -> Unit,
    onSwap: () -> Unit,
    onClose: () -> Unit
) {
    var fromQuery by remember { mutableStateOf(from?.first ?: "") }
    var toQuery by remember { mutableStateOf(to?.first ?: "") }
    var fromSuggestions by remember { mutableStateOf<List<Pair<String, LatLng>>>(emptyList()) }
    var toSuggestions by remember { mutableStateOf<List<Pair<String, LatLng>>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(fromQuery) {
        if (fromQuery.length > 2 && fromQuery != from?.first) {
            delay(300)
            fromSuggestions = getSearchSuggestions(fromQuery)
        } else {
            fromSuggestions = emptyList()
        }
    }

    LaunchedEffect(toQuery) {
        if (toQuery.length > 2 && toQuery != to?.first) {
            delay(300)
            toSuggestions = getSearchSuggestions(toQuery)
        } else {
            toSuggestions = emptyList()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 380.dp),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = if (isDark) CyberDarkBlue else CreamWhite,
        shadowElevation = 24.dp
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 12.dp, start = 24.dp, end = 24.dp, bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.2f))
                    .align(Alignment.CenterHorizontally)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            if (dragAmount > 15f) {
                                change.consume()
                                onClose()
                            }
                        }
                    }
            )
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Directions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = CyberBlue,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, null, tint = Color.Gray)
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    DirectionSearchBox(
                        query = fromQuery,
                        onQueryChange = { fromQuery = it },
                        placeholder = "From",
                        suggestions = fromSuggestions,
                        onSuggestionClick = {
                            fromQuery = it.first
                            onFromChange(it)
                            fromSuggestions = emptyList()
                        },
                        isDark = isDark
                    )
                    Spacer(Modifier.height(12.dp))
                    DirectionSearchBox(
                        query = toQuery,
                        onQueryChange = { toQuery = it },
                        placeholder = "To",
                        suggestions = toSuggestions,
                        onSuggestionClick = {
                            toQuery = it.first
                            onToChange(it)
                            toSuggestions = emptyList()
                        },
                        isDark = isDark
                    )
                }
                
                Spacer(Modifier.width(12.dp))
                
                IconButton(
                    onClick = {
                        val temp = fromQuery
                        fromQuery = toQuery
                        toQuery = temp
                        onSwap()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(CyberBlue.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(Icons.Default.SwapVert, null, tint = CyberBlue)
                }
            }

            if (distance != null) {
                Spacer(Modifier.height(20.dp))
                Surface(
                    color = CyberBlue.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Route, null, tint = CyberBlue)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (distance >= 1000) String.format("%.2f km", distance / 1000) else String.format("%.0f m", distance),
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = CyberBlue
                        )
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = onClose,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("GO", fontWeight = FontWeight.Black)
                        }
                    }
                }
            } else if (fromQuery.length > 2 && toQuery.length > 2) {
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val f = searchLocation(fromQuery)
                            val t = searchLocation(toQuery)
                            if (f != null && t != null) {
                                onFromChange(fromQuery to f)
                                onToChange(toQuery to t)
                            } else {
                                Toast.makeText(context, "Location not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("GO", fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
fun DirectionSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    suggestions: List<Pair<String, LatLng>>,
    onSuggestionClick: (Pair<String, LatLng>) -> Unit,
    isDark: Boolean
) {
    Column {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(placeholder, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = (if (isDark) Color.White else CyberBlue).copy(alpha = 0.05f),
                unfocusedContainerColor = (if (isDark) Color.White else CyberBlue).copy(alpha = 0.05f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = if (isDark) Color.White else LightTextPrimary,
                unfocusedTextColor = if (isDark) Color.White else LightTextPrimary
            ),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
        )
        if (suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isDark) CyberDarkBlue else Color.White,
                shadowElevation = 4.dp,
                shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
            ) {
                Column {
                    suggestions.take(3).forEach { suggestion ->
                        Text(
                            text = suggestion.first,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSuggestionClick(suggestion) }
                                .padding(12.dp),
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = if (isDark) Color.White else CyberBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherPanel(
    weatherInfo: com.geovault.model.WeatherInfo,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = if (isDark) CyberDarkBlue else Color.White,
        shadowElevation = 24.dp
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.2f))
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            if (dragAmount > 15f) {
                                change.consume()
                                onDismiss()
                            }
                        }
                    }
            )
            
            Spacer(Modifier.height(20.dp))
            
            Text(
                weatherInfo.cityName ?: "Detecting City...",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else LightTextPrimary
            )
            
            Spacer(Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WeatherInfoCard(
                    label = "AQI",
                    value = weatherInfo.aqi?.toString() ?: "--",
                    lottieUrl = "https://lottie.host/8024227b-2321-423c-9a3d-4c3d82a39a7b/AirIcon.json",
                    color = CyberBlue,
                    modifier = Modifier.weight(1f)
                )
                
                WeatherInfoCard(
                    label = "Temp",
                    value = if (weatherInfo.temperature != null) "${weatherInfo.temperature?.toInt()}°" else "--",
                    lottieUrl = "https://lottie.host/7e0e7a2b-8a2b-4e8a-8a2b-4e8a8a2b4e8a/TempIcon.json",
                    color = IconOrange,
                    modifier = Modifier.weight(1f)
                )
                
                WeatherInfoCard(
                    label = "Humidity",
                    value = if (weatherInfo.humidity != null) "${weatherInfo.humidity}%" else "--",
                    lottieUrl = "https://lottie.host/9f0e7a2b-8a2b-4e8a-8a2b-4e8a8a2b4e8a/HumidityIcon.json",
                    color = IconBlue,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(Modifier.height(24.dp))

            TextButton(onClick = onDismiss) {
                Text("CLOSE", fontWeight = FontWeight.Bold, color = Color.Gray)
            }
        }
    }
}

@Composable
fun WeatherInfoCard(
    label: String,
    value: String,
    lottieUrl: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Url(lottieUrl))
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (composition != null) {
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier.size(24.dp)
                )
            } else {
                val infiniteTransition = rememberInfiniteTransition(label = "icon_anim")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )

                Icon(
                    imageVector = when (label) {
                        "AQI" -> Icons.Default.Air
                        "Temp" -> Icons.Default.DeviceThermostat
                        "Humidity" -> Icons.Default.WaterDrop
                        else -> Icons.Default.Air
                    },
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp).graphicsLayer(scaleX = scale, scaleY = scale)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, color = color)
            Text(label, fontSize = 10.sp, color = color.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MapScaleBar(zoom: Double, latitude: Double, modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val metersPerPixel = (Math.cos(latitude * Math.PI / 180) * 2 * Math.PI * 6378137) / (256 * Math.pow(2.0, zoom))
    val maxBarWidthPx = with(density) { 100.dp.toPx() }
    val maxMeters = maxBarWidthPx * metersPerPixel
    val niceDistances = listOf(
        1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 
        1000.0, 2000.0, 5000.0, 10000.0, 20000.0, 50000.0, 100000.0,
        200000.0, 500000.0, 1000000.0, 2000000.0, 5000000.0, 10000000.0
    )
    val displayMeters = niceDistances.lastOrNull { it <= maxMeters } ?: 1.0
    val barWidthDp = with(density) { (displayMeters / metersPerPixel).toFloat().toDp() }
    val label = if (displayMeters >= 1000) "${(displayMeters / 1000).toInt()} km" else "${displayMeters.toInt()} m"

    Column(modifier = modifier.padding(start = 16.dp, bottom = 64.dp)) {
        Text(label, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Box(modifier = Modifier.width(barWidthDp).height(2.dp).background(Color.Black))
    }
}

@Composable
fun MapSearchBar(
    query: String, 
    onQueryChange: (String) -> Unit, 
    onSearch: (String) -> Unit, 
    isDark: Boolean,
    mapBearing: Float = 0f,
    onCompassClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.weight(1f).height(60.dp), 
            shape = RoundedCornerShape(30.dp), 
            color = if (isDark) CyberDarkBlue else Color.White, 
            shadowElevation = 8.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 24.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.removed_background_18),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp)
                )
                Spacer(Modifier.width(8.dp))

                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.weight(1f)) {
                    // Initial Search Icon (Visible when not focused and empty)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isFocused && query.isEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.search_places), color = Color.Gray, fontWeight = FontWeight.Medium)
                        }
                    }

                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
                        textStyle = androidx.compose.ui.text.TextStyle(color = if (isDark) Color.White else LightTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        singleLine = true,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(CyberBlue),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch(query); keyboardController?.hide(); focusManager.clearFocus() })
                    )
                }
                
                if (query.isNotEmpty() || isFocused) {
                    IconButton(onClick = { 
                        if (query.isNotEmpty()) {
                            onQueryChange("")
                        } else {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    }) { 
                        Icon(Icons.Default.Close, null, tint = Color.Gray) 
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        SmallMapFab(icon = Icons.Default.Explore, active = false, isDark = isDark, modifier = Modifier.rotate(-mapBearing)) { onCompassClick() }
    }
}
