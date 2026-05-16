package com.geovault.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.geovault.R
import com.geovault.model.*
import com.geovault.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.geovault.core.AppCloner
import com.geovault.core.VirtualAppManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalFilePicker(
    initialCategory: FileCategory,
    galleryItems: List<GalleryItem>,
    isFetching: Boolean,
    onCategoryChanged: (FileCategory) -> Unit,
    onHide: (List<Uri>) -> Unit,
    onCancel: () -> Unit
) {
    var selectedUris by remember { mutableStateOf(setOf<Uri>()) }
    var currentCategory by remember { mutableStateOf(initialCategory) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf("All") }
    var showPreviewSheet by remember { mutableStateOf(false) }

    val folders = remember(galleryItems) {
        listOf("All") + galleryItems.map { it.folderName }.distinct()
    }

    val filteredItems = remember(galleryItems, selectedFolder) {
        if (selectedFolder == "All") galleryItems
        else galleryItems.filter { it.folderName == selectedFolder }
    }

    val groupedItems = remember(filteredItems) {
        filteredItems.groupBy { item ->
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = item.dateAdded * 1000L
            val now = Calendar.getInstance()
            
            when {
                isSameDay(calendar, now) -> "Today"
                isYesterday(calendar, now) -> "Yesterday"
                else -> SimpleDateFormat("MMMM d", Locale.getDefault()).format(calendar.time)
            }
        }
    }

    if (showPreviewSheet) {
        ImportPreviewBottomSheet(
            uris = selectedUris.toList(),
            category = currentCategory,
            galleryItems = galleryItems,
            onConfirm = { onHide(selectedUris.toList()); showPreviewSheet = false },
            onDismiss = { showPreviewSheet = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showCategoryMenu = true }
                        ) {
                            Text(
                                when(currentCategory) {
                                    FileCategory.PHOTO -> "Photos"
                                    FileCategory.VIDEO -> "Videos"
                                    FileCategory.AUDIO -> "Audio"
                                    FileCategory.DOCUMENT -> "Documents"
                                    else -> "Files"
                                },
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Icon(Icons.Default.ArrowDropDown, null, tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false },
                            modifier = Modifier.background(CyberDarkBlue)
                        ) {
                            FileCategory.entries.filter { it != FileCategory.INTRUDER && it != FileCategory.OTHER }.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name, color = Color.White) },
                                    onClick = {
                                        currentCategory = cat
                                        onCategoryChanged(cat)
                                        showCategoryMenu = false
                                        selectedUris = emptySet()
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberBlack)
            )
        },
        bottomBar = {
            if (selectedUris.isNotEmpty()) {
                Surface(
                    color = CyberBlack,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Button(
                        onClick = { showPreviewSheet = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberPurple),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("PREVIEW & HIDE (${selectedUris.size})", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                }
            }
        },
        containerColor = CyberBlack
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Folder Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(folders) { folder ->
                    FilterChip(
                        selected = selectedFolder == folder,
                        onClick = { selectedFolder = folder },
                        label = { Text(folder) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            selectedContainerColor = CyberPurple,
                            labelColor = Color.Gray,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedFolder == folder,
                            borderColor = Color.DarkGray,
                            selectedBorderColor = Color.Transparent,
                            borderWidth = 1.dp
                        )
                    )
                }
            }

            if (isFetching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CyberBlue)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    groupedItems.forEach { (date, items) ->
                        item(span = { GridItemSpan(3) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(date, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(
                                    "Select",
                                    color = CyberBlue,
                                    fontSize = 14.sp,
                                    modifier = Modifier.clickable {
                                        val dateUris = items.map { it.uri }.toSet()
                                        selectedUris = if (selectedUris.containsAll(dateUris)) selectedUris - dateUris else selectedUris + dateUris
                                    }
                                )
                            }
                        }
                        items(items) { item ->
                            GalleryGridItem(
                                item = item,
                                category = currentCategory,
                                isSelected = selectedUris.contains(item.uri),
                                onToggle = {
                                    selectedUris = if (selectedUris.contains(item.uri)) selectedUris - item.uri else selectedUris + item.uri
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GalleryGridItem(item: GalleryItem, category: FileCategory, isSelected: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clickable { onToggle() }
    ) {
        if (category == FileCategory.AUDIO) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CyberDarkBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    tint = IconRed,
                    modifier = Modifier.size(48.dp)
                )
            }
        } else {
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        if (category == FileCategory.VIDEO) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Text(formatDuration(item.duration ?: 0), color = Color.White, fontSize = 10.sp)
                }
            }
        }

        if (isSelected) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = CyberPurple,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).background(Color.White, CircleShape)
            )
        }
        
        Text(
            item.name,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.4f)).padding(2.dp),
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(cal1: Calendar, cal2: Calendar): Boolean {
    val yesterday = Calendar.getInstance()
    yesterday.add(Calendar.DAY_OF_YEAR, -1)
    return cal1.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultContentScreen(
    state: VaultState,
    onLockClick: () -> Unit,
    onAppClick: (String) -> Unit,
    onRemoveApp: (String) -> Unit,
    onOpenUsageSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenProtectedApps: () -> Unit,
    onToggleMasterStealth: () -> Unit,
    onAddFiles: (List<Uri>, FileCategory) -> Unit,
    onToggleAppLock: (String) -> Unit,
    onRemoveVault: (String) -> Unit,
    onClearAllVaults: () -> Unit,
    onGrantCamera: () -> Unit,
    onGrantStorage: () -> Unit,
    onGrantFullStorage: () -> Unit,
    onFetchGalleryItems: (FileCategory) -> Unit,
    onDeleteFile: (String) -> Unit,
    onRestoreFile: (String) -> Unit,
    onToggleDarkMode: () -> Unit,
    onToggleFingerprint: () -> Unit,
    onSetLanguage: (String) -> Unit,
    onCompleteTour: () -> Unit,
    onToggleScreenshotRestriction: () -> Unit,
    onCreateFolder: (String) -> Unit = {},
    onAddFilesToFolder: (List<Uri>, String) -> Unit = { _, _ -> },
    onStartAction: () -> Unit = {},
    onEndAction: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val virtualAppManager = remember { VirtualAppManager(context) }
    val appCloner = remember { AppCloner(context) }
    
    var currentScreen by remember { mutableStateOf<ContentScreen>(ContentScreen.Dashboard) }
    var selectedCategoryForAdd by remember { mutableStateOf<FileCategory?>(null) }
    var viewingFile by remember { mutableStateOf<VaultFile?>(null) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var selectedFolderForAdd by remember { mutableStateOf<String?>(null) }

    val dashboardScrollState = rememberLazyListState()
    val appLockScrollState = rememberLazyListState()
    val categoryGridStates = remember { mutableMapOf<FileCategory, LazyGridState>() }

    var appToHide by remember { mutableStateOf<String?>(null) }
    var isCloning by remember { mutableStateOf(false) }
    
    var showUserGuide by remember { mutableStateOf(state.showTour) }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { 
        onEndAction()
    }

    val isDark = state.isDarkMode
    val backgroundColor = if (isDark) CyberBlack else CreamWhite

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = backgroundColor,
            topBar = {
                TopAppBar(
                    title = { 
                        val title = when {
                            currentScreen is ContentScreen.CategoryView -> {
                                val category = (currentScreen as ContentScreen.CategoryView).category
                                when (category) {
                                    FileCategory.PHOTO -> stringResource(R.string.photos)
                                    FileCategory.VIDEO -> stringResource(R.string.videos)
                                    FileCategory.AUDIO -> stringResource(R.string.audio)
                                    FileCategory.DOCUMENT -> stringResource(R.string.documents)
                                    FileCategory.INTRUDER -> stringResource(R.string.wrong_unlocks)
                                    else -> stringResource(R.string.categories)
                                }
                            }
                            currentScreen is ContentScreen.FolderView -> {
                                (currentScreen as ContentScreen.FolderView).folderName
                            }
                            else -> stringResource(R.string.app_name)
                        }
                        Text(
                            title,
                            fontWeight = FontWeight.Black, 
                            color = if (isDark) CyberBlue else AppBlue,
                            fontSize = 26.sp,
                            letterSpacing = (-0.5).sp
                        ) 
                    },
                    navigationIcon = {
                        if (currentScreen != ContentScreen.Dashboard) {
                            IconButton(onClick = { currentScreen = ContentScreen.Dashboard }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = if (isDark) Color.White else Color.Black)
                            }
                        }
                    },
                    actions = {
                        if (currentScreen == ContentScreen.Dashboard) {
                            IconButton(onClick = { currentScreen = ContentScreen.Settings }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f))
                            }
                        }
                        IconButton(onClick = onLockClick) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock", tint = if (isDark) CyberBlue else AppBlue)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            floatingActionButton = {
                val isIntruderCategory = currentScreen is ContentScreen.CategoryView && (currentScreen as ContentScreen.CategoryView).category == FileCategory.INTRUDER
                if (!isIntruderCategory && (currentScreen is ContentScreen.Dashboard || currentScreen is ContentScreen.CategoryView || currentScreen is ContentScreen.FolderView)) {
                    FloatingActionButton(
                        onClick = { 
                            if (currentScreen == ContentScreen.Dashboard) {
                                showCreateFolderDialog = true
                            } else {
                                val cat = if (currentScreen is ContentScreen.CategoryView) (currentScreen as ContentScreen.CategoryView).category else FileCategory.PHOTO
                                val folder = if (currentScreen is ContentScreen.FolderView) (currentScreen as ContentScreen.FolderView).folderName else null
                                
                                // Contextual Permission Check
                                val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    when {
                                        folder != null -> state.hasStoragePermission
                                        cat == FileCategory.PHOTO -> androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        cat == FileCategory.VIDEO -> androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        cat == FileCategory.AUDIO -> androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        else -> state.hasStoragePermission
                                    }
                                } else {
                                    state.hasStoragePermission
                                }

                                if (hasPermission) {
                                    if (folder != null) {
                                        selectedFolderForAdd = folder
                                        onFetchGalleryItems(FileCategory.PHOTO) // Default to photos for folder add
                                    } else {
                                        selectedCategoryForAdd = cat
                                        onFetchGalleryItems(cat)
                                    }
                                } else {
                                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        when {
                                            folder != null -> arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO)
                                            cat == FileCategory.PHOTO -> arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
                                            cat == FileCategory.VIDEO -> arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO)
                                            cat == FileCategory.AUDIO -> arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
                                            else -> arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO)
                                        }
                                    } else {
                                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    }
                                    onStartAction()
                                    mediaPermissionLauncher.launch(perms)
                                }
                            }
                        },
                        containerColor = if (currentScreen == ContentScreen.Dashboard) FolderPurple else CyberBlue,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        val duration = 400
                        if (targetState is ContentScreen.Dashboard) {
                            (fadeIn(animationSpec = tween(duration)) + slideInHorizontally(animationSpec = tween(duration), initialOffsetX = { -it / 5 }))
                                .togetherWith(fadeOut(animationSpec = tween(duration / 2)) + slideOutHorizontally(animationSpec = tween(duration), targetOffsetX = { it / 5 }))
                        } else {
                            (fadeIn(animationSpec = tween(duration)) + slideInHorizontally(animationSpec = tween(duration), initialOffsetX = { it / 5 }))
                                .togetherWith(fadeOut(animationSpec = tween(duration / 2)) + slideOutHorizontally(animationSpec = tween(duration), targetOffsetX = { -it / 5 }))
                        }
                    },
                    label = "ContentTransition"
                ) { screen ->
                    when (screen) {
                        is ContentScreen.Dashboard -> DashboardContent(
                            state = state, 
                            scrollState = dashboardScrollState,
                            onAppLockClick = { currentScreen = ContentScreen.AppLock },
                            onCategoryClick = { currentScreen = ContentScreen.CategoryView(it) },
                            onFolderClick = { currentScreen = ContentScreen.FolderView(it) },
                            onBackupClick = { showBackupDialog = true }
                        )
                        is ContentScreen.AppLock -> AppLockManagement(
                            state = state, 
                            scrollState = appLockScrollState,
                            onToggleAppLock = onToggleAppLock,
                            onHideApp = { pkg ->
                                appToHide = pkg
                            }
                        )
                        is ContentScreen.Settings -> SettingsSection(
                            state = state,
                            onOpenUsageSettings = onOpenUsageSettings,
                            onOpenOverlaySettings = onOpenOverlaySettings,
                            onOpenProtectedApps = onOpenProtectedApps,
                            onToggleMasterStealth = onToggleMasterStealth,
                            onGrantCamera = onGrantCamera,
                            onGrantStorage = onGrantStorage,
                            onGrantFullStorage = onGrantFullStorage,
                            onToggleDarkMode = onToggleDarkMode,
                            onToggleFingerprint = onToggleFingerprint,
                            onSetLanguage = onSetLanguage,
                            onOpenLanguageSelection = { currentScreen = ContentScreen.LanguageSelection },
                            onToggleScreenshotRestriction = onToggleScreenshotRestriction
                        )
                        is ContentScreen.CategoryView -> {
                            val gridState = categoryGridStates.getOrPut(screen.category) { LazyGridState() }
                            FileCategoryList(
                                category = screen.category,
                                files = state.files.filter { it.category == screen.category && it.folderName == null },
                                gridState = gridState,
                                onFileClick = { viewingFile = it }
                            )
                        }
                        is ContentScreen.FolderView -> {
                            val gridState = rememberLazyGridState()
                            FileCategoryList(
                                category = FileCategory.OTHER,
                                files = state.files.filter { it.folderName == screen.folderName },
                                gridState = gridState,
                                onFileClick = { viewingFile = it }
                            )
                        }
                        is ContentScreen.LanguageSelection -> LanguageSelectionScreen(
                            currentLanguageCode = state.currentLanguage,
                            onLanguageSelected = { 
                                onSetLanguage(it)
                                currentScreen = ContentScreen.Dashboard
                            },
                            onBack = { currentScreen = ContentScreen.Settings }
                        )
                    }
                }

                // Progress Overlay
                state.operationProgress?.let { progress ->
                    OperationProgressOverlay(progress, isDark)
                }
            }
        }

        // Overlays for Media Viewer and File Picker
        AnimatedVisibility(
            visible = viewingFile != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            viewingFile?.let { file ->
                MediaViewerScreen(
                    file = file,
                    allFiles = state.files,
                    onBack = { viewingFile = null },
                    onDelete = { id ->
                        onDeleteFile(id)
                        viewingFile = null
                    },
                    onRestore = { id ->
                        onRestoreFile(id)
                        viewingFile = null
                    },
                    onStartAction = onStartAction,
                    onEndAction = onEndAction
                )
            }
        }

        AnimatedVisibility(
            visible = selectedCategoryForAdd != null || selectedFolderForAdd != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val cat = selectedCategoryForAdd ?: FileCategory.PHOTO
            LocalFilePicker(
                initialCategory = cat,
                galleryItems = state.galleryItems,
                isFetching = state.isFetchingGallery,
                onCategoryChanged = { 
                    if (selectedCategoryForAdd != null) {
                        selectedCategoryForAdd = it
                    }
                    onFetchGalleryItems(it)
                },
                onHide = { uris ->
                    selectedFolderForAdd?.let { folder ->
                        onAddFilesToFolder(uris, folder)
                    } ?: run {
                        selectedCategoryForAdd?.let { category ->
                            onAddFiles(uris, category)
                        }
                    }
                    selectedCategoryForAdd = null
                    selectedFolderForAdd = null
                },
                onCancel = { 
                    selectedCategoryForAdd = null
                    selectedFolderForAdd = null
                }
            )
        }
    }


    if (showBackupDialog) {
        BackupManagementDialog(
            state = state,
            onDismiss = { showBackupDialog = false },
            onRemoveVault = onRemoveVault,
            onClearAll = onClearAllVaults
        )
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create New Folder", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Folders can contain any file type (Images, Videos, PDFs, etc).", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Folder Name") },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FolderPurple,
                            unfocusedBorderColor = if (isDark) Color.DarkGray else Color.LightGray
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        if (newFolderName.isNotBlank()) {
                            onCreateFolder(newFolderName)
                            showCreateFolderDialog = false 
                            newFolderName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FolderPurple)
                ) {
                    Text("CREATE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (appToHide != null) {
        AlertDialog(
            onDismissRequest = { if (!isCloning) appToHide = null },
            title = { Text("Hide App (Clone & Uninstall)") },
            text = { 
                if (isCloning) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(color = CyberBlue)
                        Spacer(Modifier.height(16.dp))
                        Text("Cloning app to secure sandbox...")
                    }
                } else {
                    Text("This will create a secure clone of the app inside our vault and ask you to uninstall the original one from your phone. Proceed?")
                }
            },
            confirmButton = {
                if (!isCloning) {
                    Button(onClick = {
                        val pkg = appToHide ?: return@Button
                        isCloning = true
                        scope.launch {
                            val success = withContext(Dispatchers.IO) {
                                appCloner.cloneApp(pkg)
                            }
                            if (success) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "App cloned successfully!", Toast.LENGTH_SHORT).show()
                                    virtualAppManager.uninstallOriginalApp(pkg)
                                }
                            }
                            isCloning = false
                            appToHide = null
                        }
                    }) { Text("CLONE & HIDE") }
                }
            },
            dismissButton = {
                if (!isCloning) {
                    TextButton(onClick = { appToHide = null }) { Text("CANCEL") }
                }
            }
        )
    }

    if (showUserGuide) {
        InteractiveUserGuide(
            onDismiss = { 
                showUserGuide = false
                onCompleteTour()
            }
        )
    }
}

sealed class ContentScreen {
    object Dashboard : ContentScreen()
    object AppLock : ContentScreen()
    object Settings : ContentScreen()
    object LanguageSelection : ContentScreen()
    data class CategoryView(val category: FileCategory) : ContentScreen()
    data class FolderView(val folderName: String) : ContentScreen()
}

@Composable
fun DashboardContent(
    state: VaultState, 
    scrollState: LazyListState = rememberLazyListState(),
    onAppLockClick: () -> Unit,
    onCategoryClick: (FileCategory) -> Unit,
    onFolderClick: (String) -> Unit,
    onBackupClick: () -> Unit
) {
    val isDark = state.isDarkMode
    val textPrimary = if (isDark) Color.White else Color.Black
    val textSecondary = if (isDark) Color.Gray else LightTextSecondary
    var isGridView by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardCard(
                    title = stringResource(R.string.app_lock_title),
                    subtitle = stringResource(R.string.app_lock_subtitle),
                    icon = Icons.Default.Shield,
                    modifier = Modifier.weight(1f),
                    color = IconGreen,
                    isDark = isDark,
                    onClick = onAppLockClick
                )
                DashboardCard(
                    title = stringResource(R.string.history_title),
                    subtitle = stringResource(R.string.history_subtitle),
                    icon = Icons.Default.History,
                    modifier = Modifier.weight(1f),
                    color = IconBlue,
                    isDark = isDark,
                    onClick = onBackupClick
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.categories),
                    style = MaterialTheme.typography.titleMedium,
                    color = textPrimary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { isGridView = !isGridView }) {
                    Icon(
                        if (isGridView) Icons.Default.ViewList else Icons.Default.GridView, 
                        null, 
                        tint = textSecondary, 
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        item {
            val categories = mutableListOf(
                CategoryData(FileCategory.PHOTO, stringResource(R.string.photos), state.photoCount, Icons.Default.Image, IconBlue, SoftBlue),
                CategoryData(FileCategory.VIDEO, stringResource(R.string.videos), state.videoCount, Icons.Default.PlayCircle, IconOrange, SoftOrange),
                CategoryData(FileCategory.AUDIO, stringResource(R.string.audio), state.audioCount, Icons.Default.MusicNote, IconRed, SoftRed),
                CategoryData(FileCategory.DOCUMENT, stringResource(R.string.documents), state.documentCount, Icons.Default.Description, IconGreen, SoftGreen),
                CategoryData(FileCategory.INTRUDER, stringResource(R.string.wrong_unlocks), state.intruderCount, Icons.Default.PersonSearch, IconOrange, SoftOrange),
                CategoryData(FileCategory.RECYCLE_BIN, stringResource(R.string.recycle_bin), state.recycleBinCount, Icons.Default.Delete, IconGray, SoftGray)
            )
            
            // Add custom folders
            state.customFolders.forEach { folderName ->
                val count = state.files.count { it.folderName == folderName }
                categories.add(CategoryData(FileCategory.OTHER, folderName, count, Icons.Default.Folder, IconPurple, SoftPurple, folderName))
            }

            if (isGridView) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (i in categories.indices step 2) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val cat1 = categories[i]
                            CategoryGridItem(cat1.title, cat1.count, cat1.icon, cat1.color, isDark, Modifier.weight(1f)) { 
                                if (cat1.customFolderName != null) onFolderClick(cat1.customFolderName) else onCategoryClick(cat1.category) 
                            }
                            
                            if (i + 1 < categories.size) {
                                val cat2 = categories[i+1]
                                CategoryGridItem(cat2.title, cat2.count, cat2.icon, cat2.color, isDark, Modifier.weight(1f)) { 
                                    if (cat2.customFolderName != null) onFolderClick(cat2.customFolderName) else onCategoryClick(cat2.category) 
                                }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    categories.forEach { cat ->
                        CategoryItem(cat.title, cat.count, cat.icon, cat.color, cat.bgColor, isDark) { 
                            if (cat.customFolderName != null) onFolderClick(cat.customFolderName) else onCategoryClick(cat.category) 
                        }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

private data class CategoryData(
    val category: FileCategory,
    val title: String,
    val count: Int,
    val icon: ImageVector,
    val color: Color,
    val bgColor: Color,
    val customFolderName: String? = null
)

@Composable
fun CategoryGridItem(
    title: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 120.dp),
        color = if (isDark) CyberDarkBlue else CreamWhite,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(if (isDark) Color.White.copy(alpha = 0.05f) else color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, 
                    null, 
                    tint = if (isDark) CyberBlue else color, 
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                title, 
                color = if (isDark) Color.White else Color.Black.copy(alpha = 0.8f), 
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 1,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.items_count, count), 
                color = Color.Gray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}



@Composable
fun FileCategoryList(
    category: FileCategory,
    files: List<VaultFile>,
    gridState: LazyGridState = rememberLazyGridState(),
    onFileClick: (VaultFile) -> Unit
) {
    if (files.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = Color.DarkGray)
                Spacer(Modifier.height(16.dp))
                Text("No files in this category", color = Color.Gray)
            }
        }
    } else {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(files) { file ->
                FileItem(file) { onFileClick(file) }
            }
        }
    }
}

@Composable
fun FileItem(file: VaultFile, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CyberDarkBlue)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FileThumbnail(file)
            
            // File Extension Overlay
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    file.originalName.substringAfterLast(".", "").uppercase(),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun FileThumbnail(file: VaultFile) {
    if (file.thumbnailPath != null && File(file.thumbnailPath).exists()) {
        AsyncImage(
            model = File(file.thumbnailPath),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val icon = when (file.category) {
                FileCategory.PHOTO -> Icons.Default.Image
                FileCategory.VIDEO -> Icons.Default.PlayCircle
                FileCategory.AUDIO -> Icons.Default.Audiotrack
                FileCategory.DOCUMENT -> Icons.AutoMirrored.Filled.InsertDriveFile
                else -> Icons.Default.InsertDriveFile
            }
            Icon(icon, null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun AppLockManagement(
    state: VaultState, 
    scrollState: LazyListState = rememberLazyListState(),
    onToggleAppLock: (String) -> Unit,
    onHideApp: (String) -> Unit
) {
    val isDark = state.isDarkMode
    val textPrimary = if (isDark) Color.White else Color.Black
    val textSecondary = if (isDark) Color.Gray else LightTextSecondary
    
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(state.installedApps, searchQuery) {
        state.installedApps.filter { it.appName.contains(searchQuery, ignoreCase = true) }
    }
    val lockedApps = state.vaults.flatMap { it.hiddenApps }.toSet()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            placeholder = { Text("Search apps...", color = textSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = CyberBlue) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberBlue,
                unfocusedBorderColor = if (isDark) Color.DarkGray else Color.LightGray,
                cursorColor = CyberBlue,
                focusedTextColor = textPrimary,
                unfocusedTextColor = textPrimary
            )
        )

        LazyColumn(state = scrollState, modifier = Modifier.weight(1f)) {
            items(filteredApps) { app ->
                val isLocked = lockedApps.contains(app.packageName)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppMiniIcon(app.packageName)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.appName, color = textPrimary, fontWeight = FontWeight.Bold)
                        Text(app.packageName, color = textSecondary, fontSize = 10.sp)
                    }
                    
                    Row {
                        IconButton(onClick = { onHideApp(app.packageName) }) {
                            Icon(Icons.Default.VisibilityOff, null, tint = if (isLocked) CyberBlue else textSecondary)
                        }
                        Switch(
                            checked = isLocked,
                            onCheckedChange = { onToggleAppLock(app.packageName) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberBlue,
                                checkedTrackColor = CyberBlue.copy(alpha = 0.5f),
                                uncheckedThumbColor = if (isDark) Color.Gray else Color.White,
                                uncheckedTrackColor = if (isDark) Color.DarkGray else Color.LightGray
                            )
                        )
                    }
                }
                HorizontalDivider(color = textSecondary.copy(alpha = 0.1f))
            }
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    color: Color = AppBlue,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(140.dp),
        shape = RoundedCornerShape(28.dp),
        color = if (isDark) CyberDarkBlue else CreamWhite,
        border = null,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon, 
                contentDescription = null, 
                tint = if (isDark) CyberBlue else color,
                modifier = Modifier.size(32.dp)
            )
            
            Column {
                Text(
                    title, 
                    color = if (isDark) Color.White else Color.Black.copy(alpha = 0.8f), 
                    fontWeight = FontWeight.Black, 
                    fontSize = 18.sp,
                    maxLines = 1
                )
                Text(
                    subtitle, 
                    color = if (isDark) Color.Gray else Color.Gray, 
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun CategoryItem(
    title: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    bgColor: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isDark) CyberDarkBlue else CreamWhite,
        shape = RoundedCornerShape(24.dp),
        border = null,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, 
                null, 
                tint = if (isDark) CyberBlue else color, 
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title, 
                    color = if (isDark) Color.White else Color.Black.copy(alpha = 0.8f), 
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
                Text(
                    stringResource(R.string.items_count, count), 
                    color = if (isDark) Color.Gray else Color.Gray,
                    fontSize = 13.sp
                )
            }
            Icon(
                Icons.Default.ChevronRight, 
                null, 
                tint = if (isDark) Color.Gray.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingsSection(
    state: VaultState,
    onOpenUsageSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenProtectedApps: () -> Unit,
    onToggleMasterStealth: () -> Unit,
    onGrantCamera: () -> Unit,
    onGrantStorage: () -> Unit,
    onGrantFullStorage: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onToggleFingerprint: () -> Unit,
    onSetLanguage: (String) -> Unit,
    onOpenLanguageSelection: () -> Unit,
    onToggleScreenshotRestriction: () -> Unit
) {
    val isDark = state.isDarkMode
    val textPrimary = if (isDark) Color.White else Color.Black.copy(alpha = 0.8f)
    val textSecondary = Color.Gray
    val surfaceColor = if (isDark) CyberDarkBlue else CreamWhite.copy(alpha = 0.95f)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

            PermissionItem(stringResource(R.string.permission_camera), stringResource(R.string.permission_camera_desc), state.hasCameraPermission, isDark, onGrantCamera)
            PermissionItem(stringResource(R.string.permission_storage), stringResource(R.string.permission_storage_desc), state.hasStoragePermission, isDark, onGrantStorage)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                PermissionItem(
                    "Automated Hiding Mode", 
                    "Skip system 'Delete' prompts by granting All Files Access.", 
                    state.hasFullStoragePermission, 
                    isDark, 
                    onGrantFullStorage
                )
            }

            // Other necessary permissions hidden in screenshots but needed
            if (!state.hasUsageStatsPermission || !state.hasOverlayPermission) {
                Text(stringResource(R.string.system_permissions), color = if (isDark) CyberBlue else AppBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                if (!state.hasUsageStatsPermission) PermissionItem("App Usage Access", "Required to detect app launches", false, isDark, onOpenUsageSettings)
                if (!state.hasOverlayPermission) PermissionItem("Overlay Permission", "Required to show lock screen", false, isDark, onOpenOverlaySettings)
            }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.lock_options), 
                    color = if (isDark) CyberBlue else AppBlue, 
                    fontSize = 16.sp, 
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                Surface(
                    color = surfaceColor,
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, (if (isDark) Color.White else Color.Black).copy(alpha = 0.05f)),
                    shadowElevation = 4.dp
                ) {
                    Column {
                        SettingsToggleItem(
                            title = stringResource(R.string.fingerprint_unlock),
                            subtitle = stringResource(R.string.fingerprint_unlock_desc),
                            icon = Icons.Default.Fingerprint,
                            checked = state.isFingerprintEnabled,
                            isDark = isDark,
                            onCheckedChange = { onToggleFingerprint() }
                        )
                        // Removed Dividers to clean up "lines"
                        SettingsToggleItem(
                            title = stringResource(R.string.screenshot_restriction),
                            subtitle = stringResource(R.string.screenshot_restriction_desc),
                            icon = Icons.Default.Screenshot,
                            checked = state.isScreenshotRestricted,
                            isDark = isDark,
                            onCheckedChange = { onToggleScreenshotRestriction() }
                        )
                        SettingsToggleItem(
                            title = stringResource(R.string.dark_mode),
                            subtitle = stringResource(R.string.dark_mode_desc),
                            icon = Icons.Default.WbSunny,
                            checked = state.isDarkMode,
                            isDark = isDark,
                            onCheckedChange = { onToggleDarkMode() }
                        )
                        SettingsLinkItem(
                            title = stringResource(R.string.language),
                            subtitle = if (state.currentLanguage == "hi") "हिन्दी" else "English",
                            icon = Icons.Default.Language,
                            isDark = isDark,
                            onClick = onOpenLanguageSelection
                        )
                    }
                }
            }

        // Support & Legal
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.support_links), 
                color = if (isDark) CyberBlue else AppBlue, 
                fontSize = 14.sp, 
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            Surface(
                color = surfaceColor,
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, (if (isDark) Color.White else Color.Black).copy(alpha = 0.05f)),
                shadowElevation = 4.dp
            ) {
                Column {
                    SettingsActionItem(stringResource(R.string.feedback), Icons.Default.ChatBubbleOutline, isDark) {}
                    SettingsActionItem(stringResource(R.string.faq), Icons.Default.HelpOutline, isDark) {}
                    SettingsActionItem(stringResource(R.string.terms_of_use), Icons.Default.Description, isDark) {}
                    SettingsActionItem(stringResource(R.string.privacy_policy), Icons.Default.Security, isDark) {}
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun PermissionItem(title: String, subtitle: String, isGranted: Boolean, isDark: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isDark) CyberDarkBlue else CreamWhite,
        shape = RoundedCornerShape(24.dp),
        border = null,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isGranted) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, 
                null, 
                tint = if (isGranted) IconGreen else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = if (isDark) Color.White else Color.Black.copy(alpha = 0.8f), fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(subtitle, color = if (isDark) Color.Gray else Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SettingsToggleItem(title: String, subtitle: String, icon: ImageVector, checked: Boolean, isDark: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (isDark) CyberBlue else AppBlue, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (isDark) Color.White else Color.Black.copy(alpha = 0.8f), fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text(subtitle, color = if (isDark) Color.Gray else Color.Gray, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = if (isDark) CyberBlue else AppBlue,
                uncheckedThumbColor = if (isDark) Color.Gray else Color.White,
                uncheckedTrackColor = if (isDark) Color.DarkGray else Color.LightGray
            )
        )
    }
}

@Composable
fun SettingsLinkItem(title: String, subtitle: String, icon: ImageVector, isDark: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (isDark) CyberBlue else AppBlue, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (isDark) Color.White else Color.Black.copy(alpha = 0.8f), fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text(subtitle, color = if (isDark) Color.Gray else Color.Gray, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = if (isDark) Color.Gray else Color.LightGray)
    }
}

@Composable
fun SettingsActionItem(title: String, icon: ImageVector, isDark: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (isDark) CyberBlue else AppBlue, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, color = if (isDark) Color.White else Color.Black.copy(alpha = 0.8f), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.Default.OpenInNew, null, tint = if (isDark) Color.Gray else Color.LightGray, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun BackupManagementDialog(
    state: VaultState,
    onDismiss: () -> Unit,
    onRemoveVault: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (state.isDarkMode) CyberDarkBlue else Color.White,
            border = BorderStroke(1.dp, (if (state.isDarkMode) Color.White else Color.Black).copy(alpha = 0.1f)),
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "MANAGE VAULTS", 
                    color = if (state.isDarkMode) Color.White else Color.Black, 
                    fontWeight = FontWeight.Black, 
                    fontSize = 20.sp
                )
                Spacer(Modifier.height(16.dp))
                
                if (state.vaults.isEmpty()) {
                    Text("No active vaults found.", color = Color.Gray)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(state.vaults) { vault ->
                            val mapsLink = "https://www.google.com/maps/search/?api=1&query=${vault.location.latitude},${vault.location.longitude}"
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Coordinates: ${String.format("%.5f", vault.location.latitude)}, ${String.format("%.5f", vault.location.longitude)}",
                                            color = CyberBlue,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsLink))
                                                context.startActivity(intent)
                                            }
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Lock Type: ${vault.lockType}",
                                            color = if (state.isDarkMode) Color.LightGray else Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                    IconButton(onClick = { onRemoveVault(vault.id) }) {
                                        Icon(Icons.Default.Delete, null, tint = CyberNeonRed)
                                    }
                                }
                                
                                Spacer(Modifier.height(8.dp))
                                
                                Text(
                                    "Secured Apps:", 
                                    color = if (state.isDarkMode) Color.White else Color.Black, 
                                    fontSize = 12.sp, 
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    vault.hiddenApps.forEach { pkg ->
                                        val appInfo = state.installedApps.find { it.packageName == pkg }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            AppMiniIcon(pkg)
                                            Text(
                                                appInfo?.appName ?: "App", 
                                                fontSize = 9.sp, 
                                                color = if (state.isDarkMode) Color.Gray else Color.DarkGray,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = (if (state.isDarkMode) Color.White else Color.Black).copy(alpha = 0.05f))
                        }
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("CLOSE", color = Color.Gray)
                    }
                    Button(
                        onClick = { onClearAll(); onDismiss() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberNeonRed)
                    ) {
                        Text("CLEAR ALL", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun AppMiniIcon(packageName: String) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        try { context.packageManager.getApplicationIcon(packageName).toBitmap() } catch (e: Exception) { null }
    }
    if (icon != null) {
        Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(modifier = Modifier.size(40.dp).background(Color.DarkGray, RoundedCornerShape(8.dp)))
    }
}

fun copyToClipboard(context: Context, lat: Double, lon: Double) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Location", "$lat,$lon")
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Location copied!", Toast.LENGTH_SHORT).show()
}

@Composable
fun InteractiveUserGuide(onDismiss: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf(
        GuideStep(
            "Welcome to GeoVault",
            "Secure your media and apps based on your location. Let's learn how to use the player and import files.",
            Icons.Default.Security
        ),
        GuideStep(
            "Importing Files",
            "Tap the '+' button on the dashboard to select photos, videos, or audio. You'll see a preview before they are encrypted.",
            Icons.Default.Add
        ),
        GuideStep(
            "Video Player Gestures",
            "Swipe UP/DOWN on the LEFT side to adjust Brightness.\nSwipe UP/DOWN on the RIGHT side to adjust Volume.",
            Icons.Default.SettingsSystemDaydream
        ),
        GuideStep(
            "Audio Player Gestures",
            "Just like the video player, you can swipe UP/DOWN anywhere to adjust volume while listening to your secure audio.",
            Icons.Default.VolumeUp
        )
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = CyberDarkBlue,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val currentStep = steps[step]
                
                Icon(
                    currentStep.icon,
                    null,
                    tint = CyberBlue,
                    modifier = Modifier.size(64.dp)
                )
                
                Spacer(Modifier.height(24.dp))
                
                Text(
                    currentStep.title,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    currentStep.description,
                    color = Color.Gray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                
                Spacer(Modifier.height(32.dp))
                
                Button(
                    onClick = {
                        if (step < steps.size - 1) step++ else onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBlue)
                ) {
                    Text(if (step < steps.size - 1) "NEXT" else "GOT IT", fontWeight = FontWeight.Black)
                }
                
                if (step > 0) {
                    TextButton(onClick = { step-- }) {
                        Text("PREVIOUS", color = Color.Gray)
                    }
                }
            }
        }
    }
}

data class GuideStep(val title: String, val description: String, val icon: ImageVector)

@Composable
fun OperationProgressOverlay(progress: OperationProgress, isDark: Boolean) {
    val surfaceColor = if (isDark) Color(0xFF1A1A1A) else Color.White
    val contentColor = if (isDark) Color.White else Color.Black
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.85f),
            shape = RoundedCornerShape(28.dp),
            color = surfaceColor,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    progress.title.uppercase(),
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = contentColor,
                    letterSpacing = 1.sp
                )
                
                Spacer(Modifier.height(24.dp))
                
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress.percentage / 100f },
                        modifier = Modifier.size(120.dp),
                        color = CyberBlue,
                        strokeWidth = 8.dp,
                        trackColor = Color.Gray.copy(alpha = 0.2f),
                        strokeCap = StrokeCap.Round
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${progress.percentage.toInt()}%",
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            color = contentColor
                        )
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                Text(
                    progress.currentFile,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    color = contentColor.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
                
                Spacer(Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${progress.processedFiles}/${progress.totalFiles} files",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    if (progress.speedMbps > 0) {
                        Text(
                            String.format("%.1f Mbps", progress.speedMbps),
                            color = CyberBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                if (progress.timeRemainingSeconds > 0) {
                    Text(
                        "Remaining: ${formatRemainingTime(progress.timeRemainingSeconds)}",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private fun formatRemainingTime(seconds: Long): String {
    return if (seconds >= 60) {
        val mins = seconds / 60
        val secs = seconds % 60
        String.format("%dm %ds", mins, secs)
    } else {
        String.format("%ds", seconds)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewBottomSheet(
    uris: List<Uri>,
    category: FileCategory,
    galleryItems: List<GalleryItem>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedItems = remember(uris, galleryItems) {
        galleryItems.filter { uris.contains(it.uri) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121212),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.DarkGray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Text(
                "PREVIEW IMPORT",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Text(
                "Review the files you're about to secure in the vault.",
                color = Color.Gray,
                fontSize = 14.sp
            )
            
            Spacer(Modifier.height(24.dp))
            
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(selectedItems) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(64.dp)
                        ) {
                            if (category == FileCategory.AUDIO) {
                                Box(modifier = Modifier.fillMaxSize().background(CyberDarkBlue), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.MusicNote, null, tint = IconRed)
                                }
                            } else {
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        
                        Spacer(Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(formatFileSize(item.size), color = Color.Gray, fontSize = 12.sp)
                                if (item.duration != null && item.duration > 0) {
                                    Text(" • ", color = Color.Gray)
                                    Text(formatDuration(item.duration), color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }

                        IconButton(onClick = { 
                            selectedItems.find { it.uri == item.uri }?.let { galleryItem ->
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(galleryItem.uri, if (category == FileCategory.VIDEO) "video/*" else "audio/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No app found to preview this file", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.PlayCircle, null, tint = CyberBlue, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
            
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("MOVE TO VAULT", fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("CANCEL", color = Color.Gray)
            }
        }
    }
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups.coerceIn(0, units.size - 1)])
}
