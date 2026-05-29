package com.geovault.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.geovault.R
import androidx.compose.ui.res.stringResource
import com.geovault.model.FileCategory
import com.geovault.model.VaultFile
import com.geovault.security.CryptoManager
import com.geovault.ui.theme.CyberBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    file: VaultFile,
    allFiles: List<VaultFile> = emptyList(),
    onBack: () -> Unit,
    onDelete: (String) -> Unit,
    onRestore: (String) -> Unit,
    onStartAction: () -> Unit = {},
    onEndAction: () -> Unit = {}
) {
    androidx.activity.compose.BackHandler {
        onBack()
    }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cryptoManager = remember { CryptoManager() }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    val pagerFiles = remember(file, allFiles) {
        if (allFiles.isEmpty()) listOf(file)
        else {
            val baseList = if (file.folderName != null) {
                allFiles.filter { it.folderName == file.folderName }
            } else {
                allFiles.filter { it.category == file.category && it.folderName == null }
            }
            // Optimization: Filter out categories that don't support preview if needed,
            // but for custom folders, we allow all.
            baseList
        }
    }

    val initialPage = remember(file, pagerFiles) {
        val index = pagerFiles.indexOfFirst { it.id == file.id }
        if (index != -1) index else 0
    }

    val pagerState = rememberPagerState(initialPage = initialPage) { pagerFiles.size }

    // Ultra Fast: Pre-decrypt neighboring files with optimized buffers
    LaunchedEffect(pagerState.currentPage) {
        val cacheDir = File(context.cacheDir, "decrypted_vault")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val nextIdx = pagerState.currentPage + 1
        val prevIdx = pagerState.currentPage - 1
        
        listOfNotNull(pagerFiles.getOrNull(nextIdx), pagerFiles.getOrNull(prevIdx)).forEach { f ->
            launch(Dispatchers.IO) {
                val tempFile = File(cacheDir, "preview_${f.id}_${f.originalName}")
                if (!tempFile.exists()) {
                    try {
                        cryptoManager.decryptToStream(
                            File(f.encryptedPath).inputStream().buffered(1024 * 256),
                            FileOutputStream(tempFile).buffered(1024 * 256)
                        )
                    } catch (e: Exception) {}
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val currentFile = pagerFiles.getOrNull(pagerState.currentPage) ?: file
                    Text(currentFile.originalName, style = MaterialTheme.typography.titleMedium, maxLines = 1, fontWeight = FontWeight.Black)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val currentFile = pagerFiles.getOrNull(pagerState.currentPage)
                    IconButton(onClick = {
                        currentFile?.let { cf ->
                            scope.launch {
                                val cacheDir = File(context.cacheDir, "decrypted_vault")
                                if (!cacheDir.exists()) cacheDir.mkdirs()
                                
                                val tempFile = File(cacheDir, "share_${cf.id}_${cf.originalName}")
                                if (!tempFile.exists()) {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            cryptoManager.decryptToStream(
                                                File(cf.encryptedPath).inputStream().buffered(1024 * 256),
                                                FileOutputStream(tempFile).buffered(1024 * 256)
                                            )
                                        } catch (e: Exception) {}
                                    }
                                }
                                
                                if (tempFile.exists()) {
                                    onStartAction()
                                    shareFile(context, tempFile)
                                    // Removed delay to fix slow redirect complaint
                                    onEndAction()
                                }
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open In...", tint = Color.White)
                    }
                    IconButton(onClick = { showRestoreDialog = true }) {
                        Icon(Icons.Default.Save, contentDescription = "Restore", tint = Color.White)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.padding(padding).fillMaxSize(),
                pageSpacing = 16.dp,
                beyondViewportPageCount = 1,
                key = { pageIndex -> if (pageIndex < pagerFiles.size) pagerFiles[pageIndex].id else pageIndex }
            ) { pageIndex ->
                val currentFile = pagerFiles[pageIndex]
                val isVisible = pagerState.currentPage == pageIndex

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    var decryptedFile by remember(currentFile.id) { mutableStateOf<File?>(null) }
                    var isDecrypting by remember(currentFile.id) { mutableStateOf(true) }

                    LaunchedEffect(currentFile.id) {
                        isDecrypting = true
                        withContext(Dispatchers.IO) {
                            try {
                                val cacheDir = File(context.cacheDir, "decrypted_vault")
                                if (!cacheDir.exists()) cacheDir.mkdirs()
                                
                                val tempFile = File(cacheDir, "preview_${currentFile.id}_${currentFile.originalName}")
                                
                                // INSTANT OPEN STRATEGY:
                                // If it's a small file (Images, Docs < 5MB), it should be instant.
                                // We use a high-speed buffer and check existence first.
                                if (!tempFile.exists()) {
                                    cryptoManager.decryptToStream(
                                        File(currentFile.encryptedPath).inputStream().buffered(1024 * 256), // 256KB buffer
                                        FileOutputStream(tempFile).buffered(1024 * 256)
                                    )
                                }
                                decryptedFile = tempFile
                            } catch (e: Exception) {}
                            finally { isDecrypting = false }
                        }
                    }

                    if (decryptedFile != null) {
                        Box(Modifier.fillMaxSize()) {
                            when (currentFile.category) {
                                FileCategory.PHOTO, FileCategory.INTRUDER -> PhotoViewer(decryptedFile!!)
                                FileCategory.VIDEO -> VideoViewer(decryptedFile!!, isVisible)
                                FileCategory.AUDIO -> AudioViewer(decryptedFile!!, isVisible)
                                FileCategory.DOCUMENT -> {
                                    if (currentFile.originalName.lowercase().endsWith(".pdf")) {
                                        PdfViewer(decryptedFile!!)
                                    } else {
                                        ExternalViewer(currentFile.originalName)
                                    }
                                }
                                else -> ExternalViewer(currentFile.originalName)
                            }
                        }
                    } else if (isDecrypting) {
                        Box(contentAlignment = Alignment.Center) {
                            if (currentFile.thumbnailPath != null && File(currentFile.thumbnailPath).exists()) {
                                AsyncImage(
                                    model = File(currentFile.thumbnailPath),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().blur(12.dp),
                                    contentScale = ContentScale.Fit,
                                    alpha = 0.5f
                                )
                            }
                            CircularProgressIndicator(color = CyberBlue, strokeWidth = 2.dp)
                        }
                    }
                }
            }

            if (showRestoreDialog) {
                MediaActionDialog(
                    title = stringResource(R.string.restore_file_title),
                    message = stringResource(R.string.restore_file_message),
                    confirmText = stringResource(R.string.restore_confirm),
                    onDismiss = { showRestoreDialog = false },
                    onConfirm = {
                        pagerFiles.getOrNull(pagerState.currentPage)?.let { onRestore(it.id) }
                        showRestoreDialog = false
                    }
                )
            }

            if (showDeleteDialog) {
                MediaActionDialog(
                    title = stringResource(R.string.delete_file_title),
                    message = stringResource(R.string.delete_file_message),
                    confirmText = stringResource(R.string.delete_confirm),
                    confirmColor = Color.Red.copy(alpha = 0.8f),
                    onDismiss = { showDeleteDialog = false },
                    onConfirm = {
                        pagerFiles.getOrNull(pagerState.currentPage)?.let { onDelete(it.id) }
                        showDeleteDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun PhotoViewer(file: File) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += offsetChange
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(scale) {
                if (scale > 1f) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offset += dragAmount
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 3f
                    }
                })
            }
            .transformable(state = state)
    ) {
        AsyncImage(
            model = Uri.fromFile(file),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit
        )
    }
}

@UnstableApi
@Composable
fun VideoViewer(file: File, isVisible: Boolean) {
    val context = LocalContext.current
    val exoPlayer = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }

    LaunchedEffect(isVisible) {
        if (!isVisible) {
            exoPlayer.pause()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@UnstableApi
@Composable
fun AudioViewer(file: File, isVisible: Boolean) {
    val context = LocalContext.current
    val exoPlayer = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }

    LaunchedEffect(isVisible) {
        if (!isVisible) {
            exoPlayer.pause()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.MusicNote, 
            null, 
            tint = CyberBlue, 
            modifier = Modifier.size(120.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            file.name, 
            color = Color.White, 
            fontSize = 20.sp, 
            fontWeight = FontWeight.Black, 
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerHideOnTouch = false
                    controllerShowTimeoutMs = 0
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        )
    }
}

@Composable
fun PdfViewer(file: File) {
    val renderer = remember(file) {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(fd)
    }
    DisposableEffect(renderer) { onDispose { renderer.close() } }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(renderer.pageCount) { index -> PdfPage(renderer, index) }
    }
}

@Composable
fun PdfPage(renderer: PdfRenderer, index: Int) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(index) {
        withContext(Dispatchers.IO) {
            try {
                renderer.openPage(index).use { page ->
                    val b = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(b)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap = b
                }
            } catch (e: Exception) {}
        }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth
        )
    } ?: Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = CyberBlue)
    }
}

@Composable
fun ExternalViewer(fileName: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp)
    ) {
        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = Color.Gray, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.no_preview, fileName), color = Color.White, fontWeight = FontWeight.Black)
        Text(stringResource(R.string.open_in_hint), color = CyberBlue, textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MediaActionDialog(
    title: String,
    message: String,
    confirmText: String,
    confirmColor: Color = CyberBlue,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Black) },
        text = { Text(message, fontWeight = FontWeight.Bold) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = confirmColor)) {
                Text(confirmText, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
            }
        }
    )
}

private fun shareFile(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "No app found", android.widget.Toast.LENGTH_SHORT).show()
    }
}
