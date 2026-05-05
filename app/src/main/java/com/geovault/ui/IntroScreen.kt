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
            .background(CyberBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Logo (Using a placeholder icon if not exists, but prompt says "App name: Mapp Lock")
            // I'll use a simple Box with a shield/lock icon or just text for now if resource is missing
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .background(CyberBlue.copy(alpha = 0.1f * opacity), shape = androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Assuming there's an ic_launcher or similar. If not, I'll just use a styled text logo
                Text(
                    "M",
                    style = MaterialTheme.typography.displayLarge,
                    color = CyberBlue,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.scale(scale)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Mapp Lock",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp,
                modifier = Modifier.scale(scale)
            )
            
            Text(
                "SECURE MAP VAULT",
                style = MaterialTheme.typography.labelMedium,
                color = CyberBlue.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
