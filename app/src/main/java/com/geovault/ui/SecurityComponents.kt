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
fun CompactPinPad(correctPin: String? = null, onPinComplete: (String) -> Unit, onError: (() -> Unit)? = null) {
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    
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
                    targetValue = if (filled) 1f else 0.8f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                    label = "PinScale"
                )

                Box(
                    modifier = Modifier
                        .padding(horizontal = 5.dp)
                        .size(44.dp)
                        .scale(scale)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isError -> CyberNeonRed.copy(alpha = 0.2f)
                                filled -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                            }
                        )
                        .border(
                            1.dp, 
                            if (isError) CyberNeonRed else if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), 
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (filled) {
                        Text(
                            pin[index].toString(), 
                            color = if (isError) CyberNeonRed else MaterialTheme.colorScheme.onPrimary, 
                            fontWeight = FontWeight.Black, 
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK")
        keys.chunked(3).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                row.forEach { key ->
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(60.dp)
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
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isError) CyberNeonRed.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(key, color = if (isError) CyberNeonRed else MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactPatternGrid(correctPattern: String? = null, onPatternComplete: (String) -> Unit, onError: (() -> Unit)? = null) {
    var secret by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val inactiveDotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val activeDotColor = MaterialTheme.colorScheme.primary
    
    Box(
        modifier = Modifier
            .size(240.dp)
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
            val dotRadius = 8.dp.toPx()

            for (i in 0..2) {
                for (j in 0..2) {
                    val index = i * 3 + j
                    val isActive = secret.contains(index.toString())
                    drawCircle(
                        color = when {
                            isError && isActive -> CyberNeonRed
                            isActive -> activeDotColor
                            else -> inactiveDotColor
                        },
                        radius = dotRadius,
                        center = Offset(startOffset + j * spacing, startOffset + i * spacing)
                    )
                }
            }

            if (secret.length >= 2) {
                for (i in 0 until secret.length - 1) {
                    val p1 = getCenterForIndex(secret[i].toString().toInt(), spacing, startOffset)
                    val p2 = getCenterForIndex(secret[i+1].toString().toInt(), spacing, startOffset)
                    drawLine(
                        if (isError) CyberNeonRed else activeDotColor, 
                        p1, p2, 
                        strokeWidth = 4.dp.toPx(), 
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
    
    Spacer(Modifier.height(8.dp))
    Text(
        if (isError) "INVALID PATTERN" else "CONNECT DOTS TO VERIFY", 
        color = if (isError) CyberNeonRed else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp, 
        letterSpacing = 1.sp
    )
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
