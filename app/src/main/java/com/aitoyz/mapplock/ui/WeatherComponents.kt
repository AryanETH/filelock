package com.aitoyz.mapplock.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aitoyz.mapplock.ui.theme.*
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

data class WeatherData(
    val aqi: Int,
    val temperature: Float,
    val humidity: Int,
    val locationName: String,
    val address: String? = null,
    val status: String,
    val no2: Double? = null,
    val pm25: Double? = null,
    val pm10: Double? = null,
    val o3: Double? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherDetailSheet(
    data: WeatherData,
    isLoading: Boolean = false,
    isNetworkAvailable: Boolean = true,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(24.dp)
                .padding(bottom = 24.dp)
        ) {
            if (isLoading) {
                WeatherSkeleton(onDismiss)
            } else if (!isNetworkAvailable) {
                WeatherErrorState(onDismiss)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    AqiGauge(
                        aqi = data.aqi,
                        modifier = Modifier.size(100.dp)
                    )

                    Spacer(Modifier.width(20.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = data.locationName.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = data.status,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF131C27)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = data.address ?: "Unknown Location",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFFE8EFFF), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close, 
                            contentDescription = "Close",
                            modifier = Modifier.size(18.dp),
                            tint = CyberBlue
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PollutantItem(
                        label = "Temp", 
                        value = data.temperature.toInt(), 
                        unit = "°C",
                        icon = Icons.Default.DeviceThermostat,
                        iconColor = IconOrange
                    )
                    PollutantItem(
                        label = "AQI", 
                        value = data.aqi, 
                        unit = "",
                        icon = Icons.Default.Air,
                        iconColor = CyberBlue
                    )
                    PollutantItem(
                        label = "Humidity", 
                        value = data.humidity, 
                        unit = "%",
                        icon = Icons.Default.WaterDrop,
                        iconColor = CyberPurple
                    )
                }
            }
        }
    }
}

@Composable
fun AqiGauge(
    aqi: Int,
    modifier: Modifier = Modifier
) {
    var animationTriggered by remember { mutableStateOf(false) }
    val sweepProgress = remember { Animatable(0f) }
    
    LaunchedEffect(aqi) {
        if (!animationTriggered) {
            sweepProgress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
            sweepProgress.animateTo(aqi / 300f, tween(400, easing = LinearOutSlowInEasing))
            animationTriggered = true
        } else {
            sweepProgress.animateTo(aqi / 300f, tween(500))
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val innerRadius = (size.minDimension - strokeWidth) / 2
            
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to CyberNeonGreen,
                    0.2f to Color(0xFFFFEB3B),
                    0.4f to Color(0xFFFF9800),
                    0.6f to CyberNeonRed,
                    0.8f to CyberPurple,
                    1.0f to CyberNeonGreen
                ),
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(innerRadius * 2, innerRadius * 2),
                topLeft = Offset((size.width - innerRadius * 2) / 2, (size.height - innerRadius * 2) / 2)
            )

            val angle = 150f + sweepProgress.value.coerceIn(0f, 1f) * 240f
            val rad = Math.toRadians(angle.toDouble())
            val x = (size.width / 2) + innerRadius * cos(rad).toFloat()
            val y = (size.height / 2) + innerRadius * sin(rad).toFloat()
            
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = Offset(x, y)
            )
            drawCircle(
                color = getAqiColor(aqi),
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedCountingText(
                targetValue = aqi,
                fontSize = 28.sp,
                color = Color(0xFF131C27)
            )
            Text(
                text = "AQI",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun PollutantItem(
    label: String,
    value: Int,
    unit: String,
    icon: ImageVector,
    iconColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconColor.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedCountingText(
                    targetValue = value,
                    fontSize = 15.sp,
                    color = Color(0xFF131C27)
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF131C27),
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
            }
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AnimatedCountingText(
    targetValue: Int,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color
) {
    var displayValue by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(targetValue) {
        val startValue = displayValue
        val duration = 800L
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < duration) {
            val progress = (System.currentTimeMillis() - startTime).toFloat() / duration
            displayValue = (startValue + (targetValue - startValue) * progress).toInt()
            if (progress < 0.9f) {
                displayValue += (-2..2).random()
            }
            delay(30)
        }
        displayValue = targetValue
    }

    Text(
        text = displayValue.toString(),
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        color = color
    )
}

@Composable
fun WeatherSkeleton(onDismiss: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.size(100.dp).background(Color.LightGray.copy(alpha = alpha), CircleShape))
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.size(80.dp, 12.dp).background(Color.LightGray.copy(alpha = alpha), RoundedCornerShape(4.dp)))
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.size(120.dp, 24.dp).background(Color.LightGray.copy(alpha = alpha), RoundedCornerShape(4.dp)))
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.size(150.dp, 14.dp).background(Color.LightGray.copy(alpha = alpha), RoundedCornerShape(4.dp)))
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp).background(Color(0xFFE8EFFF), CircleShape)) {
                Icon(Icons.Default.Close, null, tint = CyberBlue)
            }
        }
        Spacer(Modifier.height(40.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(3) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(32.dp).background(Color.LightGray.copy(alpha = alpha), CircleShape))
                    Column {
                        Box(modifier = Modifier.size(40.dp, 15.dp).background(Color.LightGray.copy(alpha = alpha), RoundedCornerShape(4.dp)))
                        Spacer(Modifier.height(4.dp))
                        Box(modifier = Modifier.size(30.dp, 10.dp).background(Color.LightGray.copy(alpha = alpha), RoundedCornerShape(4.dp)))
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherErrorState(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp).background(Color(0xFFE8EFFF), CircleShape)) {
                Icon(Icons.Default.Close, null, tint = CyberBlue)
            }
        }
        Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
        Spacer(Modifier.height(16.dp))
        Text("No Internet Connection", fontWeight = FontWeight.Bold, color = Color.Gray)
        Text("Check your network to update weather data", fontSize = 12.sp, color = Color.LightGray)
        Spacer(Modifier.height(24.dp))
    }
}

fun getAqiColor(aqi: Int): Color = when {
    aqi <= 50 -> CyberNeonGreen
    aqi <= 100 -> Color(0xFFFFEB3B)
    aqi <= 150 -> Color(0xFFFF9800)
    else -> CyberNeonRed
}

fun getAqiStatus(aqi: Int): String = when {
    aqi <= 50 -> "Excellent"
    aqi <= 100 -> "Moderate"
    aqi <= 150 -> "Unhealthy"
    else -> "Hazardous"
}
