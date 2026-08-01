package com.aitoyz.mapplock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import com.aitoyz.mapplock.ui.theme.CyberBlue

/**
 * Apple-style Glassmorphic Material Layer
 * Implements: Blur + Adaptive Tint + Material Layering + Border
 */
@Composable
fun GlassMaterialCard(
    isDark: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    // Apple's "Material" approach:
    // 1. Base blur (applied via parent or here if possible)
    // 2. Adaptive Tint (Light/Dark)
    // 3. Subtle contrast border
    // 4. Subtle inner glow
    
    val surfaceBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.04f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.65f)
            )
        }
    )

    val borderBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.25f),
                Color.Transparent,
                Color.White.copy(alpha = 0.1f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.5f),
                Color.Black.copy(alpha = 0.05f)
            )
        }
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(surfaceBrush)
            .then(
                if (isDark) Modifier.background(Color.Black.copy(alpha = 0.2f))
                else Modifier
            )
    ) {
        // Subtle Noise/Grain Effect using Canvas
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRoundRect(
                brush = borderBrush,
                cornerRadius = CornerRadius(32.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

/**
 * Contrast-aware Text for Glass backgrounds
 */
@Composable
fun GlassText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    isDark: Boolean = true,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Text(
        text = text,
        style = style.copy(
            shadow = if (isDark) androidx.compose.ui.graphics.Shadow(
                color = Color.Black.copy(alpha = 0.5f),
                offset = Offset(0f, 2f),
                blurRadius = 8f
            ) else null
        ),
        color = if (isDark) Color.White else Color.Black.copy(alpha = 0.8f),
        modifier = modifier
    )
}
