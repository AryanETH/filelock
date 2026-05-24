package com.geovault.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
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
    isFullPage: Boolean = false
) {
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
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
                delay(300)
                if (correctPin != null) {
                    if (pin == correctPin) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPinComplete(pin)
                        pin = ""
                    } else {
                        isError = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        delay(100)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onError?.invoke()
                        delay(1000)
                        isError = false
                        pin = ""
                    }
                } else {
                    onPinComplete(pin)
                    pin = ""
                }
            }
        }

        val primaryColor = if (isLightTheme) AppBlue else CyberBlue
        val onPrimaryColor = Color.White
        val surfaceColor = if (isLightTheme) CreamWhite else CyberDarkBlue
        val textColor = if (isLightTheme) Color.Black else Color.White
        val borderColor = if (isLightTheme) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().animateContentSize()
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
                            .padding(horizontal = 10.dp)
                            .size(dotSize * 1.3f), // Larger container to prevent clipping
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(dotSize)
                                .scale(scale)
                                .background(
                                    if (filled) AppBlue else surfaceColor,
                                    CircleShape
                                )
                                .border(
                                    2.dp, 
                                    if (isError) Color.Red else if (isLightTheme) Color.Black else Color.White,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (filled) {
                                Text(
                                    text = pin[index].toString(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = (dotSize.value * 0.45f).sp,
                                    textAlign = TextAlign.Center
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
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                when (key) {
                                    "DEL" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                    "OK" -> if (pin.length == 4) {
                                        if (correctPin != null && pin != correctPin) {
                                            isError = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            scope.launch {
                                                delay(100)
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            onError?.invoke()
                                        } else {
                                            onPinComplete(pin)
                                        }
                                    }
                                    else -> if (pin.length < 4) pin += key
                                }
                            },
                            shape = CircleShape,
                            color = surfaceColor,
                            shadowElevation = 0.dp
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(surfaceColor)
                                    .border(2.dp, if (isError) Color.Red else borderColor, CircleShape)
                            ) {
                                if (key == "DEL") {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Backspace, 
                                        contentDescription = null, 
                                        tint = textColor, 
                                        modifier = Modifier.size(keySize * 0.35f)
                                    )
                                } else if (key == "OK") {
                                    Text(
                                        key, 
                                        color = textColor, 
                                        fontSize = (fontSize.value * 0.75f).sp, 
                                        fontWeight = FontWeight.Black
                                    )
                                } else {
                                    Text(
                                        key, 
                                        color = if (isError) Color.Red else textColor, 
                                        fontSize = fontSize, 
                                        fontWeight = FontWeight.ExtraBold
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
    isFullPage: Boolean = false
) {
    var secret by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        
        val gridSize = (availableWidth * 0.8f).coerceIn(240.dp, 400.dp)
        val dotRadius = (gridSize.value / 35).dp
        val lineWidth = (gridSize.value / 60).dp

        val inactiveDotColor = if (isLightTheme) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f)
        val activeDotColor = if (isLightTheme) AppBlue else CyberBlue

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(gridSize)
                    .pointerInput(isError) {
                        if (isError) return@pointerInput
                        detectDragGestures(
                            onDragStart = { secret = "" },
                            onDrag = { change, _ ->
                                val dotIndex = getDotIndexAt(change.position, size.width.toFloat())
                                if (dotIndex != -1 && !secret.contains(dotIndex.toString())) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    secret += dotIndex.toString()
                                }
                            },
                            onDragEnd = {
                                if (secret.length >= 3) {
                                    if (correctPattern != null) {
                                        if (secret == correctPattern) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onPatternComplete(secret)
                                            secret = ""
                                        } else {
                                            isError = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            scope.launch {
                                                delay(100)
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            onError?.invoke()
                                            scope.launch {
                                                delay(1000)
                                                isError = false
                                                secret = ""
                                            }
                                        }
                                    } else {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onPatternComplete(secret)
                                        secret = ""
                                    }
                                }
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
                            drawCircle(
                                color = when {
                                    isError && isActive -> Color.Red
                                    isActive -> activeDotColor
                                    else -> inactiveDotColor
                                },
                                radius = dotRadius.toPx(),
                                center = Offset(startOffset + j * spacing, startOffset + i * spacing)
                            )
                        }
                    }

                    if (secret.length >= 2) {
                        for (i in 0 until secret.length - 1) {
                            val p1 = getCenterForIndex(secret[i].toString().toInt(), spacing, startOffset)
                            val p2 = getCenterForIndex(secret[i+1].toString().toInt(), spacing, startOffset)
                            drawLine(
                                if (isError) Color.Red else activeDotColor, 
                                p1, p2, 
                                strokeWidth = lineWidth.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            Text(
                text = if (isError) "TRY AGAIN" else if (availableWidth > 500.dp) "DRAW PATTERN TO UNLOCK" else "CONNECT DOTS TO VERIFY", 
                color = if (isError) Color.Red else (if (isLightTheme) Color.Gray else Color.White.copy(alpha = 0.6f)),
                fontSize = if (availableWidth > 500.dp) 14.sp else 10.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
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
