package com.geovault.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.geovault.R
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cryptoManager = remember { CryptoManager() }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    val pagerFiles = remember(file, allFiles) {
        if (allFiles.isEmpty()) listOf(file)
        else allFiles.filter { it.category == file.category }
    }

    val initialPage = remember(file, pagerFiles) {
        val index = pagerFiles.indexOfFirst { it.id == file.id }
        if (index != -1) index else 0
    }

    val pagerState = rememberPagerState(initialPage = initialPage) { pagerFiles.size }
    val openWithStr = stringResource(R.string.open_with)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val currentFile = pagerFiles.getOrNull(pagerState.currentPage) ?: file
                    Text(currentFile.originalName, style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    val currentFile = pagerFiles.getOrNull(pagerState.currentPage)

                    IconButton(onClick = {
                        currentFile?.let { cf ->
                            scope.launch {
                                val tempFile = File(context.cacheDir, "share_${cf.id}_${cf.originalName}")
                                withContext(Dispatchers.IO) {
                                    try {
                                        cryptoManager.decryptToStream(File(cf.encryptedPath).inputStream(), FileOutputStream(tempFile))
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                                if (tempFile.exists()) {
                                    onStartAction()
                                    shareFile(context, tempFile, openWithStr)
                                    scope.launch {
                                        delay(2000)
                                        onEndAction()
                                    }
                                }
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.open_in), tint = Color.White)
                    }
                    IconButton(onClick = { showRestoreDialog = true }) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.restore), tint = Color.White)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = Color.White)
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
                    var decryptedFile by remember(currentFile.id) { mutableStateOf<File?>(null) }

                    LaunchedEffect(currentFile.id) {
                        withContext(Dispatchers.IO) {
                            try {
                                val tempFile = File(context.cacheDir, "preview_${currentFile.id}_${currentFile.originalName}")
                                if (!tempFile.exists()) {
                                    cryptoManager.decryptToStream(File(currentFile.encryptedPath).inputStream(), FileOutputStream(tempFile))
                                }
                                decryptedFile = tempFile
                            } catch (e: Exception) { e.printStackTrace() }
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
                    } else {
                        CircularProgressIndicator(color = CyberBlue)
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
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onDoubleTap = {
                scale = if (scale > 1f) 1f else 3f
                offset = Offset.Zero
            })
        }.transformable(state = state)
    ) {
        AsyncImage(
            model = Uri.fromFile(file),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
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
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }
    AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } }, modifier = Modifier.fillMaxSize())
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
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.MusicNote, null, tint = CyberBlue, modifier = Modifier.size(120.dp))
        Spacer(modifier = Modifier.height(32.dp))
        Text(file.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(32.dp))
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = true } },
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
    DisposableEffect(renderer) { onDispose { renderer.close() } }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(renderer.pageCount) { index -> PdfPage(renderer, index) }
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
        Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
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
        Text(stringResource(R.string.no_preview, fileName), color = Color.White, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.open_in_hint), color = CyberBlue, textAlign = TextAlign.Center, fontSize = 12.sp)
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
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF1A1A1A), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                Text(message, color = Color.Gray, fontSize = 14.sp)
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Color.Gray) }
                    Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = confirmColor)) {
                        Text(confirmText, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun shareFile(context: android.content.Context, file: File, openWithLabel: String) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, openWithLabel))
    } catch (e: Exception) { e.printStackTrace() }
}