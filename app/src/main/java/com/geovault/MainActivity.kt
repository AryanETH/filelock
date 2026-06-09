package com.geovault

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import android.Manifest
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import com.geovault.ui.VaultScreen
import com.geovault.ui.VaultViewModel
import com.geovault.ui.OnboardingScreen
import com.geovault.ui.PermissionScreen
import com.geovault.ui.IntroScreen
import com.geovault.ui.LanguageOnboardingScreen
import com.geovault.ui.BackgroundPopupGuideDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.statusBarsPadding
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.geovault.ui.theme.GeoVaultTheme
import com.geovault.ui.theme.CyberBlue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import android.content.Context
import android.content.IntentSender
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import android.content.Intent
import android.content.pm.PackageManager
import org.maplibre.android.MapLibre

import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.edit

import androidx.appcompat.app.AppCompatActivity
import com.geovault.security.SecurityUtils
import com.geovault.security.LocaleManager
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

import androidx.compose.runtime.saveable.rememberSaveable

class MainActivity : AppCompatActivity() {
    private val viewModel: VaultViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleManager.getLanguage(newBase)
        super.attachBaseContext(LocaleManager.getLocaleContext(newBase, lang))
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Root Detection
        if (SecurityUtils.isDeviceRooted()) {
            Toast.makeText(this, "Security Alert: Rooted device detected.", Toast.LENGTH_LONG).show()
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        try {
            MapLibre.getInstance(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            var showSplash by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                // showSplash = false // Removed initial custom splash delay to use System Splash only
            }

            LaunchedEffect(uiState.isScreenshotRestricted) {
                if (uiState.isScreenshotRestricted) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            LaunchedEffect(Unit) {
                viewModel.recreateEvent.collect {
                    recreate()
                }
            }

            LaunchedEffect(uiState.isLocked) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                // Always show system bars to satisfy user request
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                
                if (uiState.isLocked) {
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                }
            }

            GeoVaultTheme(darkTheme = uiState.isDarkMode) {
                val context = LocalContext.current

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    viewModel.setPerformingAction(false)
                    viewModel.checkPermissions()
                    
                    if (results[Manifest.permission.ACCESS_FINE_LOCATION] == false) {
                        Toast.makeText(this@MainActivity, "Location is required for Map Security", Toast.LENGTH_LONG).show()
                    }

                    if (results[Manifest.permission.CAMERA] == true) {
                        viewModel.toggleIntruderCapture(true)
                    }
                }

                val resolutionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        viewModel.checkPermissions()
                    }
                }

                val deleteLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartIntentSenderForResult()
                ) { _ ->
                    viewModel.setPerformingAction(false)
                    viewModel.clearPendingDelete()
                }

                LaunchedEffect(uiState.pendingDeleteIntent) {
                    uiState.pendingDeleteIntent?.let {
                        val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(it.intentSender).build()
                        viewModel.setPerformingAction(true)
                        deleteLauncher.launch(intentSenderRequest)
                    }
                }

                LaunchedEffect(Unit) {
                    // Contextual permissions: do not ask primarily
                }

                LaunchedEffect(Unit) {
                    viewModel.checkPermissions()
                    val serviceIntent = Intent(context, com.geovault.service.AppLockerService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }

                LaunchedEffect(uiState.vaults) {
                    if (uiState.vaults.isEmpty()) return@LaunchedEffect
                    
                    val locationCallback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            result.lastLocation?.let {
                                viewModel.onLocationChanged(it.latitude, it.longitude)
                            }
                        }
                    }

                    // Attempt GMS Location first
                    try {
                        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15000)
                            .setMinUpdateIntervalMillis(10000)
                            .build()
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
                        }
                    } catch (_: Exception) {
                        // Fallback to standard LocationManager for Huawei/Non-GMS devices
                        val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
                        val listener = object : android.location.LocationListener {
                            override fun onLocationChanged(l: android.location.Location) {
                                viewModel.onLocationChanged(l.latitude, l.longitude)
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                            override fun onProviderEnabled(p: String) {}
                            override fun onProviderDisabled(p: String) {}
                        }
                        try {
                            locationManager.requestLocationUpdates(
                                android.location.LocationManager.GPS_PROVIDER,
                                15000L, 500f, listener
                            )
                            locationManager.requestLocationUpdates(
                                android.location.LocationManager.NETWORK_PROVIDER,
                                15000L, 500f, listener
                            )
                        } catch (_: SecurityException) {}
                    }
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnimatedContent(
                        targetState = when {
                            showSplash -> "intro"
                            uiState.isFirstRun && !uiState.isLanguageSelected -> "language_selection"
                            uiState.isFirstRun && !uiState.hasLocationPermission -> "onboarding_location"
                            uiState.isFirstRun || !uiState.disclaimerAccepted -> "onboarding"

                            uiState.isMapDownloading -> "downloading"

                            else -> "vault"
                        },
                        transitionSpec = {
                            val duration = 700
                            if (targetState == "vault" || initialState == "intro") {
                                fadeIn(animationSpec = tween(duration)).togetherWith(fadeOut(animationSpec = tween(duration)))
                            } else {
                                (fadeIn(animationSpec = tween(duration)) + slideInHorizontally(animationSpec = tween(duration), initialOffsetX = { 100 }))
                                    .togetherWith(fadeOut(animationSpec = tween(duration / 2)))
                            }
                        },
                        label = "ScreenTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { target ->
                        when (target) {
                            "intro" -> IntroScreen()
                            "language_selection" -> {
                                LanguageOnboardingScreen(
                                    onLanguageSelected = { viewModel.setLanguage(it) },
                                    onBack = null
                                )
                            }
                            "onboarding_location" -> {
                                OnboardingLocationGrant(
                                    onGranted = { viewModel.checkPermissions() },
                                    onBack = { viewModel.resetLanguageSelection() },
                                    onStartAction = { viewModel.setPerformingAction(true) },
                                    onEndAction = { viewModel.setPerformingAction(false) }
                                )
                            }
                            "onboarding" -> {
                                OnboardingScreen(
                                    onFinished = { viewModel.completeOnboarding() },
                                    onBack = { viewModel.resetLanguageSelection() },
                                    onStartAction = { viewModel.setPerformingAction(true) },
                                    onEndAction = { viewModel.setPerformingAction(false) }
                                )
                            }
                            "permissions" -> {
                                PermissionScreen(
                                    state = uiState,
                                    onGrantUsage = { viewModel.openUsageStatsSettings() },
                                    onGrantOverlay = { viewModel.openOverlaySettings() },
                                    onGrantBackgroundPopups = { viewModel.setShowBackgroundPopupGuide(true) },
                                    onGrantLocation = {
                                        viewModel.setPerformingAction(true)
                                        checkLocationSettings(this@MainActivity, resolutionLauncher) {
                                            if (!uiState.hasLocationPermission) {
                                                permissionLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                                    )
                                                )
                                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !uiState.hasBackgroundLocationPermission) {
                                                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                                            }
                                        }
                                    },
                                    onGrantBattery = {
                                        viewModel.openProtectedAppsSettings()
                                    },
                                    onGrantCamera = {
                                        viewModel.setPerformingAction(true)
                                        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                                    },
                                    onGrantStorage = {
                                        viewModel.setPerformingAction(true)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))
                                        } else {
                                            permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                                        }
                                    },
                                    onGrantFullStorage = {
                                        viewModel.openFullStorageSettings()
                                    }
                                )
                            }
                            "downloading" -> {
                                StartAnimationScreen()
                            }
                            "vault" -> {
                                VaultScreen(
                                    state = uiState,
                                    onUnlockAttempt = { lat, lon, pin -> 
                                        viewModel.attemptUnlockAtLocation(lat, lon, pin)
                                    },
                                    onIntruderCaptured = { uri, thumb ->
                                        viewModel.addIntruderFile(uri, thumb)
                                    },
                                    onSaveConfig = { point, secret, apps, lockType, radius ->
                                        viewModel.saveVaultConfiguration(point, secret, apps, lockType, radius)
                                    },
                                    onLockClick = { viewModel.lock() },
                                    onAppClick = { packageName -> viewModel.launchApp(packageName) },
                                    onOpenUsageSettings = { 
                                        viewModel.setPerformingAction(true)
                                        viewModel.openUsageStatsSettings() 
                                    },
                                    onOpenOverlaySettings = { 
                                        viewModel.setPerformingAction(true)
                                        viewModel.openOverlaySettings() 
                                    },
                                    onOpenProtectedApps = { 
                                        viewModel.setPerformingAction(true)
                                        viewModel.openProtectedAppsSettings() 
                                    },
                                    onToggleMasterStealth = { viewModel.toggleMasterStealth() },
                                    onAddFiles = { uris, category -> viewModel.addFilesToVault(uris, category) },
                                    onToggleAppLock = { packageName -> 
                                        if (packageName.isEmpty()) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                            }
                                        } else {
                                            viewModel.toggleAppLock(packageName)
                                        }
                                    },
                                    onRemoveVault = { id -> viewModel.removeVault(id) },
                                    onClearAllVaults = { viewModel.clearAllVaults() },
                                    onGrantCamera = {
                                        viewModel.setPerformingAction(true)
                                        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                                    },
                                    onGrantStorage = {
                                        viewModel.setPerformingAction(true)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))
                                        } else {
                                            permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                                        }
                                    },
                                    onGrantFullStorage = {
                                        viewModel.setPerformingAction(true)
                                        viewModel.openFullStorageSettings()
                                    },
                                    onGrantBackgroundPopups = { 
                                        viewModel.setPerformingAction(true)
                                        viewModel.setShowBackgroundPopupGuide(true) 
                                    },
                                    onDeleteFile = { fileId -> viewModel.removeFileFromVault(fileId) },
                                    onRestoreFile = { fileId -> viewModel.restoreFileToGallery(fileId) },
                                    onRemoveAppFromVault = { vaultId, pkg -> viewModel.removeAppFromSpecificVault(vaultId, pkg) },
                                    onFetchGalleryItems = { cat -> viewModel.fetchGalleryItems(cat) },
                                    onToggleDarkMode = { viewModel.toggleDarkMode() },
                                    onToggleFingerprint = { viewModel.toggleFingerprint() },
                                    onSetLanguage = { lang -> viewModel.setLanguage(lang) },
                                    onCompleteTour = { viewModel.completeTour() },
                                    onToggleScreenshotRestriction = { viewModel.toggleScreenshotRestriction() },
                                    onToggleIntruderCapture = { enabled ->
                                        if (enabled && !uiState.hasCameraPermission) {
                                            viewModel.setPerformingAction(true)
                                            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                                        } else {
                                            viewModel.toggleIntruderCapture(enabled)
                                        }
                                    },
                                    onCreateFolder = { viewModel.createFolder(it) },
                                    onDeleteFolder = { name, recover -> viewModel.deleteFolder(name, recover) },
                                    onBulkDelete = { ids, isPermanent -> viewModel.bulkDeleteFiles(ids, isPermanent) },
                                    onBulkRestore = { ids -> viewModel.bulkRestoreFiles(ids) },
                                    onAddFilesToFolder = { uris, folder -> viewModel.addFilesToVault(uris, com.geovault.model.FileCategory.OTHER, folder) },
                                    onSetCustomBackground = { viewModel.setCustomBackground(it) },
                                    onFetchWeather = { lat, lon -> viewModel.fetchWeatherAndAQI(lat, lon) },
                                    onStartAction = { viewModel.setPerformingAction(true) },
                                    onEndAction = { viewModel.setPerformingAction(false) },
                                    onRequestGps = { onEnabled ->
                                        checkLocationSettings(this@MainActivity, resolutionLauncher) {
                                            onEnabled()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                if (uiState.showBackgroundPopupGuide) {
                    BackgroundPopupGuideDialog(
                        onDismiss = {
                            viewModel.openBackgroundPopupSettings()
                        }
                    )
                }
            }
        }
    }

    private fun checkLocationSettings(
        context: Context,
        launcher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>,
        onAlreadyEnabled: () -> Unit
    ) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(context)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            onAlreadyEnabled()
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(exception.resolution).build()
                    launcher.launch(intentSenderRequest)
                } catch (sendEx: IntentSender.SendIntentException) {
                }
            } else {
                onAlreadyEnabled()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations && !viewModel.isPerformingAction()) {
            com.geovault.security.SecureManager.getInstance(this).prefs.edit {
                remove("bypass_package")
            }
            viewModel.lock()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermissions()
        viewModel.setPerformingAction(false)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            viewModel.checkPermissions()
        }
    }

    @Composable
    fun StartAnimationScreen() {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = CyberBlue,
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Starting Map Engine",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    "Initializing local security layers...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    @Composable
    fun OnboardingLocationGrant(
        onGranted: () -> Unit,
        onBack: () -> Unit,
        onStartAction: () -> Unit,
        onEndAction: () -> Unit
    ) {
        val context = LocalContext.current
        val prefs = remember { com.geovault.security.SecureManager.getInstance(context).prefs }
        var showDialog by remember { mutableStateOf(false) }
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            onEndAction()
            if (results.values.any { it }) {
                onGranted()
            } else {
                showDialog = true
            }
        }

        val requestLocation = {
            onStartAction()
            val fineDenied = androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
            if (fineDenied || !prefs.getBoolean("location_asked", false)) {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                prefs.edit().putBoolean("location_asked", true).apply()
            } else {
                // Redirect to settings if permanently denied
                try {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
        }

        Scaffold(
            containerColor = Color.White,
            topBar = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(top = 32.dp, start = 8.dp).statusBarsPadding()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.Black
                    )
                }
            }
        ) { padding ->
            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text(stringResource(R.string.location_required_title), fontWeight = FontWeight.Bold) },
                    text = { Text(stringResource(R.string.location_required_message)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDialog = false
                                requestLocation()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberBlue)
                        ) {
                            Text(stringResource(R.string.location_allow_btn))
                        }
                    },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(24.dp)
                )
            }

            Box(modifier = Modifier.fillMaxSize().padding(padding).background(Color.White), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp),
                        tint = CyberBlue
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = stringResource(R.string.location_access_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(R.string.location_access_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    Button(
                        onClick = { requestLocation() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.grant_location_access), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        stringResource(R.string.system_initialization),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}
