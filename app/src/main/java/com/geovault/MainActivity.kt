package com.geovault

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.geovault.ui.VaultScreen
import com.geovault.ui.VaultViewModel
import com.geovault.ui.OnboardingScreen
import com.geovault.ui.PermissionScreen
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
import android.view.WindowManager
class MainActivity : ComponentActivity() {
    private val viewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        MapLibre.getInstance(this)
        setContent {
            GeoVaultTheme {
                val uiState by viewModel.uiState.collectAsState()
                val context = LocalContext.current

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { /* handle results */ }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
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
                    when {
                        uiState.isFirstRun -> {
                            OnboardingScreen(onFinished = { viewModel.completeOnboarding() })
                        }
                        !uiState.hasUsageStatsPermission || !uiState.hasOverlayPermission -> {
                            PermissionScreen(
                                state = uiState,
                                onGrantUsage = { viewModel.openUsageStatsSettings() },
                                onGrantOverlay = { viewModel.openOverlaySettings() },
                                onGrantLocation = {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            )
                        }
                        uiState.isMapDownloading -> {
                            StartAnimationScreen()
                        }
                        else -> {
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
                                onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                                onOpenProtectedApps = { viewModel.openProtectedAppsSettings() },
                                onToggleMasterStealth = { viewModel.toggleMasterStealth() },
                                onAddFile = { uri, category -> viewModel.addFileToVault(uri, category) },
                                onToggleAppLock = { packageName -> viewModel.toggleAppLock(packageName) },
                                onRemoveVault = { id -> viewModel.removeVault(id) },
                                onClearAllVaults = { viewModel.clearAllVaults() }
                            )
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
