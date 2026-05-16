package com.geovault.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import androidx.core.graphics.createBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
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
        else {
            if (file.folderName != null) {
                // If in a folder, show all files in that folder
                allFiles.filter { it.folderName == file.folderName }
            } else {
                // If in a category, show all files in that category that are NOT in any folder
                allFiles.filter { it.category == file.category && it.folderName == null }
            }
        }
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
                                    shareFile(context, tempFile)
                                    scope.launch {
                                        delay(2000)
                                        onEndAction()
                                    }
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
                beyondViewportPageCount = 1
            ) { pageIndex ->
                val currentFile = pagerFiles[pageIndex]
                val isVisible = pagerState.currentPage == pageIndex

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
                    } else {
                        CircularProgressIndicator(color = CyberBlue)
                    }
                }
            }

            if (showRestoreDialog) {
                MediaActionDialog(
                    title = "RESTORE FILE?",
                    message = "This will move the file back to your phone's gallery.",
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
                    message = "This action cannot be undone.",
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

    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var volume by remember { mutableFloatStateOf(exoPlayer.volume) }
    var isLocked by remember { mutableStateOf(false) }
    
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var showVolumeOverlay by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                duration = player.duration.coerceAtLeast(0L)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            delay(500)
        }
    }

    LaunchedEffect(showControls) {
        if (showControls && !isLocked) {
            delay(3000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                detectTapGestures(onTap = { if (!isLocked) showControls = !showControls })
            }
            .pointerInput(isLocked) {
                if (isLocked) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { },
                    onDragEnd = { 
                        showBrightnessOverlay = false
                        showVolumeOverlay = false
                    },
                    onDragCancel = {
                        showBrightnessOverlay = false
                        showVolumeOverlay = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        val isLeft = change.position.x < size.width / 2
                        if (isLeft) {
                            brightness = (brightness - dragAmount / size.height).coerceIn(0f, 1f)
                            showBrightnessOverlay = true
                            // In a real app, you'd use WindowManager.LayoutParams to set screen brightness
                        } else {
                            volume = (volume - dragAmount / size.height).coerceIn(0f, 1f)
                            exoPlayer.volume = volume
                            showVolumeOverlay = true
                        }
                    }
                )
            }
    ) {
        val window = (context as? android.app.Activity)?.window
        LaunchedEffect(brightness, showBrightnessOverlay) {
            if (showBrightnessOverlay && window != null) {
                val params = window.attributes
                params.screenBrightness = brightness.coerceIn(0.01f, 1f)
                window.attributes = params
            }
        }

        AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = false } }, modifier = Modifier.fillMaxSize())

        // Quick Settings Overlays
        if (showBrightnessOverlay) {
            GestureOverlay(icon = Icons.Default.BrightnessMedium, value = brightness, label = "Brightness")
        }
        if (showVolumeOverlay) {
            GestureOverlay(icon = Icons.Default.VolumeUp, value = volume, label = "Volume")
        }

        // Custom Controls
        AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
            VideoPlayerControls(
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                isLocked = isLocked,
                onPlayPause = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                onSeek = { exoPlayer.seekTo(it) },
                onToggleLock = { isLocked = !isLocked },
                onForward = { exoPlayer.seekTo(currentPosition + 10000) },
                onRewind = { exoPlayer.seekTo(currentPosition - 10000) }
            )
        }
    }
}

@Composable
fun VideoPlayerControls(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    isLocked: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLock: () -> Unit,
    onForward: () -> Unit,
    onRewind: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))) {
        // Lock Button
        IconButton(
            onClick = onToggleLock,
            modifier = Modifier.align(Alignment.CenterStart).padding(16.dp)
        ) {
            Icon(if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen, null, tint = Color.White)
        }

        if (!isLocked) {
            // Center Controls
            Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRewind) { Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(48.dp)) }
                Spacer(Modifier.width(32.dp))
                IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled, null, tint = Color.White, modifier = Modifier.size(80.dp)) }
                Spacer(Modifier.width(32.dp))
                IconButton(onClick = onForward) { Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(48.dp)) }
            }

            // Bottom Bar
            Column(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(thumbColor = CyberBlue, activeTrackColor = CyberBlue)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(currentPosition), color = Color.White, fontSize = 12.sp)
                    Text(formatDuration(duration), color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun AudioViewer(file: File, isVisible: Boolean) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    
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

    var playbackState by remember { mutableIntStateOf(exoPlayer.playbackState) }
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    
    var volume by remember { mutableFloatStateOf(exoPlayer.volume) }
    var showVolumeOverlay by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                playbackState = player.playbackState
                isPlaying = player.isPlaying
                duration = player.duration.coerceAtLeast(0L)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            delay(500)
        }
    }

    // Audio Focus Handling with backward compatibility
    DisposableEffect(Unit) {
        val listener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> exoPlayer.pause()
            }
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(listener)
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        
        onDispose {
            // Unregister listener if needed
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2C3E50), Color(0xFF000000))))
            .padding(24.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { },
                    onDragEnd = { showVolumeOverlay = false },
                    onDragCancel = { showVolumeOverlay = false },
                    onVerticalDrag = { _, dragAmount ->
                        volume = (volume - dragAmount / size.height).coerceIn(0f, 1f)
                        exoPlayer.volume = volume
                        showVolumeOverlay = true
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showVolumeOverlay) {
            GestureOverlay(icon = Icons.Default.VolumeUp, value = volume, label = "Volume")
        }

        Text("PLAYING NOW", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        
        Spacer(Modifier.height(48.dp))
        
        // Artwork with glassmorphism/neomorphism effect
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(260.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {}
            
            val infiniteTransition = rememberInfiniteTransition()
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing))
            )

            Surface(
                modifier = Modifier.size(220.dp).graphicsLayer { rotationZ = if (isPlaying) rotation else 0f },
                shape = androidx.compose.foundation.shape.CircleShape,
                shadowElevation = 20.dp
            ) {
                Image(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(40.dp).background(Color(0xFF1A1A1A)),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(CyberBlue)
                )
            }
        }
        
        Spacer(Modifier.height(48.dp))
        
        Text(file.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1)
        Text("Secure Vault Audio", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
        
        Spacer(Modifier.height(48.dp))
        
        // Seekbar
        Column {
            Slider(
                value = currentPosition.toFloat(),
                onValueChange = { exoPlayer.seekTo(it.toLong()) },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = CyberBlue,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(currentPosition), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Text(formatDuration(duration), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
        
        Spacer(Modifier.height(48.dp))
        
        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { exoPlayer.seekTo(currentPosition - 10000) }) {
                Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            
            FloatingActionButton(
                onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                containerColor = Color.White,
                contentColor = Color.Black,
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(40.dp))
            }
            
            IconButton(onClick = { exoPlayer.seekTo(currentPosition + 10000) }) {
                Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}

@Composable
fun PdfViewer(file: File) {
    val renderer = remember(file) {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(fd)
    }
    DisposableEffect(renderer) { onDispose { renderer.close() } }
    
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
                    scale = if (scale > 1f) 1f else 2.5f
                    offset = Offset.Zero
                })
            }
            .transformable(state = state)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(renderer.pageCount) { index -> 
                PdfPage(renderer, index) 
            }
        }
    }
}

@Composable
fun PdfPage(renderer: PdfRenderer, index: Int) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(index) {
        withContext(Dispatchers.IO) {
            try {
                renderer.openPage(index).use { page ->
                    // High quality rendering
                    val b = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(b)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap = b
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    bitmap?.let {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        }
    } ?: Box(modifier = Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
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
        Text("No preview for $fileName", color = Color.White, fontWeight = FontWeight.Bold)
        Text("Use 'Open In' to view with another app.", color = CyberBlue, textAlign = TextAlign.Center, fontSize = 12.sp)
    }
}

@Composable
fun GestureOverlay(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Float, label: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier.size(120.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { value },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(androidx.compose.foundation.shape.CircleShape),
                    color = CyberBlue,
                    trackColor = Color.White.copy(alpha = 0.2f)
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
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Warning,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        val infiniteTransition = rememberInfiniteTransition()
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse)
        )

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1A1A1A),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    icon,
                    null,
                    tint = confirmColor,
                    modifier = Modifier.size(64.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                )
                
                Spacer(Modifier.height(24.dp))
                
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    message,
                    color = Color.Gray,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                
                Spacer(Modifier.height(32.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Text("CANCEL", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = confirmColor),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Text(confirmText, color = Color.White, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

private fun shareFile(context: android.content.Context, file: File) {
    try {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        
        val extension = file.name.substringAfterLast(".", "").lowercase()
        val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val chooser = Intent.createChooser(intent, "Open ${file.name} with:")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(context, "No app found to open this file type", android.widget.Toast.LENGTH_SHORT).show()
    }
}
