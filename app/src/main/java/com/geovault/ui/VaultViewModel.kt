package com.geovault.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.app.AppOpsManager
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geovault.location.LocationHelper
import com.geovault.model.AppInfo
import com.geovault.model.GeoPoint
import com.geovault.model.VaultState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.geovault.map.OfflineMapHelper
import com.geovault.model.*
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import android.app.admin.DevicePolicyManager
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.withContext

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(VaultState())
    val uiState: StateFlow<VaultState> = _uiState.asStateFlow()

    private val prefs = com.geovault.security.SecureManager.getInstance(application).prefs
    private val offlineHelper = OfflineMapHelper(application)
    private val cryptoManager = com.geovault.security.CryptoManager()

    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "vault_file_ids" || key?.startsWith("file_") == true) {
            updateFileCounts()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        ensureMainActivityEnabled()
        createNoMediaFile()
        loadInstalledApps()
        loadPersistedVaults()
        checkFirstRun()
        checkPermissions()
        updateFileCounts()
        
        if (!prefs.getBoolean("is_first_run", true)) {
            startMapDownload()
        }
    }

    private fun createNoMediaFile() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val vaultDir = File(context.filesDir, ".secure_vault_data")
            if (!vaultDir.exists()) vaultDir.mkdirs()
            
            val noMedia = File(vaultDir, ".nomedia")
            if (!noMedia.exists()) {
                try {
                    noMedia.createNewFile()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }
    
    // Polling based locker removed in favor of AccessibilityService

    fun completeOnboarding() {
        prefs.edit().putBoolean("is_first_run", false).apply()
        val tourCompleted = prefs.getBoolean("tour_completed", false)
        _uiState.update { it.copy(isFirstRun = false, isMapDownloading = false, showTour = !tourCompleted) }
        startMapDownload()
    }

    fun completeTour() {
        prefs.edit().putBoolean("tour_completed", true).apply()
        _uiState.update { it.copy(showTour = false) }
    }

    fun addIntruderFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileId = UUID.randomUUID().toString()
            val fileName = "Intruder_${System.currentTimeMillis()}.jpg"
            saveFileInfo(fileId, fileName, uri.path ?: "", FileCategory.INTRUDER, 0L)
            updateFileCounts()
        }
    }

    private fun checkFirstRun() {
        val isFirstRun = prefs.getBoolean("is_first_run", true)
        if (isFirstRun) {
            // Ensure clean state on first run by clearing potentially stale or default data
            prefs.edit().apply {
                remove("vault_ids")
                remove("vault_file_ids")
                remove("vault_history_ids")
                remove("bypass_package")
                remove("active_vault_id")
                putBoolean("is_locked", true)
                putBoolean("master_stealth_enabled", false)
                apply()
            }
        }
        _uiState.update { it.copy(isFirstRun = isFirstRun) }
    }

    private fun startMapDownload() {
        if (!isNetworkAvailable(getApplication())) {
            _uiState.update { it.copy(isNetworkAvailable = false) }
            return
        }

        _uiState.update { it.copy(isMapDownloading = true, isNetworkAvailable = true) }
        
        val bounds = LatLngBounds.Builder()
            .include(LatLng(85.0, 180.0))
            .include(LatLng(-85.0, -180.0))
            .build()

        offlineHelper.downloadRegion(
            styleUrl = "https://tiles.openfreemap.org/styles/dark",
            bounds = bounds,
            minZoom = 0.0,
            maxZoom = 8.0,
            regionName = "GlobalOffline",
            onProgress = { /* progress could be shown */ },
            onComplete = {
                _uiState.update { it.copy(isMapDownloading = false, isMapLoaded = true) }
            },
            onError = {
                _uiState.update { it.copy(isMapDownloading = false) }
            }
        )
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun addFilesToVault(uris: List<Uri>, category: FileCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val urisToDelete = mutableListOf<Uri>()

            uris.forEach { uri ->
                try {
                    val contentResolver = context.contentResolver
                    val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return@forEach
                    val fileName = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
                    
                    val vaultDir = File(context.filesDir, ".secure_vault_data")
                    if (!vaultDir.exists()) vaultDir.mkdirs()

                    val encryptedFile = File(vaultDir, UUID.randomUUID().toString() + ".dat")
                    val outputStream = FileOutputStream(encryptedFile)
                    
                    val fileSize = cryptoManager.encryptStream(inputStream!!, outputStream)
                    inputStream.close()
                    outputStream.close()

                    val thumbFile = File(vaultDir, "thumb_${encryptedFile.nameWithoutExtension}.jpg")
                    generateThumbnail(context, uri, category, thumbFile)

                    val fileId = UUID.randomUUID().toString()
                    saveFileInfo(fileId, fileName, encryptedFile.absolutePath, category, fileSize, thumbFile.absolutePath)
                    
                    urisToDelete.add(uri)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (urisToDelete.isNotEmpty()) {
                requestDeletion(context, urisToDelete)
            }
            
            updateFileCounts()
        }
    }

    private fun requestDeletion(context: Context, uris: List<Uri>) {
        // FILTER: Only keep Uris that can be deleted via MediaStore
        // createDeleteRequest expects content://media/external/ (ID-based) Uris
        val mediaStoreUris = uris.mapNotNull { uri ->
            resolveToMediaStoreUri(context, uri)
        }

        if (mediaStoreUris.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, mediaStoreUris)
                _uiState.update { it.copy(pendingDeleteIntent = pendingIntent) }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to manual deletion for non-scoped-storage scenarios
                manualDelete(context, mediaStoreUris)
            }
        } else {
            manualDelete(context, mediaStoreUris)
        }
    }

    private fun manualDelete(context: Context, uris: List<Uri>) {
        uris.forEach { uri ->
            try {
                context.contentResolver.delete(uri, null, null)
                // Thorough refresh
                android.media.MediaScannerConnection.scanFile(context, arrayOf(uri.path), null) { _, _ -> }
            } catch (e: Exception) {
                getFilePathFromUri(context, uri)?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                        android.media.MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> }
                    }
                }
            }
        }
    }

    private fun resolveToMediaStoreUri(context: Context, uri: Uri): Uri? {
        val uriString = uri.toString()
        if (uriString.contains("content://media/external/")) {
            return uri // Already a valid MediaStore Uri
        }

        // 1. Try to extract ID directly from the URI (common for document providers)
        try {
            val id = android.provider.DocumentsContract.getDocumentId(uri).split(":").last()
            val contentUri = if (uriString.contains("video")) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            return Uri.withAppendedPath(contentUri, id)
        } catch (e: Exception) {
            // Ignore and try fallback
        }

        // 2. Fallback: Search by Display Name
        try {
            val fileName = getFileName(context, uri) ?: return null
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            
            val collections = listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )

            for (collection in collections) {
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(fileName)
                
                context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        return Uri.withAppendedPath(collection, id.toString())
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return null
    }

    fun clearPendingDelete() {
        _uiState.update { it.copy(pendingDeleteIntent = null) }
    }

    private fun refreshGallery(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        intent.data = uri
        context.sendBroadcast(intent)
        
        // Also try to use MediaScannerConnection for better compatibility on newer Android
        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(uri.path),
            null
        ) { path, scanUri ->
            // Scan complete
        }
    }

    private fun getFilePathFromUri(context: Context, uri: Uri): String? {
        if ("file" == uri.scheme) return uri.path
        val projection = arrayOf(android.provider.MediaStore.Images.Media.DATA)
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA)
                return it.getString(index)
            }
        }
        return null
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private fun generateThumbnail(context: Context, uri: Uri, category: FileCategory, outputFile: File) {
        try {
            val bitmap = when (category) {
                FileCategory.PHOTO, FileCategory.INTRUDER -> {
                    val input = context.contentResolver.openInputStream(uri)
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeStream(input, null, options)
                }
                FileCategory.VIDEO -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(uri, android.util.Size(300, 300), null)
                    } else {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(context, uri)
                        retriever.getFrameAtTime(1000000) // 1 second in
                    }
                }
                else -> null
            }
            
            bitmap?.let {
                val out = FileOutputStream(outputFile)
                it.compress(Bitmap.CompressFormat.JPEG, 70, out)
                out.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveFileInfo(id: String, name: String, path: String, category: FileCategory, size: Long, thumbPath: String? = null) {
        val fileIds = (prefs.getStringSet("vault_file_ids", emptySet()) ?: emptySet()).toMutableSet()
        fileIds.add(id)
        prefs.edit().apply {
            putStringSet("vault_file_ids", fileIds)
            putString("file_${id}_name", name)
            putString("file_${id}_path", path)
            putString("file_${id}_category", category.name)
            putLong("file_${id}_size", size)
            putLong("file_${id}_timestamp", System.currentTimeMillis())
            thumbPath?.let { putString("file_${id}_thumb", it) }
            apply()
        }
    }

    private fun updateFileCounts() {
        val fileIds = prefs.getStringSet("vault_file_ids", emptySet()) ?: emptySet()
        val files = mutableListOf<VaultFile>()
        var photos = 0
        var videos = 0
        var audio = 0
        var docs = 0
        var intruders = 0
        
        fileIds.forEach { id ->
            val name = prefs.getString("file_${id}_name", "") ?: ""
            val path = prefs.getString("file_${id}_path", "") ?: ""
            val catStr = prefs.getString("file_${id}_category", "") ?: ""
            val size = prefs.getLong("file_${id}_size", 0L)
            val timestamp = prefs.getLong("file_${id}_timestamp", 0L)
            
            val category = try { FileCategory.valueOf(catStr) } catch (e: Exception) { FileCategory.OTHER }
            val thumbPath = prefs.getString("file_${id}_thumb", null)
            
            files.add(VaultFile(id, name, path, category, size, timestamp, thumbPath))

            when (category) {
                FileCategory.PHOTO -> photos++
                FileCategory.VIDEO -> videos++
                FileCategory.AUDIO -> audio++
                FileCategory.DOCUMENT -> docs++
                FileCategory.INTRUDER -> intruders++
                else -> {}
            }
        }
        
        _uiState.update { 
            it.copy(
                files = files.sortedByDescending { f -> f.addedTimestamp },
                photoCount = photos,
                videoCount = videos,
                audioCount = audio,
                documentCount = docs,
                intruderCount = intruders
            )
        }
    }

    private fun ensureMainActivityEnabled() {
        val context = getApplication<Application>()
        val pm = context.packageManager
        
        // ALWAYS keep the Activity enabled so it can be launched by the system/IDE
        pm.setComponentEnabledSetting(
            ComponentName(context, "com.geovault.MainActivity"),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        val hasUsage = hasUsageStatsPermission(context)
        val hasOverlay = Settings.canDrawOverlays(context)
        val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasLocation = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBattery = hasBatteryOptimizationPermission(context)
        
        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        _uiState.update { 
            it.copy(
                hasUsageStatsPermission = hasUsage,
                hasOverlayPermission = hasOverlay,
                hasCameraPermission = hasCamera,
                hasLocationPermission = hasLocation,
                hasStoragePermission = hasStorage,
                hasBatteryOptimizationPermission = hasBattery
            )
        }
    }

    private fun hasBatteryOptimizationPermission(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = ComponentName(context, com.geovault.service.WindowChangeDetector::class.java).flattenToShortString()
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        
        if (enabledServices == null) return false
        
        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun openUsageStatsSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    fun openOverlaySettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${getApplication<Application>().packageName}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    fun openProtectedAppsSettings() {
        val context = getApplication<Application>()
        
        if (!hasBatteryOptimizationPermission(context)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:${context.packageName}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val intent = Intent()
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        when {
            manufacturer.contains("huawei") -> {
                intent.setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            }
            manufacturer.contains("xiaomi") -> {
                intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            }
            manufacturer.contains("oppo") -> {
                intent.setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            }
            manufacturer.contains("vivo") -> {
                intent.setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
            }
            else -> {
                intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            }
        }
        
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS)
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            
            val lockedApps = getLockedApps()
            
            val apps = packages
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .filter { !lockedApps.contains(it.packageName) } // EXCLUDE LOCKED APPS
                .map { 
                    AppInfo(
                        packageName = it.packageName,
                        appName = it.loadLabel(pm).toString(),
                        icon = it.loadIcon(pm)
                    )
                }
                .sortedBy { it.appName }
            
            _uiState.update { it.copy(installedApps = apps) }
        }
    }

    private fun getLockedApps(): Set<String> {
        val allLockedApps = mutableSetOf<String>()
        val allVaultIds = prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()
        allVaultIds.forEach { id ->
            val apps = prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet()
            allLockedApps.addAll(apps)
        }
        return allLockedApps
    }

    private fun ensureOffline(point: GeoPoint) {
        val bounds = LatLngBounds.Builder()
            .include(LatLng(point.latitude + 0.05, point.longitude + 0.05))
            .include(LatLng(point.latitude - 0.05, point.longitude - 0.05))
            .build()
            
        offlineHelper.downloadRegion(
            styleUrl = "https://tiles.openfreemap.org/styles/dark",
            bounds = bounds,
            minZoom = 12.0,
            maxZoom = 16.0,
            regionName = "VaultArea",
            onProgress = {},
            onComplete = {},
            onError = {}
        )
    }

    private fun loadPersistedVaults() {
        val vaultIds = prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()
        val vaults = vaultIds.mapNotNull { id ->
            val lat = prefs.getFloat("vault_${id}_lat", -1000f).toDouble()
            if (lat == -1000.0) return@mapNotNull null
            
            val lon = prefs.getFloat("vault_${id}_lon", 0f).toDouble()
            val lockType = LockType.valueOf(prefs.getString("vault_${id}_lock_type", "PIN") ?: "PIN")
            val secret = prefs.getString("vault_${id}_secret", "") ?: ""
            val hiddenApps = prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet()
            val timestamp = prefs.getLong("vault_${id}_timestamp", 0L)
            val radius = prefs.getFloat("vault_${id}_radius", 500f)
            
            VaultConfig(id, GeoPoint(lat, lon), radius = radius, lockType = lockType, secret = secret, hiddenApps = hiddenApps, addedTimestamp = timestamp)
        }
        
        val historyIds = prefs.getStringSet("vault_history_ids", emptySet()) ?: emptySet()
        val history = historyIds.mapNotNull { id ->
            val lat = prefs.getFloat("hist_${id}_lat", -1000f).toDouble()
            if (lat == -1000.0) return@mapNotNull null
            val lon = prefs.getFloat("hist_${id}_lon", 0f).toDouble()
            val count = prefs.getInt("hist_${id}_count", 0)
            val packages = prefs.getStringSet("hist_${id}_packages", emptySet()) ?: emptySet()
            val timestamp = prefs.getLong("hist_${id}_timestamp", 0L)
            VaultHistory(id, GeoPoint(lat, lon), count, packages, timestamp)
        }.sortedByDescending { it.timestamp }

        val isLocked = prefs.getBoolean("is_locked", true)
        val isMasterStealth = prefs.getBoolean("master_stealth_enabled", false)
        val isDarkMode = prefs.getBoolean("is_dark_mode", false)
        val isFirstRunLocal = prefs.getBoolean("is_first_run", true)
        val isSatelliteMode = prefs.getBoolean("is_satellite_mode", false)
        val tourCompleted = prefs.getBoolean("tour_completed", false)
        val showTour = !tourCompleted
        val language = prefs.getString("language", "en") ?: "en"
        val isScreenshotRestricted = prefs.getBoolean("screenshot_restriction", true)
        val isFingerprintEnabled = prefs.getBoolean("fingerprint_enabled", false)
        _uiState.update { it.copy(
            vaults = vaults, 
            vaultHistory = history, 
            isLocked = isLocked, 
            isMasterStealthEnabled = isMasterStealth, 
            isFingerprintEnabled = isFingerprintEnabled,
            isDarkMode = isDarkMode, 
            isSatelliteMode = isSatelliteMode, 
            showTour = showTour, 
            currentLanguage = language, 
            isFirstRun = isFirstRunLocal,
            isScreenshotRestricted = isScreenshotRestricted
        ) }
        
        // Apply language on startup
        com.geovault.security.LocaleManager.applyLanguage(language)
        
        // Refresh apps list to reflect new lock state
        loadInstalledApps()
    }

    fun saveVaultConfiguration(point: GeoPoint, secret: String, hiddenApps: Set<String>, lockType: LockType = LockType.PIN, radius: Float = 500f) {
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val vaultIds = (prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()).toMutableSet()
        vaultIds.add(id)
        
        val historyIds = (prefs.getStringSet("vault_history_ids", emptySet()) ?: emptySet()).toMutableSet()
        historyIds.add(id)

        prefs.edit().apply {
            putStringSet("vault_ids", vaultIds)
            putFloat("vault_${id}_lat", point.latitude.toFloat())
            putFloat("vault_${id}_lon", point.longitude.toFloat())
            putFloat("vault_${id}_radius", radius)
            putString("vault_${id}_lock_type", lockType.name)
            putString("vault_${id}_secret", secret)
            putStringSet("vault_${id}_apps", hiddenApps)
            putLong("vault_${id}_timestamp", timestamp)
            putBoolean("is_locked", true)
            
            putStringSet("vault_history_ids", historyIds)
            putFloat("hist_${id}_lat", point.latitude.toFloat())
            putFloat("hist_${id}_lon", point.longitude.toFloat())
            putInt("hist_${id}_count", hiddenApps.size)
            putStringSet("hist_${id}_packages", hiddenApps)
            putLong("hist_${id}_timestamp", timestamp)
            
            apply()
        }
        
        ensureOffline(point)
        loadPersistedVaults()
        
        // Notify service to refresh its package list
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, com.geovault.service.AppLockerService::class.java)
        serviceIntent.putExtra("refresh_locked_apps", true)
        context.startService(serviceIntent)
    }

    fun clearAllVaults() {
        val vaultIds = prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()
        prefs.edit().apply {
            vaultIds.forEach { id ->
                remove("vault_${id}_lat")
                remove("vault_${id}_lon")
                remove("vault_${id}_lock_type")
                remove("vault_${id}_secret")
                remove("vault_${id}_apps")
                remove("vault_${id}_timestamp")
            }
            putStringSet("vault_ids", emptySet())
            putBoolean("is_locked", true)
            apply()
        }
        loadPersistedVaults()

        // Notify service
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, com.geovault.service.AppLockerService::class.java)
        serviceIntent.putExtra("refresh_locked_apps", true)
        context.startService(serviceIntent)
    }

    fun onLocationChanged(latitude: Double, longitude: Double) {
        _uiState.update { it.copy(currentLocation = GeoPoint(latitude, longitude)) }
        
        // Optimization: Pre-download region for current location to ensure map is snappy
        if (!_uiState.value.isFirstRun) {
            ensureOffline(GeoPoint(latitude, longitude))
        }

        val nearby = _uiState.value.vaults.filter { vault ->
            val effectiveRadius = if (vault.radius > 0) vault.radius else 500f
            LocationHelper.isWithinRadius(
                latitude, longitude,
                vault.location.latitude, vault.location.longitude,
                effectiveRadius
            )
        }.map { it.id }.toSet()
        
        _uiState.update { 
            it.copy(
                isNearAnyVault = nearby.isNotEmpty(),
                nearbyVaultIds = nearby
            )
        }
    }

    fun updateProximity(isNear: Boolean) {
        _uiState.update { it.copy(isNearAnyVault = isNear) }
        if (isNear && _uiState.value.vaults.isNotEmpty()) {
            _uiState.update { it.copy(nearbyVaultIds = setOf(_uiState.value.vaults.first().id)) }
        }
    }

    fun unlock(id: String, secret: String): Boolean {
        val vault = _uiState.value.vaults.find { it.id == id } ?: return false
        if (secret == vault.secret) {
            prefs.edit().putBoolean("is_locked", false).apply()
            prefs.edit().putString("active_vault_id", id).apply()
            _uiState.update { it.copy(isLocked = false, activeVaultId = id) }
            return true
        }
        return false
    }

    fun attemptUnlockAtLocation(tapLat: Double, tapLon: Double, pin: String): Boolean {
        val nearbyVault = _uiState.value.vaults.find { vault ->
            LocationHelper.isWithinRadius(
                tapLat, tapLon,
                vault.location.latitude, vault.location.longitude,
                100f // Back to 100m radius for precision
            )
        } ?: return false

        // Feature: Radius-based lock (Optional: enabled if radius > 0)
        if (nearbyVault.radius > 0) {
            val current = _uiState.value.currentLocation
            if (current == null) return false // Cannot verify presence
            
            val distance = LocationHelper.calculateDistance(
                current.latitude, current.longitude,
                nearbyVault.location.latitude, nearbyVault.location.longitude
            )
            
            if (distance > nearbyVault.radius) {
                // User is physically outside the required radius
                return false
            }
        }

        if (pin == nearbyVault.secret) {
            prefs.edit().putBoolean("is_locked", false).apply()
            prefs.edit().putString("active_vault_id", nearbyVault.id).apply()
            _uiState.update { it.copy(isLocked = false, activeVaultId = nearbyVault.id) }
            return true
        }
        return false
    }

    fun findVaultAtLocation(tapLat: Double, tapLon: Double): VaultConfig? {
        return _uiState.value.vaults.find { vault ->
            LocationHelper.isWithinRadius(
                tapLat, tapLon,
                vault.location.latitude, vault.location.longitude,
                100f
            )
        }
    }

    fun lock() {
        prefs.edit().putBoolean("is_locked", true).apply()
        prefs.edit().remove("active_vault_id").apply()
        _uiState.update { it.copy(isLocked = true, activeVaultId = null) }
    }

    fun removeVault(id: String) {
        val vaultIds = (prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()).toMutableSet()
        vaultIds.remove(id)
        prefs.edit().putStringSet("vault_ids", vaultIds).apply()
        loadPersistedVaults()

        // Notify service
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, com.geovault.service.AppLockerService::class.java)
        serviceIntent.putExtra("refresh_locked_apps", true)
        context.startService(serviceIntent)
    }

    fun removeAppFromVault(packageName: String) {
        val vaultId = _uiState.value.activeVaultId ?: return
        val vault = _uiState.value.vaults.find { it.id == vaultId } ?: return
        val newHiddenApps = vault.hiddenApps.toMutableSet()
        newHiddenApps.remove(packageName)
        
        prefs.edit().putStringSet("vault_${vaultId}_apps", newHiddenApps).apply()
        loadPersistedVaults()
    }

    fun toggleAppLock(packageName: String) {
        val vaultId = _uiState.value.activeVaultId ?: return
        val vault = _uiState.value.vaults.find { it.id == vaultId } ?: return
        val newHiddenApps = vault.hiddenApps.toMutableSet()
        
        if (newHiddenApps.contains(packageName)) {
            newHiddenApps.remove(packageName)
        } else {
            newHiddenApps.add(packageName)
        }
        
        prefs.edit().putStringSet("vault_${vaultId}_apps", newHiddenApps).apply()
        loadPersistedVaults()
    }

    fun launchApp(packageName: String) {
        val context = getApplication<Application>()
        
        // Grant temporary bypass for the service
        prefs.edit().putString("bypass_package", packageName).apply()

        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    }

    fun toggleMasterStealth() {
        val newValue = !_uiState.value.isMasterStealthEnabled
        prefs.edit().putBoolean("master_stealth_enabled", newValue).apply()
        _uiState.update { it.copy(isMasterStealthEnabled = newValue) }
        
        // Notify service to update monitoring logic
        val intent = android.content.Intent(getApplication(), com.geovault.service.AppLockerService::class.java).apply {
            putExtra("refresh_locked_apps", true)
        }
        getApplication<android.app.Application>().startService(intent)
    }

    fun toggleDarkMode() {
        val newValue = !_uiState.value.isDarkMode
        prefs.edit().putBoolean("is_dark_mode", newValue).apply()
        _uiState.update { it.copy(isDarkMode = newValue) }
    }

    fun toggleSatelliteMode() {
        val newValue = !_uiState.value.isSatelliteMode
        prefs.edit().putBoolean("is_satellite_mode", newValue).apply()
        _uiState.update { it.copy(isSatelliteMode = newValue) }
    }

    fun toggleScreenshotRestriction() {
        val newValue = !_uiState.value.isScreenshotRestricted
        prefs.edit().putBoolean("screenshot_restriction", newValue).apply()
        _uiState.update { it.copy(isScreenshotRestricted = newValue) }
    }

    fun toggleFingerprint() {
        val newValue = !_uiState.value.isFingerprintEnabled
        prefs.edit().putBoolean("fingerprint_enabled", newValue).apply()
        _uiState.update { it.copy(isFingerprintEnabled = newValue) }
    }

    fun setLanguage(langCode: String) {
        prefs.edit().putString("language", langCode).apply()
        _uiState.update { it.copy(currentLanguage = langCode) }
        com.geovault.security.LocaleManager.applyLanguage(langCode)
    }

    fun removeFileFromVault(fileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            com.geovault.security.SecureManager.getInstance(getApplication()).removeFileInfo(fileId)
            updateFileCounts()
        }
    }

    fun restoreFileToGallery(fileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val secureManager = com.geovault.security.SecureManager.getInstance(context)
            val prefs = secureManager.prefs

            val fileName = prefs.getString("file_${fileId}_name", null) ?: return@launch
            val encryptedPath = prefs.getString("file_${fileId}_path", null) ?: return@launch
            val categoryStr = prefs.getString("file_${fileId}_category", null) ?: return@launch
            val category = try { FileCategory.valueOf(categoryStr) } catch (e: Exception) { FileCategory.OTHER }

            try {
                val encryptedFile = File(encryptedPath)
                if (!encryptedFile.exists()) return@launch

                val contentResolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(fileName))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val relativePath = when (category) {
                            FileCategory.PHOTO, FileCategory.INTRUDER -> Environment.DIRECTORY_PICTURES
                            FileCategory.VIDEO -> Environment.DIRECTORY_MOVIES
                            FileCategory.AUDIO -> Environment.DIRECTORY_MUSIC
                            else -> Environment.DIRECTORY_DOWNLOADS
                        }
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativePath/MappLockRestored")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val collection = when (category) {
                    FileCategory.PHOTO, FileCategory.INTRUDER -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    FileCategory.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    FileCategory.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Files.getContentUri("external")
                    }
                }

                val uri = contentResolver.insert(collection, contentValues)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        cryptoManager.decryptToStream(encryptedFile.inputStream(), outputStream)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        contentResolver.update(uri, contentValues, null, null)
                    }

                    // Success: Remove from vault
                    secureManager.removeFileInfo(fileId)
                    updateFileCounts()
                    
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "File restored to gallery", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Restoration failed", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getMimeType(fileName: String): String {
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            fileName.substringAfterLast('.', "").lowercase()
        ) ?: "*/*"
    }
}
