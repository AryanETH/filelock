package com.aitoyz.mapplock.ui

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
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.*
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.aitoyz.mapplock.R
import com.aitoyz.mapplock.model.*
import com.aitoyz.mapplock.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.aitoyz.mapplock.core.AppCloner
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.pow
import androidx.compose.ui.platform.LocalConfiguration
import com.aitoyz.mapplock.core.VirtualAppManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalFilePicker(
    initialCategory: FileCategory,
    galleryItems: List<GalleryItem>,
    isFetching: Boolean,
    onHide: (List<Uri>) -> Unit,
    onCancel: () -> Unit
) {
    var selectedUris by remember { mutableStateOf(setOf<Uri>()) }
    var currentCategory by remember { mutableStateOf(initialCategory) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf("All") }
    var showPreviewSheet by remember { mutableStateOf(false) }
    
    val config = LocalConfiguration.current
    val locale = remember(config) { config.locales[0] }

    val folders = remember(galleryItems) {
        listOf("All") + galleryItems.map { it.folderName }.distinct()
    }

    val filteredItems = remember(galleryItems, selectedFolder) {
        if (selectedFolder == "All") galleryItems
        else galleryItems.filter { it.folderName == selectedFolder }
    }

    val groupedItems = remember(filteredItems, locale) {
        filteredItems.groupBy { item ->
            val calendar = Calendar.getInstance(locale)
            calendar.timeInMillis = item.dateAdded * 1000L
            val now = Calendar.getInstance(locale)
            
            when {
                isSameDay(calendar, now) -> "Today"
                isYesterday(calendar) -> "Yesterday"
                else -> {
                    val sdf = SimpleDateFormat("MMMM d", locale)
                    sdf.format(calendar.time)
                }
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showCategoryMenu = true }
                    ) {
                        Text(
                            stringResource(R.string.photos),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
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
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = { onHide(selectedUris.toList()) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.preview_and_hide, selectedUris.size), fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White)
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
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .combinedClickable(
                onClick = { onToggle() },
                onLongClick = {
                    HapticHelper.vibrate(context, 1)
                    onToggle()
                }
            )
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
                model = item.thumbnail ?: item.uri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .border(0.5.dp, Color.Black.copy(alpha = 0.4f)),
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

private fun isYesterday(cal1: Calendar): Boolean {
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

val ContentScreenSaver = listSaver<ContentScreen, Any>(
    save = { screen ->
        when (screen) {
            is ContentScreen.Dashboard -> listOf("Dashboard")
            is ContentScreen.AppLock -> listOf("AppLock")
            is ContentScreen.Settings -> listOf("Settings")
            is ContentScreen.LanguageSelection -> listOf("LanguageSelection")
            is ContentScreen.FAQ -> listOf("FAQ")
            is ContentScreen.CategoryView -> listOf("CategoryView", screen.category.name)
            is ContentScreen.FolderView -> listOf("FolderView", screen.folderName)
            is ContentScreen.WebView -> listOf("WebView", screen.url, screen.title)
            is ContentScreen.ImageAdjustment -> listOf("ImageAdjustment", screen.uri.toString())
            is ContentScreen.ProtectionGuide -> listOf("ProtectionGuide")
        }
    },
    restore = { list ->
        when (list[0] as String) {
            "Dashboard" -> ContentScreen.Dashboard
            "AppLock" -> ContentScreen.AppLock
            "Settings" -> ContentScreen.Settings
            "LanguageSelection" -> ContentScreen.LanguageSelection
            "FAQ" -> ContentScreen.FAQ
            "CategoryView" -> ContentScreen.CategoryView(FileCategory.valueOf(list[1] as String))
            "FolderView" -> ContentScreen.FolderView(list[1] as String)
            "WebView" -> ContentScreen.WebView(list[1] as String, list[2] as String)
            "ImageAdjustment" -> ContentScreen.ImageAdjustment((list[1] as String).toUri())
            "ProtectionGuide" -> ContentScreen.ProtectionGuide
            else -> ContentScreen.Dashboard
        }
    }
)

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultContentScreen(
    state: VaultState,
    virtualAppManager: com.aitoyz.mapplock.core.VirtualAppManager,
    appCloner: com.aitoyz.mapplock.core.AppCloner,
    onLockClick: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenProtectedApps: () -> Unit,
    onOpenAutoStartSettings: () -> Unit = {},
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
    onToggleIntruderCapture: (Boolean) -> Unit = {},
    onToggleUninstallShield: (Boolean) -> Unit = {},
    onRestoreAndUninstall: () -> Unit = {},
    onCreateFolder: (String) -> Unit = {},
    onDeleteFolder: (String, Boolean) -> Unit = { _, _ -> },
    onBulkDelete: (Set<String>, Boolean) -> Unit = { _, _ -> },
    onBulkRestore: (Set<String>) -> Unit = {},
    onAddFilesToFolder: (List<Uri>, String) -> Unit = { _, _ -> },
    onSetCustomBackground: (String?) -> Unit = {},
    onSetMonitoringMode: (MonitoringMode) -> Unit = {},
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
    
    var currentScreen by rememberSaveable(stateSaver = ContentScreenSaver) { mutableStateOf(ContentScreen.Dashboard) }

    var categoryForBulkAction by remember { mutableStateOf<FileCategory?>(null) }
    var folderForBulkAction by remember { mutableStateOf<String?>(null) }
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
    var showStoragePermissionScreen by remember { mutableStateOf(false) }
    
    var showUserGuide by remember { mutableStateOf(state.showTour) }

    LaunchedEffect(state.hasFullStoragePermission, state.hasStoragePermission) {
        val hasFullStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) state.hasFullStoragePermission else state.hasStoragePermission
        if (hasFullStorage) {
            showStoragePermissionScreen = false
        }
    }

    // Navigation Stack Handling
    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            viewingFile != null -> viewingFile = null
            showStoragePermissionScreen -> showStoragePermissionScreen = false
            selectedCategoryForAdd != null || selectedFolderForAdd != null -> {
                selectedCategoryForAdd = null
                selectedFolderForAdd = null
            }
            currentScreen is ContentScreen.WebView -> currentScreen = ContentScreen.Settings
            currentScreen is ContentScreen.FAQ -> currentScreen = ContentScreen.Settings
            currentScreen is ContentScreen.LanguageSelection -> currentScreen = ContentScreen.Settings
            currentScreen is ContentScreen.ProtectionGuide -> currentScreen = ContentScreen.AppLock
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

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { selectedUris ->
        onEndAction()
        if (selectedUris.isNotEmpty()) {
            val folder = if (currentScreen is ContentScreen.FolderView) (currentScreen as ContentScreen.FolderView).folderName else null
            val cat = if (currentScreen is ContentScreen.CategoryView) (currentScreen as ContentScreen.CategoryView).category else FileCategory.PHOTO
            
            if (folder != null) {
                onAddFilesToFolder(selectedUris, folder)
            } else {
                onAddFiles(selectedUris, cat)
            }
        }
    }

    val isDark = state.isDarkMode
    val backgroundColor = if (isDark) CyberBlack else CreamWhite

    if (folderForBulkAction != null) {
        AlertDialog(
            onDismissRequest = { folderForBulkAction = null },
            title = { Text(stringResource(R.string.delete_folder_confirm), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.bulk_action_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFolder(folderForBulkAction!!, true)
                    folderForBulkAction = null
                }) {
                    Text(stringResource(R.string.recover_folder), color = CyberBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDeleteFolder(folderForBulkAction!!, false)
                    folderForBulkAction = null
                }) {
                    Text(stringResource(R.string.delete), color = Color.Red)
                }
            },
            containerColor = if (isDark) CyberDarkBlue else Color.White,
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (categoryForBulkAction != null) {
        AlertDialog(
            onDismissRequest = { categoryForBulkAction = null },
            title = { Text(stringResource(R.string.bulk_action_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.bulk_action_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    val ids = state.files.filter { it.category == categoryForBulkAction && it.folderName == null }.map { it.id }.toSet()
                    if (ids.isNotEmpty()) onBulkRestore(ids)
                    categoryForBulkAction = null
                }) {
                    Text(stringResource(R.string.recover_all), color = CyberBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val ids = state.files.filter { it.category == categoryForBulkAction && it.folderName == null }.map { it.id }.toSet()
                    if (ids.isNotEmpty()) onBulkDelete(ids, categoryForBulkAction == FileCategory.RECYCLE_BIN)
                    categoryForBulkAction = null
                }) {
                    Text(stringResource(if (categoryForBulkAction == FileCategory.RECYCLE_BIN) R.string.delete_confirm else R.string.delete), color = Color.Red)
                }
            },
            containerColor = if (isDark) CyberDarkBlue else Color.White,
            shape = RoundedCornerShape(28.dp)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = backgroundColor,
            topBar = {
                if (currentScreen !is ContentScreen.ImageAdjustment) {
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
                                        FileCategory.RECYCLE_BIN -> stringResource(R.string.recycle_bin)
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
                                            painter = painterResource(id = R.drawable.removed_background_19),
                                            contentDescription = null,
                                            modifier = Modifier.size(45.dp).padding(end = 8.dp)
                                        )
                                    }
                                    Text(
                                        title,
                                        fontWeight = FontWeight.Black, 
                                        color = CyberBlue,
                                        fontSize = 22.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                                        is ContentScreen.LanguageSelection -> ContentScreen.Settings
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
                }
            },
            floatingActionButton = {
                val isIntruderCategory = currentScreen is ContentScreen.CategoryView && (currentScreen as ContentScreen.CategoryView).category == FileCategory.INTRUDER
                val isRecycleBin = currentScreen is ContentScreen.CategoryView && (currentScreen as ContentScreen.CategoryView).category == FileCategory.RECYCLE_BIN
                val isComingSoon = currentScreen is ContentScreen.CategoryView && (
                    (currentScreen as ContentScreen.CategoryView).category == FileCategory.VIDEO ||
                    (currentScreen as ContentScreen.CategoryView).category == FileCategory.AUDIO ||
                    (currentScreen as ContentScreen.CategoryView).category == FileCategory.DOCUMENT
                )
                if (!isSelectionActive && !isIntruderCategory && !isRecycleBin && !isComingSoon && (currentScreen is ContentScreen.Dashboard || currentScreen is ContentScreen.CategoryView || currentScreen is ContentScreen.FolderView)) {
                    FloatingActionButton(
                        onClick = { 
                            if (currentScreen == ContentScreen.Dashboard) {
                                showCreateFolderDialog = true
                            } else {
                                onStartAction()
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                    )
                                )
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
                        is ContentScreen.Dashboard -> {
                            if (showStoragePermissionScreen) {
                                AppLockPermissionScreen(
                                    title = stringResource(R.string.full_storage_access),
                                    description = stringResource(R.string.full_storage_access_desc),
                                    icon = Icons.Default.Folder,
                                    isDark = isDark,
                                    onGrant = {
                                        onStartAction()
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            onGrantFullStorage()
                                        } else {
                                            onGrantStorage()
                                        }
                                    }
                                )
                            } else {
                                DashboardContent(
                                    state = state,
                                    scrollState = dashboardScrollState,
                                    onAppLockClick = { currentScreen = ContentScreen.AppLock },
                                    onCategoryClick = { cat ->
                                        val hasFullStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) state.hasFullStoragePermission else state.hasStoragePermission
                                        if (!hasFullStorage) {
                                            showStoragePermissionScreen = true
                                        } else {
                                            if (cat == FileCategory.VIDEO || cat == FileCategory.AUDIO || cat == FileCategory.DOCUMENT) {
                                                Toast.makeText(context, "Coming Soon", Toast.LENGTH_SHORT).show()
                                            } else {
                                                currentScreen = ContentScreen.CategoryView(cat)
                                            }
                                        }
                                    },
                                    onFolderClick = { folderName ->
                                        val hasFullStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) state.hasFullStoragePermission else state.hasStoragePermission
                                        if (!hasFullStorage) {
                                            showStoragePermissionScreen = true
                                        } else {
                                            currentScreen = ContentScreen.FolderView(folderName)
                                        }
                                    },
                                    onCategoryBulkAction = { categoryForBulkAction = it },
                                    onFolderBulkAction = { folderForBulkAction = it },
                                    onBackupClick = { showBackupDialog = true },
                                    onHideAppsRectCaptured = { hideAppsRect = it },
                                    onHistoryRectCaptured = { historyRect = it },
                                    onCategoriesRectCaptured = { categoriesRect = it }
                                )
                            }
                        }
                        is ContentScreen.AppLock -> {
                            if (state.installedApps.isEmpty()) {
                                repeat(5) {
                                    SkeletonBox(modifier = Modifier.fillMaxWidth().height(70.dp).padding(vertical = 8.dp), isDark = isDark)
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    val targetScreen = when {
                                        !state.hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> "NotificationPermission"
                                        !state.hasUsageStatsPermission -> "UsagePermission"
                                        !state.hasOverlayPermission -> "OverlayPermission"
                                        !state.hasBackgroundPopupsPermission -> "BackgroundPopupsPermission"
                                        !state.hasBatteryOptimizationPermission -> "BatteryPermission"
                                        else -> "AppList"
                                    }

                                    when (targetScreen) {
                                        "NotificationPermission" -> {
                                            AppLockPermissionScreen(
                                                title = stringResource(R.string.notification_perm_title),
                                                description = stringResource(R.string.notification_perm_desc),
                                                icon = Icons.Default.Notifications,
                                                onGrant = { 
                                                    onStartAction()
                                                    onToggleAppLock("") 
                                                },
                                                isDark = isDark
                                            )
                                        }
                                        "UsagePermission" -> {
                                            AppLockPermissionScreen(
                                                title = stringResource(R.string.usage_perm_title),
                                                description = stringResource(R.string.usage_perm_desc),
                                                icon = Icons.Default.Timeline,
                                                onGrant = { 
                                                    onStartAction()
                                                    onOpenUsageSettings() 
                                                },
                                                isDark = isDark
                                            )
                                        }
                                        "OverlayPermission" -> {
                                            AppLockPermissionScreen(
                                                title = stringResource(R.string.overlay_perm_title),
                                                description = stringResource(R.string.overlay_perm_desc),
                                                icon = Icons.Default.Layers,
                                                onGrant = { 
                                                    onStartAction()
                                                    onOpenOverlaySettings() 
                                                },
                                                isDark = isDark
                                            )
                                        }
                                        "BackgroundPopupsPermission" -> {
                                            AppLockPermissionScreen(
                                                title = stringResource(R.string.background_popups_perm_title),
                                                description = stringResource(R.string.background_popups_perm_desc),
                                                icon = Icons.AutoMirrored.Filled.OpenInNew,
                                                onGrant = { 
                                                    onStartAction()
                                                    onGrantBackgroundPopups() 
                                                },
                                                isDark = isDark
                                            )
                                        }
                                        "BatteryPermission" -> {
                                            AppLockPermissionScreen(
                                                title = stringResource(R.string.battery_perm_title),
                                                description = stringResource(R.string.battery_perm_desc),
                                                icon = Icons.Default.BatteryChargingFull,
                                                onGrant = { 
                                                    onStartAction()
                                                    onOpenProtectedApps() 
                                                },
                                                isDark = isDark
                                            )
                                        }
                                        else -> {
                                            AppLockManagement(
                                                state = state, 
                                                scrollState = appLockScrollState,
                                                onToggleAppLock = onToggleAppLock
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        is ContentScreen.ProtectionGuide -> ProtectionGuideScreen(
                            isDark = isDark,
                            onOpenBatterySettings = onOpenProtectedApps,
                            onOpenAutoStartSettings = onOpenAutoStartSettings,
                            onBack = { currentScreen = ContentScreen.AppLock }
                        )
                        is ContentScreen.Settings -> SettingsSection(
                            state = state,
                            onToggleDarkMode = onToggleDarkMode,
                            onToggleFingerprint = onToggleFingerprint,
                            onOpenLanguageSelection = { currentScreen = ContentScreen.LanguageSelection },
                            onToggleScreenshotRestriction = onToggleScreenshotRestriction,
                            onToggleIntruderCapture = onToggleIntruderCapture,
                            onOpenWebView = { url, title -> currentScreen = ContentScreen.WebView(url, title) },
                            onOpenFAQ = { currentScreen = ContentScreen.FAQ },
                            onPickBackground = { currentScreen = ContentScreen.ImageAdjustment(it) },
                            onSetCustomBackground = onSetCustomBackground,
                            onSetMonitoringMode = onSetMonitoringMode,
                            onStartAction = onStartAction,
                            onEndAction = onEndAction
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
                                files = state.files.filter { it.folderName == screen.folderName && it.category != FileCategory.RECYCLE_BIN },
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
                            url = screen.url
                        )
                        is ContentScreen.ImageAdjustment -> ImageAdjustmentScreen(
                            uri = screen.uri,
                            isDark = isDark,
                            onPickNewImage = { newUri ->
                                currentScreen = ContentScreen.ImageAdjustment(newUri)
                            },
                            onApply = { path ->
                                onSetCustomBackground(path)
                                currentScreen = ContentScreen.Settings
                            },
                            onCancel = { currentScreen = ContentScreen.Settings },
                            onStartAction = onStartAction
                        )
                    }
                }

                state.operationProgress?.let { progress ->
                    OperationProgressOverlay(progress, isDark)
                }
            }
        }

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
            onRemoveAppFromVault = onRemoveAppFromVault,
            onClearAll = onClearAllVaults,
            onStartAction = onStartAction
        )
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(stringResource(R.string.create_new_folder), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(stringResource(R.string.create_new_folder_desc), fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.folder_name)) },
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
    data class ImageAdjustment(val uri: Uri) : ContentScreen()
    object ProtectionGuide : ContentScreen()
}

@Composable
fun AppLockPermissionScreen(
    title: String,
    description: String,
    icon: ImageVector,
    isDark: Boolean,
    onGrant: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            color = (if (isDark) Color.White else CyberBlue).copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = CyberBlue
                )
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = if (isDark) Color.White else CyberNavy
        )
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = if (isDark) Color.LightGray else Color.DarkGray
        )
        
        Spacer(Modifier.height(40.dp))
        
        Button(
            onClick = onGrant,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(stringResource(R.string.grant_permission), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            stringResource(R.string.security_monitoring_note),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
fun DashboardContent(
    state: VaultState,
    scrollState: LazyListState = rememberLazyListState(),
    onAppLockClick: () -> Unit,
    onCategoryClick: (FileCategory) -> Unit,
    onFolderClick: (String) -> Unit,
    onCategoryBulkAction: (FileCategory) -> Unit,
    onFolderBulkAction: (String) -> Unit,
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

    val categories = remember(state) {
        val list = mutableListOf(
            CategoryData(FileCategory.PHOTO, "", state.photoCount, Icons.Filled.Image, IconBlue, SoftBlue, imageRes = R.drawable.images),
            CategoryData(FileCategory.VIDEO, "", state.videoCount, Icons.Filled.PlayCircle, IconOrange, SoftOrange, imageRes = R.drawable.video, isComingSoon = true),
            CategoryData(FileCategory.AUDIO, "", state.audioCount, Icons.Filled.MusicNote, IconRed, SoftRed, imageRes = R.drawable.audio_music, isComingSoon = true),
            CategoryData(FileCategory.DOCUMENT, "", state.documentCount, Icons.Filled.Description, IconGreen, SoftGreen, imageRes = R.drawable.documents, isComingSoon = true),
            CategoryData(FileCategory.INTRUDER, "", state.intruderCount, Icons.Filled.PersonSearch, IconOrange, SoftOrange, imageRes = R.drawable.intruder),
            CategoryData(FileCategory.RECYCLE_BIN, "", state.recycleBinCount, Icons.Filled.Delete, IconGray, SoftGray, imageRes = R.drawable.recycle_bin)
        )
        state.customFolders.forEach { folderName ->
            val count = state.files.count { it.folderName == folderName && it.category != FileCategory.RECYCLE_BIN }
            list.add(CategoryData(FileCategory.OTHER, folderName, count, Icons.Default.Folder, IconPurple, SoftPurple, folderName, R.drawable.custom_folder))
        }
        list
    }

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .captureRect { onCategoriesRectCaptured(it) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
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

                if (isGridView) {
                    val chunks = localizedCategories.chunked(2)
                    chunks.forEach { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEach { cat ->
                                val modifier = Modifier.weight(1f)
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = {
                                        if (it == SwipeToDismissBoxValue.EndToStart) {
                                            if (cat.customFolderName != null) {
                                                onFolderBulkAction(cat.customFolderName)
                                            } else {
                                                onCategoryBulkAction(cat.category)
                                            }
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
                                            Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(24.dp))
                                                .background(Color.Red)
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                ) {
                                    if (cat.customFolderName != null) {
                                        CategoryGridItem(
                                            cat.title,
                                            cat.count,
                                            cat.icon,
                                            cat.color,
                                            cat.bgColor,
                                            isDark,
                                            imageRes = cat.imageRes,
                                            isLargeSquare = cat.isLargeSquare,
                                            isComingSoon = cat.isComingSoon
                                        ) {
                                            onFolderClick(cat.customFolderName)
                                        }
                                    } else {
                                        CategoryGridItem(
                                            cat.title,
                                            cat.count,
                                            cat.icon,
                                            cat.color,
                                            cat.bgColor,
                                            isDark,
                                            Modifier.fillMaxSize(),
                                            cat.imageRes,
                                            isLargeSquare = cat.isLargeSquare,
                                            isComingSoon = cat.isComingSoon
                                        ) {
                                            onCategoryClick(cat.category)
                                        }
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    localizedCategories.forEach { cat ->
                        Box(modifier = Modifier.padding(bottom = 12.dp)) {
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = {
                                    if (it == SwipeToDismissBoxValue.EndToStart) {
                                        if (cat.customFolderName != null) {
                                            onFolderBulkAction(cat.customFolderName)
                                        } else {
                                            onCategoryBulkAction(cat.category)
                                        }
                                        false
                                    } else false
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(Color.Red)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.White
                                        )
                                    }
                                }
                            ) {
                                if (cat.customFolderName != null) {
                                    CategoryItem(
                                        cat.title,
                                        cat.count,
                                        cat.icon,
                                        cat.color,
                                        cat.bgColor,
                                        isDark,
                                        cat.imageRes,
                                        isLargeSquare = cat.isLargeSquare,
                                        isComingSoon = cat.isComingSoon
                                    ) {
                                        onFolderClick(cat.customFolderName)
                                    }
                                } else {
                                    CategoryItem(
                                        cat.title,
                                        cat.count,
                                        cat.icon,
                                        cat.color,
                                        cat.bgColor,
                                        isDark,
                                        cat.imageRes,
                                        isLargeSquare = cat.isLargeSquare,
                                        isComingSoon = cat.isComingSoon
                                    ) {
                                        onCategoryClick(cat.category)
                                    }
                                }
                            }
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
    val customFolderName: String? = null,
    val imageRes: Int? = null,
    val isLargeSquare: Boolean = false,
    val isComingSoon: Boolean = false
)

@Composable
fun CategoryGridItem(
    title: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    bgColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    imageRes: Int? = null,
    isLargeSquare: Boolean = false,
    isComingSoon: Boolean = false,
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
                        .size(if (isLargeSquare) 72.dp else 60.dp)
                        .background(
                            bgColor, 
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                if (imageRes != null) {
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = null,
                        modifier = Modifier.size(if (isLargeSquare) 60.dp else 48.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        icon, 
                        null, 
                        tint = if (isDark) CyberBlue else color, 
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                title, 
                color = if (isDark) Color.White else LightTextPrimary, 
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(2.dp))
            val resources = LocalContext.current.resources
            Text(
                if (isComingSoon) "Coming Soon" else resources.getQuantityString(R.plurals.items_count, count, count), 
                color = if (isDark) Color.Gray else LightTextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        }
    }
}



@Composable
fun IllustrationBox(category: FileCategory) {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    val imageRes = when(category) {
        FileCategory.PHOTO -> R.drawable.images
        FileCategory.VIDEO -> R.drawable.video
        FileCategory.AUDIO -> R.drawable.audio_music
        FileCategory.DOCUMENT -> R.drawable.documents
        FileCategory.INTRUDER -> R.drawable.intruder
        FileCategory.RECYCLE_BIN -> R.drawable.recycle_bin
        else -> R.drawable.custom_folder
    }
    
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageRes)
                .build(),
            imageLoader = imageLoader,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
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
    onBulkDelete: (Set<String>, Boolean) -> Unit = { _, _ -> },
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
                    IllustrationBox(category)
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

                        text = when(category) {
                            FileCategory.INTRUDER -> "I'm on duty sir 24x7."
                            else -> "Tap the + button to secure your items."
                        },
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
                            val isRecycleBin = category == FileCategory.RECYCLE_BIN
                            onBulkDelete(selectedIds, isRecycleBin)
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
fun FileItem(file: VaultFile, modifier: Modifier = Modifier, isSelected: Boolean = false, isDark: Boolean, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val context = LocalContext.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    HapticHelper.vibrate(context, 1)
                    onLongClick()
                }
            ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(3.dp, CyberBlue) else BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.4f)),
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
                else -> Icons.AutoMirrored.Filled.InsertDriveFile
            }
            Icon(icon, null, tint = if (isDark) Color.Gray.copy(alpha = 0.5f) else LightOutline.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun AppLockManagement(
    state: VaultState, 
    scrollState: LazyListState = rememberLazyListState(),
    onToggleAppLock: (String) -> Unit
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
            placeholder = { Text(stringResource(R.string.search_apps), color = textSecondary) },
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
                        stringResource(R.string.recommended),
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
                        onToggleLock = { onToggleAppLock(app.packageName) }
                    )
                }
            }

            if (otherApps.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.all_apps),
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
                        onToggleLock = { onToggleAppLock(app.packageName) }
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
    onToggleLock: () -> Unit
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
                    val statsText = stringResource(R.string.user_locked_stats, stats)
                    val percentStr = "$stats%"
                    val start = statsText.indexOf(percentStr)
                    if (start != -1) {
                        append(statsText.substring(0, start))
                        withStyle(style = SpanStyle(color = CyberBlue, fontWeight = FontWeight.Black)) {
                            append(percentStr)
                        }
                        append(statsText.substring(start + percentStr.length))
                    } else {
                        append(statsText)
                    }
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
    imageRes: Int? = null,
    isLargeSquare: Boolean = false,
    isComingSoon: Boolean = false,
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
                    .size(if (isLargeSquare) 72.dp else 60.dp)
                    .background(
                        bgColor,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (imageRes != null) {
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = null,
                        modifier = Modifier.size(if (isLargeSquare) 60.dp else 48.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        icon, 
                        null, 
                        tint = if (isDark) CyberBlue else color, 
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title, 
                    color = if (isDark) Color.White else LightTextPrimary, 
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val resources = LocalContext.current.resources
                Text(
                    if (isComingSoon) "Coming Soon" else resources.getQuantityString(R.plurals.items_count, count, count),
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
    onToggleDarkMode: () -> Unit,
    onToggleFingerprint: () -> Unit,
    onOpenLanguageSelection: () -> Unit,
    onToggleScreenshotRestriction: () -> Unit,
    onToggleIntruderCapture: (Boolean) -> Unit,
    onOpenWebView: (String, String) -> Unit,
    onOpenFAQ: () -> Unit,
    onPickBackground: (Uri) -> Unit,
    onSetCustomBackground: (String?) -> Unit,
    onSetMonitoringMode: (MonitoringMode) -> Unit = {},
    onStartAction: () -> Unit = {},
    onEndAction: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = state.isDarkMode
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        onEndAction()
        if (uri != null) {
            onPickBackground(uri)
        }
    }

    val textSecondary = Color.Gray
    val surfaceColor = if (isDark) CyberDarkBlue else CreamWhite.copy(alpha = 0.95f)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

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
                            title = stringResource(R.string.intruder_capture),
                            subtitle = stringResource(R.string.intruder_capture_desc),
                            icon = Icons.Default.CameraAlt,
                            checked = state.isIntruderCaptureEnabled,
                            isDark = isDark,
                            onCheckedChange = { onToggleIntruderCapture(it) }
                        )
                        SettingsLinkItem(
                            title = stringResource(R.string.language),
                            subtitle = if (state.currentLanguage == "hi") "हिन्दी" else "English",
                            icon = Icons.Default.Language,
                            isDark = isDark,
                            onClick = onOpenLanguageSelection
                        )

                        var showMonitoringDialog by remember { mutableStateOf(false) }
                        val monitoringModeText = when (state.monitoringMode) {
                            MonitoringMode.ACCESSIBILITY -> stringResource(R.string.monitoring_mode_hard)
                            else -> stringResource(R.string.monitoring_mode_moderate)
                        }

                        SettingsLinkItem(
                            title = stringResource(R.string.monitoring_mode),
                            subtitle = monitoringModeText,
                            icon = Icons.Default.PrecisionManufacturing,
                            isDark = isDark,
                            onClick = { showMonitoringDialog = true }
                        )

                        if (showMonitoringDialog) {
                            MonitoringModeDialog(
                                currentMode = state.monitoringMode,
                                isDark = isDark,
                                onDismiss = { showMonitoringDialog = false },
                                onSelect = {
                                    onSetMonitoringMode(it)
                                    showMonitoringDialog = false
                                }
                            )
                        }
                    }
                }
            }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.appearance),
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
                        title = stringResource(R.string.dark_mode),
                        subtitle = stringResource(R.string.dark_mode_desc),
                        icon = Icons.Default.WbSunny,
                        checked = state.isDarkMode,
                        isDark = isDark,
                        onCheckedChange = { onToggleDarkMode() }
                    )
                    
                    if (state.customBackgroundPath != null) {
                        var showBgOptions by remember { mutableStateOf(false) }
                        
                        Column {
                            SettingsLinkItem(
                                title = stringResource(R.string.custom_lock_background),
                                subtitle = stringResource(R.string.bg_active_desc),
                                icon = Icons.Default.Image,
                                isDark = isDark,
                                onClick = { showBgOptions = !showBgOptions }
                            )
                            
                            if (showBgOptions) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(60.dp, 80.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
                                    ) {
                                        AsyncImage(
                                            model = File(state.customBackgroundPath),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Button(
                                            onClick = {
                                                onStartAction()
                                                imagePickerLauncher.launch("image/*")
                                            },
                                            modifier = Modifier.fillMaxWidth().height(36.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(stringResource(R.string.change), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = { onSetCustomBackground(null) },
                                            modifier = Modifier.fillMaxWidth().height(36.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.6f)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(stringResource(R.string.remove_btn), fontSize = 12.sp, color = Color.Red.copy(alpha = 0.8f))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        SettingsLinkItem(
                            title = stringResource(R.string.custom_lock_background),
                            subtitle = stringResource(R.string.custom_lock_background_desc),
                            icon = Icons.Default.Image,
                            isDark = isDark,
                            onClick = {
                                onStartAction()
                                imagePickerLauncher.launch("image/*")
                            }
                        )
                    }
                }
            }
        }

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
                        onStartAction()
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=YOUR_VIDEO_ID"))
                        context.startActivity(intent)
                    }
                    SettingsActionItem(stringResource(R.string.feedback), Icons.Default.ChatBubbleOutline, isDark) {
                        onStartAction()
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:hello@aitoyz.in")
                            putExtra(Intent.EXTRA_SUBJECT, "Feedback for Mapplock")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    SettingsActionItem(stringResource(R.string.share_mapplock), Icons.Default.Share, isDark) {
                        onStartAction()
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            val shareMessage = "Discover the Next Generation of App Locking and File Protection\n\nThe Future of App Security and File Privacy : https://maps.aitoyz.in"
                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                    }
                    SettingsActionItem(stringResource(R.string.faq), Icons.AutoMirrored.Filled.HelpOutline, isDark) {
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
                text = "Map data © OpenStreetMap",
                color = textSecondary.copy(alpha = 0.4f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Mapplock v1.0 - Aitoyz Labs",
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
    val context = LocalContext.current
    val questions = androidx.compose.ui.res.stringArrayResource(R.array.faq_questions)
    val answers = androidx.compose.ui.res.stringArrayResource(R.array.faq_answers)
    val faqs = remember(questions, answers) {
        questions.zip(answers)
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
fun WebViewScreen(url: String) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            android.webkit.WebView(context).apply {
                // Safe: Only used to display app-internal policy/terms from maps.aitoyz.in
                @android.annotation.SuppressLint("SetJavaScriptEnabled")
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = android.webkit.WebViewClient()
                loadUrl(url)
            }
        }
    )
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
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = if (isDark) Color.Gray else LightOutline, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun BackupManagementDialog(
    state: VaultState,
    onDismiss: () -> Unit,
    onRemoveAppFromVault: (String, String) -> Unit,
    onClearAll: () -> Unit,
    onStartAction: () -> Unit = {}
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
                    stringResource(R.string.locked_apps_header),
                    color = if (state.isDarkMode) Color.White else Color.Black, 
                    fontWeight = FontWeight.Black, 
                    fontSize = 20.sp,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(16.dp))
                
                if (state.vaults.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.locked_apps),
                            contentDescription = null,
                            modifier = Modifier.size(180.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.vault_empty),
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 450.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(state.vaults) { vault ->
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        onStartAction()
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
                                        text = "${String.format(Locale.US, "%.4f", vault.location.latitude)}, ${String.format(Locale.US, "%.4f", vault.location.longitude)}",
                                        color = CyberBlue,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                    )
                                }
                                
                                Spacer(Modifier.height(12.dp))

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
                                            appInfo?.appName ?: try { 
                                                context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() 
                                            } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                                                "App"
                                            } catch (_: Exception) { 
                                                "App" 
                                            },
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
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.locked_apps),
                                            contentDescription = null,
                                            modifier = Modifier.size(100.dp)
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            stringResource(R.string.no_apps_in_zone),
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
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
                        Text(stringResource(R.string.close), color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { onClearAll(); onDismiss() }) {
                        Text(stringResource(R.string.unlock_all), fontWeight = FontWeight.Black, color = CyberNeonRed)
                    }
                }
            }
        }
    }
}

@Composable
fun AppMiniIcon(packageName: String) {
    coil.compose.AsyncImage(
        model = com.aitoyz.mapplock.security.AppIcon(packageName),
        contentDescription = null,
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
        error = coil.compose.rememberAsyncImagePainter(model = android.R.drawable.sym_def_app_icon),
        placeholder = coil.compose.rememberAsyncImagePainter(model = android.R.drawable.sym_def_app_icon)
    )
}

@Composable
fun OperationProgressOverlay(progress: OperationProgress, isDark: Boolean) {
    val surfaceColor = if (isDark) Color(0xFF1A1A1A) else Color.White
    val contentColor = if (isDark) Color.White else LightTextPrimary
    
    val config = LocalConfiguration.current
    val locale = remember(config) { config.locales[0] }
    
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
                    progress.title.uppercase(locale),
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
                            stringResource(R.string.completed_percent, progress.percentage.toInt()),
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = contentColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                if (progress.showHoldOn) {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        color = CyberBlue,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            stringResource(R.string.hold_on_message),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
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
                            String.format(locale, "%.1f Mbps", progress.speedMbps),
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
    val digitGroups = (kotlin.math.log10(size.toDouble()) / kotlin.math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups.coerceIn(0, units.size - 1)])
}

@Composable
fun ImageAdjustmentScreen(
    uri: Uri,
    isDark: Boolean,
    onPickNewImage: (Uri) -> Unit = {},
    onApply: (String) -> Unit,
    onCancel: () -> Unit,
    onStartAction: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var offset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var guideSize by remember { mutableStateOf(IntSize.Zero) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { newUri ->
        if (newUri != null) {
            onPickNewImage(newUri)
        }
    }

    val backgroundColor = if (isDark) Color(0xFF0A0E14) else Color(0xFFF7F9FC)
    val textColor = if (isDark) Color.White else LightTextPrimary
    val subtitleColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray
    val cardBg = if (isDark) Color(0xFF131C27) else Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 2.dp, start = 8.dp, end = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = CyberBlue
                )
            }
            Text(
                text = "Background",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = CyberBlue
            )
            IconButton(
                onClick = { 
                    onStartAction()
                    imagePickerLauncher.launch("image/*") 
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Gallery",
                    tint = CyberBlue,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .aspectRatio(9f / 16f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(20.dp),
                color = cardBg,
                shadowElevation = if (isDark) 0.dp else 8.dp,
                border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.LightGray.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .onGloballyPositioned { 
                            containerSize = it.size
                            guideSize = it.size
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    offset += pan
                                    scale = (scale * zoom).coerceIn(0.5f, 8f)
                                }
                            }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.TouchApp,
                contentDescription = null,
                tint = subtitleColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Pinch to zoom • Drag to reposition",
                fontSize = 13.sp,
                color = subtitleColor
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (isDark) Color.Gray.copy(alpha = 0.5f) else Color.LightGray)
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
            }
            Button(
                onClick = {
                    scope.launch {
                        val savedPath = saveCroppedImage(context, uri, scale, offset, containerSize, guideSize)
                        onApply(savedPath)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberBlue)
            ) {
                Text(
                    text = "Apply",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun MonitoringModeDialog(
    currentMode: MonitoringMode,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onSelect: (MonitoringMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.monitoring_mode),
                color = if (isDark) Color.White else Color.Black,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MonitoringModeItem(MonitoringMode.USAGE_STATS, stringResource(R.string.monitoring_mode_moderate), currentMode == MonitoringMode.USAGE_STATS, isDark) { onSelect(MonitoringMode.USAGE_STATS) }
                MonitoringModeItem(MonitoringMode.ACCESSIBILITY, stringResource(R.string.monitoring_mode_hard), currentMode == MonitoringMode.ACCESSIBILITY, isDark) { onSelect(MonitoringMode.ACCESSIBILITY) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close_btn), color = CyberBlue)
            }
        },
        containerColor = if (isDark) Color(0xFF1A1C1E) else Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun MonitoringModeItem(
    mode: MonitoringMode,
    title: String,
    isSelected: Boolean,
    isDark: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        color = if (isSelected) CyberBlue.copy(alpha = 0.1f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = CyberBlue)
            )
            Text(
                title,
                color = if (isDark) Color.White else Color.Black,
                fontSize = 14.sp
            )
        }
    }
}

suspend fun saveCroppedImage(
    context: Context,
    uri: Uri,
    scale: Float,
    offset: Offset,
    containerSize: IntSize,
    guideSize: IntSize
): String = withContext(Dispatchers.IO) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (originalBitmap == null || containerSize.width == 0 || guideSize.width == 0) return@withContext ""

        val containerW = containerSize.width.toFloat()
        val containerH = containerSize.height.toFloat()
        val bitmapW = originalBitmap.width.toFloat()
        val bitmapH = originalBitmap.height.toFloat()

        val fitScale = min(containerW / bitmapW, containerH / bitmapH)
        val totalScale = fitScale * scale

        val guideW = guideSize.width.toFloat()
        val guideH = guideSize.height.toFloat()

        val guideLeft = (containerW - guideW) / 2
        val guideTop = (containerH - guideH) / 2

        val currentImageLeft = (containerW - (bitmapW * totalScale)) / 2 + offset.x
        val currentImageTop = (containerH - (bitmapH * totalScale)) / 2 + offset.y

        val relativeX = guideLeft - currentImageLeft
        val relativeY = guideTop - currentImageTop

        val cropX = relativeX / totalScale
        val cropY = relativeY / totalScale
        val cropW = guideW / totalScale
        val cropH = guideH / totalScale

        val bitmapRect = android.graphics.Rect(
            cropX.toInt().coerceIn(0, originalBitmap.width),
            cropY.toInt().coerceIn(0, originalBitmap.height),
            (cropX + cropW).toInt().coerceIn(0, originalBitmap.width),
            (cropY + cropH).toInt().coerceIn(0, originalBitmap.height)
        )
        
        if (bitmapRect.width() <= 10 || bitmapRect.height() <= 10) {
            return@withContext saveImageToInternal(context, uri)
        }

        val croppedBitmap = android.graphics.Bitmap.createBitmap(
            originalBitmap,
            bitmapRect.left,
            bitmapRect.top,
            bitmapRect.width(),
            bitmapRect.height()
        )

        val file = File(context.filesDir, "custom_lock_bg.jpg")
        val out = FileOutputStream(file)
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.close()
        
        originalBitmap.recycle()
        croppedBitmap.recycle()

        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}

private suspend fun saveImageToInternal(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val inputStream = context.contentResolver.openInputStream(uri)
    val file = File(context.filesDir, "custom_lock_bg.jpg")
    inputStream?.use { input ->
        FileOutputStream(file).use { output ->
            input.copyTo(output)
        }
    }
    file.absolutePath
}

