package com.geovault.model

import android.graphics.drawable.Drawable

data class GeoPoint(val latitude: Double, val longitude: Double)

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null
)

enum class LockType {
    PIN, PATTERN, FINGERPRINT, MAP
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
    val isNearAnyVault: Boolean = false,
    val nearbyVaultIds: Set<String> = emptySet(),
    
    val installedApps: List<AppInfo> = emptyList(),
    val isMapDownloading: Boolean = false,
    val isFirstRun: Boolean = true,

    val hasUsageStatsPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasAccessibilityPermission: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val hasStoragePermission: Boolean = false,

    // Files
    val files: List<VaultFile> = emptyList(),

    // File counts for dashboard
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val audioCount: Int = 0,
    val documentCount: Int = 0,
    val intruderCount: Int = 0,
    val recycleBinCount: Int = 0,
    val isMasterStealthEnabled: Boolean = false
)
