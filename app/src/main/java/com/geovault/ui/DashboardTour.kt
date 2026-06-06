package com.geovault.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.geovault.ui.theme.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geovault.R
import com.geovault.ui.theme.AppBlue
import com.geovault.ui.theme.CyberBlue

// ─────────────────────────────────────────────
// Data model for a single tour step
// ─────────────────────────────────────────────
data class DashboardTourStep(
    val titleResId: Int,
    val descResId: Int,
    val targetRect: Rect? = null  // null = centre-screen card, no spotlight
)

// ─────────────────────────────────────────────
// Rect-animation helpers (reused from AppTour)
// ─────────────────────────────────────────────
@Composable
fun animateDashboardRectAsState(
    targetValue: Rect,
    animationSpec: AnimationSpec<Rect> = tween(500, easing = FastOutSlowInEasing),
    label: String = "DashboardRectAnim"
): State<Rect> = animateValueAsState(
    targetValue = targetValue,
    typeConverter = TwoWayConverter(
        convertToVector = { AnimationVector4D(it.left, it.top, it.right, it.bottom) },
        convertFromVector = { Rect(it.v1, it.v2, it.v3, it.v4) }
    ),
    animationSpec = animationSpec,
    label = label
)

// ─────────────────────────────────────────────
// Main tour overlay
// ─────────────────────────────────────────────
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DashboardTourOverlay(
    steps: List<DashboardTourStep>,
    onCompleted: () -> Unit
) {
    var currentIdx by remember { mutableIntStateOf(0) }
    val currentStep = steps[currentIdx]

    val pulse by rememberInfiniteTransition(label = "Pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "PulseFloat"
    )

    val animRect by animateDashboardRectAsState(
        targetValue = currentStep.targetRect ?: Rect.Zero
    )

    // Consume all touches so map/dashboard underneath is blocked
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        // ── Spotlight canvas ──────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            with(drawContext.canvas.nativeCanvas) {
                val checkpoint = saveLayer(null, null)

                // Dark scrim
                drawRect(Color.Black.copy(alpha = 0.78f))

                if (animRect != Rect.Zero) {
                    val pad = 18.dp.toPx()
                    val hole = Rect(
                        left   = animRect.left   - pad,
                        top    = animRect.top    - pad,
                        right  = animRect.right  + pad,
                        bottom = animRect.bottom + pad
                    )
                    val cx = hole.center.x
                    val cy = hole.center.y
                    val w  = hole.width  * pulse
                    val h  = hole.height * pulse

                    drawRoundRect(
                        color     = Color.Transparent,
                        topLeft   = Offset(cx - w / 2, cy - h / 2),
                        size      = androidx.compose.ui.geometry.Size(w, h),
                        cornerRadius = CornerRadius(20.dp.toPx()),
                        blendMode = BlendMode.Clear
                    )
                }

                restoreToCount(checkpoint)
            }
        }

        // ── Tooltip card ──────────────────────────────────────────
        AnimatedContent(
            targetState = currentIdx,
            transitionSpec = {
                (fadeIn(tween(350)) + slideInVertically { it / 8 })
                    .togetherWith(fadeOut(tween(250)))
            },
            modifier = Modifier.fillMaxSize(),
            label = "TourTooltip"
        ) { idx ->
            val step = steps[idx]
            val config = LocalConfiguration.current
            val inBottomHalf = step.targetRect?.let {
                it.center.y > config.screenHeightDp * 3
            } ?: false

            val cardAlignment = when {
                step.targetRect == null -> Alignment.Center
                inBottomHalf            -> Alignment.TopCenter
                else                    -> Alignment.BottomCenter
            }
            val cardPaddingV = if (step.targetRect == null) 0.dp else 120.dp

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .align(cardAlignment)
                        .padding(horizontal = 24.dp, vertical = cardPaddingV)
                        .widthIn(max = 420.dp)
                        .background(
                            color = Color.White, // Always light for the tour cards
                            shape = RoundedCornerShape(28.dp)
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Progress dots
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(steps.size) { i ->
                            Box(
                                modifier = Modifier
                                    .height(4.dp)
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(
                                        if (i <= idx) CyberBlue
                                        else Color.Gray.copy(alpha = 0.25f)
                                    )
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Title
                    Text(
                        text = stringResource(step.titleResId),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        color = Color.Black,
                        fontSize = 18.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    // Description
                    Text(
                        text = stringResource(step.descResId),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = Color.DarkGray,
                        lineHeight = 22.sp
                    )

                    Spacer(Modifier.height(28.dp))

                    // Buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onCompleted) {
                            Text(
                                stringResource(R.string.tour_skip),
                                color = Color.Gray,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = {
                                if (currentIdx < steps.size - 1) currentIdx++
                                else onCompleted()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberBlue
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                if (currentIdx < steps.size - 1) stringResource(R.string.tour_next)
                                else stringResource(R.string.get_started),
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = if (currentIdx < steps.size - 1) Icons.Default.ArrowForward else Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Modifier extension — capture a composable's
// screen bounds and expose them as a Rect
// ─────────────────────────────────────────────
fun Modifier.captureRect(onRect: (Rect) -> Unit): Modifier =
    this.onGloballyPositioned { coords ->
        val bounds = coords.boundsInRoot()
        onRect(
            Rect(
                left   = bounds.left,
                top    = bounds.top,
                right  = bounds.right,
                bottom = bounds.bottom
            )
        )
    }