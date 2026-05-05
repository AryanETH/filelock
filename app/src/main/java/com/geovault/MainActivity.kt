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
import androidx.core.view.WindowCompat
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val viewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        MapLibre.getInstance(this)
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            var showSplash by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                delay(1500)
                showSplash = false
            }

            GeoVaultTheme(darkTheme = uiState.isDarkMode) {
                val context = LocalContext.current

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { /* handle results */ }

                LaunchedEffect(Unit) {
                    val permissions = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.CAMERA,
                        Manifest.permission.VIBRATE
                    )
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                        permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                        permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
                            uiState.isFirstRun -> "onboarding"
                            !uiState.hasUsageStatsPermission || !uiState.hasOverlayPermission || !uiState.hasCameraPermission || !uiState.hasLocationPermission || !uiState.hasStoragePermission -> "permissions"
                            uiState.isMapDownloading -> "downloading"
                            else -> "vault"
                        },
                        transitionSpec = {
                            val duration = 700
                            if (targetState == "vault" || initialState == "intro") {
                                fadeIn(animationSpec = tween(duration)).togetherWith(fadeOut(animationSpec = tween(duration)))
                            } else {
                                (fadeIn(animationSpec = tween(duration)) + slideInVertically(animationSpec = tween(duration), initialOffsetY = { 100 }))
                                    .togetherWith(fadeOut(animationSpec = tween(duration / 2)))
                            }
                        },
                        label = "ScreenTransition"
                    ) { target ->
                        when (target) {
                            "intro" -> IntroScreen()
                            "onboarding" -> {
                                OnboardingScreen(onFinished = { viewModel.completeOnboarding() })
                            }
                            "permissions" -> {
                                PermissionScreen(
                                    state = uiState,
                                    onGrantUsage = { viewModel.openUsageStatsSettings() },
                                    onGrantOverlay = { viewModel.openOverlaySettings() },
                                    onGrantCamera = {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                                    },
                                    onGrantLocation = {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    },
                                    onGrantStorage = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))
                                        } else {
                                            permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                                        }
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
                                    onSaveConfig = { point, secret, apps, lockType ->
                                        viewModel.saveVaultConfiguration(point, secret, apps, lockType)
                                    },
                                    onLockClick = { viewModel.lock() },
                                    onAppClick = { packageName -> viewModel.launchApp(packageName) },
                                    onRemoveApp = { packageName -> viewModel.removeAppFromVault(packageName) },
                                    onSimulateArrive = { viewModel.updateProximity(true) },
                                    onOpenUsageSettings = { viewModel.openUsageStatsSettings() },
                                    onOpenOverlaySettings = { viewModel.openOverlaySettings() },
                                    onOpenProtectedApps = { viewModel.openProtectedAppsSettings() },
                                    onToggleMasterStealth = { viewModel.toggleMasterStealth() },
                                    onAddFile = { uri, category -> viewModel.addFileToVault(uri, category) },
                                    onToggleAppLock = { packageName -> viewModel.toggleAppLock(packageName) },
                                    onRemoveVault = { id -> viewModel.removeVault(id) },
                                    onClearAllVaults = { viewModel.clearAllVaults() },
                                    onGrantCamera = {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                                    },
                                    onGrantStorage = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))
                                        } else {
                                            permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                                        }
                                    },
                                    onDeleteFile = { fileId -> viewModel.removeFileFromVault(fileId) },
                                    onRestoreFile = { fileId -> viewModel.restoreFileToGallery(fileId) },
                                    onToggleDarkMode = { viewModel.toggleDarkMode() },
                                    onToggleSatellite = { viewModel.toggleSatelliteMode() },
                                    onToggleUninstallProtection = {
                                        viewModel.toggleUninstallProtection(
                                            onBiometricPrompt = {
                                                showUninstallBiometricPrompt {
                                                    viewModel.deactivateUninstallProtection()
                                                }
                                            }
                                        )
                                    },
                                    onSetLanguage = { lang -> viewModel.setLanguage(lang) },
                                    onCompleteTour = { viewModel.completeTour() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermissions()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            viewModel.checkPermissions()
        }
    }

    private fun showUninstallBiometricPrompt(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
            })

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Identity Verification")
            .setSubtitle("Confirm your Phone PIN/Pattern to disable protection")
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )

        biometricPrompt.authenticate(builder.build())
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
