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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

import com.geovault.map.OfflineMapHelper
import com.geovault.model.*
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID
import android.app.admin.DevicePolicyManager
import android.content.ContentValues
import android.os.Environment
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import com.geovault.R
import kotlinx.coroutines.withContext

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(VaultState())
    val uiState: StateFlow<VaultState> = _uiState.asStateFlow()

    private val _recreateEvent = MutableSharedFlow<Unit>()
    val recreateEvent = _recreateEvent.asSharedFlow()

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
        
        // Removed lock() from init to prevent re-locking on activity/process recreation 
        // (e.g. when granting system permissions or on configuration changes).
        // The locked state is already managed via loadPersistedVaults() and onPause().
        
        if (!prefs.getBoolean("is_first_run", true)) {
            startMapDownload()
        }
    }

    private fun createNoMediaFile() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val vaultDir = com.geovault.security.StorageManager.getVaultDir(context)
            if (!vaultDir.exists()) vaultDir.mkdirs()
            val noMedia = File(vaultDir, ".nomedia")
            if (!noMedia.exists()) {
                try { noMedia.createNewFile() } catch (e: Exception) {}
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun completeOnboarding() {
        prefs.edit().putBoolean("is_first_run", false).putBoolean("disclaimer_accepted", true).apply()
        _uiState.update { it.copy(isFirstRun = false, disclaimerAccepted = true, isMapDownloading = false, showTour = false) }
        startMapDownload()
    }

    fun completeTour() {
        prefs.edit().putBoolean("tour_completed", true).apply()
        _uiState.update { it.copy(showTour = false) }
    }

    fun addIntruderFile(uri: Uri, thumbPath: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileId = UUID.randomUUID().toString()
            val name = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US).format(java.util.Date())
            val fileName = "Intruder_$name.jpg"
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
                putBoolean("fingerprint_enabled", true)
                putBoolean("master_stealth_enabled", false)
                putBoolean("intruder_capture_enabled", false)
                putBoolean("screenshot_restriction", false)
                apply()
            }
        }
        _uiState.update { it.copy(isFirstRun = isFirstRun) }
    }

    private fun startMapDownload() {
        if (!isNetworkAvailable(getApplication())) {
            _uiState.update { it.copy(isNetworkAvailable = false, isMapDownloading = false) }
            return
        }

        // FAST LOAD: Don't show full screen loader if we've been here before
        if (prefs.getBoolean("map_initialized", false)) {
            _uiState.update { it.copy(isMapDownloading = false, isMapLoaded = true) }
        } else {
            _uiState.update { it.copy(isMapDownloading = true) }
        }

        _uiState.update { it.copy(isNetworkAvailable = true) }

        // Proactive Local Download: Focus ONLY on current location for speed
        com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(getApplication<Application>()).lastLocation.addOnSuccessListener { location ->
            location?.let { 
                val geoPoint = GeoPoint(it.latitude, it.longitude)
                viewModelScope.launch {
                    ensureOffline(geoPoint, isInitial = true)
                }
            } ?: run {
                // If no location yet, just let them in after a tiny delay
                viewModelScope.launch {
                    delay(500)
                    _uiState.update { it.copy(isMapDownloading = false) }
                }
            }
        }.addOnFailureListener {
            _uiState.update { it.copy(isMapDownloading = false) }
        }
        
        // ULTIMATE SAFETY: Never block the user for more than 3 seconds
        viewModelScope.launch {
            delay(3000)
            if (_uiState.value.isMapDownloading) {
                _uiState.update { it.copy(isMapDownloading = false) }
            }
        }
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
                    _uiState.update { state -> state.copy(operationProgress = OperationProgress("Importing Files", fileName, total, index, (index.toFloat() / total) * 100f)) }
                    val originalSize = getFileSize(context, uri)
                    val originalPath = getFilePathFromUri(context, uri)
                    val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return@forEachIndexed
                    val vaultDir = com.geovault.security.StorageManager.getVaultDir(context)
                    if (!vaultDir.exists()) vaultDir.mkdirs()
                    val encryptedFileName = UUID.randomUUID().toString().replace("-", "")
                    val encryptedFile = File(vaultDir, encryptedFileName)
                    val outputStream = FileOutputStream(encryptedFile)
                    val encryptedSize = cryptoManager.encryptStream(inputStream, outputStream) { bytesProcessed ->
                        val subProgress = if (originalSize > 0) (bytesProcessed.toFloat() / originalSize) else 0f
                        val overallPercentage = ((index.toFloat() + subProgress) / total) * 100f
                        _uiState.update { state -> state.copy(operationProgress = state.operationProgress?.copy(percentage = overallPercentage)) }
                    }
                    inputStream.close()
                    outputStream.close()
                    val thumbFile = File(vaultDir, "thumb_${encryptedFile.name}.jpg")
                    generateThumbnail(context, uri, category, thumbFile)
                    val fileId = UUID.randomUUID().toString()
                    saveFileInfo(fileId, fileName, encryptedFile.absolutePath, category, encryptedSize, thumbFile.absolutePath, folderName)
                    itemsToDelete.add(Triple(uri, originalSize, originalPath))
                } catch (e: Exception) {}
            }
            _uiState.update { it.copy(operationProgress = null) }
            if (itemsToDelete.isNotEmpty()) requestDeletion(context, itemsToDelete)
            updateFileCounts()
        }
    }

    fun createFolder(name: String) {
        val folders = (prefs.getStringSet("custom_folders", emptySet()) ?: emptySet()).toMutableSet()
        folders.add(name)
        prefs.edit().putStringSet("custom_folders", folders).apply()
        updateFileCounts()
    }

    fun deleteFolder(name: String, recoverFiles: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val folders = (prefs.getStringSet("custom_folders", emptySet()) ?: emptySet()).toMutableSet()
            folders.remove(name)
            prefs.edit().putStringSet("custom_folders", folders).apply()

            val folderFiles = _uiState.value.files.filter { it.folderName == name }
            folderFiles.forEach { file ->
                if (recoverFiles) {
                    restoreFileToGallerySync(file.id)
                } else {
                    removeFileFromVault(file.id)
                }
            }
            updateFileCounts()
        }
    }

    fun bulkDeleteFiles(fileIds: Set<String>, isPermanent: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            fileIds.forEach { id ->
                if (isPermanent) {
                    com.geovault.security.SecureManager.getInstance(getApplication()).removeFileInfo(id)
                } else {
                    // Move to Recycle Bin instead of deleting
                    com.geovault.security.SecureManager.getInstance(getApplication()).updateFileCategory(id, FileCategory.RECYCLE_BIN)
                }
            }
            updateFileCounts()
        }
    }

    fun bulkRestoreFiles(fileIds: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            fileIds.forEach { id ->
                restoreFileToGallerySync(id)
            }
            updateFileCounts()
        }
    }

    private fun requestDeletion(context: Context, items: List<Triple<Uri, Long, String?>>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            manualDelete(context, items)
            return
        }
        val mediaStoreUris = items.mapNotNull { (uri, size, _) -> resolveToMediaStoreUri(context, uri, size) }
        if (mediaStoreUris.isEmpty()) {
            manualDelete(context, items)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, mediaStoreUris)
                _uiState.update { it.copy(pendingDeleteIntent = pendingIntent) }
            } catch (e: Exception) { manualDelete(context, items) }
        } else { manualDelete(context, items) }
    }

    private fun manualDelete(context: Context, items: List<Triple<Uri, Long, String?>>) {
        items.forEach { (uri, _, path) ->
            try {
                val mediaStoreUri = resolveToMediaStoreUri(context, uri, 0L) ?: uri
                context.contentResolver.delete(mediaStoreUri, null, null)
                path?.let { p -> File(p).delete(); android.media.MediaScannerConnection.scanFile(context, arrayOf(p), null, null) }
            } catch (e: Exception) {}
        }
    }

    private fun resolveToMediaStoreUri(context: Context, uri: Uri, size: Long): Uri? {
        val uriString = uri.toString()
        if (uriString.contains("content://media/external/")) return uri
        try {
            val fileName = getFileName(context, uri) ?: return null
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val collections = mutableListOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) collections.add(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
            collections.add(MediaStore.Files.getContentUri("external"))
            for (collection in collections) {
                val selection = if (size > 0) "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.SIZE} = ?" else "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                val selectionArgs = if (size > 0) arrayOf(fileName, size.toString()) else arrayOf(fileName)
                context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) return Uri.withAppendedPath(collection, cursor.getLong(0).toString())
                }
            }
        } catch (e: Exception) {}
        return null
    }

    private fun getFileSize(context: Context, uri: Uri): Long = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else 0L } ?: 0L

    fun clearPendingDelete() { _uiState.update { it.copy(pendingDeleteIntent = null) } }

    private fun getFilePathFromUri(context: Context, uri: Uri): String? {
        if ("file" == uri.scheme) return uri.path
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
        } catch (e: Exception) {}
        return null
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { if (it.moveToFirst()) result = it.getString(it.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }

    private fun generateThumbnail(context: Context, uri: Uri, category: FileCategory, outputFile: File) {
        try {
            val bitmap = when (category) {
                FileCategory.PHOTO, FileCategory.INTRUDER -> context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 }) }
                FileCategory.VIDEO -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) context.contentResolver.loadThumbnail(uri, android.util.Size(320, 320), null) else getVideoFrameFallback(context, uri)
                FileCategory.DOCUMENT -> if (getFileName(context, uri)?.lowercase()?.endsWith(".pdf") == true) generatePdfThumbnail(context, uri) else null
                else -> null
            }
            bitmap?.let { FileOutputStream(outputFile).use { out -> it.compress(Bitmap.CompressFormat.JPEG, 75, out) } }
        } catch (e: Exception) {}
    }

    private fun getVideoFrameFallback(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try { retriever.setDataSource(context, uri); retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) } catch (e: Exception) { null } finally { retriever.release() }
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

    private fun saveFileInfo(id: String, name: String, path: String, category: FileCategory, size: Long, thumbPath: String? = null, folderName: String? = null) {
        val fileIds = (prefs.getStringSet("vault_file_ids", emptySet()) ?: emptySet()).toMutableSet()
        fileIds.add(id)
        val activeVaultId = _uiState.value.activeVaultId
        prefs.edit().apply {
            putStringSet("vault_file_ids", fileIds)
            putString("file_${id}_name", name)
            putString("file_${id}_path", path)
            putString("file_${id}_category", category.name)
            putLong("file_${id}_size", size)
            putLong("file_${id}_timestamp", System.currentTimeMillis())
            thumbPath?.let { putString("file_${id}_thumb", it) }
            folderName?.let { putString("file_${id}_folder", it) }
            putString("file_${id}_vault_id", activeVaultId)
            apply()
        }
    }

    private fun updateFileCounts() {
        val fileIds = prefs.getStringSet("vault_file_ids", emptySet()) ?: emptySet()
        val customFolders = (prefs.getStringSet("custom_folders", emptySet()) ?: emptySet()).toList().sorted()
        val activeVaultId = _uiState.value.activeVaultId
        val files = mutableListOf<VaultFile>()
        var photos = 0; var videos = 0; var audio = 0; var docs = 0; var intruders = 0; var trashed = 0
        fileIds.forEach { id ->
            val vaultId = prefs.getString("file_${id}_vault_id", null)
            // Filter files by active vault ID
            if (vaultId != activeVaultId) return@forEach

            val name = prefs.getString("file_${id}_name", "") ?: ""
            val path = prefs.getString("file_${id}_path", "") ?: ""
            val catStr = prefs.getString("file_${id}_category", "") ?: ""
            val category = try { FileCategory.valueOf(catStr) } catch (e: Exception) { FileCategory.OTHER }
            val thumbPath = prefs.getString("file_${id}_thumb", null)
            val folderName = prefs.getString("file_${id}_folder", null)
            val file = VaultFile(id, name, path, category, prefs.getLong("file_${id}_size", 0), prefs.getLong("file_${id}_timestamp", 0), thumbPath, folderName, vaultId)
            files.add(file)
            
            if (category == FileCategory.RECYCLE_BIN) {
                trashed++
            } else if (folderName == null) {
                when (category) {
                    FileCategory.PHOTO -> photos++; FileCategory.VIDEO -> videos++; FileCategory.AUDIO -> audio++; FileCategory.DOCUMENT -> docs++; FileCategory.INTRUDER -> intruders++
                    else -> {}
                }
            }
        }
        _uiState.update { it.copy(files = files.sortedByDescending { f -> f.addedTimestamp }, customFolders = customFolders, photoCount = photos, videoCount = videos, audioCount = audio, documentCount = docs, intruderCount = intruders, recycleBinCount = trashed) }
    }

    private fun ensureMainActivityEnabled() {
        val context = getApplication<Application>()
        context.packageManager.setComponentEnabledSetting(ComponentName(context, "com.geovault.MainActivity"), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        val hasUsage = hasUsageStatsPermission(context)
        val hasOverlay = Settings.canDrawOverlays(context)
        val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasLocation = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        val hasBattery = hasBatteryOptimizationPermission(context)
        val hasBackgroundPopups = prefs.getBoolean("perm_background_popups", false)
        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        val hasFullStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        
        if (hasLocation && !_uiState.value.hasLocationPermission) startMapDownload()
        _uiState.update {
            it.copy(
                hasUsageStatsPermission = hasUsage,
                hasOverlayPermission = hasOverlay,
                hasCameraPermission = hasCamera,
                hasLocationPermission = hasLocation,
                hasBackgroundLocationPermission = hasBackgroundLocation,
                hasStoragePermission = hasStorage,
                hasFullStoragePermission = hasFullStorage,
                hasBatteryOptimizationPermission = hasBattery,
                hasBackgroundPopupsPermission = hasBackgroundPopups,
                hasNotificationPermission = hasNotifications
            )
        }
    }

    fun toggleUninstallShield(enable: Boolean) {
        val context = getApplication<Application>()
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, com.geovault.security.UninstallShieldReceiver::class.java)
        
        if (enable) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Protects your hidden files from accidental deletion.")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            setPerformingAction(true)
            context.startActivity(intent)
        } else {
            dpm.removeActiveAdmin(adminComponent)
            checkPermissions()
        }
    }

    fun restoreEverythingAndUninstall() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val fileIds = prefs.getStringSet("vault_file_ids", emptySet()) ?: emptySet()
            
            // Restore all files
            fileIds.forEach { id ->
                restoreFileToGallerySync(id)
            }
            
            withContext(Dispatchers.Main) {
                // Deactivate Admin
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(context, com.geovault.security.UninstallShieldReceiver::class.java)
                dpm.removeActiveAdmin(adminComponent)
                
                // Trigger Uninstall
                val intent = Intent(Intent.ACTION_DELETE)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPerformingAction(true)
                context.startActivity(intent)
            }
        }
    }

    private fun restoreFileToGallerySync(fileId: String) {
        val context = getApplication<Application>()
        val fileName = prefs.getString("file_${fileId}_name", null) ?: return
        val encryptedPath = prefs.getString("file_${fileId}_path", null) ?: return
        val categoryStr = prefs.getString("file_${fileId}_category", "OTHER") ?: "OTHER"
        val category = try { FileCategory.valueOf(categoryStr) } catch (e: Exception) { FileCategory.OTHER }
        
        try {
            val encryptedFile = File(encryptedPath)
            if (!encryptedFile.exists()) return
            
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase()) ?: "*/*")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val relPath = when (category) { 
                        FileCategory.PHOTO -> Environment.DIRECTORY_PICTURES
                        FileCategory.VIDEO -> Environment.DIRECTORY_MOVIES
                        FileCategory.AUDIO -> Environment.DIRECTORY_MUSIC
                        else -> Environment.DIRECTORY_DOWNLOADS 
                    }
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$relPath/GeoVaultRestored")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            
            val collection = when (category) { 
                FileCategory.PHOTO -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                FileCategory.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                FileCategory.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.EXTERNAL_CONTENT_URI else MediaStore.Files.getContentUri("external")
            }
            
            context.contentResolver.insert(collection, contentValues)?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    cryptoManager.decryptToStream(encryptedFile.inputStream(), os) 
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }
                com.geovault.security.SecureManager.getInstance(context).removeFileInfo(fileId)
            }
        } catch (e: Exception) {}
    }

    private fun hasBatteryOptimizationPermission(context: Context): Boolean = (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(context.packageName)

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) else appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageStatsSettings() { getApplication<Application>().startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    fun openFullStorageSettings() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) getApplication<Application>().startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${getApplication<Application>().packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    fun openOverlaySettings() { getApplication<Application>().startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${getApplication<Application>().packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    fun openProtectedAppsSettings() {
        val context = getApplication<Application>()
        
        // Try manufacturer specific settings first (Realme/Oppo/Vivo/Xiaomi)
        val intents = listOf(
            // Realme / Oppo Auto-start
            Intent().apply { component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity") },
            Intent().apply { component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity") },
            Intent().apply { component = ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity") },
            // Xiaomi Auto-start
            Intent().apply { component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity") },
            // Vivo Auto-start
            Intent().apply { component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity") },
            Intent().apply { component = ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager") },
            // Samsung
            Intent().apply { component = ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity") },
            Intent().apply { component = ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.ram.AutoRunActivity") }
        )

        var opened = false
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                opened = true
                break
            } catch (e: Exception) {}
        }

        if (opened) {
            Toast.makeText(context, "Find '${context.getString(R.string.app_name)}' and enable 'Auto-start' / 'Background Running'", Toast.LENGTH_LONG).show()
        } else {
            // Fallback to standard battery optimization
            if (!hasBatteryOptimizationPermission(context)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                } catch (e: Exception) {}
            }
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openBackgroundPopupSettings() {
        val context = getApplication<Application>()
        val packageName = context.packageName

        val intents = listOf(
            // Xiaomi / MIUI
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", packageName)
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            },
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", packageName)
            },
            // Vivo
            Intent().apply {
                component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity")
                putExtra("packagename", packageName)
            },
            // Oppo / Realme
            Intent("com.coloros.safecenter.permission.PermissionManagerActivity"),
            // General App Info as fallback
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )

        var opened = false
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                opened = true
                break
            } catch (e: Exception) {}
        }

        if (!opened) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {}
        }

        setShowBackgroundPopupGuide(false)
        
        // Mark as "Checked" for UI purposes
        prefs.edit().putBoolean("perm_background_popups", true).apply()
        checkPermissions()
    }

    fun setShowBackgroundPopupGuide(show: Boolean) {
        _uiState.update {
            it.copy(showBackgroundPopupGuide = show)
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val lockedApps = getLockedApps()
            val apps = packages.filter { pm.getLaunchIntentForPackage(it.packageName) != null }.filter { !lockedApps.contains(it.packageName) }.map { AppInfo(it.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }.sortedBy { it.appName }
            _uiState.update { it.copy(installedApps = apps) }
        }
    }

    private fun getLockedApps(): Set<String> {
        val allLockedApps = mutableSetOf<String>()
        prefs.getStringSet("vault_ids", emptySet())?.forEach { id -> allLockedApps.addAll(prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet()) }
        return allLockedApps
    }

    private fun ensureOffline(location: GeoPoint, isInitial: Boolean = false) {
        // ACCELERATED DOWNLOAD: Increased radius for better initial coverage
        val offset = if (isInitial) 0.05 else 0.08 // 5km vs 8km
        val bounds = LatLngBounds.Builder()
            .include(LatLng(location.latitude + offset, location.longitude + offset))
            .include(LatLng(location.latitude - offset, location.longitude - offset))
            .build()
        
        offlineHelper.downloadRegion(
            styleUrl = "https://tiles.openfreemap.org/styles/dark",
            bounds = bounds,
            minZoom = 10.0,
            maxZoom = 15.0,
            regionName = "Vault_${location.latitude}_${location.longitude}",
            onProgress = {},
            onComplete = {
                if (isInitial) {
                    prefs.edit().putBoolean("map_initialized", true).apply()
                    _uiState.update { it.copy(isMapLoaded = true, isMapDownloading = false) }
                }
            },
            onError = {
                if (isInitial) {
                    _uiState.update { it.copy(isMapDownloading = false) }
                }
            }
        )
    }

    private fun loadPersistedVaults() {
        val vaultIds = prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()
        val vaults = vaultIds.mapNotNull { id ->
            val lat = prefs.getFloat("vault_${id}_lat", -1000f).toDouble()
            if (lat == -1000.0) return@mapNotNull null
            VaultConfig(id, GeoPoint(lat, prefs.getFloat("vault_${id}_lon", 0f).toDouble()), prefs.getFloat("vault_${id}_radius", 500f), LockType.valueOf(prefs.getString("vault_${id}_lock_type", "PIN") ?: "PIN"), prefs.getString("vault_${id}_secret", "") ?: "", prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet(), prefs.getLong("vault_${id}_timestamp", 0L))
        }
        val language = com.geovault.security.LocaleManager.getLanguage(getApplication())
        _uiState.update { it.copy(
            vaults = vaults, 
            isLocked = prefs.getBoolean("is_locked", true), 
            activeVaultId = prefs.getString("active_vault_id", null), 
            isFingerprintEnabled = prefs.getBoolean("fingerprint_enabled", true), 
            isDarkMode = prefs.getBoolean("is_dark_mode", false), 
            isScreenshotRestricted = prefs.getBoolean("screenshot_restriction", false),
            isIntruderCaptureEnabled = prefs.getBoolean("intruder_capture_enabled", false),
            currentLanguage = language, 
            isFirstRun = prefs.getBoolean("is_first_run", true), 
            disclaimerAccepted = prefs.getBoolean("disclaimer_accepted", false),
            isLanguageSelected = prefs.contains("language") || language != "en",
            customBackgroundPath = prefs.getString("lock_background_path", null)
        ) }
        com.geovault.security.LocaleManager.applyLanguage(getApplication(), language)
        loadInstalledApps()
    }

    fun saveVaultConfiguration(point: GeoPoint, secret: String, hiddenApps: Set<String>, lockType: LockType = LockType.PIN, radius: Float = 500f) {
        val vaultIds = (prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()).toMutableSet()
        if (vaultIds.size >= 2) return
        val id = UUID.randomUUID().toString()
        val appsToLock = hiddenApps.toMutableSet().apply { 
            remove("com.android.settings")
            remove("com.android.vending")
            remove("com.google.android.vending")
        }
        vaultIds.add(id)
        prefs.edit().apply { putStringSet("vault_ids", vaultIds); putFloat("vault_${id}_lat", point.latitude.toFloat()); putFloat("vault_${id}_lon", point.longitude.toFloat()); putFloat("vault_${id}_radius", radius); putString("vault_${id}_lock_type", lockType.name); putString("vault_${id}_secret", secret); putStringSet("vault_${id}_apps", appsToLock); putLong("vault_${id}_timestamp", System.currentTimeMillis()); putBoolean("is_locked", false); putString("active_vault_id", id); commit() }
        ensureOffline(point); loadPersistedVaults(); notifyServiceToRefresh()
    }

    fun clearAllVaults() {
        prefs.getStringSet("vault_ids", emptySet())?.forEach { id -> prefs.edit().remove("vault_${id}_lat").remove("vault_${id}_lon").remove("vault_${id}_lock_type").remove("vault_${id}_secret").remove("vault_${id}_apps").remove("vault_${id}_timestamp").apply() }
        prefs.edit().putStringSet("vault_ids", emptySet()).putBoolean("is_locked", true).apply(); loadPersistedVaults(); notifyServiceToRefresh()
    }

    private var lastLocationFetchTime = 0L
    private var lastLocationEnsured: GeoPoint? = null

    fun onLocationChanged(latitude: Double, longitude: Double) {
        val now = System.currentTimeMillis()
        val currentPoint = GeoPoint(latitude, longitude)
        
        // BATTERY OPTIMIZATION: Throttle location-based updates (once every 2 mins or if moved significantly)
        val hasMovedSignificantly = lastLocationEnsured?.let { 
            LocationHelper.calculateDistance(it.latitude, it.longitude, latitude, longitude) > 500f // 500m
        } ?: true

        val isIndia = latitude in 8.4..37.6 && longitude in 68.7..97.2
        _uiState.update { it.copy(currentLocation = currentPoint, isIndiaRegion = isIndia) }
        
        if (now - lastLocationFetchTime > 120000 || hasMovedSignificantly) {
            lastLocationFetchTime = now
            lastLocationEnsured = currentPoint
            
            if (!_uiState.value.isFirstRun) ensureOffline(currentPoint)
            
            // Fetch weather and AQI if network is available
            if (isNetworkAvailable(getApplication())) {
                fetchWeatherAndAQI(latitude, longitude)
            }
        }
    }

    fun fetchWeatherAndAQI(lat: Double, lon: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Get City Name (Reverse Geocoding)
                val cityUrl = URL("https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json")
                val cityConn = cityUrl.openConnection()
                cityConn.setRequestProperty("User-Agent", "GeoVault")
                val cityResponse = cityConn.getInputStream().bufferedReader().use { it.readText() }
                val cityJson = JSONObject(cityResponse)
                val address = cityJson.optJSONObject("address")
                val city = address?.optString("city") ?: address?.optString("town") ?: address?.optString("village") ?: "Unknown"

                // 2. Get Weather (Temperature & Humidity)
                val weatherUrl = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m")
                val weatherResponse = weatherUrl.openStream().bufferedReader().use { it.readText() }
                val weatherJson = JSONObject(weatherResponse)
                val current = weatherJson.getJSONObject("current")
                val temp = current.getDouble("temperature_2m")
                val humidity = current.getInt("relative_humidity_2m")

                // 3. Get AQI
                val aqiUrl = URL("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=us_aqi")
                val aqiResponse = aqiUrl.openStream().bufferedReader().use { it.readText() }
                val aqiJson = JSONObject(aqiResponse)
                val aqiCurrent = aqiJson.getJSONObject("current")
                val aqi = aqiCurrent.getInt("us_aqi")

                _uiState.update { state ->
                    state.copy(weatherInfo = WeatherInfo(
                        temperature = temp,
                        humidity = humidity,
                        aqi = aqi,
                        cityName = city,
                        lastUpdated = System.currentTimeMillis()
                    ))
                }
            } catch (e: Exception) {
                // Keep old data if fetch fails
            }
        }
    }

    fun attemptUnlockAtLocation(tapLat: Double, tapLon: Double, pin: String): Boolean {
        val nearbyVault = _uiState.value.vaults.find { LocationHelper.isWithinRadius(tapLat, tapLon, it.location.latitude, it.location.longitude, 100f) } ?: return false
        if (nearbyVault.radius > 0) {
            val current = _uiState.value.currentLocation ?: return false
            if (LocationHelper.calculateDistance(current.latitude, current.longitude, nearbyVault.location.latitude, nearbyVault.location.longitude) > nearbyVault.radius) return false
        }
        if (pin == nearbyVault.secret) { 
            prefs.edit().putBoolean("is_locked", false).putString("active_vault_id", nearbyVault.id).apply()
            _uiState.update { it.copy(isLocked = false, activeVaultId = nearbyVault.id) }
            updateFileCounts()
            return true 
        }
        return false
    }

    fun lock() { 
        prefs.edit().putBoolean("is_locked", true).remove("active_vault_id").apply()
        _uiState.update { it.copy(isLocked = true, activeVaultId = null) }
        updateFileCounts()
    }

    fun removeVault(id: String) {
        val vaultIds = (prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()).toMutableSet()
        vaultIds.remove(id); prefs.edit().putStringSet("vault_ids", vaultIds).apply(); loadPersistedVaults(); notifyServiceToRefresh()
    }

    fun removeAppFromSpecificVault(vaultId: String, packageName: String) {
        val vault = _uiState.value.vaults.find { it.id == vaultId } ?: return
        val newHiddenApps = vault.hiddenApps.toMutableSet().apply { remove(packageName) }
        prefs.edit().putStringSet("vault_${vaultId}_apps", newHiddenApps).commit(); loadPersistedVaults(); notifyServiceToRefresh()
    }

    private fun notifyServiceToRefresh() {
        val context = getApplication<Application>()
        context.startService(Intent(context, com.geovault.service.AppLockerService::class.java).apply { putExtra("refresh_locked_apps", true) })
        context.startService(Intent(context, com.geovault.service.WindowChangeDetector::class.java).apply { putExtra("refresh_locked_apps", true) })
    }

    fun toggleAppLock(packageName: String) {
        val vaultId = _uiState.value.activeVaultId ?: return
        val vault = _uiState.value.vaults.find { it.id == vaultId } ?: return
        val newHiddenApps = vault.hiddenApps.toMutableSet().apply { if (contains(packageName)) remove(packageName) else add(packageName) }
        prefs.edit().putStringSet("vault_${vaultId}_apps", newHiddenApps).commit(); loadPersistedVaults(); notifyServiceToRefresh()
    }

    fun launchApp(packageName: String) {
        val context = getApplication<Application>()
        prefs.edit().putString("bypass_package", packageName).apply()
        context.packageManager.getLaunchIntentForPackage(packageName)?.apply { 
            setPerformingAction(true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(this) 
        }
    }

    fun toggleMasterStealth() {
        val newValue = !_uiState.value.isMasterStealthEnabled
        prefs.edit().putBoolean("master_stealth_enabled", newValue).apply(); _uiState.update { it.copy(isMasterStealthEnabled = newValue) }
        getApplication<Application>().startService(Intent(getApplication(), com.geovault.service.AppLockerService::class.java).apply { putExtra("refresh_locked_apps", true) })
    }

    fun toggleDarkMode() { val newValue = !_uiState.value.isDarkMode; prefs.edit().putBoolean("is_dark_mode", newValue).apply(); _uiState.update { it.copy(isDarkMode = newValue) } }
    fun toggleScreenshotRestriction() { val newValue = !_uiState.value.isScreenshotRestricted; prefs.edit().putBoolean("screenshot_restriction", newValue).apply(); _uiState.update { it.copy(isScreenshotRestricted = newValue) } }
    fun toggleIntruderCapture(enabled: Boolean) { prefs.edit().putBoolean("intruder_capture_enabled", enabled).apply(); _uiState.update { it.copy(isIntruderCaptureEnabled = enabled) } }
    fun toggleFingerprint() { val newValue = !_uiState.value.isFingerprintEnabled; prefs.edit().putBoolean("fingerprint_enabled", newValue).apply(); _uiState.update { it.copy(isFingerprintEnabled = newValue) } }

    fun setLanguage(langCode: String) { 
        prefs.edit().putString("language", langCode).apply()
        _uiState.update { it.copy(currentLanguage = langCode, isLanguageSelected = true) }
        com.geovault.security.LocaleManager.applyLanguage(getApplication(), langCode) 
        viewModelScope.launch { _recreateEvent.emit(Unit) }
    }

    fun resetLanguageSelection() {
        _uiState.update { it.copy(isLanguageSelected = false) }
    }

    fun setCustomBackground(path: String?) {
        if (path == null) {
            prefs.edit().remove("lock_background_path").apply()
        } else {
            prefs.edit().putString("lock_background_path", path).apply()
        }
        _uiState.update { it.copy(customBackgroundPath = path) }
    }

    fun removeFileFromVault(fileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val catStr = prefs.getString("file_${fileId}_category", "") ?: ""
            val category = try { FileCategory.valueOf(catStr) } catch (e: Exception) { FileCategory.OTHER }
            
            if (category == FileCategory.RECYCLE_BIN) {
                com.geovault.security.SecureManager.getInstance(getApplication()).removeFileInfo(fileId)
            } else {
                com.geovault.security.SecureManager.getInstance(getApplication()).updateFileCategory(fileId, FileCategory.RECYCLE_BIN)
            }
            updateFileCounts()
        }
    }

    fun fetchGalleryItems(category: FileCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isFetchingGallery = true) }
            val items = mutableListOf<GalleryItem>()
            val context = getApplication<Application>()
            val collection = when (category) { FileCategory.PHOTO -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI; FileCategory.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI; FileCategory.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI; else -> MediaStore.Files.getContentUri("external") }
            val projection = mutableListOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME).apply { if (category == FileCategory.VIDEO || category == FileCategory.AUDIO) add(MediaStore.MediaColumns.DURATION) }
            val selection = when (category) { FileCategory.DOCUMENT -> "${MediaStore.MediaColumns.MIME_TYPE} LIKE ? OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE ?"; else -> null }
            val selectionArgs = when (category) { FileCategory.DOCUMENT -> arrayOf("application/pdf", "text/%"); else -> null }

            context.contentResolver.query(collection, projection.toTypedArray(), selection, selectionArgs, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val bucketCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val durCol = if (category == FileCategory.VIDEO || category == FileCategory.AUDIO) cursor.getColumnIndex(MediaStore.MediaColumns.DURATION) else -1

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol)
                    val uri = Uri.withAppendedPath(collection, cursor.getLong(idCol).toString())
                    val thumbPath = if (category == FileCategory.DOCUMENT && name.lowercase().endsWith(".pdf")) File(context.cacheDir, "temp_thumb_${name}.jpg").absolutePath else null
                    if (thumbPath != null) {
                        val thumbFile = File(thumbPath)
                        if (!thumbFile.exists()) generatePdfThumbnail(context, uri)?.let { bitmap -> FileOutputStream(thumbFile).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 70, it) } }
                    }
                    items.add(GalleryItem(uri, name, cursor.getLong(dateCol), cursor.getLong(sizeCol), if (bucketCol != -1) cursor.getString(bucketCol) ?: "Internal" else "Internal", if (durCol != -1) cursor.getLong(durCol) else null, thumbPath))
                }
            }
            _uiState.update { it.copy(galleryItems = items, isFetchingGallery = false) }
        }
    }

    fun restoreFileToGallery(fileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val secureManager = com.geovault.security.SecureManager.getInstance(context)
            val fileName = secureManager.prefs.getString("file_${fileId}_name", null) ?: return@launch
            _uiState.update { it.copy(operationProgress = OperationProgress("Restoring File", fileName, 1, 0, 0f)) }
            val encryptedPath = secureManager.prefs.getString("file_${fileId}_path", null) ?: return@launch
            val categoryStr = secureManager.prefs.getString("file_${fileId}_category", "OTHER") ?: "OTHER"
            val category = try { FileCategory.valueOf(categoryStr) } catch (e: Exception) { FileCategory.OTHER }
            try {
                val encryptedFile = File(encryptedPath)
                if (!encryptedFile.exists()) return@launch
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase()) ?: "*/*")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val relPath = when (category) { FileCategory.PHOTO -> Environment.DIRECTORY_PICTURES; FileCategory.VIDEO -> Environment.DIRECTORY_MOVIES; FileCategory.AUDIO -> Environment.DIRECTORY_MUSIC; else -> Environment.DIRECTORY_DOWNLOADS }
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "$relPath/GeoVaultRestored")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                val collection = when (category) { FileCategory.PHOTO -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI; FileCategory.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI; FileCategory.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI; else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.EXTERNAL_CONTENT_URI else MediaStore.Files.getContentUri("external") }
                context.contentResolver.insert(collection, contentValues)?.let { uri ->
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        val totalSize = encryptedFile.length()
                        cryptoManager.decryptToStream(encryptedFile.inputStream(), os) { bytesProcessed ->
                            val p = if (totalSize > 0) (bytesProcessed.toFloat() / totalSize) * 100f else 0f
                            _uiState.update { state -> state.copy(operationProgress = state.operationProgress?.copy(percentage = p)) }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { contentValues.clear(); contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0); context.contentResolver.update(uri, contentValues, null, null) }
                    secureManager.removeFileInfo(fileId); updateFileCounts(); android.media.MediaScannerConnection.scanFile(context, arrayOf(uri.path), null, null)
                }
            } catch (e: Exception) {} finally { _uiState.update { it.copy(operationProgress = null) } }
        }
    }
}
