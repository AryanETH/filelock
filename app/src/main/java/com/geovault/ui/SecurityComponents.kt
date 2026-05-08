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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geovault.ui.theme.CyberBlue
import com.geovault.ui.theme.CyberDarkBlue
import com.geovault.ui.theme.CyberNeonRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CompactPinPad(
    correctPin: String? = null, 
    onPinComplete: (String) -> Unit, 
    onError: (() -> Unit)? = null,
    isLightTheme: Boolean = false,
    isFullPage: Boolean = false
) {
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    
    val primaryColor = if (isLightTheme) Color.Black else MaterialTheme.colorScheme.primary
    val onPrimaryColor = if (isLightTheme) Color.White else MaterialTheme.colorScheme.onPrimary
    val surfaceColor = if (isLightTheme) Color.Black.copy(alpha = 0.03f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val textColor = if (isLightTheme) Color.Black else MaterialTheme.colorScheme.onSurface

    val dotSize = if (isFullPage) 52.dp else 44.dp
    val keySize = if (isFullPage) 72.dp else 60.dp
    val spacing = if (isFullPage) 56.dp else 24.dp
    val keyPadding = if (isFullPage) 12.dp else 6.dp

    LaunchedEffect(pin) {
        if (pin.length == 4) {
            delay(300)
            if (correctPin != null) {
                if (pin == correctPin) {
                    onPinComplete(pin)
                    pin = ""
                } else {
                    isError = true
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().animateContentSize()
        ) {
            repeat(4) { index ->
                val filled = index < pin.length
                
                val scale by animateFloatAsState(
                    targetValue = if (filled) 1.1f else 0.9f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
                    label = "PinScale"
                )

                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(dotSize)
                        .scale(scale)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            when {
                                isError -> Color.Red.copy(alpha = 0.1f)
                                filled -> primaryColor
                                else -> surfaceColor
                            }
                        )
                        .border(
                            1.dp, 
                            if (isError) Color.Red else if (filled) primaryColor else textColor.copy(alpha = 0.1f), 
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (filled) {
                        Text(
                            pin[index].toString(), 
                            color = if (isError) Color.Red else onPrimaryColor, 
                            fontWeight = FontWeight.Bold, 
                            fontSize = if (isFullPage) 22.sp else 18.sp
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(spacing))
        
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK")
        keys.chunked(3).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = if (isFullPage) 10.dp else 6.dp)
            ) {
                row.forEach { key ->
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = keyPadding)
                            .size(keySize)
                            .clickable(enabled = !isError) {
                            when (key) {
                                "C" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                "OK" -> if (pin.length == 4) {
                                    if (correctPin != null && pin != correctPin) {
                                        isError = true
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isError) Color.Red.copy(alpha = 0.3f) else textColor.copy(alpha = 0.05f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(key, color = if (isError) Color.Red else textColor, fontSize = if (isFullPage) 24.sp else 20.sp, fontWeight = FontWeight.Medium)
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
    
    val inactiveDotColor = if (isLightTheme) Color.Black.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val activeDotColor = if (isLightTheme) Color.Black else MaterialTheme.colorScheme.primary
    
    val gridSize = if (isFullPage) 320.dp else 240.dp
    val dotRadius = if (isFullPage) 10.dp else 8.dp
    val lineWidth = if (isFullPage) 6.dp else 4.dp

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
                                secret += dotIndex.toString()
                            }
                        },
                        onDragEnd = {
                            if (secret.length >= 3) {
                                if (correctPattern != null) {
                                    if (secret == correctPattern) {
                                        onPatternComplete(secret)
                                        secret = ""
                                    } else {
                                        isError = true
                                        onError?.invoke()
                                        scope.launch {
                                            delay(1000)
                                            isError = false
                                            secret = ""
                                        }
                                    }
                                } else {
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
            if (isError) "TRY AGAIN" else if (isFullPage) "DRAW PATTERN TO UNLOCK" else "CONNECT DOTS TO VERIFY", 
            color = if (isError) Color.Red else if (isLightTheme) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = if (isFullPage) 12.sp else 10.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Medium
        )
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
