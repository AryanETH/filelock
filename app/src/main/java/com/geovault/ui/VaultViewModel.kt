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
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import kotlinx.coroutines.withContext

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(VaultState())
    val uiState: StateFlow<VaultState> = _uiState.asStateFlow()

    private var isPerformingAction = false

    fun setPerformingAction(performing: Boolean) {
        isPerformingAction = performing
    }

    fun isPerformingAction(): Boolean = isPerformingAction

    private val prefs = com.geovault.security.SecureManager.getInstance(application).prefs
    private val offlineHelper = OfflineMapHelper(application)
    private val cryptoManager = com.geovault.security.CryptoManager()

    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "vault_file_ids" || key == "custom_folders" || key?.startsWith("file_") == true) {
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
        
        // Force lock on startup to ensure we always start with the map
        lock()
        
        if (!prefs.getBoolean("is_first_run", true)) {
            startMapDownload()
        }
    }

    private fun createNoMediaFile() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            // Professional vault path
            val vaultDir = File(context.filesDir, ".vault")
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

    fun addIntruderFile(uri: Uri, thumbPath: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileId = UUID.randomUUID().toString()
            val fileName = "Intruder_${System.currentTimeMillis()}.jpg"
            saveFileInfo(fileId, fileName, uri.path ?: "", FileCategory.INTRUDER, 0L, thumbPath)
            updateFileCounts()
        }
    }

    private fun checkFirstRun() {
        val isFirstRun = prefs.getBoolean("is_first_run", true)
        if (isFirstRun) {
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

    fun addFilesToVault(uris: List<Uri>, category: FileCategory, folderName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val itemsToDelete = mutableListOf<Triple<Uri, Long, String?>>()
            val total = uris.size
            val startTime = System.currentTimeMillis()

            uris.forEachIndexed { index, uri ->
                try {
                    val contentResolver = context.contentResolver
                    val fileName = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
                    
                    _uiState.update { state ->
                        state.copy(operationProgress = OperationProgress(
                            title = "Importing Files",
                            currentFile = fileName,
                            totalFiles = total,
                            processedFiles = index,
                            percentage = (index.toFloat() / total) * 100f,
                            speedMbps = calculateSpeed(startTime, itemsToDelete.sumOf { it.second }),
                            timeRemainingSeconds = calculateTimeRemaining(startTime, index, total)
                        ))
                    }

                    val originalSize = getFileSize(context, uri)
                    val originalPath = getFilePathFromUri(context, uri)
                    val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return@forEachIndexed
                    
                    val vaultDir = File(context.filesDir, ".vault")
                    if (!vaultDir.exists()) {
                        vaultDir.mkdirs()
                        File(vaultDir, ".nomedia").createNewFile()
                    }

                    val encryptedFileName = UUID.randomUUID().toString().replace("-", "")
                    val encryptedFile = File(vaultDir, encryptedFileName)
                    val outputStream = FileOutputStream(encryptedFile)
                    
                    val encryptedSize = cryptoManager.encryptStream(inputStream, outputStream)
                    inputStream.close()
                    outputStream.close()

                    val thumbFile = File(vaultDir, "thumb_${encryptedFile.name}.jpg")
                    generateThumbnail(context, uri, category, thumbFile)

                    val fileId = UUID.randomUUID().toString()
                    saveFileInfo(fileId, fileName, encryptedFile.absolutePath, category, encryptedSize, thumbFile.absolutePath, folderName)
                    
                    itemsToDelete.add(Triple(uri, originalSize, originalPath))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            _uiState.update { it.copy(operationProgress = null) }

            if (itemsToDelete.isNotEmpty()) {
                requestDeletion(context, itemsToDelete)
            }
            
            updateFileCounts()
        }
    }

    fun createFolder(name: String) {
        val folders = (prefs.getStringSet("custom_folders", emptySet()) ?: emptySet()).toMutableSet()
        folders.add(name)
        prefs.edit().putStringSet("custom_folders", folders).apply()
        updateFileCounts()
    }

    private fun calculateSpeed(startTime: Long, totalBytes: Long): Double {
        val elapsedMillis = System.currentTimeMillis() - startTime
        if (elapsedMillis <= 0) return 0.0
        val megabits = (totalBytes * 8) / 1_000_000.0
        return megabits / (elapsedMillis / 1000.0)
    }

    private fun calculateTimeRemaining(startTime: Long, processed: Int, total: Int): Long {
        if (processed <= 0) return 0L
        val elapsedMillis = System.currentTimeMillis() - startTime
        val millisPerItem = elapsedMillis / processed
        val remainingItems = total - processed
        return (remainingItems * millisPerItem) / 1000
    }

    private fun requestDeletion(context: Context, items: List<Triple<Uri, Long, String?>>) {
        // AUTOMATED SKIP: If user granted "All Files Access", we can delete directly without the system prompt.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            manualDelete(context, items)
            return
        }

        val mediaStoreUris = items.mapNotNull { (uri, size, _) ->
            resolveToMediaStoreUri(context, uri, size)
        }

        if (mediaStoreUris.isEmpty()) {
            manualDelete(context, items)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, mediaStoreUris)
                _uiState.update { it.copy(pendingDeleteIntent = pendingIntent) }
            } catch (e: Exception) {
                manualDelete(context, items)
            }
        } else {
            manualDelete(context, items)
        }
    }

    private fun manualDelete(context: Context, items: List<Triple<Uri, Long, String?>>) {
        items.forEach { (uri, _, path) ->
            try {
                val mediaStoreUri = resolveToMediaStoreUri(context, uri, 0L) ?: uri
                context.contentResolver.delete(mediaStoreUri, null, null)
                
                path?.let { p ->
                    val file = File(p)
                    if (file.exists()) file.delete()
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(p), null, null)
                }
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException) {
                    _uiState.update { it.copy(pendingDeleteIntent = e.userAction.actionIntent) }
                }
            }
        }
    }

    private fun resolveToMediaStoreUri(context: Context, uri: Uri, size: Long): Uri? {
        val uriString = uri.toString()
        if (uriString.contains("content://media/external/")) {
            return uri
        }

        // Handle Document URIs from MediaProvider or other providers
        if (uriString.contains("com.android.providers.media.documents") || uriString.contains("com.android.externalstorage.documents")) {
            try {
                val docId = android.provider.DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val id = split.last()
                val type = split.first().lowercase()
                
                val baseUri = when {
                    type.contains("image") || uriString.contains("images") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    type.contains("video") || uriString.contains("video") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    type.contains("audio") || uriString.contains("audio") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Files.getContentUri("external")
                }
                if (baseUri != null) {
                    return Uri.withAppendedPath(baseUri, id)
                }
            } catch (e: Exception) {}
        }

        try {
            val fileName = getFileName(context, uri) ?: return null
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val collections = mutableListOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                collections.add(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
            }
            collections.add(MediaStore.Files.getContentUri("external"))

            for (collection in collections) {
                val selection = if (size > 0) {
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.SIZE} = ?"
                } else {
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                }
                val selectionArgs = if (size > 0) arrayOf(fileName, size.toString()) else arrayOf(fileName)
                
                context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        return Uri.withAppendedPath(collection, id.toString())
                    }
                }
            }
        } catch (e: Exception) {}
        
        return null
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        return context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        } ?: 0L
    }

    fun clearPendingDelete() {
        _uiState.update { it.copy(pendingDeleteIntent = null) }
    }

    private fun refreshGallery(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        intent.data = uri
        context.sendBroadcast(intent)
        
        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(uri.path),
            null
        ) { path, scanUri -> }
    }

    private fun getFilePathFromUri(context: Context, uri: Uri): String? {
        if ("file" == uri.scheme) return uri.path
        
        val projection = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                    if (index != -1) return cursor.getString(index)
                }
            }
        } catch (e: Exception) {}
        
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
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val options = BitmapFactory.Options().apply { 
                            inJustDecodeBounds = true
                            BitmapFactory.decodeStream(input, null, this)
                            inSampleSize = calculateInSampleSize(this, 320, 320)
                            inJustDecodeBounds = false
                        }
                        // Need to reopen stream after inJustDecodeBounds
                        context.contentResolver.openInputStream(uri)?.use { input2 ->
                            BitmapFactory.decodeStream(input2, null, options)
                        }
                    }
                }
                FileCategory.VIDEO -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            context.contentResolver.loadThumbnail(uri, android.util.Size(320, 320), null)
                        } catch (e: Exception) {
                            getVideoFrameFallback(context, uri)
                        }
                    } else {
                        getVideoFrameFallback(context, uri)
                    }
                }
                FileCategory.DOCUMENT -> {
                    if (getFileName(context, uri)?.lowercase()?.endsWith(".pdf") == true) {
                        generatePdfThumbnail(context, uri)
                    } else null
                }
                else -> null
            }
            
            bitmap?.let {
                FileOutputStream(outputFile).use { out ->
                    it.compress(Bitmap.CompressFormat.JPEG, 75, out)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getVideoFrameFallback(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) { null } finally { retriever.release() }
    }

    private fun generatePdfThumbnail(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)
                            canvas.drawColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            ThumbnailUtils.extractThumbnail(bitmap, 320, 320)
                        }
                    } else null
                }
            }
        } catch (e: Exception) { null }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun saveFileInfo(id: String, name: String, path: String, category: FileCategory, size: Long, thumbPath: String? = null, folderName: String? = null) {
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
            folderName?.let { putString("file_${id}_folder", it) }
            apply()
        }
    }

    private fun updateFileCounts() {
        val fileIds = prefs.getStringSet("vault_file_ids", emptySet()) ?: emptySet()
        val customFolders = (prefs.getStringSet("custom_folders", emptySet()) ?: emptySet()).toList().sorted()
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
            val folderName = prefs.getString("file_${id}_folder", null)
            
            files.add(VaultFile(id, name, path, category, size, timestamp, thumbPath, folderName))

            if (folderName == null) {
                when (category) {
                    FileCategory.PHOTO -> photos++
                    FileCategory.VIDEO -> videos++
                    FileCategory.AUDIO -> audio++
                    FileCategory.DOCUMENT -> docs++
                    FileCategory.INTRUDER -> intruders++
                    else -> {}
                }
            }
        }
        
        _uiState.update { 
            it.copy(
                files = files.sortedByDescending { f -> f.addedTimestamp },
                customFolders = customFolders,
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

        val hasFullStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Legacy storage permission is enough for manual delete on older versions
        }

        _uiState.update { 
            it.copy(
                hasUsageStatsPermission = hasUsage,
                hasOverlayPermission = hasOverlay,
                hasCameraPermission = hasCamera,
                hasLocationPermission = hasLocation,
                hasStoragePermission = hasStorage,
                hasFullStoragePermission = hasFullStorage,
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

    fun openUsageStatsSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    fun openFullStorageSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:${getApplication<Application>().packageName}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(intent)
        }
    }

    fun openOverlaySettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${getApplication<Application>().packageName}"))
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
            } catch (e: Exception) {}
        }
        val fallback = Intent(Settings.ACTION_SETTINGS)
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(fallback)
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val lockedApps = getLockedApps()
            val apps = packages
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .filter { !lockedApps.contains(it.packageName) }
                .map { 
                    AppInfo(it.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm))
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
        offlineHelper.downloadRegion("https://tiles.openfreemap.org/styles/dark", bounds, 12.0, 16.0, "VaultArea", {}, {}, {})
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
            VaultConfig(id, GeoPoint(lat, lon), radius, lockType, secret, hiddenApps, timestamp)
        }
        
        val isLocked = prefs.getBoolean("is_locked", true)
        val isDarkMode = prefs.getBoolean("is_dark_mode", false)
        val isFirstRunLocal = prefs.getBoolean("is_first_run", true)
        val language = prefs.getString("language", "en") ?: "en"
        val isFingerprintEnabled = prefs.getBoolean("fingerprint_enabled", false)
        val isLanguageSelected = prefs.contains("language")
        _uiState.update { it.copy(
            vaults = vaults, 
            isLocked = isLocked, 
            isFingerprintEnabled = isFingerprintEnabled,
            isDarkMode = isDarkMode, 
            currentLanguage = language, 
            isFirstRun = isFirstRunLocal,
            isLanguageSelected = isLanguageSelected
        ) }
        com.geovault.security.LocaleManager.applyLanguage(language)
        loadInstalledApps()
    }

    fun saveVaultConfiguration(point: GeoPoint, secret: String, hiddenApps: Set<String>, lockType: LockType = LockType.PIN, radius: Float = 500f) {
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val vaultIds = (prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()).toMutableSet()
        vaultIds.add(id)
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
            apply()
        }
        ensureOffline(point)
        loadPersistedVaults()
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
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, com.geovault.service.AppLockerService::class.java)
        serviceIntent.putExtra("refresh_locked_apps", true)
        context.startService(serviceIntent)
    }

    fun onLocationChanged(latitude: Double, longitude: Double) {
        _uiState.update { it.copy(currentLocation = GeoPoint(latitude, longitude)) }
        if (!_uiState.value.isFirstRun) {
            ensureOffline(GeoPoint(latitude, longitude))
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
            LocationHelper.isWithinRadius(tapLat, tapLon, vault.location.latitude, vault.location.longitude, 100f)
        } ?: return false

        if (nearbyVault.radius > 0) {
            val current = _uiState.value.currentLocation
            if (current == null) return false
            val distance = LocationHelper.calculateDistance(current.latitude, current.longitude, nearbyVault.location.latitude, nearbyVault.location.longitude)
            if (distance > nearbyVault.radius) return false
        }

        if (pin == nearbyVault.secret) {
            prefs.edit().putBoolean("is_locked", false).apply()
            prefs.edit().putString("active_vault_id", nearbyVault.id).apply()
            _uiState.update { it.copy(isLocked = false, activeVaultId = nearbyVault.id) }
            return true
        }
        return false
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
        if (newHiddenApps.contains(packageName)) newHiddenApps.remove(packageName) else newHiddenApps.add(packageName)
        prefs.edit().putStringSet("vault_${vaultId}_apps", newHiddenApps).apply()
        loadPersistedVaults()
    }

    fun launchApp(packageName: String) {
        val context = getApplication<Application>()
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
        val intent = android.content.Intent(getApplication(), com.geovault.service.AppLockerService::class.java).apply { putExtra("refresh_locked_apps", true) }
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
        _uiState.update { it.copy(currentLanguage = langCode, isLanguageSelected = true) }
        com.geovault.security.LocaleManager.applyLanguage(langCode)
    }

    fun removeFileFromVault(fileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            com.geovault.security.SecureManager.getInstance(getApplication()).removeFileInfo(fileId)
            updateFileCounts()
        }
    }

    fun fetchGalleryItems(category: FileCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isFetchingGallery = true) }
            val items = mutableListOf<GalleryItem>()
            val context = getApplication<Application>()
            
            val collection = when (category) {
                FileCategory.PHOTO -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                FileCategory.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                FileCategory.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Files.getContentUri("external")
            }

            val projection = mutableListOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
            )
            
            if (category == FileCategory.VIDEO || category == FileCategory.AUDIO) {
                projection.add(MediaStore.MediaColumns.DURATION)
            }

            val selection = when (category) {
                FileCategory.DOCUMENT -> "${MediaStore.MediaColumns.MIME_TYPE} LIKE ? OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE ? OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE ? OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE ?"
                else -> null
            }
            val selectionArgs = when (category) {
                FileCategory.DOCUMENT -> arrayOf("application/pdf", "text/%", "application/msword", "application/vnd.openxmlformats-officedocument.%")
                else -> null
            }

            context.contentResolver.query(
                collection,
                projection.toTypedArray(),
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val bucketCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val durationCol = if (category == FileCategory.VIDEO || category == FileCategory.AUDIO) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    val name = cursor.getString(nameCol)
                    val date = cursor.getLong(dateCol)
                    val size = cursor.getLong(sizeCol)
                    val folder = if (bucketCol != -1) cursor.getString(bucketCol) ?: "Internal" else "Internal"
                    val duration = if (durationCol != -1) cursor.getLong(durationCol) else null
                    
                    items.add(GalleryItem(uri, name, date, size, folder, duration))
                }
            }
            
            _uiState.update { it.copy(galleryItems = items, isFetchingGallery = false) }
        }
    }

    fun restoreFileToGallery(fileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val secureManager = com.geovault.security.SecureManager.getInstance(context)
            val prefs = secureManager.prefs
            val fileName = prefs.getString("file_${fileId}_name", null) ?: return@launch
            
            _uiState.update { state ->
                state.copy(operationProgress = OperationProgress(
                    title = "Restoring File",
                    currentFile = fileName,
                    totalFiles = 1,
                    processedFiles = 0,
                    percentage = 0f
                ))
            }

            val encryptedPath = prefs.getString("file_${fileId}_path", null) ?: return@launch
            val categoryStr = prefs.getString("file_${fileId}_category", null) ?: return@launch
            var category = try { FileCategory.valueOf(categoryStr) } catch (e: Exception) { FileCategory.OTHER }
            
            // Fix for hidden folders: Detect real category for better restoration destination
            if (category == FileCategory.OTHER) {
                val mime = getMimeType(fileName)
                category = when {
                    mime.startsWith("image/") -> FileCategory.PHOTO
                    mime.startsWith("video/") -> FileCategory.VIDEO
                    mime.startsWith("audio/") -> FileCategory.AUDIO
                    else -> FileCategory.DOCUMENT
                }
            }

            try {
                val encryptedFile = File(encryptedPath)
                if (!encryptedFile.exists()) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Encrypted source file missing", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    _uiState.update { it.copy(operationProgress = null) }
                    return@launch
                }

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
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativePath/GeoVaultRestored")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val collection = when (category) {
                    FileCategory.PHOTO, FileCategory.INTRUDER -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    FileCategory.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    FileCategory.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.EXTERNAL_CONTENT_URI else MediaStore.Files.getContentUri("external")
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
                    
                    // Success! Now remove from vault
                    secureManager.removeFileInfo(fileId)
                    updateFileCounts()
                    
                    refreshGallery(context, uri)

                    withContext(Dispatchers.Main) { 
                        android.widget.Toast.makeText(context, "File restored to gallery", android.widget.Toast.LENGTH_SHORT).show() 
                    }
                } else {
                    throw Exception("Failed to create MediaStore entry")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { 
                    android.widget.Toast.makeText(context, "Restoration failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally {
                _uiState.update { it.copy(operationProgress = null) }
            }
        }
    }

    private fun getMimeType(fileName: String): String = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase()) ?: "*/*"
}
