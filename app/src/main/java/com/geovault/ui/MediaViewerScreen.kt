package com.geovault.ui

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.pager.*
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.geovault.ui.theme.CyberBlue
import androidx.compose.material.icons.filled.MusicNote
import androidx.core.content.FileProvider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.geovault.model.FileCategory
import com.geovault.model.VaultFile
import com.geovault.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    file: VaultFile,
    allFiles: List<VaultFile> = emptyList(),
    onBack: () -> Unit,
    onDelete: (String) -> Unit,
    onRestore: (String) -> Unit
) {
    val context = LocalContext.current
    val cryptoManager = remember { CryptoManager() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    // Filter files of same category for paging
    val pagerFiles = remember(file, allFiles) {
        if (allFiles.isEmpty()) listOf(file)
        else allFiles.filter { it.category == file.category }
    }
    
    val initialPage = remember(file, pagerFiles) {
        val index = pagerFiles.indexOfFirst { it.id == file.id }
        if (index != -1) index else 0
    }

    val pagerState = rememberPagerState(initialPage = initialPage) { pagerFiles.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    val currentFile = pagerFiles.getOrNull(pagerState.currentPage) ?: file
                    Text(currentFile.originalName, style = MaterialTheme.typography.titleMedium) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val currentFile = pagerFiles.getOrNull(pagerState.currentPage)
                    
                    // SAVE TO GALLERY BUTTON
                    IconButton(onClick = { 
                        currentFile?.let { cf -> onRestore(cf.id) }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Save, 
                            contentDescription = "Save to Gallery", 
                            tint = CyberBlue
                        )
                    }

                    IconButton(onClick = { 
                        currentFile?.let { cf ->
                            // Decrypt current file to a temporary location for sharing
                            val tempFile = File(context.cacheDir, "share_${cf.id}_${cf.originalName}")
                            if (!tempFile.exists()) {
                                try {
                                    val encryptedFile = File(cf.encryptedPath)
                                    cryptoManager.decryptToStream(encryptedFile.inputStream(), FileOutputStream(tempFile))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            if (tempFile.exists()) {
                                shareFile(context, tempFile)
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open In...", tint = Color.White)
                    }
                    IconButton(onClick = { showRestoreDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Restore", tint = Color.White)
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
                beyondViewportPageCount = 1
            ) { pageIndex ->
                val currentFile = pagerFiles[pageIndex]
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    var decryptedFile by remember(currentFile.id) { 
                        mutableStateOf<File?>(
                            File(context.cacheDir, "temp_${currentFile.id}_${currentFile.originalName}").let { 
                                if (it.exists()) it else null 
                            }
                        ) 
                    }
                    
                    // 1. Show Instant Thumbnail Background
                    if (currentFile.category == FileCategory.PHOTO || currentFile.category == FileCategory.INTRUDER) {
                        if (currentFile.thumbnailPath != null) {
                            AsyncImage(
                                model = File(currentFile.thumbnailPath),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    if (decryptedFile == null) {
                        LaunchedEffect(currentFile) {
                            withContext(Dispatchers.IO) {
                                try {
                                    val encryptedFile = File(currentFile.encryptedPath)
                                    if (encryptedFile.exists()) {
                                        val tempFile = File(context.cacheDir, "temp_${currentFile.id}_${currentFile.originalName}")
                                        if (!tempFile.exists()) {
                                            cryptoManager.decryptToStream(encryptedFile.inputStream(), FileOutputStream(tempFile))
                                        }
                                        decryptedFile = tempFile
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }

                    if (decryptedFile != null) {
                        when (currentFile.category) {
                            FileCategory.PHOTO, FileCategory.INTRUDER -> PhotoViewer(decryptedFile!!)
                            FileCategory.VIDEO -> VideoViewer(decryptedFile!!)
                            FileCategory.AUDIO -> AudioViewer(decryptedFile!!)
                            FileCategory.DOCUMENT -> {
                                if (currentFile.originalName.lowercase().endsWith(".pdf")) {
                                    PdfViewer(decryptedFile!!)
                                } else {
                                    ExternalViewer(currentFile.originalName)
                                }
                            }
                            else -> ExternalViewer(currentFile.originalName)
                        }
                    } else if (currentFile.category != FileCategory.PHOTO && currentFile.category != FileCategory.INTRUDER) {
                        CircularProgressIndicator(color = Color.Cyan)
                    }
                }
            }

            if (showRestoreDialog) {
                MediaActionDialog(
                    title = "RESTORE FILE?",
                    message = "This will move the file back to your phone's gallery and make it visible to other apps.",
                    confirmText = "RESTORE",
                    onDismiss = { showRestoreDialog = false },
                    onConfirm = {
                        pagerFiles.getOrNull(pagerState.currentPage)?.let { onRestore(it.id) }
                        showRestoreDialog = false
                    }
                )
            }

            if (showDeleteDialog) {
                MediaActionDialog(
                    title = "DELETE FILE?",
                    message = "This action cannot be undone. The file will be permanently removed from the vault.",
                    confirmText = "DELETE",
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
fun MediaActionDialog(
    title: String,
    message: String,
    confirmText: String,
    confirmColor: Color = CyberBlue,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1A1A1A),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                Text(message, color = Color.Gray, fontSize = 14.sp)
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = Color.Gray)
                    }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = confirmColor)
                    ) {
                        Text(confirmText, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ExternalViewer(fileName: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = Color.Gray, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(24.dp))
        Text("Internal preview unavailable for:", color = Color.White, fontWeight = FontWeight.Bold)
        Text(fileName, color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Text("Use the 'Open In' button at the top to view with another app.", color = CyberBlue, textAlign = TextAlign.Center, fontSize = 12.sp)
    }
}

private fun shareFile(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with..."))
    } catch (e: Exception) {
        e.printStackTrace()
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
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > 1f) 1f else 3f
                    offset = Offset.Zero
                })
            }
            .transformable(
                state = state,
                lockRotationOnZoomPan = true,
                enabled = scale > 1f
            )
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
fun VideoViewer(file: File) {
    val context = LocalContext.current
    val exoPlayer = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

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

@UnstableApi
@Composable
fun AudioViewer(file: File) {
    val context = LocalContext.current
    val exoPlayer = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = Color.Cyan,
            modifier = Modifier.size(120.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(file.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(32.dp))
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerShowTimeoutMs = 0
                    controllerHideOnTouch = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )
    }
}

@Composable
fun PdfViewer(file: File) {
    val renderer = remember(file) {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(fd)
    }

    DisposableEffect(renderer) {
        onDispose {
            renderer.close()
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        val count = renderer.pageCount
        items(count) { index ->
            PdfPage(renderer, index)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun PdfPage(renderer: PdfRenderer, index: Int) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(index) {
        withContext(Dispatchers.IO) {
            val page = renderer.openPage(index)
            val b = createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap = b
            page.close()
        }
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            contentScale = ContentScale.FillWidth
        )
    } ?: Box(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color.Gray)
    }
}
