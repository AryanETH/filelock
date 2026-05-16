package com.geovault.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geovault.R
import com.geovault.ui.theme.CyberBlue

data class TourStep(
    val textResId: Int,
    val targetRect: Rect? = null
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppTour(
    steps: List<TourStep>,
    onCompleted: () -> Unit
) {
    var currentStepIdx by remember { mutableIntStateOf(0) }
    val currentStep = steps[currentStepIdx]

    val infiniteTransition = rememberInfiniteTransition(label = "HolePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    val animatedRect by animateRectAsState(
        targetValue = currentStep.targetRect ?: Rect.Zero,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "RectTransition"
    )

    // Block all interactions with the map below
    Box(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectTapGestures { }
        }
    ) {
        // Dimmed Background with Hole
        Canvas(modifier = Modifier.fillMaxSize()) {
            with(drawContext.canvas.nativeCanvas) {
                val checkPoint = saveLayer(null, null)

                // Draw dark overlay
                drawRect(Color.Black.copy(alpha = 0.85f))

                if (animatedRect != Rect.Zero) {
                    val padding = 20.dp.toPx()
                    val holeRect = Rect(
                        left = animatedRect.left - padding,
                        top = animatedRect.top - padding,
                        right = animatedRect.right + padding,
                        bottom = animatedRect.bottom + padding
                    )

                    val center = holeRect.center
                    val size = holeRect.size * pulseScale

                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = Offset(center.x - size.width / 2, center.y - size.height / 2),
                        size = size,
                        cornerRadius = CornerRadius(24.dp.toPx()),
                        blendMode = BlendMode.Clear
                    )
                }

                restoreToCount(checkPoint)
            }
        }

        // Animated Content
        AnimatedContent(
            targetState = currentStepIdx,
            transitionSpec = {
                (fadeIn(animationSpec = tween(400)) + slideInVertically { it / 10 })
                    .togetherWith(fadeOut(animationSpec = tween(300)))
            },
            modifier = Modifier.fillMaxSize(),
            label = "TooltipTransition"
        ) { stepIdx ->
            val step = steps[stepIdx]
            val configuration = LocalConfiguration.current
            val screenHeight = configuration.screenHeightDp.dp

            val isTargetInBottomHalf = step.targetRect?.let {
                it.center.y > (configuration.screenHeightDp * 2).toFloat()
            } ?: false

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .align(if (step.targetRect == null) Alignment.Center else if (isTargetInBottomHalf) Alignment.TopCenter else Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = if (step.targetRect == null) 0.dp else 140.dp)
                        .widthIn(max = 400.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Progress Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(steps.size) { i ->
                            Box(
                                modifier = Modifier
                                    .height(4.dp)
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(if (i <= stepIdx) CyberBlue else Color.Gray.copy(alpha = 0.3f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = stringResource(step.textResId),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onCompleted) {
                            Text(stringResource(R.string.tour_skip), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                if (currentStepIdx < steps.size - 1) {
                                    currentStepIdx++
                                } else {
                                    onCompleted()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (currentStepIdx < steps.size - 1) stringResource(R.string.tour_next) else stringResource(R.string.get_started),
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    if (currentStepIdx < steps.size - 1) Icons.Default.ArrowForward else Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun animateRectAsState(
    targetValue: Rect,
    animationSpec: AnimationSpec<Rect> = spring(),
    label: String = "RectAnimation",
    finishedListener: ((Rect) -> Unit)? = null
): State<Rect> {
    return animateValueAsState(
        targetValue = targetValue,
        typeConverter = RectConverter,
        animationSpec = animationSpec,
        label = label,
        finishedListener = finishedListener
    )
}

private val RectConverter: TwoWayConverter<Rect, AnimationVector4D> = TwoWayConverter(
    convertToVector = { AnimationVector4D(it.left, it.top, it.right, it.bottom) },
    convertFromVector = { Rect(it.v1, it.v2, it.v3, it.v4) }
)