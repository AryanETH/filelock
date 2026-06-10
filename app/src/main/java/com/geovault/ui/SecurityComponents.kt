package com.geovault.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import com.geovault.ui.theme.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import com.geovault.ui.theme.CyberBlue
import com.geovault.ui.theme.CyberDarkBlue
import com.geovault.ui.theme.CyberNeonRed
import com.geovault.ui.theme.AppBlue
import com.geovault.ui.theme.CreamWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CompactPinPad(
    correctPin: String? = null, 
    onPinComplete: (String) -> Unit, 
    onError: (() -> Unit)? = null,
    isLightTheme: Boolean = true,
    isFullPage: Boolean = false,
    autoConfirm: Boolean = true,
    isGlassMode: Boolean = false
) {
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        
        // Dynamic sizing based on screen width/height
        val dotSize = (availableWidth / 8).coerceIn(40.dp, 56.dp)
        val keySize = (availableWidth / 5).coerceIn(56.dp, 80.dp)
        val spacing = (availableHeight / 15).coerceIn(16.dp, 48.dp)
        val keyPadding = (availableWidth / 40).coerceIn(4.dp, 12.dp)
        val fontSize = (keySize.value * 0.35f).sp

        LaunchedEffect(pin) {
            if (pin.length == 4) {
                if (correctPin != null) {
                    delay(300)
                    if (pin == correctPin) {
                        HapticHelper.vibrate(context, 1) // Medium on Success
                        onPinComplete(pin)
                        pin = ""
                    } else {
                        isError = true
                        HapticHelper.vibrate(context, 2) // Strong on Error
                        delay(100)
                        onError?.invoke()
                        delay(1000)
                        isError = false
                        pin = ""
                    }
                } else if (autoConfirm) {
                    delay(300)
                    HapticHelper.vibrate(context, 1)
                    onPinComplete(pin)
                    pin = ""
                }
            }
        }

        // Apple-style Material Tinting
        val surfaceBrush = if (isGlassMode) Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.08f))
        ) else null
        
        // Adaptive Border: Brighter on top, subtle on sides
        val glassBorderBrush = if (isGlassMode) Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.1f))
        ) else null

        val surfaceColor = if (isGlassMode) Color.Transparent else (if (isLightTheme) Color.White else CyberDarkBlue)
        val textColor = if (isGlassMode) Color.White else (if (isLightTheme) LightTextPrimary else Color.White)
        val borderColor = if (isGlassMode) Color.Transparent else (if (isLightTheme) LightOutline.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).animateContentSize()
            ) {
                repeat(4) { index ->
                    val filled = index < pin.length
                    
                    val scale by animateFloatAsState(
                        targetValue = if (filled) 1.15f else 1.0f,
                        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
                        label = "PinScale"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .requiredHeight(dotSize * 1.3f),
                        contentAlignment = Alignment.Center
                    ) {
                        val backgroundModifier = if (isGlassMode) {
                            Modifier.background(surfaceBrush!!, CircleShape)
                        } else {
                            Modifier.background(if (filled) Color(0xFF0980FC) else surfaceColor, CircleShape)
                        }

                        Box(
                            modifier = Modifier
                                .requiredSize(dotSize)
                                .scale(scale)
                                .then(backgroundModifier)
                                .then(
                                    if (isGlassMode) {
                                        Modifier.border(BorderStroke(1.dp, glassBorderBrush!!), CircleShape)
                                    } else {
                                        Modifier.border(
                                            2.dp, 
                                            if (isError) CyberNeonRed else (if (!isLightTheme) Color.White else Color.Black),
                                            CircleShape
                                        )
                                    }
                                )
                                .then(if (isGlassMode) Modifier.shadow(12.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.3f)) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            if (filled) {
                                Text(
                                    text = pin[index].toString(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = (dotSize.value * 0.45f).sp,
                                    textAlign = TextAlign.Center,
                                    style = if (isGlassMode) MaterialTheme.typography.bodyLarge.copy(
                                        shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 2f), 6f)
                                    ) else MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(spacing))
            
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "DEL", "0", "OK")
            keys.chunked(3).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = keyPadding / 2)
                ) {
                    row.forEach { key ->
                        Surface(
                            modifier = Modifier
                                .padding(horizontal = keyPadding)
                                .size(keySize)
                                .clickable(enabled = !isError) {
                                    HapticHelper.vibrate(context, 0)
                                    when (key) {
                                        "DEL" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                        "OK" -> if (pin.length == 4) onPinComplete(pin)
                                        else -> if (pin.length < 4) pin += key
                                    }
                                },
                            shape = CircleShape,
                            color = if (isGlassMode) Color.Transparent else surfaceColor,
                            shadowElevation = 0.dp
                        ) {
                            val keyBackground = if (isGlassMode) {
                                Modifier.background(surfaceBrush!!)
                            } else {
                                Modifier.background(surfaceColor)
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(keyBackground)
                                    .then(
                                        if (isGlassMode) {
                                            Modifier.border(BorderStroke(1.dp, glassBorderBrush!!), CircleShape)
                                        } else {
                                            Modifier.border(2.dp, if (isError) Color.Red else borderColor, CircleShape)
                                        }
                                    )
                                    .then(if (isGlassMode) Modifier.shadow(8.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.2f)) else Modifier)
                            ) {
                                if (key == "DEL") {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Backspace, 
                                        contentDescription = null, 
                                        tint = Color.Red,
                                        modifier = Modifier.size(keySize * 0.35f)
                                    )
                                } else if (key == "OK") {
                                    Text(
                                        key, 
                                        color = CyberBlue,
                                        fontSize = (fontSize.value * 0.75f).sp, 
                                        fontWeight = FontWeight.Black,
                                        style = if (isGlassMode) MaterialTheme.typography.bodyLarge.copy(
                                            shadow = Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 1f), 4f)
                                        ) else MaterialTheme.typography.bodyLarge
                                    )
                                } else {
                                    Text(
                                        key, 
                                        color = if (isError) Color.Red else textColor, 
                                        fontSize = fontSize, 
                                        fontWeight = FontWeight.ExtraBold,
                                        style = if (isGlassMode) MaterialTheme.typography.bodyLarge.copy(
                                            shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 2f), 4f)
                                        ) else MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactPatternGrid(
    correctPattern: String? = null, 
    onPatternComplete: (String) -> Unit, 
    onError: (() -> Unit)? = null,
    isLightTheme: Boolean = false,
    isFullPage: Boolean = false,
    showConfirmButton: Boolean = false,
    isGlassMode: Boolean = false
) {
    var secret by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var currentTouchPosition by remember { mutableStateOf<Offset?>(null) }
    val segmentProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    LaunchedEffect(secret) {
        if (secret.length >= 2) {
            segmentProgress.snapTo(0f)
            segmentProgress.animateTo(1f, tween(150))
        }
    }
    
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        
        val gridSize = (availableWidth * 0.95f).coerceIn(280.dp, 450.dp)
        val dotRadius = (gridSize.value / 22).dp
        val lineWidth = (gridSize.value / 45).dp

        val inactiveDotColor = if (isGlassMode) Color.White.copy(alpha = 0.35f) else (if (isLightTheme) LightOutline.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f))
        val activeDotColor = CyberBlue

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(gridSize)
                    .then(
                        if (isGlassMode) Modifier
                            .background(
                                Brush.radialGradient(
                                    listOf(Color.White.copy(alpha = 0.1f), Color.Transparent),
                                    center = Offset.Unspecified,
                                    radius = gridSize.value
                                )
                            )
                        else Modifier
                    )
                    .pointerInput(isError) {
                        if (isError) return@pointerInput
                        detectDragGestures(
                            onDragStart = { 
                                secret = "" 
                                currentTouchPosition = null
                            },
                            onDrag = { change, _ ->
                                currentTouchPosition = change.position
                                val dotIndex = getDotIndexAt(change.position, size.width.toFloat())
                                if (dotIndex != -1 && !secret.contains(dotIndex.toString())) {
                                    HapticHelper.vibrate(context, 0) // Light for dots
                                    secret += dotIndex.toString()
                                }
                            },
                            onDragEnd = {
                                currentTouchPosition = null
                                if (secret.length >= 3) {
                                    if (correctPattern != null) {
                                        if (secret == correctPattern) {
                                            HapticHelper.vibrate(context, 1) // Medium for success
                                            onPatternComplete(secret)
                                            secret = ""
                                        } else {
                                            isError = true
                                            HapticHelper.vibrate(context, 2) // Strong for error
                                            onError?.invoke()
                                            scope.launch {
                                                delay(1000)
                                                isError = false
                                                secret = ""
                                            }
                                        }
                                    } else if (!showConfirmButton) {
                                        HapticHelper.vibrate(context, 1)
                                        onPatternComplete(secret)
                                        secret = ""
                                    }
                                }
                            },
                            onDragCancel = {
                                currentTouchPosition = null
                                secret = ""
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val spacing = size.width / 3
                    val startOffset = spacing / 2

                    for (i in 0..2) {
                        for (j in 0..2) {
                            val index = i * 3 + j
                            val isActive = secret.contains(index.toString())
                            
                            // Apple-style Dot: Inner glow/shadow for active
                            drawCircle(
                                color = when {
                                    isError && isActive -> CyberNeonRed
                                    isActive -> activeDotColor
                                    else -> inactiveDotColor
                                },
                                radius = dotRadius.toPx() * (if (isActive) 1.2f else 1.0f),
                                center = Offset(startOffset + j * spacing, startOffset + i * spacing)
                            )
                        }
                    }

                    if (secret.length >= 2) {
                        for (i in 0 until secret.length - 1) {
                            val p1 = getCenterForIndex(secret[i].toString().toInt(), spacing, startOffset)
                            val p2 = getCenterForIndex(secret[i+1].toString().toInt(), spacing, startOffset)
                            
                            val progress = if (i == secret.length - 2) segmentProgress.value else 1f
                            val animatedEndPoint = p1 + (p2 - p1) * progress

                            drawLine(
                                if (isError) CyberNeonRed else activeDotColor, 
                                p1, animatedEndPoint, 
                                strokeWidth = lineWidth.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Dynamic Dragging Line (from last connected point to finger)
                    currentTouchPosition?.let { touch ->
                        if (secret.isNotEmpty()) {
                            val lastIndex = secret.last().toString().toInt()
                            
                            // Draw from current animated tip to the finger for perfect fluidity
                            val segmentStart = if (secret.length >= 2) {
                                val pPrev = getCenterForIndex(secret[secret.length - 2].toString().toInt(), spacing, startOffset)
                                val pCurr = getCenterForIndex(secret.last().toString().toInt(), spacing, startOffset)
                                pPrev + (pCurr - pPrev) * segmentProgress.value
                            } else {
                                getCenterForIndex(lastIndex, spacing, startOffset)
                            }

                            drawLine(
                                if (isError) CyberNeonRed.copy(alpha = 0.5f) else activeDotColor.copy(alpha = 0.6f),
                                segmentStart, touch,
                                strokeWidth = lineWidth.toPx() * 0.8f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            if (showConfirmButton && secret.length >= 3) {
                Button(
                    onClick = {
                        HapticHelper.vibrate(context, 1)
                        onPatternComplete(secret)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CONFIRM PATTERN", fontWeight = FontWeight.Black)
                }
            } else {
                Text(
                    text = if (isError) "TRY AGAIN" else if (availableWidth > 500.dp) "DRAW PATTERN TO UNLOCK" else "CONNECT DOTS TO VERIFY", 
                    color = if (isError) CyberNeonRed else (if (isGlassMode || !isLightTheme) Color.White.copy(alpha = 0.8f) else LightTextSecondary),
                    fontSize = if (availableWidth > 500.dp) 14.sp else 10.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    style = if (isGlassMode) MaterialTheme.typography.bodyLarge.copy(
                        shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 2f), 8f)
                    ) else MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

fun getDotIndexAt(offset: Offset, size: Float): Int {
    val spacing = size / 3
    val startOffset = spacing / 2
    val threshold = spacing / 3

    for (i in 0..2) {
        for (j in 0..2) {
            val center = Offset(startOffset + j * spacing, startOffset + i * spacing)
            val distance = (offset - center).getDistance()
            if (distance < threshold) {
                return i * 3 + j
            }
        }
    }
    return -1
}

fun getCenterForIndex(index: Int, spacing: Float, startOffset: Float): Offset {
    val row = index / 3
    val col = index % 3
    return Offset(startOffset + col * spacing, startOffset + row * spacing)
}
