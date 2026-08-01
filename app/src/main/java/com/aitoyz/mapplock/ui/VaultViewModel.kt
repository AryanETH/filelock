package com.aitoyz.mapplock.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.Manifest
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
import com.aitoyz.mapplock.location.LocationHelper
import com.aitoyz.mapplock.model.AppInfo
import com.aitoyz.mapplock.model.GeoPoint
import com.aitoyz.mapplock.model.VaultState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

import com.aitoyz.mapplock.map.OfflineMapHelper
import com.aitoyz.mapplock.service.AppLockerService
import com.aitoyz.mapplock.model.*
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID
import com.aitoyz.mapplock.core.VirtualAppManager
import com.aitoyz.mapplock.core.AppCloner
import android.app.admin.DevicePolicyManager
import android.content.ContentValues
import android.os.Environment
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.edit
import com.aitoyz.mapplock.R
import kotlinx.coroutines.withContext
import com.posthog.PostHog
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
class VaultViewModel(application: Application) : AndroidViewModel(application) {
    val virtualAppManager = VirtualAppManager(application)
    val appCloner = AppCloner(application)

    private val _uiState = MutableStateFlow(VaultState())
    val uiState: StateFlow<VaultState> = _uiState.asStateFlow()

    private val _recreateEvent = MutableSharedFlow<Unit>()
    val recreateEvent = _recreateEvent.asSharedFlow()

    private var isPerformingAction = false

    fun setPerformingAction(performing: Boolean) {
        isPerformingAction = performing
    }

    fun isPerformingAction(): Boolean = isPerformingAction

    private val prefs = com.aitoyz.mapplock.security.SecureManager.getInstance(application).prefs
    private val offlineHelper = OfflineMapHelper(application)
    private val cryptoManager = com.aitoyz.mapplock.security.CryptoManager()

    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "vault_file_ids" || key == "custom_folders" || key?.startsWith("file_") == true) {
            updateFileCounts()
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            com.aitoyz.mapplock.security.StorageManager.ensureDirsExist(getApplication())
            // Trigger lazy loading of virtual app manager on IO thread
            virtualAppManager.isAppHidden("test") 
            prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
            ensureMainActivityEnabled()
            createNoMediaFile()
            loadPersistedVaults()
            checkFirstRun()
            checkPermissions()
            updateFileCounts()
            
            withContext(Dispatchers.Main) {
                lock()
                startLockerServiceIfNeeded()
                
                if (!prefs.getBoolean("is_first_run", true)) {
                    startMapDownload()
                }
            }
        }
    }

    private fun createNoMediaFile() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val vaultDir = com.aitoyz.mapplock.security.StorageManager.getVaultDir(context)
            if (!vaultDir.exists()) vaultDir.mkdirs()
            val noMedia = File(vaultDir, ".nomedia")
            if (!noMedia.exists()) {
                try { 
                    noMedia.createNewFile() 
                } catch (e: Exception) { 
                    com.aitoyz.mapplock.security.LockerLogger.e(com.aitoyz.mapplock.security.LockerLogger.Event.ERROR, "Failed to create .nomedia", e) 
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun completeOnboarding() {
        // Legacy method, will be replaced by granular steps
        prefs.edit { putBoolean("is_first_run", false) }
        _uiState.update { it.copy(isFirstRun = false) }
    }

    fun completeIntroGuide() {
        prefs.edit { putBoolean("intro_guide_completed", true) }
        _uiState.update { it.copy(introGuideCompleted = true) }
    }

    fun completeDisclaimer() {
        prefs.edit().apply {
            putBoolean("disclaimer_accepted", true)
            putBoolean("is_first_run", false)
            apply()
        }
        _uiState.update { it.copy(disclaimerAccepted = true, isFirstRun = false) }
        lock()
    }

    fun saveOnboardingPin(secret: String, type: LockType) {
        prefs.edit().apply {
            putString("master_secret", secret)
            putString("master_lock_type", type.name)
            putBoolean("is_pin_set", true)
            putBoolean("is_first_run", false)
            apply()
        }
        _uiState.update { it.copy(isPinSet = true, isFirstRun = false) }
        lock()
        startMapDownload()
        PostHog.capture(event = "onboarding_completed")
    }

    fun completeTour() {
        prefs.edit { putBoolean("tour_completed", true) }
        _uiState.update { it.copy(showTour = false) }
    }

    fun addIntruderFile(uri: Uri, thumbPath: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileId = UUID.randomUUID().toString()
            val name = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US).format(java.util.Date())
            val fileName = "Intruder_$name.jpg"
            saveFileInfo(fileId, fileName, uri.path ?: "", FileCategory.INTRUDER, 0L, thumbPath)
            withContext(Dispatchers.Main) { updateFileCounts() }
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

        if (prefs.getBoolean("map_initialized", false)) {
            _uiState.update { it.copy(isMapDownloading = false, isMapLoaded = true) }
        } else {
            _uiState.update { it.copy(isMapDownloading = true) }
        }

        _uiState.update { it.copy(isNetworkAvailable = true) }

        val context = getApplication<Application>()
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            _uiState.update { it.copy(isMapDownloading = false) }
            return
        }

        com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { location ->
            location?.let { 
                val geoPoint = GeoPoint(it.latitude, it.longitude)
                viewModelScope.launch {
                    ensureOffline(geoPoint, isInitial = true)
                }
            } ?: run {
                viewModelScope.launch {
                    delay(500)
                    _uiState.update { it.copy(isMapDownloading = false) }
                }
            }
        }.addOnFailureListener {
            _uiState.update { it.copy(isMapDownloading = false) }
        }
        
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
            PostHog.capture(event = "files_import_started", properties = mapOf("count" to uris.size, "category" to category.name))
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
                    val vaultDir = com.aitoyz.mapplock.security.StorageManager.getVaultDir(context)
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
                } catch (e: Exception) {
                    com.aitoyz.mapplock.security.LockerLogger.e(com.aitoyz.mapplock.security.LockerLogger.Event.ERROR, "Error adding file to vault", e)
                }
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
            val total = fileIds.size
            val startTime = System.currentTimeMillis()
            fileIds.forEachIndexed { index, id ->
                val name = prefs.getString("file_${id}_name", "file") ?: "file"
                _uiState.update { it.copy(operationProgress = OperationProgress(getApplication<Application>().getString(R.string.deleting_files), name, total, index, (index.toFloat() / total) * 100f, showHoldOn = System.currentTimeMillis() - startTime > 10000)) }
                
                if (isPermanent) {
                    com.aitoyz.mapplock.security.SecureManager.getInstance(getApplication()).removeFileInfo(id)
                } else {
                    com.aitoyz.mapplock.security.SecureManager.getInstance(getApplication()).updateFileCategory(id, FileCategory.RECYCLE_BIN)
                }
                
                if (total < 10 || index % 5 == 0) {
                    withContext(Dispatchers.Main) { updateFileCounts() }
                }
            }
            _uiState.update { it.copy(operationProgress = null) }
            withContext(Dispatchers.Main) { updateFileCounts() }
        }
    }

    fun bulkRestoreFiles(fileIds: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val total = fileIds.size
            val startTime = System.currentTimeMillis()
            fileIds.forEachIndexed { index, id ->
                val name = prefs.getString("file_${id}_name", "file") ?: "file"
                _uiState.update { it.copy(operationProgress = OperationProgress(getApplication<Application>().getString(R.string.restoring_files), name, total, index, (index.toFloat() / total) * 100f, showHoldOn = System.currentTimeMillis() - startTime > 10000)) }
                
                restoreFileToGallerySync(id)
                
                if (total < 10 || index % 5 == 0) {
                    withContext(Dispatchers.Main) { updateFileCounts() }
                }
            }
            _uiState.update { it.copy(operationProgress = null) }
            withContext(Dispatchers.Main) { updateFileCounts() }
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
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
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

    private fun saveFileInfo(id: String, name: String, path: String, category: FileCategory, size: Long, thumbPath: String? = null, folderName: String? = null, vaultId: String? = null) {
        val fileIds = (prefs.getStringSet("vault_file_ids", emptySet()) ?: emptySet()).toMutableSet()
        fileIds.add(id)
        val activeVaultId = vaultId ?: _uiState.value.activeVaultId
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
        viewModelScope.launch(Dispatchers.IO) {
            val fileIds = prefs.getStringSet("vault_file_ids", emptySet()) ?: emptySet()
            val customFolders = (prefs.getStringSet("custom_folders", emptySet()) ?: emptySet()).toList().sorted()
            val activeVaultId = _uiState.value.activeVaultId
            val filesList = mutableListOf<VaultFile>()
            var photos = 0; var videos = 0; var audio = 0; var docs = 0; var intruders = 0; var trashed = 0
            
            fileIds.forEach { id ->
                val vaultId = prefs.getString("file_${id}_vault_id", null)
                if (vaultId != activeVaultId) return@forEach

                val name = prefs.getString("file_${id}_name", "") ?: ""
                val path = prefs.getString("file_${id}_path", "") ?: ""
                val catStr = prefs.getString("file_${id}_category", "") ?: ""
                val category = try { FileCategory.valueOf(catStr) } catch (e: Exception) { FileCategory.OTHER }
                val thumbPath = prefs.getString("file_${id}_thumb", null)
                val folderName = prefs.getString("file_${id}_folder", null)
                val file = VaultFile(id, name, path, category, prefs.getLong("file_${id}_size", 0), prefs.getLong("file_${id}_timestamp", 0), thumbPath, folderName, vaultId)
                filesList.add(file)
                
                if (category == FileCategory.RECYCLE_BIN) {
                    trashed++
                } else if (folderName == null) {
                    when (category) {
                        FileCategory.PHOTO -> photos++; FileCategory.VIDEO -> videos++; FileCategory.AUDIO -> audio++; FileCategory.DOCUMENT -> docs++; FileCategory.INTRUDER -> intruders++
                        else -> {}
                    }
                }
            }
            
            val sortedFiles = filesList.sortedByDescending { f -> f.addedTimestamp }
            
            _uiState.update { 
                it.copy(
                    files = sortedFiles, 
                    customFolders = customFolders, 
                    photoCount = photos, 
                    videoCount = videos, 
                    audioCount = audio, 
                    documentCount = docs, 
                    intruderCount = intruders, 
                    recycleBinCount = trashed
                ) 
            }
        }
    }

    private fun ensureMainActivityEnabled() {
        val context = getApplication<Application>()
        val componentName = ComponentName(context, com.aitoyz.mapplock.MainActivity::class.java)
        try {
            context.packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            com.aitoyz.mapplock.security.LockerLogger.e(com.aitoyz.mapplock.security.LockerLogger.Event.ERROR, "Failed to enable MainActivity", e)
        }
    }

    private fun hasBatteryOptimizationPermission(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Check for "Unrestricted" state if possible, though isIgnoringBatteryOptimizations is the standard check
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
    }

    fun openAutoStartSettings() {
        val context = getApplication<Application>()
        val intents = listOf(
            Intent().apply { component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity") },
            Intent().apply { component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity") },
            Intent().apply { component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity") },
            Intent().apply { component = ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity") },
            Intent().apply { component = ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager") },
            Intent().apply { component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity") },
            Intent().apply { component = ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.ram.AutoRunActivity") },
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                break
            } catch (e: Exception) {}
        }
    }

    fun checkPermissions() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val hasUsage = hasUsageStatsPermission(context)
            val hasOverlay = Settings.canDrawOverlays(context)
            val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            val hasLocation = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
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
            val hasAccessibility = isAccessibilityServiceEnabled(context)
            
            // System Health Diagnostics
            val isLockerRunning = com.aitoyz.mapplock.service.AppLockerService.isRunning()
            
            // Indestructible Mode check
            val isIndestructible = hasBattery && hasUsage && hasOverlay && isLockerRunning
            
            withContext(Dispatchers.Main) {
                if (hasUsage && hasOverlay && !isLockerRunning) {
                    startLockerServiceIfNeeded()
                }

                if (hasLocation && !_uiState.value.hasLocationPermission) startMapDownload()
                
                _uiState.update {
                    it.copy(
                        hasUsageStatsPermission = hasUsage,
                        hasOverlayPermission = hasOverlay,
                        hasCameraPermission = hasCamera,
                        hasLocationPermission = hasLocation,
                        hasStoragePermission = hasStorage,
                        hasFullStoragePermission = hasFullStorage,
                        hasBatteryOptimizationPermission = hasBattery,
                        hasBackgroundPopupsPermission = hasBackgroundPopups,
                        hasNotificationPermission = hasNotifications,
                        hasAccessibilityPermission = hasAccessibility,
                        isLockerServiceRunning = isLockerRunning,
                        isUsageAccessActive = hasUsage,
                        isIndestructibleModeActive = isIndestructible
                    )
                }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedServiceName = ComponentName(context, com.aitoyz.mapplock.backend.accessibility.AppLockAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val splitter = android.text.TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedServiceName, ignoreCase = true)) return true
        }
        return false
    }

    fun toggleUninstallShield(enable: Boolean) {
        val context = getApplication<Application>()
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, com.aitoyz.mapplock.security.UninstallShieldReceiver::class.java)
        
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
            
            fileIds.forEach { id ->
                restoreFileToGallerySync(id)
            }
            
            withContext(Dispatchers.Main) {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(context, com.aitoyz.mapplock.security.UninstallShieldReceiver::class.java)
                dpm.removeActiveAdmin(adminComponent)
                
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
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$relPath/mapplock restored")
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
                com.aitoyz.mapplock.security.SecureManager.getInstance(context).removeFileInfo(fileId)
            }
        } catch (e: Exception) {
            com.aitoyz.mapplock.security.LockerLogger.e(com.aitoyz.mapplock.security.LockerLogger.Event.ERROR, "Failed to restore file", e)
        }
    }

    private fun startLockerServiceIfNeeded() {
        val context = getApplication<Application>()
        if (hasUsageStatsPermission(context) && Settings.canDrawOverlays(context)) {
            val intent = Intent(context, AppLockerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) else appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageStatsSettings() { getApplication<Application>().startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    fun openAccessibilitySettings() { getApplication<Application>().startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    fun openFullStorageSettings() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) getApplication<Application>().startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${getApplication<Application>().packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    fun openOverlaySettings() { getApplication<Application>().startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${getApplication<Application>().packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    fun openProtectedAppsSettings() {
        val context = getApplication<Application>()
        
        // 1. First try the standard System Popup (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            com.aitoyz.mapplock.security.LockerLogger.e(com.aitoyz.mapplock.security.LockerLogger.Event.ERROR, "Failed to launch standard ignore battery dialog", e)
        }

        // 2. Fallback to OEM specific settings
        val intents = listOf(
            Intent().apply { component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity") },
            Intent().apply { component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity") },
            Intent().apply { component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity") },
            Intent().apply { component = ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity") },
            Intent().apply { component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity") },
            Intent().apply { component = ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager") },
            Intent().apply { component = ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity") },
            Intent().apply { component = ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.ram.AutoRunActivity") },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )

        var opened = false
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                opened = true
                if (intent.action != Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) break
            } catch (e: Exception) {}
        }

        if (opened) {
            Toast.makeText(context, "Tip: Set Battery to 'Unrestricted' and enable 'Auto-start'", Toast.LENGTH_LONG).show()
        } else {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Toast.makeText(context, "Go to Battery -> Unrestricted", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun openBackgroundPopupSettings() {
        val context = getApplication<Application>()
        val packageName = context.packageName

        val intents = listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", packageName)
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            },
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", packageName)
            },
            Intent().apply {
                component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity")
                putExtra("packagename", packageName)
            },
            Intent("com.coloros.safecenter.permission.PermissionManagerActivity"),
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
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            val lockedApps = getLockedApps()
            
            val apps = resolveInfos.asSequence().mapNotNull { info ->
                try {
                    val activityInfo = info.activityInfo ?: return@mapNotNull null
                    val pkg = activityInfo.packageName ?: return@mapNotNull null
                    
                    // Pre-verify that we can load the icon to avoid empty slots later
                    try { pm.getApplicationIcon(pkg) } catch (e: Exception) { return@mapNotNull null }

                    if (lockedApps.contains(pkg)) return@mapNotNull null
                    
                    AppInfo(
                        packageName = pkg,
                        appName = info.loadLabel(pm).toString()
                    )
                } catch (e: Exception) {
                    com.aitoyz.mapplock.security.LockerLogger.e(com.aitoyz.mapplock.security.LockerLogger.Event.ERROR, "Failed to load info for app", e)
                    null
                }
            }.distinctBy { it.packageName }.sortedBy { it.appName }.toList()

            _uiState.update { it.copy(installedApps = apps) }
        }
    }

    private fun getLockedApps(): Set<String> {
        val allLockedApps = mutableSetOf<String>()
        prefs.getStringSet("vault_ids", emptySet())?.forEach { id -> allLockedApps.addAll(prefs.getStringSet("vault_${id}_apps", emptySet()) ?: emptySet()) }
        return allLockedApps
    }

    private fun ensureOffline(location: GeoPoint, isInitial: Boolean = false) {
        val offset = if (isInitial) 0.05 else 0.08
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
        val language = com.aitoyz.mapplock.security.LocaleManager.getLanguage(getApplication())
        _uiState.update { it.copy(
            vaults = vaults, 
            isLocked = prefs.getBoolean("is_locked", true), 
            activeVaultId = prefs.getString("active_vault_id", null), 
            isFingerprintEnabled = prefs.getBoolean("fingerprint_enabled", true), 
            isDarkMode = prefs.getBoolean("is_dark_mode", false), 
            monitoringMode = MonitoringMode.valueOf(prefs.getString("monitoring_mode", MonitoringMode.USAGE_STATS.name) ?: MonitoringMode.USAGE_STATS.name),
            isScreenshotRestricted = prefs.getBoolean("screenshot_restriction", false),
            isIntruderCaptureEnabled = prefs.getBoolean("intruder_capture_enabled", false),
            currentLanguage = language, 
            isFirstRun = prefs.getBoolean("is_first_run", true), 
            introGuideCompleted = prefs.getBoolean("intro_guide_completed", false),
            disclaimerAccepted = prefs.getBoolean("disclaimer_accepted", false),
            isPinSet = prefs.contains("master_secret"),
            isLanguageSelected = prefs.contains("language") || language != "en",
            customBackgroundPath = prefs.getString("lock_background_path", null)
        ) }
        com.aitoyz.mapplock.security.LocaleManager.applyLanguage(getApplication(), language)
        loadInstalledApps()
    }

    fun saveVaultConfiguration(point: GeoPoint, secret: String, hiddenApps: Set<String>, lockType: LockType = LockType.PIN, radius: Float = 500f) {
        val vaultIds = (prefs.getStringSet("vault_ids", emptySet()) ?: emptySet()).toMutableSet()
        if (vaultIds.size >= 2) return
        
        viewModelScope.launch(Dispatchers.IO) {
            PostHog.capture(event = "vault_created", properties = mapOf(
                "radius" to radius,
                "lock_type" to lockType.name,
                "apps_locked_count" to hiddenApps.size
            ))

            val id = UUID.randomUUID().toString()
            val appsToLock = hiddenApps.toMutableSet().apply { 
                remove("com.android.settings")
                remove("com.android.vending")
                remove("com.google.android.vending")
            }
            vaultIds.add(id)
            
            prefs.edit {
                putStringSet("vault_ids", vaultIds)
                putFloat("vault_${id}_lat", point.latitude.toFloat())
                putFloat("vault_${id}_lon", point.longitude.toFloat())
                putFloat("vault_${id}_radius", radius)
                putString("vault_${id}_lock_type", lockType.name)
                putString("vault_${id}_secret", secret)
                putStringSet("vault_${id}_apps", appsToLock)
                putLong("vault_${id}_timestamp", System.currentTimeMillis())
                putBoolean("is_locked", false)
                putString("active_vault_id", id)
            }
            
            ensureOffline(point)
            
            withContext(Dispatchers.Main) {
                loadPersistedVaults()
                notifyServiceToRefresh()
            }
        }
    }

    fun clearAllVaults() {
        prefs.getStringSet("vault_ids", emptySet())?.forEach { id -> prefs.edit().remove("vault_${id}_lat").remove("vault_${id}_lon").remove("vault_${id}_lock_type").remove("vault_${id}_secret").remove("vault_${id}_apps").remove("vault_${id}_timestamp").apply() }
        prefs.edit().putStringSet("vault_ids", emptySet()).putBoolean("is_locked", true).apply(); loadPersistedVaults(); notifyServiceToRefresh()
    }

    private var lastLocationFetchTime = 0L
    private var lastLocationEnsured: GeoPoint? = null

    fun onLocationChanged(latitude: Double, longitude: Double) {
        val currentPoint = GeoPoint(latitude, longitude)
        
        val hasMovedSignificantly = lastLocationEnsured?.let { 
            LocationHelper.calculateDistance(it.latitude, it.longitude, latitude, longitude) > 500f
        } ?: true

        val isIndia = latitude in 8.4..37.6 && longitude in 68.7..97.2
        _uiState.update { it.copy(currentLocation = currentPoint, isIndiaRegion = isIndia) }
        
        if (lastLocationEnsured == null || hasMovedSignificantly) {
            lastLocationEnsured = currentPoint
            if (!_uiState.value.isFirstRun) ensureOffline(currentPoint)
            
            if (lastLocationFetchTime == 0L && isNetworkAvailable(getApplication())) {
                fetchWeatherAndAQI(latitude, longitude)
            }
        }
    }

    fun refreshWeather() {
        val current = _uiState.value.currentLocation ?: return
        if (isNetworkAvailable(getApplication())) {
            fetchWeatherAndAQI(current.latitude, current.longitude)
        }
    }

    fun fetchWeatherAndAQI(lat: Double, lon: Double) {
        _uiState.update { it.copy(isWeatherLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cityUrl = URL("https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json")
                val cityConn = cityUrl.openConnection()
                cityConn.setRequestProperty("User-Agent", "Mapplock")
                val cityResponse = cityConn.getInputStream().bufferedReader().use { it.readText() }
                val cityJson = JSONObject(cityResponse)
                val address = cityJson.optJSONObject("address")
                val city = address?.optString("city") ?: address?.optString("town") ?: address?.optString("village") ?: "Unknown"
                val fullAddress = cityJson.optString("display_name")

                val weatherUrl = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m")
                val weatherResponse = weatherUrl.openStream().bufferedReader().use { it.readText() }
                val weatherJson = JSONObject(weatherResponse)
                val current = weatherJson.getJSONObject("current")
                val temp = current.getDouble("temperature_2m")
                val humidity = current.getInt("relative_humidity_2m")

                val aqiUrl = URL("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=us_aqi,pm2_5,pm10,nitrogen_dioxide,ozone")
                val aqiResponse = aqiUrl.openStream().bufferedReader().use { it.readText() }
                val aqiJson = JSONObject(aqiResponse)
                val aqiCurrent = aqiJson.getJSONObject("current")
                val aqi = aqiCurrent.getInt("us_aqi")
                val pm25 = aqiCurrent.optDouble("pm2_5")
                val pm10 = aqiCurrent.optDouble("pm10")
                val no2 = aqiCurrent.optDouble("nitrogen_dioxide")
                val o3 = aqiCurrent.optDouble("ozone")

                _uiState.update { state ->
                    state.copy(weatherInfo = WeatherInfo(
                        temperature = temp,
                        humidity = humidity,
                        aqi = aqi,
                        cityName = city,
                        address = fullAddress,
                        pm25 = pm25,
                        pm10 = pm10,
                        no2 = no2,
                        o3 = o3,
                        lastUpdated = System.currentTimeMillis()
                    ), isWeatherLoading = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isWeatherLoading = false) }
            }
        }
    }

    fun attemptUnlockAtLocation(tapLat: Double, tapLon: Double, pin: String): Boolean {
        val nearbyVault = _uiState.value.vaults.find { LocationHelper.isWithinRadius(tapLat, tapLon, it.location.latitude, it.location.longitude, 100f) } ?: run {
            PostHog.capture(event = "unlock_failed", properties = mapOf("reason" to "no_nearby_vault"))
            return false
        }
        if (nearbyVault.radius > 0) {
            val current = _uiState.value.currentLocation ?: run {
                PostHog.capture(event = "unlock_failed", properties = mapOf("reason" to "no_location"))
                return false
            }
            if (LocationHelper.calculateDistance(current.latitude, current.longitude, nearbyVault.location.latitude, nearbyVault.location.longitude) > nearbyVault.radius) {
                PostHog.capture(event = "unlock_failed", properties = mapOf("reason" to "outside_radius"))
                return false
            }
        }
        if (pin == nearbyVault.secret) { 
            prefs.edit().putBoolean("is_locked", false).putString("active_vault_id", nearbyVault.id).apply()
            _uiState.update { it.copy(isLocked = false, activeVaultId = nearbyVault.id) }
            updateFileCounts()
            PostHog.capture(event = "vault_unlocked", properties = mapOf("radius" to nearbyVault.radius))
            return true 
        }
        PostHog.capture(event = "unlock_failed", properties = mapOf("reason" to "wrong_pin"))
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
        prefs.edit { putStringSet("vault_${vaultId}_apps", newHiddenApps) }; loadPersistedVaults(); notifyServiceToRefresh()
    }

    private fun notifyServiceToRefresh() {
        val context = getApplication<Application>()
        context.startService(Intent(context, com.aitoyz.mapplock.service.AppLockerService::class.java).apply { putExtra("refresh_locked_apps", true) })
    }

    fun toggleAppLock(packageName: String) {
        val vaultId = _uiState.value.activeVaultId ?: return
        val vault = _uiState.value.vaults.find { it.id == vaultId } ?: return
        val newHiddenApps = vault.hiddenApps.toMutableSet().apply { if (contains(packageName)) remove(packageName) else add(packageName) }
        prefs.edit { putStringSet("vault_${vaultId}_apps", newHiddenApps) }; loadPersistedVaults(); notifyServiceToRefresh()
    }

    fun launchApp(packageName: String) {
        val context = getApplication<Application>()
        prefs.edit().putString("bypass_package", packageName).apply()
        try {
            context.packageManager.getLaunchIntentForPackage(packageName)?.apply { 
                setPerformingAction(true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(this) 
            }
        } catch (e: Exception) {
            com.aitoyz.mapplock.security.LockerLogger.e(com.aitoyz.mapplock.security.LockerLogger.Event.ERROR, "Failed to launch app: $packageName", e)
        }
    }

    fun toggleMasterStealth() {
        val newValue = !_uiState.value.isMasterStealthEnabled
        PostHog.capture(event = "master_stealth_toggled", properties = mapOf("enabled" to newValue))
        prefs.edit().putBoolean("master_stealth_enabled", newValue).apply(); _uiState.update { it.copy(isMasterStealthEnabled = newValue) }
        getApplication<Application>().startService(Intent(getApplication(), com.aitoyz.mapplock.service.AppLockerService::class.java).apply { putExtra("refresh_locked_apps", true) })
    }

    fun toggleDarkMode() { val newValue = !_uiState.value.isDarkMode; prefs.edit().putBoolean("is_dark_mode", newValue).apply(); _uiState.update { it.copy(isDarkMode = newValue) } }

    fun setMonitoringMode(mode: MonitoringMode) {
        val context = getApplication<Application>()
        if (mode == MonitoringMode.ACCESSIBILITY && !isAccessibilityServiceEnabled(context)) {
            openAccessibilitySettings()
            return
        }
        
        prefs.edit().putString("monitoring_mode", mode.name).apply()
        _uiState.update { it.copy(monitoringMode = mode) }
        
        // Restart service to apply new backend
        if (com.aitoyz.mapplock.service.AppLockerService.isRunning()) {
            val intent = Intent(context, com.aitoyz.mapplock.service.AppLockerService::class.java)
            context.stopService(intent)
            viewModelScope.launch {
                delay(500)
                startLockerServiceIfNeeded()
            }
        }
    }

    fun toggleScreenshotRestriction() { val newValue = !_uiState.value.isScreenshotRestricted; prefs.edit().putBoolean("screenshot_restriction", newValue).apply(); _uiState.update { it.copy(isScreenshotRestricted = newValue) } }
    fun toggleIntruderCapture(enabled: Boolean) { prefs.edit().putBoolean("intruder_capture_enabled", enabled).apply(); _uiState.update { it.copy(isIntruderCaptureEnabled = enabled) } }
    fun toggleFingerprint() { val newValue = !_uiState.value.isFingerprintEnabled; prefs.edit().putBoolean("fingerprint_enabled", newValue).apply(); _uiState.update { it.copy(isFingerprintEnabled = newValue) } }

    fun setLanguage(langCode: String) { 
        PostHog.capture(event = "language_changed", properties = mapOf("language" to langCode))
        prefs.edit().putString("language", langCode).apply()
        _uiState.update { it.copy(currentLanguage = langCode, isLanguageSelected = true) }
        com.aitoyz.mapplock.security.LocaleManager.applyLanguage(getApplication(), langCode) 
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
                com.aitoyz.mapplock.security.SecureManager.getInstance(getApplication()).removeFileInfo(fileId)
            } else {
                com.aitoyz.mapplock.security.SecureManager.getInstance(getApplication()).updateFileCategory(fileId, FileCategory.RECYCLE_BIN)
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
            val secureManager = com.aitoyz.mapplock.security.SecureManager.getInstance(context)
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
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "$relPath/mapplock restored")
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
