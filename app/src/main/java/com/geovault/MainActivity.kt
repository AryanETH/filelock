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
import com.geovault.ui.theme.CyberBlack
import com.geovault.ui.theme.CyberBlue
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.maplibre.android.MapLibre

import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

import androidx.appcompat.app.AppCompatActivity
import com.geovault.security.SecurityUtils
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    private val viewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Professional Security: Prevent screenshots and recent app previews
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        // Root Detection
        if (SecurityUtils.isDeviceRooted()) {
            Toast.makeText(this, "Security Alert: Rooted device detected. Some features may be disabled.", Toast.LENGTH_LONG).show()
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        MapLibre.getInstance(this)
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            var showSplash by remember { mutableStateOf(true) }

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

            GeoVaultTheme(darkTheme = uiState.isDarkMode) {
                val context = LocalContext.current

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { 
                    viewModel.setPerformingAction(false)
                }

                val deleteLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    viewModel.setPerformingAction(false)
                    if (result.resultCode == android.app.Activity.RESULT_OK) {
                        // Deletion confirmed
                    }
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
                    val permissions = mutableListOf<String>()
                    
                    // Essential for Map-Gate and Service
                    permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                    permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        permissions.add(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE)
                    }
                    
                    permissionLauncher.launch(permissions.toTypedArray())
                }

                // Tracking location
                LaunchedEffect(Unit) {
                    viewModel.checkPermissions()
                    // Re-start the service to ensure it's active
                    val serviceIntent = Intent(context, com.geovault.service.AppLockerService::class.java)
                    context.startService(serviceIntent)
                }

                LaunchedEffect(uiState.vaults) {
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
                    
                    val locationCallback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            result.lastLocation?.let {
                                viewModel.onLocationChanged(it.latitude, it.longitude)
                            }
                        }
                    }
                    
                    try {
                        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
                    } catch (e: SecurityException) {}
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnimatedContent(
                        targetState = when {
                            showSplash -> "intro"
                            uiState.isFirstRun && !uiState.isLanguageSelected -> "language_selection"
                            uiState.isFirstRun -> "onboarding"
                            !uiState.hasUsageStatsPermission || !uiState.hasOverlayPermission || !uiState.hasLocationPermission || !uiState.hasBatteryOptimizationPermission -> "permissions"
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
                                OnboardingScreen(onFinished = { viewModel.completeOnboarding() })
                            }
                            "permissions" -> {
                                PermissionScreen(
                                    state = uiState,
                                    onGrantUsage = { viewModel.openUsageStatsSettings() },
                                    onGrantOverlay = { viewModel.openOverlaySettings() },
                                    onGrantLocation = {
                                        viewModel.setPerformingAction(true)
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    },
                                    onGrantBattery = {
                                        viewModel.openProtectedAppsSettings()
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
                                    onRemoveApp = { packageName -> viewModel.removeAppFromVault(packageName) },
                                    onSimulateArrive = { viewModel.updateProximity(true) },
                                    onOpenUsageSettings = { viewModel.openUsageStatsSettings() },
                                    onOpenOverlaySettings = { viewModel.openOverlaySettings() },
                                    onOpenProtectedApps = { viewModel.openProtectedAppsSettings() },
                                    onToggleMasterStealth = { viewModel.toggleMasterStealth() },
                                    onAddFiles = { uris, category -> viewModel.addFilesToVault(uris, category) },
                                    onToggleAppLock = { packageName -> viewModel.toggleAppLock(packageName) },
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
                                        viewModel.openFullStorageSettings()
                                    },
                                    onDeleteFile = { fileId -> viewModel.removeFileFromVault(fileId) },
                                    onRestoreFile = { fileId -> viewModel.restoreFileToGallery(fileId) },
                                    onFetchGalleryItems = { cat -> viewModel.fetchGalleryItems(cat) },
                                    onToggleDarkMode = { viewModel.toggleDarkMode() },
                                    onToggleFingerprint = { viewModel.toggleFingerprint() },
                                    onToggleSatellite = { viewModel.toggleSatelliteMode() },
                                    onSetLanguage = { lang -> viewModel.setLanguage(lang) },
                                    onCompleteTour = { viewModel.completeTour() },
                                    onToggleScreenshotRestriction = { viewModel.toggleScreenshotRestriction() },
                                    onCreateFolder = { viewModel.createFolder(it) },
                                    onAddFilesToFolder = { uris, folder -> viewModel.addFilesToVault(uris, com.geovault.model.FileCategory.OTHER, folder) },
                                    onStartAction = { viewModel.setPerformingAction(true) },
                                    onEndAction = { viewModel.setPerformingAction(false) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Clear any bypass token when user leaves the main app
        com.geovault.security.SecureManager.getInstance(this).prefs.edit().remove("bypass_package").apply()
        
        // Force lock when leaving the app to ensure it opens on map next time
        // BUG FIX: Only lock if we are NOT performing an internal action (like picking files)
        if (!viewModel.isPerformingAction()) {
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
            modifier = Modifier.fillMaxSize().background(CyberBlack),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Circular loading as requested in "start animation downloading map"
                CircularProgressIndicator(
                    color = CyberBlue,
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "INITIALIZING OFFLINE SYSTEM",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyberBlue,
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
