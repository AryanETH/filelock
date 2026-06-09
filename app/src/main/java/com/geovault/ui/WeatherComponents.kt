package com.geovault.ui

import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.geovault.ui.theme.*

data class WeatherData(
    val aqi: Int,
    val temperature: Float,
    val humidity: Int,
    val locationName: String,
    val status: String,
)

/**
 * Enhanced Weather Bottom Sheet - Always Light Theme
 * Incorporates premium floating card design and detailed stats.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherDetailSheet(
    data: WeatherData,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Always Light Theme Colors
        containerColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null, // Using custom handle inside for better control
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 8.dp
    ) {
        // Main Content Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            // Custom Drag Handle & Close Button Row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .size(40.dp, 4.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.1f))
                        .align(Alignment.Center)
                )
                
                // Close Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.05f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Close, 
                        contentDescription = "Close",
                        modifier = Modifier.size(18.dp),
                        tint = LightTextPrimary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Header Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = CyberNeonGreen.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(CyberNeonGreen, CircleShape))
                        Text(
                            "LIVE SENSOR DATA",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00854D), // Darker green for contrast on light
                            letterSpacing = 1.sp
                        )
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Text(
                    text = data.locationName,
                    style = MaterialTheme.typography.titleLarge,
                    color = LightTextSecondary,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = data.status,
                    style = MaterialTheme.typography.displaySmall,
                    color = LightTextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp
                )
            }

            Spacer(Modifier.height(32.dp))

            // Stats Grid - Modern Floating Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WeatherStatCard(
                    label = "AQI",
                    value = data.aqi.toString(),
                    lottieUrl = "https://lottie.host/8024227b-2321-423c-9a3d-4c3d82a39a7b/AirIcon.json",
                    color = getAqiColor(data.aqi),
                    modifier = Modifier.weight(1f)
                )
                WeatherStatCard(
                    label = "Temp",
                    value = "${data.temperature.toInt()}°C",
                    lottieUrl = "https://lottie.host/7e0e7a2b-8a2b-4e8a-8a2b-4e8a8a2b4e8a/TempIcon.json",
                    color = CyberBlue,
                    modifier = Modifier.weight(1f)
                )
                WeatherStatCard(
                    label = "Humidity",
                    value = "${data.humidity}%",
                    lottieUrl = "https://lottie.host/9f0e7a2b-8a2b-4e8a-8a2b-4e8a8a2b4e8a/HumidityIcon.json",
                    color = CyberPurple,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(32.dp))

            // Detailed AQI Analysis Section
            Surface(
                color = SoftGray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Air Quality Index", 
                            fontSize = 14.sp, 
                            fontWeight = FontWeight.Bold,
                            color = LightTextPrimary
                        )
                        
                        Text(
                            text = getAqiStatus(data.aqi).uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = getAqiColor(data.aqi)
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Gradient AQI Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(0.05f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (data.aqi / 300f).coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(CyberNeonGreen, Color(0xFFFFEB3B), CyberNeonRed)
                                    )
                                )
                        )
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Text(
                        text = "Current air quality is ${getAqiStatus(data.aqi).lowercase()}. Recommended for outdoor activities.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = LightTextSecondary
                    )
                }
            }
        }
    }
}

fun getAqiColor(aqi: Int): Color = when {
    aqi <= 50 -> CyberNeonGreen
    aqi <= 100 -> Color(0xFFFFEB3B) // Yellow
    aqi <= 150 -> Color(0xFFFF9800) // Orange
    else -> CyberNeonRed
}

fun getAqiStatus(aqi: Int): String = when {
    aqi <= 50 -> "Excellent"
    aqi <= 100 -> "Moderate"
    aqi <= 150 -> "Unhealthy"
    else -> "Hazardous"
}

@Composable
fun WeatherStatCard(
    label: String,
    value: String,
    lottieUrl: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Url(lottieUrl))
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever
    )

    Surface(
        modifier = modifier.height(110.dp),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (composition != null) {
                    LottieAnimation(
                        composition = composition,
                        progress = { progress },
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    // Motion Fallback: Simple Animated Icon
                    val infiniteTransition = rememberInfiniteTransition(label = "icon_anim")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scale"
                    )
                    
                    Icon(
                        imageVector = when(label) {
                            "AQI" -> Icons.Default.Air
                            "Temp" -> Icons.Default.DeviceThermostat
                            "Humidity" -> Icons.Default.WaterDrop
                            else -> Icons.Default.Air
                        },
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp).graphicsLayer(scaleX = scale, scaleY = scale)
                    )
                }
            }
            
            Column {
                Text(
                    text = value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = LightTextPrimary
                )
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = LightTextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
