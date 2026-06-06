package com.geovault.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.AudioManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.geovault.ui.theme.*
import com.geovault.R
import androidx.compose.ui.res.stringResource
import com.geovault.model.FileCategory
import com.geovault.model.VaultFile
import com.geovault.security.CryptoManager
import com.geovault.ui.theme.CyberBlue
import com.geovault.ui.theme.CyberDarkBlue
import com.geovault.ui.theme.CyberPurple
import com.geovault.ui.theme.CreamWhite
import com.geovault.ui.theme.AppBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    file: VaultFile,
    allFiles: List<VaultFile> = emptyList(),
    isDarkMode: Boolean = true, // Still kept for signature, but will be ignored for players
    onBack: () -> Unit,
    onDelete: (String) -> Unit,
    onRestore: (String) -> Unit,
    onStartAction: () -> Unit = {},
    onEndAction: () -> Unit = {}
) {
    // FORCE DARK THEME for players as requested
    val forcedDarkTheme = true
    
    val context = LocalContext.current
    val activity = context as? Activity

    androidx.activity.compose.BackHandler {
        // Reset orientation when going back
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onBack()
    }
    
    // Ensure portrait when entering (unless video player changes it)
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

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
                            File(f.encryptedPath).inputStream().buffered(1024 * 512), // Larger buffer for speed
                            FileOutputStream(tempFile).buffered(1024 * 512)
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
                                    // FIX: Do NOT call onEndAction() here, let onResume handle it 
                                    // to prevent the lock screen from appearing while sharing.
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
                                        File(currentFile.encryptedPath).inputStream().buffered(1024 * 512), // 512KB buffer
                                        FileOutputStream(tempFile).buffered(1024 * 512)
                                    )
                                }
                                decryptedFile = tempFile
                            } catch (e: Exception) {}
                            finally { isDecrypting = false }
                        }
                    }

                    if (decryptedFile != null) {
                        Box(Modifier.fillMaxSize()) {
                            val isDark = forcedDarkTheme
                                val displayCategory = if (currentFile.category == FileCategory.OTHER) {
                                    inferCategoryFromFileName(currentFile.originalName)
                                } else {
                                    currentFile.category
                                }

                                when (displayCategory) {
                                    FileCategory.PHOTO, FileCategory.INTRUDER -> PhotoViewer(decryptedFile!!, isDark)
                                    FileCategory.VIDEO -> VideoViewer(decryptedFile!!, isVisible, isDark)
                                    FileCategory.AUDIO -> AudioViewer(decryptedFile!!, isVisible, currentFile.thumbnailPath, isDark)
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
fun PhotoViewer(file: File, isDark: Boolean = true) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += offsetChange
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color.Black else CreamWhite)
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
fun VideoViewer(file: File, isVisible: Boolean, isDark: Boolean = true) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    
    val exoPlayer = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    
    // Gestures state
    var brightness by remember { mutableFloatStateOf(view.context.let { 
        (it as? Activity)?.window?.attributes?.screenBrightness ?: -1f 
    }.let { if (it < 0) 0.5f else it }) }
    
    var volume by remember { mutableFloatStateOf(
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / 
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    ) }
    
    var gestureType by remember { mutableStateOf<GestureType?>(null) }
    var gestureValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(exoPlayer) {
        while (true) {
            playbackPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            delay(500)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color.Black else CreamWhite)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { isControlsVisible = !isControlsVisible })
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        gestureType = if (offset.x < size.width / 2) GestureType.BRIGHTNESS else GestureType.VOLUME
                    },
                    onDragEnd = { 
                        gestureType = null 
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount / size.height
                        if (gestureType == GestureType.BRIGHTNESS) {
                            brightness = (brightness + delta).coerceIn(0f, 1f)
                            gestureValue = brightness
                            activity?.window?.let { window ->
                                val lp = window.attributes
                                lp.screenBrightness = brightness
                                window.attributes = lp
                            }
                        } else if (gestureType == GestureType.VOLUME) {
                            volume = (volume + delta).coerceIn(0f, 1f)
                            gestureValue = volume
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                (volume * maxVol).roundToInt(),
                                0
                            )
                        }
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Custom UI
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Modern Controls Overlay
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize().background((if (isDark) Color.Black else Color.White).copy(alpha = 0.3f))) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(file.name, color = if (isDark) Color.White else Color.Black, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        val current = activity?.requestedOrientation
                        activity?.requestedOrientation = if (current == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                    }) {
                        Icon(Icons.Default.ScreenRotation, null, tint = if (isDark) Color.White else CyberBlue)
                    }
                }

                // Center Play/Pause
                IconButton(
                    onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                    modifier = Modifier.align(Alignment.Center).size(80.dp).background(if (isDark) Color.White else LightPrimary, CircleShape)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null,
                        tint = if (isDark) CyberBlue else Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Bottom Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDuration(playbackPosition), color = if (isDark) Color.White else Color.Black, fontSize = 12.sp)
                        Text(formatDuration(duration), color = if (isDark) Color.White else Color.Black, fontSize = 12.sp)
                    }
                    Slider(
                        value = if (duration > 0) playbackPosition.toFloat() / duration else 0f,
                        onValueChange = { exoPlayer.seekTo((it * duration).toLong()) },
                        colors = SliderDefaults.colors(
                            thumbColor = if (isDark) Color.White else Color.Black,
                            activeTrackColor = CyberBlue,
                            inactiveTrackColor = (if (isDark) Color.White else Color.Black).copy(alpha = 0.3f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = { exoPlayer.seekTo(exoPlayer.currentPosition - 10000) }) {
                            Icon(Icons.Default.Replay10, null, tint = if (isDark) Color.White else Color.Black)
                        }
                        IconButton(onClick = { exoPlayer.seekTo(exoPlayer.currentPosition + 10000) }) {
                            Icon(Icons.Default.Forward10, null, tint = if (isDark) Color.White else Color.Black)
                        }
                    }
                }
            }
        }

        // "Cool" Gesture HUD
        AnimatedVisibility(
            visible = gestureType != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.height(40.dp).width(200.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        if (gestureType == GestureType.VOLUME) Icons.Default.VolumeUp else Icons.Default.BrightnessMedium,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    LinearProgressIndicator(
                        progress = { gestureValue },
                        modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

enum class GestureType { VOLUME, BRIGHTNESS }

@UnstableApi
@Composable
fun AudioViewer(file: File, isVisible: Boolean, thumbnailPath: String? = null, isDark: Boolean = true) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    
    val exoPlayer = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    
    var volume by remember { mutableFloatStateOf(
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / 
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    ) }
    var showVolumeHUD by remember { mutableStateOf(false) }

    LaunchedEffect(exoPlayer) {
        while (true) {
            playbackPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            delay(500)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Brush.verticalGradient(listOf(CyberDarkBlue, Color.Black)) else Brush.verticalGradient(listOf(CreamWhite, Color.White)))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { showVolumeHUD = true },
                    onDragEnd = { showVolumeHUD = false },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount / size.height
                        volume = (volume + delta).coerceIn(0f, 1f)
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVol).roundToInt(), 0)
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "disc_rotation")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(15000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotation"
            )
            val color1 by infiniteTransition.animateColor(
                initialValue = CyberBlue,
                targetValue = CyberBlue,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "color1"
            )
            val color2 by infiniteTransition.animateColor(
                initialValue = CyberBlue,
                targetValue = CyberBlue,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "color2"
            )

            // Modern Disc UI
            Surface(
                modifier = Modifier
                    .size(260.dp)
                    .graphicsLayer { rotationZ = if (isPlaying) rotation else 0f },
                shape = CircleShape,
                color = if (isDark) CyberDarkBlue else Color.White,
                shadowElevation = 20.dp,
                border = BorderStroke(4.dp, Brush.linearGradient(listOf(color1, color2)))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (thumbnailPath != null && File(thumbnailPath).exists()) {
                        AsyncImage(
                            model = File(thumbnailPath),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.MusicNote, 
                            null, 
                            tint = (if (isDark) Color.White else Color.Black).copy(alpha = 0.2f), 
                            modifier = Modifier.fillMaxSize().padding(60.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(48.dp))
            
            Text(
                file.name, 
                color = if (isDark) Color.White else Color.Black, 
                fontSize = 24.sp, 
                fontWeight = FontWeight.Black, 
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .basicMarquee()
            )

            Spacer(Modifier.height(48.dp))

            // Progress Slider
            Slider(
                value = if (duration > 0) playbackPosition.toFloat() / duration else 0f,
                onValueChange = { exoPlayer.seekTo((it * duration).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = if (isDark) Color.White else Color.Black,
                    activeTrackColor = CyberBlue,
                    inactiveTrackColor = (if (isDark) Color.White else Color.Black).copy(alpha = 0.1f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(playbackPosition), color = Color.Gray, fontSize = 12.sp)
                Text(formatDuration(duration), color = Color.Gray, fontSize = 12.sp)
            }

            Spacer(Modifier.height(32.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { exoPlayer.seekTo(exoPlayer.currentPosition - 10000) }) {
                    Icon(Icons.Default.SkipPrevious, null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(32.dp))
                }
                Surface(
                    onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                    shape = CircleShape,
                    color = if (isDark) Color.White else Color.Black,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null,
                            tint = if (isDark) CyberBlue else Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                IconButton(onClick = { exoPlayer.seekTo(exoPlayer.currentPosition + 10000) }) {
                    Icon(Icons.Default.SkipNext, null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(32.dp))
                }
            }
        }

        // Volume HUD
        AnimatedVisibility(
            visible = showVolumeHUD,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.height(40.dp).width(200.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.VolumeUp, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(12.dp))
                    LinearProgressIndicator(
                        progress = { volume },
                        modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }
        }
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

private fun inferCategoryFromFileName(fileName: String): FileCategory {
    val ext = fileName.lowercase().substringAfterLast(".", "")
    return when (ext) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp" -> FileCategory.PHOTO
        "mp4", "mkv", "mov", "avi", "3gp", "webm" -> FileCategory.VIDEO
        "mp3", "wav", "m4a", "aac", "ogg", "flac" -> FileCategory.AUDIO
        "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx" -> FileCategory.DOCUMENT
        else -> FileCategory.OTHER
    }
}
