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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.geovault.ui.theme.GeoVaultTheme
import com.geovault.ui.theme.CyberBlue
import com.geovault.ui.theme.CyberNavy
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
import androidx.core.content.edit

import androidx.appcompat.app.AppCompatActivity
import com.geovault.security.SecurityUtils
import com.geovault.security.LocaleManager
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

import androidx.compose.runtime.saveable.rememberSaveable

class MainActivity : AppCompatActivity() {
    private val viewModel: VaultViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleManager.getLanguage(newBase)
        super.attachBaseContext(LocaleManager.getLocaleContext(newBase, lang))
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
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
            var showSplash by rememberSaveable { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                delay(1500)
                showSplash = false
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
                    context.startService(serviceIntent)
                }

                LaunchedEffect(uiState.vaults) {
                    if (uiState.vaults.isEmpty()) return@LaunchedEffect
                    
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                    // BATTERY OPTIMIZATION: Reduce interval to 15 seconds instead of 5, and use BALANCED power
                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15000)
                        .setMinUpdateIntervalMillis(10000)
                        .build()
                    
                    val locationCallback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            result.lastLocation?.let {
                                viewModel.onLocationChanged(it.latitude, it.longitude)
                            }
                        }
                    }
                    
                    try {
                        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
                    } catch (_: SecurityException) {}
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnimatedContent(
                        targetState = when {
                            showSplash -> "intro"
                            uiState.isFirstRun && !uiState.isLanguageSelected -> "language_selection"
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
                        label = "ScreenTransition"
                    ) { target ->
                        when (target) {
                            "intro" -> IntroScreen()
                            "language_selection" -> {
                                LanguageOnboardingScreen(onLanguageSelected = { viewModel.setLanguage(it) })
                            }
                            "onboarding" -> {
                                OnboardingScreen(
                                    onFinished = { viewModel.completeOnboarding() },
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
                                            permissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
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
                    color = CyberNavy,
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "INITIALIZING OFFLINE SYSTEM",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    "Downloading regional map data...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
