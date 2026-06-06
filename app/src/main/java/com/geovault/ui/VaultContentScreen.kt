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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.*
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
    isDark: Boolean,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding() // FIX: Add padding for system navigation buttons
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = { showPreviewSheet = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("PREVIEW & HIDE (${selectedUris.size})", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White)
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
                            selectedContainerColor = CyberBlue,
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
                                isDark = true,
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
fun GalleryGridItem(item: GalleryItem, category: FileCategory, isSelected: Boolean, isDark: Boolean, onToggle: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .combinedClickable(
                onClick = { onToggle() },
                onLongClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onToggle()
                }
            )
    ) {
        if (category == FileCategory.AUDIO) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isDark) CyberDarkBlue else SoftGray),
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
                model = item.thumbnail ?: item.uri,
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
                tint = CyberBlue,
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

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(java.util.Locale.US, "%02d:%02d", min, sec)
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultContentScreen(
    state: VaultState,
    onLockClick: () -> Unit,
    onAppClick: (String) -> Unit,
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
    onGrantBackgroundPopups: () -> Unit = {},
    onFetchGalleryItems: (FileCategory) -> Unit,
    onDeleteFile: (String) -> Unit,
    onRestoreFile: (String) -> Unit,
    onRemoveAppFromVault: (String, String) -> Unit = { _, _ -> },
    onToggleDarkMode: () -> Unit,
    onToggleFingerprint: () -> Unit,
    onSetLanguage: (String) -> Unit,
    onCompleteTour: () -> Unit,
    onToggleScreenshotRestriction: () -> Unit,
    onToggleUninstallShield: (Boolean) -> Unit = {},
    onRestoreAndUninstall: () -> Unit = {},
    onCreateFolder: (String) -> Unit = {},
    onDeleteFolder: (String, Boolean) -> Unit = { _, _ -> },
    onBulkDelete: (Set<String>) -> Unit = {},
    onBulkRestore: (Set<String>) -> Unit = {},
    onAddFilesToFolder: (List<Uri>, String) -> Unit = { _, _ -> },
    onStartAction: () -> Unit = {},
    onEndAction: () -> Unit = {}
) {
    var hideAppsRect   by remember { mutableStateOf(Rect.Zero) }
    var historyRect    by remember { mutableStateOf(Rect.Zero) }
    var categoriesRect by remember { mutableStateOf(Rect.Zero) }
    var settingsRect   by remember { mutableStateOf(Rect.Zero) }
    var fabRect        by remember { mutableStateOf(Rect.Zero) }

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
    var isSelectionActive by remember { mutableStateOf(false) }

    val dashboardScrollState = rememberLazyListState()
    val appLockScrollState = rememberLazyListState()
    val categoryGridStates = remember { mutableMapOf<FileCategory, LazyGridState>() }

    var appToHide by remember { mutableStateOf<String?>(null) }
    var isCloning by remember { mutableStateOf(false) }
    
    var showUserGuide by remember { mutableStateOf(state.showTour) }

    // Navigation Stack Handling
    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            viewingFile != null -> viewingFile = null
            selectedCategoryForAdd != null || selectedFolderForAdd != null -> {
                selectedCategoryForAdd = null
                selectedFolderForAdd = null
            }
            currentScreen is ContentScreen.WebView -> currentScreen = ContentScreen.Settings
            currentScreen is ContentScreen.FAQ -> currentScreen = ContentScreen.Settings
            currentScreen is ContentScreen.LanguageSelection -> currentScreen = ContentScreen.Settings
            currentScreen != ContentScreen.Dashboard -> currentScreen = ContentScreen.Dashboard
            else -> onLockClick()
        }
    }

    LaunchedEffect(currentScreen) {
        isSelectionActive = false
    }

    var showDashboardTour by remember {
        mutableStateOf(FeatureHintManager.shouldShow(context, "dashboard_tour"))
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        onEndAction()
        if (results.values.any { it }) {
            // Directly trigger the file picker if permission is granted
            val cat = if (currentScreen is ContentScreen.CategoryView) (currentScreen as ContentScreen.CategoryView).category else FileCategory.PHOTO
            val folder = if (currentScreen is ContentScreen.FolderView) (currentScreen as ContentScreen.FolderView).folderName else null
            
            if (folder != null) {
                selectedFolderForAdd = folder
                onFetchGalleryItems(FileCategory.PHOTO)
            } else {
                selectedCategoryForAdd = cat
                onFetchGalleryItems(cat)
            }
        }
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
                        if (currentScreen is ContentScreen.WebView) {
                            Text(
                                (currentScreen as ContentScreen.WebView).title,
                                fontWeight = FontWeight.Black, 
                                color = CyberBlue,
                                fontSize = 20.sp,
                                maxLines = 1
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (currentScreen is ContentScreen.Dashboard) {
                                    Image(
                                        painter = painterResource(id = R.drawable.removed_background_18),
                                        contentDescription = null,
                                        modifier = Modifier.size(45.dp).padding(end = 8.dp)
                                    )
                                }
                                Text(
                                    title,
                                    fontWeight = FontWeight.Black, 
                                    color = CyberBlue,
                                    fontSize = 26.sp,
                                    letterSpacing = (-0.5).sp
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (currentScreen != ContentScreen.Dashboard) {
                            IconButton(onClick = { 
                                currentScreen = when(currentScreen) {
                                    is ContentScreen.WebView -> ContentScreen.Settings
                                    is ContentScreen.FAQ -> ContentScreen.Settings
                                    else -> ContentScreen.Dashboard
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = if (isDark) Color.White else CyberBlue)
                            }
                        }
                    },
                    actions = {
                        if (currentScreen == ContentScreen.Dashboard) {
                            IconButton(
                                onClick = { currentScreen = ContentScreen.Settings },
                                modifier = Modifier.captureRect { settingsRect = it }
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black)
                            }
                        }
                        IconButton(onClick = onLockClick) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Lock", tint = CyberBlue)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            floatingActionButton = {
                val isIntruderCategory = currentScreen is ContentScreen.CategoryView && (currentScreen as ContentScreen.CategoryView).category == FileCategory.INTRUDER
                if (!isSelectionActive && !isIntruderCategory && (currentScreen is ContentScreen.Dashboard || currentScreen is ContentScreen.CategoryView || currentScreen is ContentScreen.FolderView)) {
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
                        modifier = Modifier.captureRect { fabRect = it },
                        containerColor = if (currentScreen == ContentScreen.Dashboard) FolderPurple else CyberBlue,
                        shape = CircleShape
                    ) {
                        Icon(
                            if (currentScreen == ContentScreen.Dashboard) Icons.Default.CreateNewFolder else Icons.Default.Add,
                            contentDescription = "Add",
                            tint = Color.White
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
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
                    modifier = Modifier.then(if (currentScreen is ContentScreen.WebView) Modifier else Modifier.padding(horizontal = 16.dp)),
                    label = "ContentTransition"
                ) { screen ->
                    when (screen) {
                        is ContentScreen.Dashboard -> DashboardContent(
                            state = state,
                            scrollState = dashboardScrollState,
                            onAppLockClick = { currentScreen = ContentScreen.AppLock },
                            onCategoryClick = { currentScreen = ContentScreen.CategoryView(it) },
                            onFolderClick = { currentScreen = ContentScreen.FolderView(it) },
                            onDeleteFolder = onDeleteFolder,
                            onBackupClick = { showBackupDialog = true },
                            onHideAppsRectCaptured = { hideAppsRect = it },
                            onHistoryRectCaptured = { historyRect = it },
                            onCategoriesRectCaptured = { categoriesRect = it }
                        )
                        is ContentScreen.AppLock -> {
                            if (state.installedApps.isEmpty()) {
                                repeat(5) {
                                    SkeletonBox(modifier = Modifier.fillMaxWidth().height(70.dp).padding(vertical = 8.dp), isDark = isDark)
                                }
                            } else {
                                AppLockManagement(
                                    state = state, 
                                    scrollState = appLockScrollState,
                                    onToggleAppLock = onToggleAppLock,
                                    onHideApp = { pkg ->
                                        appToHide = pkg
                                    }
                                )
                            }
                        }
                        is ContentScreen.Settings -> SettingsSection(
                            state = state,
                            onOpenUsageSettings = onOpenUsageSettings,
                            onOpenOverlaySettings = onOpenOverlaySettings,
                            onOpenProtectedApps = onOpenProtectedApps,
                            onGrantBackgroundPopups = onGrantBackgroundPopups,
                            onToggleMasterStealth = onToggleMasterStealth,
                            onGrantCamera = onGrantCamera,
                            onGrantStorage = onGrantStorage,
                            onGrantFullStorage = onGrantFullStorage,
                            onToggleDarkMode = onToggleDarkMode,
                            onToggleFingerprint = onToggleFingerprint,
                            onSetLanguage = onSetLanguage,
                            onOpenLanguageSelection = { currentScreen = ContentScreen.LanguageSelection },
                            onToggleScreenshotRestriction = onToggleScreenshotRestriction,
                            onToggleUninstallShield = onToggleUninstallShield,
                            onRestoreAndUninstall = onRestoreAndUninstall,
                            onOpenWebView = { url, title -> currentScreen = ContentScreen.WebView(url, title) },
                            onOpenFAQ = { currentScreen = ContentScreen.FAQ }
                        )
                        is ContentScreen.CategoryView -> {
                            val gridState = categoryGridStates.getOrPut(screen.category) { LazyGridState() }
                            FileCategoryList(
                                category = screen.category,
                                files = state.files.filter { it.category == screen.category && it.folderName == null },
                                isDark = isDark,
                                gridState = gridState,
                                onFileClick = { viewingFile = it },
                                onSelectionActive = { isSelectionActive = it },
                                onBulkDelete = onBulkDelete,
                                onBulkRestore = onBulkRestore
                            )
                        }
                        is ContentScreen.FolderView -> {
                            val gridState = rememberLazyGridState()
                            FileCategoryList(
                                category = FileCategory.OTHER,
                                files = state.files.filter { it.folderName == screen.folderName },
                                isDark = isDark,
                                gridState = gridState,
                                onFileClick = { viewingFile = it },
                                onSelectionActive = { isSelectionActive = it },
                                onBulkDelete = onBulkDelete,
                                onBulkRestore = onBulkRestore
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
                        is ContentScreen.FAQ -> FAQScreen(isDark = isDark)
                        is ContentScreen.WebView -> WebViewScreen(
                            url = screen.url,
                            isDark = isDark
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
                    isDarkMode = state.isDarkMode,
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
                isDark = isDark,
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
            onRemoveAppFromVault = onRemoveAppFromVault,
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

    if (showDashboardTour && currentScreen is ContentScreen.Dashboard) {
        DashboardTourOverlay(
            steps = listOf(
                DashboardTourStep(R.string.tour_dash_welcome_title, R.string.tour_dash_welcome_desc),
                DashboardTourStep(R.string.tour_dash_hide_apps_title, R.string.tour_dash_hide_apps_desc, hideAppsRect),
                DashboardTourStep(R.string.tour_dash_history_title, R.string.tour_dash_history_desc, historyRect),
                DashboardTourStep(R.string.tour_dash_categories_title, R.string.tour_dash_categories_desc, categoriesRect),
                DashboardTourStep(R.string.tour_dash_fab_title, R.string.tour_dash_fab_desc, fabRect)
            ),
            onCompleted = {
                showDashboardTour = false
                FeatureHintManager.markShown(context, "dashboard_tour")
            }
        )
    }

    if (showUserGuide) {
        val tourSteps = listOf(
            DashboardTourStep(R.string.tour_dash_welcome_title, R.string.tour_dash_welcome_desc, null),
            DashboardTourStep(R.string.tour_dash_hide_apps_title, R.string.tour_dash_hide_apps_desc, hideAppsRect.takeIf { it != Rect.Zero }),
            DashboardTourStep(R.string.tour_dash_history_title, R.string.tour_dash_history_desc, historyRect.takeIf { it != Rect.Zero }),
            DashboardTourStep(R.string.tour_dash_categories_title, R.string.tour_dash_categories_desc, categoriesRect.takeIf { it != Rect.Zero }),
            DashboardTourStep(R.string.tour_dash_fab_title, R.string.tour_dash_fab_desc, fabRect.takeIf { it != Rect.Zero }),
            DashboardTourStep(R.string.tour_dash_settings_title, R.string.tour_dash_settings_desc, settingsRect.takeIf { it != Rect.Zero })
        )
        DashboardTourOverlay(
            steps = tourSteps,
            onCompleted = {
                showUserGuide = false
                onCompleteTour()
            }
        )
        
        // Show interactive guide after tour
        InteractiveUserGuide(
            onDismiss = { /* Already handled by tour completion usually */ },
            isDark = isDark
        )
    }
}

sealed class ContentScreen {
    object Dashboard : ContentScreen()
    object AppLock : ContentScreen()
    object Settings : ContentScreen()
    object LanguageSelection : ContentScreen()
    object FAQ : ContentScreen()
    data class CategoryView(val category: FileCategory) : ContentScreen()
    data class FolderView(val folderName: String) : ContentScreen()
    data class WebView(val url: String, val title: String) : ContentScreen()
}

@Composable
fun DashboardContent(
    state: VaultState,
    scrollState: LazyListState = rememberLazyListState(),
    onAppLockClick: () -> Unit,
    onCategoryClick: (FileCategory) -> Unit,
    onFolderClick: (String) -> Unit,
    onDeleteFolder: (String, Boolean) -> Unit,
    onBackupClick: () -> Unit,
    onHideAppsRectCaptured: (Rect) -> Unit = {},
    onHistoryRectCaptured: (Rect) -> Unit = {},
    onCategoriesRectCaptured: (Rect) -> Unit = {}
) {
    val isDark = state.isDarkMode
    
    if (state.installedApps.isEmpty() && state.files.isEmpty()) {
        DashboardSkeleton(isDark)
        return
    }

    val textPrimary = if (isDark) Color.White else LightTextPrimary
    val textSecondary = if (isDark) TextSecondary else LightTextSecondary
    var isGridView by rememberSaveable { mutableStateOf(false) }
    var folderToDelete by remember { mutableStateOf<String?>(null) }

    if (folderToDelete != null) {
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete Folder") },
            text = { Text("Do you want to recover all data to the gallery or delete all data permanently?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteFolder(folderToDelete!!, true)
                        folderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBlue)
                ) { Text("RECOVER ALL") }
            },
            dismissButton = {
                Button(
                    onClick = {
                        onDeleteFolder(folderToDelete!!, false)
                        folderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("DELETE ALL") }
            }
        )
    }

    val categories = remember(state) {
        val list = mutableListOf(
            CategoryData(FileCategory.PHOTO, "", state.photoCount, Icons.Filled.Image, IconBlue, SoftBlue),
            CategoryData(FileCategory.VIDEO, "", state.videoCount, Icons.Filled.PlayCircle, IconOrange, SoftOrange),
            CategoryData(FileCategory.AUDIO, "", state.audioCount, Icons.Filled.MusicNote, IconRed, SoftRed),
            CategoryData(FileCategory.DOCUMENT, "", state.documentCount, Icons.Filled.Description, IconGreen, SoftGreen),
            CategoryData(FileCategory.INTRUDER, "", state.intruderCount, Icons.Filled.PersonSearch, IconOrange, SoftOrange),
            CategoryData(FileCategory.RECYCLE_BIN, "", state.recycleBinCount, Icons.Filled.Delete, IconGray, SoftGray)
        )
        state.customFolders.forEach { folderName ->
            val count = state.files.count { it.folderName == folderName }
            list.add(CategoryData(FileCategory.OTHER, folderName, count, Icons.Default.Folder, IconPurple, SoftPurple, folderName))
        }
        list
    }

    // Since CategoryData is a data class and its title is a String, and we need localized strings, 
    // we can't easily put stringResource inside remember without current composition.
    // However, DashboardContent is a Composable.
    
    val localizedCategories = categories.map { cat ->
        cat.copy(title = if (cat.customFolderName != null) cat.title else when(cat.category) {
            FileCategory.PHOTO -> stringResource(R.string.photos)
            FileCategory.VIDEO -> stringResource(R.string.videos)
            FileCategory.AUDIO -> stringResource(R.string.audio)
            FileCategory.DOCUMENT -> stringResource(R.string.documents)
            FileCategory.INTRUDER -> stringResource(R.string.wrong_unlocks)
            FileCategory.RECYCLE_BIN -> stringResource(R.string.recycle_bin)
            else -> cat.title
        })
    }

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(
                    title = stringResource(R.string.app_lock_title),
                    subtitle = stringResource(R.string.app_lock_subtitle),
                    icon = Icons.Default.Lock,
                    modifier = Modifier
                        .weight(1f)
                        .captureRect { onHideAppsRectCaptured(it) },
                    color = CyberBlue,
                    isDark = isDark,
                    onClick = onAppLockClick
                )
                DashboardCard(
                    title = stringResource(R.string.history_title),
                    subtitle = stringResource(R.string.history_subtitle),
                    icon = Icons.Default.History,
                    modifier = Modifier
                        .weight(1f)
                        .captureRect { onHistoryRectCaptured(it) },
                    color = IconBlue,
                    isDark = isDark,
                    onClick = onBackupClick
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .captureRect { onCategoriesRectCaptured(it) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.categories),
                    style = MaterialTheme.typography.titleMedium,
                    color = textPrimary,
                    fontWeight = FontWeight.Black
                )
                IconButton(onClick = { isGridView = !isGridView }) {
                    Icon(
                        if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                        null,
                        tint = textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (isGridView) {
            val chunks = localizedCategories.chunked(2)
            items(chunks) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { cat ->
                        val modifier = Modifier.weight(1f)
                        if (cat.customFolderName != null) {
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = {
                                    if (it == SwipeToDismissBoxValue.EndToStart) {
                                        folderToDelete = cat.customFolderName
                                        false
                                    } else false
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                modifier = modifier,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(
                                        Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Color.Red).padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                                    }
                                }
                            ) {
                                CategoryGridItem(cat.title, cat.count, cat.icon, cat.color, isDark) {
                                    onFolderClick(cat.customFolderName)
                                }
                            }
                        } else {
                            CategoryGridItem(cat.title, cat.count, cat.icon, cat.color, isDark, modifier) {
                                onCategoryClick(cat.category)
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            items(localizedCategories) { cat ->
                Box(modifier = Modifier.padding(bottom = 12.dp)) {
                    if (cat.customFolderName != null) {
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart) {
                                    folderToDelete = cat.customFolderName
                                    false
                                } else false
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(
                                    Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Color.Red).padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                                }
                            }
                        ) {
                            CategoryItem(cat.title, cat.count, cat.icon, cat.color, cat.bgColor, isDark) {
                                onFolderClick(cat.customFolderName)
                            }
                        }
                    } else {
                        CategoryItem(cat.title, cat.count, cat.icon, cat.color, cat.bgColor, isDark) {
                            onCategoryClick(cat.category)
                        }
                    }
                }
            }
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
                color = if (isDark) Color.White else LightTextPrimary, 
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 1,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.items_count, count), 
                color = if (isDark) Color.Gray else LightTextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        }
    }
}



@Composable
fun IllustrationBox(category: FileCategory, isDark: Boolean) {
    val color = when(category) {
        FileCategory.PHOTO -> CyberBlue
        FileCategory.VIDEO -> IconOrange
        FileCategory.AUDIO -> IconRed
        FileCategory.DOCUMENT -> IconGreen
        FileCategory.INTRUDER -> Color.Red
        else -> IconPurple
    }
    
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
        Canvas(modifier = Modifier.size(120.dp)) {
            drawCircle(
                color = color.copy(alpha = 0.1f),
                radius = size.minDimension / 2
            )
            drawCircle(
                color = color.copy(alpha = 0.2f),
                radius = size.minDimension / 3,
                center = Offset(size.width * 0.7f, size.height * 0.3f)
            )
        }
        
        Icon(
            imageVector = when(category) {
                FileCategory.PHOTO -> Icons.Default.AddPhotoAlternate
                FileCategory.VIDEO -> Icons.Default.VideoLibrary
                FileCategory.AUDIO -> Icons.Default.LibraryMusic
                FileCategory.DOCUMENT -> Icons.Default.ContentPasteSearch
                FileCategory.INTRUDER -> Icons.Default.VerifiedUser
                else -> Icons.Default.FolderOpen
            },
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = color
        )
    }
}

@Composable
fun FileCategoryList(
    category: FileCategory,
    files: List<VaultFile>,
    isDark: Boolean,
    gridState: LazyGridState = rememberLazyGridState(),
    onFileClick: (VaultFile) -> Unit,
    onSelectionActive: (Boolean) -> Unit = {},
    onBulkDelete: (Set<String>) -> Unit = {},
    onBulkRestore: (Set<String>) -> Unit = {}
) {
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    
    LaunchedEffect(selectedIds) {
        onSelectionActive(selectedIds.isNotEmpty())
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Branded Mascot-style empty state with illustration
                    IllustrationBox(category, isDark)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = when(category) {
                            FileCategory.INTRUDER -> "Peaceful. No intruders detected."
                            else -> "Your vault is empty"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = "Tap the + button to secure your items.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files) { file ->
                    FileItem(
                        file = file,
                        isSelected = selectedIds.contains(file.id),
                        isDark = isDark,
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                selectedIds = if (selectedIds.contains(file.id)) selectedIds - file.id else selectedIds + file.id
                            } else {
                                onFileClick(file)
                            }
                        },
                        onLongClick = {
                            selectedIds = selectedIds + file.id
                        }
                    )
                }
            }
        }

        // Selection Actions Toolbar (Moved to bottom as requested)
        AnimatedVisibility(
            visible = selectedIds.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).zIndex(10f)
        ) {
            Surface(
                color = if (isDark) CyberDarkBlue else LightSurface,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 12.dp,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                border = if (!isDark) BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)) else null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, "Cancel", tint = if (isDark) Color.White else LightTextPrimary)
                        }
                        IconButton(onClick = { 
                            selectedIds = if (selectedIds.size == files.size) emptySet() else files.map { it.id }.toSet()
                        }) {
                            Icon(
                                if (selectedIds.size == files.size) Icons.Default.Deselect else Icons.Default.SelectAll,
                                "Select All", 
                                tint = if (isDark) Color.White else LightTextPrimary
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${selectedIds.size} Selected",
                            color = if (isDark) Color.White else LightTextPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                    }
                    
                    Row {
                        IconButton(onClick = { 
                            onBulkRestore(selectedIds)
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Restore, "Restore", tint = CyberBlue)
                        }
                        
                        IconButton(onClick = { 
                            onBulkDelete(selectedIds)
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileItem(file: VaultFile, isSelected: Boolean = false, isDark: Boolean, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(3.dp, CyberBlue) else null,
        colors = CardDefaults.cardColors(containerColor = if (isDark) CyberDarkBlue else SoftGray)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FileThumbnail(file, isDark)
            
            if (isSelected) {
                Box(modifier = Modifier.fillMaxSize().background(CyberBlue.copy(alpha = 0.3f)))
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                )
            }

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
fun FileThumbnail(file: VaultFile, isDark: Boolean) {
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
            Icon(icon, null, tint = if (isDark) Color.Gray.copy(alpha = 0.5f) else LightOutline.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
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
    val textPrimary = if (isDark) Color.White else LightTextPrimary
    val textSecondary = if (isDark) TextSecondary else LightTextSecondary
    
    var searchQuery by remember { mutableStateOf("") }
    
    val recommendedPackages = remember {
        setOf(
            "com.whatsapp", "com.whatsapp.w4b", "com.snapchat.android", "com.instagram.android",
            "com.google.android.youtube", "com.truecaller", "com.google.android.apps.photos",
            "com.android.chrome", "com.google.android.apps.messaging", "com.phonepe.app",
            "org.telegram.messenger", "com.google.android.googlequicksearchbox", 
            "com.google.android.contacts", "com.android.contacts", "com.google.android.dialer"
        )
    }

    val appStats = remember(state.installedApps) {
        state.installedApps.associate { it.packageName to (93..99).random() }
    }

    val (recommendedApps, otherApps) = remember(state.installedApps, recommendedPackages, searchQuery) {
        val filtered = state.installedApps.filter { it.appName.contains(searchQuery, ignoreCase = true) }
        filtered.partition { recommendedPackages.contains(it.packageName) }
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
            if (recommendedApps.isNotEmpty()) {
                item {
                    Text(
                        "RECOMMENDED",
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberBlue.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                }
                items(recommendedApps) { app ->
                    AppLockItem(
                        app = app,
                        isLocked = lockedApps.contains(app.packageName),
                        stats = appStats[app.packageName] ?: 95,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        isDark = isDark,
                        onToggleLock = { onToggleAppLock(app.packageName) },
                        onHideApp = { onHideApp(app.packageName) }
                    )
                }
            }

            if (otherApps.isNotEmpty()) {
                item {
                    Text(
                        "ALL APPS",
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = textSecondary.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold
                    )
                }
                items(otherApps) { app ->
                    AppLockItem(
                        app = app,
                        isLocked = lockedApps.contains(app.packageName),
                        stats = null,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        isDark = isDark,
                        onToggleLock = { onToggleAppLock(app.packageName) },
                        onHideApp = { onHideApp(app.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
fun AppLockItem(
    app: AppInfo,
    isLocked: Boolean,
    stats: Int?,
    textPrimary: Color,
    textSecondary: Color,
    isDark: Boolean,
    onToggleLock: () -> Unit,
    onHideApp: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppMiniIcon(app.packageName)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.appName, color = textPrimary, fontWeight = FontWeight.Bold)
            if (stats != null) {
                Text(
                    text = buildAnnotatedString {
                        append("OVER ")
                        withStyle(
                            SpanStyle(color = CyberBlue, fontWeight = FontWeight.Black)
                        ) {
                            append("$stats%")
                        }
                        append(" OF USERS HAVE IT LOCKED")
                    },
                    fontSize = 9.sp,
                    letterSpacing = 0.2.sp,
                    color = textSecondary
                )
            }
        }
        
        Row {
            IconButton(onClick = { 
                HapticHelper.vibrate(context, 1)
                onToggleLock() 
            }) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = if (isLocked) CyberBlue else textSecondary
                )
            }
        }
    }
    HorizontalDivider(color = textSecondary.copy(alpha = 0.1f))
}

@Composable
fun DashboardSkeleton(isDark: Boolean) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SkeletonBox(modifier = Modifier.weight(1f).height(140.dp), isDark = isDark)
            SkeletonBox(modifier = Modifier.weight(1f).height(140.dp), isDark = isDark)
        }
        Spacer(Modifier.height(32.dp))
        SkeletonBox(modifier = Modifier.width(150.dp).height(24.dp), isDark = isDark)
        Spacer(Modifier.height(16.dp))
        repeat(4) {
            SkeletonBox(modifier = Modifier.fillMaxWidth().height(80.dp), isDark = isDark)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun SkeletonBox(modifier: Modifier, isDark: Boolean) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background((if (isDark) Color.White else Color.Black).copy(alpha = alpha * 0.1f))
    )
}

@Composable
fun DashboardCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    color: Color = CyberBlue,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "CardScale"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(140.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { 
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { 
                        HapticHelper.vibrate(context, 1)
                        onClick() 
                    }
                )
            },
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
                    color = if (isDark) Color.White else LightTextPrimary, 
                    fontWeight = FontWeight.Black, 
                    fontSize = 18.sp,
                    maxLines = 1
                )
                Text(
                    subtitle, 
                    color = if (isDark) TextSecondary else LightTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    fontWeight = FontWeight.Bold
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(if (isDark) Color.White.copy(alpha = 0.05f) else color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, 
                    null, 
                    tint = if (isDark) CyberBlue else color, 
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title, 
                    color = if (isDark) Color.White else LightTextPrimary, 
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
                Text(
                    stringResource(R.string.items_count, count),
                    color = if (isDark) Color.Gray else LightTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                Icons.Default.ChevronRight, 
                null, 
                tint = if (isDark) Color.Gray.copy(alpha = 0.5f) else LightOutline,
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
    onGrantBackgroundPopups: () -> Unit = {},
    onToggleDarkMode: () -> Unit,
    onToggleFingerprint: () -> Unit,
    onSetLanguage: (String) -> Unit,
    onOpenLanguageSelection: () -> Unit,
    onToggleScreenshotRestriction: () -> Unit,
    onToggleUninstallShield: (Boolean) -> Unit = {},
    onRestoreAndUninstall: () -> Unit = {},
    onOpenWebView: (String, String) -> Unit,
    onOpenFAQ: () -> Unit
) {
    val context = LocalContext.current
    val isDark = state.isDarkMode
    val textPrimary = if (isDark) Color.White else LightTextPrimary
    val textSecondary = Color.Gray
    val surfaceColor = if (isDark) CyberDarkBlue else CreamWhite.copy(alpha = 0.95f)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

            // Other necessary permissions hidden in screenshots but needed
            if (!state.hasUsageStatsPermission || !state.hasOverlayPermission || !state.hasBatteryOptimizationPermission || !state.hasBackgroundPopupsPermission || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !state.hasFullStoragePermission)) {
                Text(stringResource(R.string.system_permissions), color = CyberBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                if (!state.hasUsageStatsPermission) PermissionItem("App Usage Access", "Required to detect app launches", false, isDark, onOpenUsageSettings)
                if (!state.hasOverlayPermission) PermissionItem("Overlay Permission", "Required to show lock screen", false, isDark, onOpenOverlaySettings)
                if (!state.hasBackgroundPopupsPermission) PermissionItem("Background Pop-ups", "Allows lock to appear in background", false, isDark, onGrantBackgroundPopups)
                if (!state.hasBatteryOptimizationPermission) PermissionItem("Battery Optimization", "Allows protection to run 24/7", false, isDark, onOpenProtectedApps)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !state.hasFullStoragePermission) PermissionItem("Full Storage Access", "Required to delete files from gallery", false, isDark, onGrantFullStorage)
            }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.lock_options), 
                    color = CyberBlue,
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
                color = CyberBlue, 
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
                    SettingsActionItem(stringResource(R.string.tutorial), Icons.Default.PlayCircleOutline, isDark) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=YOUR_VIDEO_ID"))
                        context.startActivity(intent)
                    }
                    SettingsActionItem(stringResource(R.string.feedback), Icons.Default.ChatBubbleOutline, isDark) {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:hello@aitoyz.in")
                            putExtra(Intent.EXTRA_SUBJECT, "Feedback for GeoVault")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    SettingsActionItem(stringResource(R.string.faq), Icons.Default.HelpOutline, isDark) {
                        onOpenFAQ()
                    }
                    SettingsActionItem(stringResource(R.string.terms_of_use), Icons.Default.Description, isDark) {
                        val url = if (state.currentLanguage == "hi") "https://maps.aitoyz.in/termshindi.html" else "https://maps.aitoyz.in/terms.html"
                        onOpenWebView(url, "Terms of Use")
                    }
                    SettingsActionItem(stringResource(R.string.privacy_policy), Icons.Default.Security, isDark) {
                        val url = if (state.currentLanguage == "hi") "https://maps.aitoyz.in/privacypolicyhindi.html" else "https://maps.aitoyz.in/privacypolicy.html"
                        onOpenWebView(url, "Privacy Policy")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "map data © openstreet map",
                color = textSecondary.copy(alpha = 0.4f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "mapplock v1.0 - Aitoyz labs",
                color = textSecondary.copy(alpha = 0.3f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun FAQScreen(isDark: Boolean) {
    val faqs = remember {
        listOf(
            "What is Mapplock and how is it different from other app lockers?" to "Mapplock is a next-generation security application developed by Aitoyz that uses location-based intelligence to protect your privacy. Unlike standard lockers that rely solely on PINs, Mapplock introduces \"Map-Gate\" technology, allowing you to define safe geographical zones. It provides a professional-grade vault for photos, videos, and files, combined with advanced intruder detection and stealth disguises. It is specifically built for users who want their phone's security to adapt automatically to their environment.",
            "How does Mapplock protect my data against 'Q-Day'?" to "Mapplock is built with a Quantum-Resilient architecture. Most cloud-based lockers are vulnerable to 'Harvest Now, Decrypt Later' attacks, where data is stolen today to be cracked by future quantum computers. Mapplock prevents this by using local-only AES-256 symmetric encryption and a zero-knowledge architecture. Since your data never leaves your device, it cannot be harvested. Even with quantum algorithms like Grover's, our 256-bit encryption remains computationally unbreakable for billions of years.",
            "How does the \"Map-Gate\" feature work?" to "The Map-Gate feature allows you to set specific GPS coordinates as \"Safe Zones,\" such as your home or office. While you are within the designated radius of a safe zone, your protected apps can be accessed more conveniently or kept unlocked entirely. However, the moment you move outside this radius, Mapplock triggers a high-security lockdown automatically. This ensures that if your phone is lost, stolen, or accessed in a public area, your sensitive data remains completely inaccessible to others.",
            "Are my private photos and videos stored on Mapplock's servers?" to "No, Aitoyz operates on a \"Privacy-First\" model, meaning Mapplock does not store any of your personal files on our servers. All photos, videos, and documents you move into the vault are encrypted locally on your device’s internal storage. We do not have any remote access to your private content, and we cannot see or share your files. This approach ensures that you have total control over your data and that it remains secure even if your internet connection is compromised.",
            "What happens if I forget my PIN or Pattern?" to "If you forget your primary security code, you can use the secondary verification methods established during the initial setup. This includes biometric authentication, such as fingerprint or face unlock, if you have enabled those options in the settings menu. Additionally, you can use your \"Secret Map Point\" as a recovery method to reset your credentials. We strongly recommend setting up multiple recovery options to avoid a permanent lockout, as we cannot recover your PIN remotely for security reasons.",
            "Will the map features work if I am offline or have no internet?" to "Yes, Mapplock is designed to remain fully functional even without an active data or Wi-Fi connection. The app proactively downloads and caches GPS map data for your current regional area as soon as you grant the required location permissions. This offline map engine allows the security interface to load instantly and accurately detect your location-based \"Safe Zones.\" This ensures that your Map-Gate security remains active and reliable, regardless of your signal strength or roaming status.",
            "What is \"Intruder Capture\" and where can I see the photos?" to "Intruder Capture is an automated security feature that takes a secret selfie of anyone attempting to break into your vault. If an incorrect PIN or Pattern is entered more than the allowed number of times, the app silently triggers the front camera to snap a photo of the user. These photos are stored within a dedicated \"Intruders\" category inside your vault, complete with a timestamp of the attempt. You can review these logs at any time to see exactly who tried to access your private apps without permission.",
            "What is \"File Loss Protection\" and why should I enable it?" to "File Loss Protection is a critical safeguard that uses Device Administrator rights to prevent the accidental or unauthorized uninstallation of Mapplock. Because your files are encrypted and stored within the app's protected directory, a standard uninstallation would result in the permanent loss of all your hidden data. By enabling this feature, the system requires an extra verification step before the app can be removed from the device. This ensures that your important documents and memories are never deleted by a mistake or by someone else."
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(faqs) { (question, answer) ->
            var expanded by remember { mutableStateOf(false) }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) CyberDarkBlue else CreamWhite,
                shadowElevation = 2.dp,
                onClick = { expanded = !expanded }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = question,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color.Black
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = CyberBlue
                        )
                    }
                    if (expanded) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = answer,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Color.Gray else Color.DarkGray,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WebViewScreen(url: String, isDark: Boolean) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            android.webkit.WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = android.webkit.WebViewClient()
                loadUrl(url)
            }
        },
        update = { webView ->
            // Update the webview if needed
        }
    )
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
                tint = if (isGranted) IconGreen else (if (isDark) Color.Gray else LightOutline),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = if (isDark) Color.White else LightTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(subtitle, color = if (isDark) Color.Gray else LightTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SettingsToggleItem(title: String, subtitle: String, icon: ImageVector, checked: Boolean, isDark: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                HapticHelper.vibrate(context, 1)
                onCheckedChange(!checked) 
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon, 
            contentDescription = null, 
            tint = if (checked) CyberBlue else (if (isDark) Color.Gray else LightOutline.copy(alpha = 0.6f)), 
            modifier = Modifier.size(24.dp).scale(if (checked) 1.1f else 1.0f)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (isDark) Color.White else LightTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text(subtitle, color = if (isDark) Color.Gray else LightTextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = CyberBlue,
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
        Icon(icon, null, tint = CyberBlue, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (isDark) Color.White else LightTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text(subtitle, color = if (isDark) Color.Gray else LightTextSecondary, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = if (isDark) Color.Gray else LightOutline)
    }
}

@Composable
fun SettingsActionItem(title: String, icon: ImageVector, isDark: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = CyberBlue, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, color = if (isDark) Color.White else LightTextPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.Default.OpenInNew, null, tint = if (isDark) Color.Gray else LightOutline, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun BackupManagementDialog(
    state: VaultState,
    onDismiss: () -> Unit,
    onRemoveVault: (String) -> Unit,
    onRemoveAppFromVault: (String, String) -> Unit,
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
                    "LOCKED APPS",
                    color = if (state.isDarkMode) Color.White else Color.Black, 
                    fontWeight = FontWeight.Black, 
                    fontSize = 20.sp,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(16.dp))
                
                if (state.vaults.isEmpty()) {
                    Text(stringResource(R.string.vault_empty), color = Color.Gray)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 450.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(state.vaults) { vault ->
                            Column {
                                // 1. Header: Pin Icon + Coordinates (Clickable)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        val lat = vault.location.latitude
                                        val lon = vault.location.longitude
                                        val gmmIntentUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
                                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                        mapIntent.`package` = "com.google.android.apps.maps"
                                        context.startActivity(mapIntent)
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = CyberBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "${String.format("%.4f", vault.location.latitude)}, ${String.format("%.4f", vault.location.longitude)}",
                                        color = CyberBlue,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                    )
                                }
                                
                                Spacer(Modifier.height(12.dp))

                                // 2. App List for this Coordinate
                                vault.hiddenApps.forEach { pkg ->
                                    val appInfo = state.installedApps.find { it.packageName == pkg }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp, horizontal = 4.dp)
                                    ) {
                                        AppMiniIcon(pkg)
                                        Spacer(Modifier.width(16.dp))
                                        Text(
                                            appInfo?.appName ?: try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { "App" },
                                            color = if (state.isDarkMode) Color.White else Color.Black,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 15.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { onRemoveAppFromVault(vault.id, pkg) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.VisibilityOff,
                                                contentDescription = "Unhide",
                                                tint = CyberBlue,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                                
                                if (vault.hiddenApps.isEmpty()) {
                                    Text("No apps in this zone", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 28.dp))
                                }
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(32.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CLOSE", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { onClearAll(); onDismiss() }) {
                        Text("UNLOCK ALL", fontWeight = FontWeight.Black, color = CyberNeonRed)
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
fun InteractiveUserGuide(onDismiss: () -> Unit, isDark: Boolean) {
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf(
        GuideStep(
            "Welcome to GeoVault",
            "Secure your media and apps based on your location. Let's learn how to use the player and import files.",
            Icons.Default.Security
        ),
        GuideStep(
            "Q-Day Protected",
            "Your data is shielded against the future. We use Quantum-Resilient local encryption (AES-256) to ensure your files remain uncrackable, even by next-generation quantum computers.",
            Icons.Default.Hub
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
            color = if (isDark) CyberDarkBlue else LightSurface,
            border = BorderStroke(1.dp, (if (isDark) Color.White else Color.Black).copy(alpha = 0.05f)),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shadowElevation = 8.dp
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
                    color = if (isDark) Color.White else LightTextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    currentStep.description,
                    color = if (isDark) Color.Gray else LightTextSecondary,
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
                    Text(if (step < steps.size - 1) "NEXT" else "GOT IT", fontWeight = FontWeight.Black, color = Color.White)
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
    val contentColor = if (isDark) Color.White else LightTextPrimary
    
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
