package com.geovault.ui

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
    onOpenAccessibilitySettings: () -> Unit,
    onOpenProtectedApps: () -> Unit,
    onToggleMasterStealth: () -> Unit,
    onAddFile: (android.net.Uri, FileCategory) -> Unit,
    onToggleAppLock: (String) -> Unit,
    onRemoveVault: (String) -> Unit,
    onClearAllVaults: () -> Unit,
    onGrantCamera: () -> Unit,
    onGrantStorage: () -> Unit
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
            onBack = { viewingFile = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (currentScreen is ContentScreen.CategoryView) (currentScreen as ContentScreen.CategoryView).category.name else "HIDE IT", 
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
            if (currentScreen is ContentScreen.Dashboard || currentScreen is ContentScreen.CategoryView) {
                FloatingActionButton(
                    onClick = { 
                        selectedCategoryForAdd = if (currentScreen is ContentScreen.CategoryView) (currentScreen as ContentScreen.CategoryView).category else FileCategory.OTHER
                        val mime = when(selectedCategoryForAdd) {
                            FileCategory.PHOTO -> "image/*"
                            FileCategory.VIDEO -> "video/*"
                            FileCategory.AUDIO -> "audio/*"
                            FileCategory.DOCUMENT -> "application/pdf"
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
            when (val screen = currentScreen) {
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
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenProtectedApps = onOpenProtectedApps,
                    onToggleMasterStealth = onToggleMasterStealth,
                    onGrantCamera = onGrantCamera,
                    onGrantStorage = onGrantStorage
                )
                is ContentScreen.CategoryView -> FileCategoryList(
                    category = screen.category,
                    files = state.files.filter { it.category == screen.category },
                    onFileClick = { viewingFile = it }
                )
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
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(
                    title = "App Stealth",
                    subtitle = "Hide others from use",
                    icon = Icons.Default.Security,
                    modifier = Modifier.weight(1f),
                    iconContainerColor = Color(0xFF00C853).copy(alpha = 0.2f),
                    iconColor = Color(0xFF00C853),
                    onClick = onAppLockClick
                )
                DashboardCard(
                    title = "Cloud",
                    subtitle = "Secure backup",
                    icon = Icons.Default.CloudQueue,
                    modifier = Modifier.weight(1f),
                    iconContainerColor = CyberBlue.copy(alpha = 0.2f),
                    iconColor = CyberBlue,
                    onClick = onBackupClick
                )
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text("CATEGORIES", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
        }

        item { CategoryItem("Photos", state.photoCount, Icons.Default.Image, Color(0xFF03A9F4)) { onCategoryClick(FileCategory.PHOTO) } }
        item { CategoryItem("Videos", state.videoCount, Icons.Default.PlayCircle, Color(0xFFFFC107)) { onCategoryClick(FileCategory.VIDEO) } }
        item { CategoryItem("Audio", state.audioCount, Icons.Default.MusicNote, Color(0xFFFF5252)) { onCategoryClick(FileCategory.AUDIO) } }
        item { CategoryItem("Documents", state.documentCount, Icons.Default.Description, Color(0xFF00E676)) { onCategoryClick(FileCategory.DOCUMENT) } }
        item { CategoryItem("Intruders", state.intruderCount, Icons.Default.PersonSearch, Color(0xFFFF9800)) { onCategoryClick(FileCategory.INTRUDER) } }
        item { CategoryItem("Recycle bin", state.recycleBinCount, Icons.Default.Delete, Color(0xFF90A4AE)) {} }
        
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
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(files) { file ->
                FileItem(file, onClick = { onFileClick(file) })
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
            .background(CyberDarkBlue)
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f)),
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
            color = Color.White,
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

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                val encryptedFile = File(file.encryptedPath)
                if (encryptedFile.exists()) {
                    val tempFile = File(context.cacheDir, "thumb_${file.id}.jpg")
                    if (!tempFile.exists()) {
                        cryptoManager.decryptToStream(encryptedFile.inputStream(), FileOutputStream(tempFile))
                    }
                    thumbnailPath = tempFile
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
        Icon(
            if (file.category == FileCategory.INTRUDER) Icons.Default.Face else Icons.Default.Image,
            contentDescription = null, 
            tint = CyberBlue.copy(alpha = 0.4f), 
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun AppLockManagement(state: VaultState, onToggleAppLock: (String) -> Unit) {
    val activeVault = state.vaults.find { it.id == state.activeVaultId }
    val hiddenApps = activeVault?.hiddenApps ?: emptySet()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Select apps to apply stealth mode. These apps will be blocked until bypass is authorized.",
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
                    Text(app.appName, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Switch(
                        checked = isLocked,
                        onCheckedChange = { onToggleAppLock(app.packageName) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberBlue)
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
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
        colors = CardDefaults.cardColors(containerColor = CyberDarkBlue),
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
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
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
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
            Text("$count items", color = TextSecondary, fontSize = 13.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary.copy(alpha = 0.5f))
    }
}

@Composable
fun SettingsSection(
    state: VaultState,
    onOpenUsageSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenProtectedApps: () -> Unit,
    onToggleMasterStealth: () -> Unit,
    onGrantCamera: () -> Unit,
    onGrantStorage: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("SYSTEM PERMISSIONS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CyberBlue)
        
        PermissionItem("Usage Access", "Required to detect app launches for Stealth Mode.", state.hasUsageStatsPermission, onOpenUsageSettings)
        PermissionItem("Overlay Access", "Required to block access with a lock screen.", state.hasOverlayPermission, onOpenOverlaySettings)
        PermissionItem("Accessibility", "The most reliable way to monitor security.", state.hasAccessibilityPermission, onOpenAccessibilitySettings)
        PermissionItem("Camera", "Required for Intruder Selfie feature.", state.hasCameraPermission, onGrantCamera)
        PermissionItem("Storage", "Required to encrypt and store your files.", state.hasStoragePermission, onGrantStorage)

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        Text("STEALTH OPTIONS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CyberBlue)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberDarkBlue)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.isMasterStealthEnabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = if (state.isMasterStealthEnabled) Color(0xFFFF5252) else CyberBlue
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Master Stealth Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text("Hide all protected apps from use.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Switch(
                    checked = state.isMasterStealthEnabled,
                    onCheckedChange = { onToggleMasterStealth() },
                    colors = SwitchDefaults.colors(checkedThumbColor = CyberBlue)
                )
            }
        }
    }
}

@Composable
fun PermissionItem(title: String, description: String, granted: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CyberDarkBlue.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (granted) Color(0xFF4CAF50) else Color(0xFFFF5252)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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
            color = CyberDarkBlue,
            border = BorderStroke(1.dp, CyberBlue.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "CLOUD SECURE BACKUP",
                    style = MaterialTheme.typography.titleLarge,
                    color = CyberBlue,
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
                
                Text("ACTIVE VAULTS", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                
                // NO LAZYCOLUMN HERE - It causes the intrinsic measurement crash when nested in Dialog with sub-lazy components
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    state.vaults.forEach { vault ->
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Vault ${vault.id.take(8)}", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("${vault.hiddenApps.size} apps locked", color = Color.Gray, fontSize = 11.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("(${String.format("%.4f", vault.location.latitude)}, ${String.format("%.4f", vault.location.longitude)})", color = CyberBlue, fontSize = 10.sp)
                                        Spacer(Modifier.width(8.dp))
                                        IconButton(
                                            onClick = { copyToClipboard(context, vault.location.latitude, vault.location.longitude) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, null, tint = CyberBlue, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                                IconButton(onClick = { onRemoveVault(vault.id) }) {
                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFFF5252))
                                }
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    }
                    
                    if (state.vaultHistory.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("HISTORY", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                        
                        state.vaultHistory.forEach { hist ->
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Vault Established", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(hist.timestamp),
                                            color = Color.Gray,
                                            fontSize = 10.sp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        IconButton(
                                            onClick = { copyToClipboard(context, hist.location.latitude, hist.location.longitude) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Map, null, tint = CyberBlue, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text("${hist.appsCount} apps hidden:", color = Color.Gray, fontSize = 11.sp)
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
                                    Text("Lat: ${hist.location.latitude}, Lon: ${hist.location.longitude}", color = CyberBlue, fontSize = 9.sp)
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("CLOSE", color = CyberBlue)
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
