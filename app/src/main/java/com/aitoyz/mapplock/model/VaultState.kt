package com.aitoyz.mapplock.model

import android.graphics.drawable.Drawable
import android.net.Uri

data class GeoPoint(val latitude: Double, val longitude: Double)

data class GalleryItem(
    val uri: Uri,
    val name: String,
    val dateAdded: Long,
    val size: Long,
    val folderName: String,
    val duration: Long? = null,
    val thumbnail: String? = null
)

data class AppInfo(
    val packageName: String,
    val appName: String
)

enum class LockType {
    PIN, PATTERN, FINGERPRINT, MAP
}

enum class MonitoringMode {
    USAGE_STATS, SHIZUKU, ACCESSIBILITY, AUTO
}

data class VaultConfig(
    val id: String,
    val location: GeoPoint,
    val radius: Float = 500f,
    val lockType: LockType = LockType.PIN,
    val secret: String, // PIN or Pattern string
    val hiddenApps: Set<String>,
    val addedTimestamp: Long = System.currentTimeMillis()
)

data class VaultHistory(
    val id: String,
    val location: GeoPoint,
    val appsCount: Int,
    val hiddenAppPackages: Set<String> = emptySet(),
    val timestamp: Long
)

data class VaultState(
    val isLocked: Boolean = true,
    val vaults: List<VaultConfig> = emptyList(),
    val vaultHistory: List<VaultHistory> = emptyList(),
    val activeVaultId: String? = null,
    val currentLocation: GeoPoint? = null,
    val isNearAnyVault: Boolean = false,
    val nearbyVaultIds: Set<String> = emptySet(),
    
    val installedApps: List<AppInfo> = emptyList(),
    val isMapDownloading: Boolean = false,
    val isMapLoaded: Boolean = false,
    val isFirstRun: Boolean = true,
    val introGuideCompleted: Boolean = false,
    val disclaimerAccepted: Boolean = false,
    val isPinSet: Boolean = false,
    val isNetworkAvailable: Boolean = true,

    val hasUsageStatsPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val hasStoragePermission: Boolean = false,
    val hasFullStoragePermission: Boolean = false,
    val hasBatteryOptimizationPermission: Boolean = false,
    val hasBackgroundPopupsPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasAccessibilityPermission: Boolean = false,
    val isLockerServiceRunning: Boolean = false,
    val isUsageAccessActive: Boolean = false,
    val isIndestructibleModeActive: Boolean = false,

    // Files
    val files: List<VaultFile> = emptyList(),
    val galleryItems: List<GalleryItem> = emptyList(),
    val isFetchingGallery: Boolean = false,

    // File counts for dashboard
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val audioCount: Int = 0,
    val documentCount: Int = 0,
    val intruderCount: Int = 0,
    val recycleBinCount: Int = 0,
    val customFolders: List<String> = emptyList(),
    val isMasterStealthEnabled: Boolean = false,
    val isFingerprintEnabled: Boolean = false,
    val isDarkMode: Boolean = false,
    val isSatelliteMode: Boolean = false,
    val showTour: Boolean = false,
    val isLanguageSelected: Boolean = false,
    val currentLanguage: String = "en",
    val monitoringMode: MonitoringMode = MonitoringMode.AUTO,
    val isScreenshotRestricted: Boolean = false,
    val isIntruderCaptureEnabled: Boolean = false,
    val isIndiaRegion: Boolean = false,
    val isUninstallShieldEnabled: Boolean = false,
    val showBackgroundPopupGuide: Boolean = false,
    val pendingDeleteIntent: android.app.PendingIntent? = null,
    val customBackgroundPath: String? = null,
    
    // Import/Export Progress
    val operationProgress: OperationProgress? = null,

    // Weather and AQI
    val weatherInfo: WeatherInfo = WeatherInfo(),
    val isWeatherLoading: Boolean = false
)

data class WeatherInfo(
    val temperature: Double? = null,
    val humidity: Int? = null,
    val aqi: Int? = null,
    val cityName: String? = null,
    val address: String? = null,
    val no2: Double? = null,
    val pm25: Double? = null,
    val pm10: Double? = null,
    val o3: Double? = null,
    val lastUpdated: Long = 0
)

data class OperationProgress(
    val title: String,
    val currentFile: String,
    val totalFiles: Int,
    val processedFiles: Int,
    val percentage: Float,
    val speedMbps: Double = 0.0,
    val timeRemainingSeconds: Long = 0,
    val showHoldOn: Boolean = false
)
