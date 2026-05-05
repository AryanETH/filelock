package com.geovault.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.res.stringResource
import com.geovault.R
import com.geovault.model.FileCategory
import com.geovault.model.VaultState
import com.geovault.ui.theme.CyberBlue
import com.geovault.ui.theme.CyberDarkBlue
import com.geovault.ui.theme.TextSecondary

import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.geovault.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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
    onAddFile: (android.net.Uri, FileCategory) -> Unit,
    onToggleAppLock: (String) -> Unit,
    onRemoveVault: (String) -> Unit,
    onClearAllVaults: () -> Unit,
    onGrantCamera: () -> Unit,
    onGrantStorage: () -> Unit,
    onDeleteFile: (String) -> Unit,
    onRestoreFile: (String) -> Unit,
    onToggleDarkMode: () -> Unit,
    onToggleUninstallProtection: () -> Unit,
    onSetLanguage: (String) -> Unit
) {
    var currentScreen by remember { mutableStateOf<ContentScreen>(ContentScreen.Dashboard) }
    var selectedCategoryForAdd by remember { mutableStateOf<FileCategory?>(null) }
    var viewingFile by remember { mutableStateOf<com.geovault.model.VaultFile?>(null) }
    var showBackupDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedCategoryForAdd?.let { cat ->
                onAddFile(it, cat)
            }
        }
    }

    if (viewingFile != null) {
        MediaViewerScreen(
            file = viewingFile!!,
            allFiles = state.files,
            onBack = { viewingFile = null },
            onDelete = { id ->
                onDeleteFile(id)
                viewingFile = null
            },
            onRestore = { id ->
                onRestoreFile(id)
                viewingFile = null
            }
        )
        return
    }

    Scaffold(
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
                        else -> stringResource(R.string.app_name)
                    }
                    Text(
                        title,
                        fontWeight = FontWeight.ExtraBold, 
                        letterSpacing = 2.sp,
                        color = CyberBlue
                    ) 
                },
                navigationIcon = {
                    if (currentScreen != ContentScreen.Dashboard) {
                        IconButton(onClick = { currentScreen = ContentScreen.Dashboard }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (currentScreen == ContentScreen.Dashboard) {
                        IconButton(onClick = { currentScreen = ContentScreen.Settings }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                    IconButton(onClick = onLockClick) {
                        Icon(Icons.Default.LockOpen, contentDescription = "Lock", tint = CyberBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            val isIntruderCategory = currentScreen is ContentScreen.CategoryView && (currentScreen as ContentScreen.CategoryView).category == FileCategory.INTRUDER
            if (!isIntruderCategory && (currentScreen is ContentScreen.Dashboard || currentScreen is ContentScreen.CategoryView)) {
                FloatingActionButton(
                    onClick = { 
                        selectedCategoryForAdd = if (currentScreen is ContentScreen.CategoryView) (currentScreen as ContentScreen.CategoryView).category else FileCategory.OTHER
                        val mime = when(selectedCategoryForAdd) {
                            FileCategory.PHOTO -> "image/*"
                            FileCategory.VIDEO -> "video/*"
                            FileCategory.AUDIO -> "audio/*"
                            FileCategory.DOCUMENT -> "*/*" // Allow all for documents (PDF, DOC, PPT, etc.)
                            else -> "*/*"
                        }
                        filePickerLauncher.launch(mime)
                    },
                    containerColor = CyberBlue,
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
                        onAppLockClick = { currentScreen = ContentScreen.AppLock },
                        onCategoryClick = { currentScreen = ContentScreen.CategoryView(it) },
                        onBackupClick = { showBackupDialog = true }
                    )
                    is ContentScreen.AppLock -> AppLockManagement(state, onToggleAppLock = onToggleAppLock)
                    is ContentScreen.Settings -> SettingsSection(
                        state = state,
                        onOpenUsageSettings = onOpenUsageSettings,
                        onOpenOverlaySettings = onOpenOverlaySettings,
                        onOpenProtectedApps = onOpenProtectedApps,
                        onToggleMasterStealth = onToggleMasterStealth,
                        onGrantCamera = onGrantCamera,
                        onGrantStorage = onGrantStorage,
                        onToggleDarkMode = onToggleDarkMode,
                        onToggleUninstallProtection = onToggleUninstallProtection,
                        onSetLanguage = onSetLanguage
                    )
                    is ContentScreen.CategoryView -> FileCategoryList(
                        category = screen.category,
                        files = state.files.filter { it.category == screen.category },
                        onFileClick = { viewingFile = it }
                    )
                }
            }
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
}

sealed class ContentScreen {
    object Dashboard : ContentScreen()
    object AppLock : ContentScreen()
    object Settings : ContentScreen()
    data class CategoryView(val category: FileCategory) : ContentScreen()
}

@Composable
fun DashboardContent(
    state: VaultState, 
    onAppLockClick: () -> Unit,
    onCategoryClick: (FileCategory) -> Unit,
    onBackupClick: () -> Unit
) {
    val itemsVisible = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { itemsVisible.value = true }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            AnimatedVisibility(
                visible = itemsVisible.value,
                enter = fadeIn(tween(600)) + slideInVertically(tween(600), initialOffsetY = { 50 })
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DashboardCard(
                        title = stringResource(R.string.app_lock_title),
                        subtitle = stringResource(R.string.app_lock_subtitle),
                        icon = Icons.Default.Security,
                        modifier = Modifier.weight(1f),
                        iconContainerColor = Color(0xFF00C853).copy(alpha = 0.2f),
                        iconColor = Color(0xFF00C853),
                        onClick = onAppLockClick
                    )
                    DashboardCard(
                        title = stringResource(R.string.history_title),
                        subtitle = stringResource(R.string.history_subtitle),
                        icon = Icons.Default.History,
                        modifier = Modifier.weight(1f),
                        iconContainerColor = CyberBlue.copy(alpha = 0.2f),
                        iconColor = CyberBlue,
                        onClick = onBackupClick
                    )
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = itemsVisible.value,
                enter = fadeIn(tween(600, delayMillis = 100)) + slideInVertically(tween(600, delayMillis = 100), initialOffsetY = { 50 })
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.categories), 
                        style = MaterialTheme.typography.titleSmall, 
                        fontWeight = FontWeight.Bold, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.Sort, 
                        contentDescription = "Sort", 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, 
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        val categories = listOf(
            Triple(R.string.photos, state.photoCount, FileCategory.PHOTO to Icons.Default.Image to Color(0xFF03A9F4)),
            Triple(R.string.videos, state.videoCount, FileCategory.VIDEO to Icons.Default.PlayCircle to Color(0xFFFFC107)),
            Triple(R.string.audio, state.audioCount, FileCategory.AUDIO to Icons.Default.MusicNote to Color(0xFFFF5252)),
            Triple(R.string.documents, state.documentCount, FileCategory.DOCUMENT to Icons.Default.Description to Color(0xFF00E676)),
            Triple(R.string.wrong_unlocks, state.intruderCount, FileCategory.INTRUDER to Icons.Default.PersonSearch to Color(0xFFFF9800)),
            Triple(R.string.recycle_bin, state.recycleBinCount, FileCategory.OTHER to Icons.Default.Delete to Color(0xFF90A4AE))
        )

        categories.forEachIndexed { index, (titleRes, count, data) ->
            val (catIcon, color) = data
            val (category, icon) = catIcon
            item {
                AnimatedVisibility(
                    visible = itemsVisible.value,
                    enter = fadeIn(tween(600, delayMillis = 150 + (index * 50))) + slideInVertically(tween(600, delayMillis = 150 + (index * 50)), initialOffsetY = { 50 })
                ) {
                    CategoryItem(stringResource(titleRes), count, icon, color) { onCategoryClick(category) }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun FileCategoryList(category: FileCategory, files: List<com.geovault.model.VaultFile>, onFileClick: (com.geovault.model.VaultFile) -> Unit) {
    if (files.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextSecondary)
                Spacer(Modifier.height(16.dp))
                Text("Vault is empty in this category.", color = TextSecondary)
                Text("Add files using the + button.", fontSize = 12.sp, color = TextSecondary.copy(alpha = 0.6f))
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(files) { file ->
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.8f)
                ) {
                    FileItem(file, onClick = { onFileClick(file) })
                }
            }
        }
    }
}

@Composable
fun FileItem(file: com.geovault.model.VaultFile, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            if (file.category == FileCategory.PHOTO || file.category == FileCategory.INTRUDER) {
                FileThumbnail(file)
            } else {
                val icon = when (file.category) {
                    FileCategory.VIDEO -> Icons.Default.PlayCircle
                    FileCategory.AUDIO -> Icons.Default.MusicNote
                    FileCategory.DOCUMENT -> Icons.Default.Description
                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                }
                Icon(icon, contentDescription = null, tint = CyberBlue, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            file.originalName,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 10.sp,
            maxLines = 1,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun FileThumbnail(file: com.geovault.model.VaultFile) {
    val context = LocalContext.current
    var thumbnailPath by remember { mutableStateOf<File?>(null) }
    val cryptoManager = remember { CryptoManager() }

    LaunchedEffect(file.id) {
        val tempFile = File(context.cacheDir, "thumb_${file.id}.jpg")
        if (tempFile.exists()) {
            thumbnailPath = tempFile
        } else {
            withContext(Dispatchers.IO) {
                try {
                    val encryptedFile = File(file.encryptedPath)
                    if (encryptedFile.exists()) {
                        cryptoManager.decryptToStream(encryptedFile.inputStream(), FileOutputStream(tempFile))
                        thumbnailPath = tempFile
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    if (thumbnailPath != null) {
        AsyncImage(
            model = thumbnailPath,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = CyberBlue.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun AppLockManagement(state: VaultState, onToggleAppLock: (String) -> Unit) {
    val activeVault = state.vaults.find { it.id == state.activeVaultId }
    val hiddenApps = activeVault?.hiddenApps ?: emptySet()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Select apps to lock. These apps will be hidden until you unlock them.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.installedApps) { app ->
                val isLocked = hiddenApps.contains(app.packageName)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleAppLock(app.packageName) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    app.icon?.let {
                        Image(
                            it.toBitmap().asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        app.appName, 
                        modifier = Modifier.weight(1f), 
                        fontWeight = FontWeight.SemiBold, 
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = isLocked,
                        onCheckedChange = { onToggleAppLock(app.packageName) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberBlue)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
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
    iconContainerColor: Color,
    iconColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(140.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                title, 
                fontWeight = FontWeight.ExtraBold, 
                fontSize = 18.sp, 
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle, 
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), 
                fontSize = 11.sp, 
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun CategoryItem(title: String, count: Int, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title, 
                fontWeight = FontWeight.Bold, 
                fontSize = 17.sp, 
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(R.string.items_count, count),
                color = MaterialTheme.colorScheme.onSurfaceVariant, 
                fontSize = 13.sp
            )
        }
        Icon(
            Icons.Default.ChevronRight, 
            contentDescription = null, 
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
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
    onToggleDarkMode: () -> Unit,
    onToggleUninstallProtection: () -> Unit,
    onSetLanguage: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.system_permissions), 
            style = MaterialTheme.typography.titleSmall, 
            fontWeight = FontWeight.Bold, 
            color = MaterialTheme.colorScheme.primary
        )
        
        PermissionItem(stringResource(R.string.permission_app_detection), stringResource(R.string.permission_app_detection_desc), state.hasUsageStatsPermission, onOpenUsageSettings)
        PermissionItem(stringResource(R.string.permission_lock_window), stringResource(R.string.permission_lock_window_desc), state.hasOverlayPermission, onOpenOverlaySettings)
        PermissionItem(stringResource(R.string.permission_camera), stringResource(R.string.permission_camera_desc), state.hasCameraPermission, onGrantCamera)
        PermissionItem(stringResource(R.string.permission_storage), stringResource(R.string.permission_storage_desc), state.hasStoragePermission, onGrantStorage)

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        Text(
            stringResource(R.string.advanced_security), 
            style = MaterialTheme.typography.titleSmall, 
            fontWeight = FontWeight.Bold, 
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = if (state.isUninstallProtectionEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.uninstall_protection), 
                        style = MaterialTheme.typography.bodyLarge, 
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.uninstall_protection_desc),
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = state.isUninstallProtectionEnabled,
                    onCheckedChange = { onToggleUninstallProtection() },
                    colors = SwitchDefaults.colors(checkedThumbColor = CyberBlue)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        Text(
            stringResource(R.string.lock_options), 
            style = MaterialTheme.typography.titleSmall, 
            fontWeight = FontWeight.Bold, 
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (state.isMasterStealthEnabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = if (state.isMasterStealthEnabled) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.global_lock_title), 
                            style = MaterialTheme.typography.bodyLarge, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.global_lock_desc),
                            style = MaterialTheme.typography.bodySmall, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = state.isMasterStealthEnabled,
                        onCheckedChange = { onToggleMasterStealth() },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberBlue)
                    )
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (state.isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.dark_mode), 
                            style = MaterialTheme.typography.bodyLarge, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.dark_mode_desc), 
                            style = MaterialTheme.typography.bodySmall, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = state.isDarkMode,
                        onCheckedChange = { onToggleDarkMode() },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberBlue)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.language),
                            style = MaterialTheme.typography.bodyLarge, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (state.currentLanguage == "hi") "हिन्दी (Hindi)" else "English", 
                            style = MaterialTheme.typography.bodySmall, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    
                    Row {
                        FilterChip(
                            selected = state.currentLanguage == "en",
                            onClick = { onSetLanguage("en") },
                            label = { Text("EN") }
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = state.currentLanguage == "hi",
                            onClick = { onSetLanguage("hi") },
                            label = { Text("HI") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionItem(title: String, description: String, granted: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (granted) Color(0xFF4CAF50) else Color(0xFFFF5252)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title, 
                    style = MaterialTheme.typography.bodyLarge, 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    description, 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            if (!granted) {
                Text("FIX", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
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
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "HISTORY",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = onClearAll,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252).copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, Color(0xFFFF5252))
                ) {
                    Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFFF5252))
                    Spacer(Modifier.width(8.dp))
                    Text("CLEAR ALL HIDDEN APPS", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
                
                Spacer(Modifier.height(24.dp))
                
                Text(
                    "ACTIVE VAULTS", 
                    style = MaterialTheme.typography.labelLarge, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // NO LAZYCOLUMN HERE - It causes the intrinsic measurement crash when nested in Dialog with sub-lazy components
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    state.vaults.forEach { vault ->
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Vault ${vault.id.take(8)}", 
                                        color = MaterialTheme.colorScheme.onSurface, 
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${vault.hiddenApps.size} apps locked", 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, 
                                        fontSize = 11.sp
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "(${String.format("%.4f", vault.location.latitude)}, ${String.format("%.4f", vault.location.longitude)})", 
                                            color = MaterialTheme.colorScheme.primary, 
                                            fontSize = 10.sp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        IconButton(
                                            onClick = { copyToClipboard(context, vault.location.latitude, vault.location.longitude) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                                IconButton(onClick = { onRemoveVault(vault.id) }) {
                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFFF5252))
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                    
                    if (state.vaultHistory.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "HISTORY", 
                            style = MaterialTheme.typography.labelLarge, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        state.vaultHistory.forEach { hist ->
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Vault Established", 
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), 
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(hist.timestamp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        IconButton(
                                            onClick = { copyToClipboard(context, hist.location.latitude, hist.location.longitude) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${hist.appsCount} apps hidden:", 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, 
                                        fontSize = 11.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        hist.hiddenAppPackages.forEach { pkg ->
                                            AppMiniIcon(pkg)
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Lat: ${hist.location.latitude}, Lon: ${hist.location.longitude}", 
                                        color = MaterialTheme.colorScheme.primary, 
                                        fontSize = 9.sp
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("CLOSE", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun AppMiniIcon(packageName: String) {
    val context = LocalContext.current
    val pm = context.packageManager
    val icon = remember(packageName) {
        try {
            pm.getApplicationIcon(packageName).toBitmap().asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
        )
    }
}

private fun copyToClipboard(context: Context, lat: Double, lon: Double) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val link = "https://www.google.com/maps/search/?api=1&query=$lat,$lon"
    val clip = ClipData.newPlainText("Vault Location", link)
    clipboard.setPrimaryClip(clip)
}
