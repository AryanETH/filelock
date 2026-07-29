package com.aitoyz.mapplock.ui

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
import com.aitoyz.mapplock.ui.theme.CyberBlack
import com.aitoyz.mapplock.ui.theme.CyberBlue
import com.aitoyz.mapplock.ui.theme.CyberNavy
import com.aitoyz.mapplock.R

@Composable
fun IntroScreen() {
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
            // App Logo - NO ANIMATION, NO BG CIRCLE
            Image(
                painter = painterResource(id = R.drawable.removed_background_19),
                contentDescription = "Logo",
                modifier = Modifier.size(150.dp)
            )
        }
    }
}
