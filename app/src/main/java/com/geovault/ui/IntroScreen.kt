package com.geovault.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geovault.ui.theme.CyberBlack
import com.geovault.ui.theme.CyberBlue
import com.geovault.ui.theme.CyberNavy
import com.geovault.R

@Composable
fun IntroScreen() {
    var startAnimation by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1.2f else 0.8f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "LogoScale"
    )
    
    val opacity by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "LogoOpacity"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Logo
            Box(
                modifier = Modifier
                    .size(280.dp) // Increased container size to prevent cutting
                    .scale(scale)
                    .background(CyberNavy.copy(alpha = 0.1f * opacity), shape = androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.removed_background_18),
                    contentDescription = "Logo",
                    modifier = Modifier.size(150.dp) // Kept logo size as requested
                )
            }
        }
    }
}
