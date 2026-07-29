package com.aitoyz.mapplock.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Pattern
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aitoyz.mapplock.ui.theme.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aitoyz.mapplock.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.AsyncImage

import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.text.ClickableText
import android.net.Uri
import android.content.Intent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

@Composable
fun IntroGuideScreen(
    onFinished: () -> Unit
) {
    val slides = listOf(
        OnboardingSlide(
            title = stringResource(R.string.onboarding_1_title),
            description = stringResource(R.string.onboarding_1_desc),
            imageRes = R.drawable.lock_disguise,
            color = Color(0xFF2962FF)
        ),
        OnboardingSlide(
            title = stringResource(R.string.onboarding_2_title),
            description = stringResource(R.string.onboarding_2_desc),
            imageRes = R.drawable.as_lock,
            color = Color(0xFF2962FF)
        ),
        OnboardingSlide(
            title = stringResource(R.string.onboarding_3_title),
            description = stringResource(R.string.onboarding_3_desc),
            imageRes = R.drawable.india,
            color = Color(0xFF2962FF)
        ),
        OnboardingSlide(
            title = stringResource(R.string.onboarding_4_title),
            description = stringResource(R.string.onboarding_4_desc),
            imageRes = R.drawable.fourth_page,
            color = Color(0xFF2962FF)
        )
    )

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    // Timer logic for Page 4
    var timerProgress by remember { mutableFloatStateOf(0f) }
    val isPage4 = pagerState.currentPage == 3
    
    LaunchedEffect(pagerState.currentPage) {
        if (isPage4) {
            timerProgress = 0f
            val startTime = System.currentTimeMillis()
            val duration = 10000f
            while (timerProgress < 1f) {
                timerProgress = ((System.currentTimeMillis() - startTime) / duration).coerceIn(0f, 1f)
                delay(16)
            }
        }
    }

    val isTimerRunning = isPage4 && timerProgress < 1f
    val isLastPage = pagerState.currentPage == slides.size - 1

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Indicators
                Row(
                    modifier = Modifier.padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(slides.size) { index ->
                        val active = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .width(if (active) 32.dp else 16.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (active) CyberBlue else Color.LightGray)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    if (isTimerRunning) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(timerProgress)
                                .background(CyberBlue.copy(alpha = 0.3f))
                        )
                    }

                    Button(
                        onClick = {
                            if (pagerState.currentPage < slides.size - 1) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            } else {
                                onFinished()
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTimerRunning) Color.Transparent else CyberBlue,
                            disabledContainerColor = if (isTimerRunning) Color.Transparent else CyberBlue.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isTimerRunning
                    ) {
                        Text(
                            text = if (isLastPage) stringResource(R.string.next) else stringResource(R.string.next),
                            color = if (isTimerRunning) CyberBlue else Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isTimerRunning,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { page ->
            OnboardingSlideContent(
                slide = slides[page],
                isStepPage = page == 3
            )
        }
    }
}

@Composable
fun DisclaimerScreen(
    onAccepted: () -> Unit
) {
    var isAccepted by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AsyncImage(
            model = R.drawable.slide_3_calf,
            contentDescription = null,
            modifier = Modifier.size(260.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = stringResource(R.string.onboarding_disclaimer_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_disclaimer_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        val annotatedString = buildAnnotatedString {
            append(stringResource(R.string.onboarding_accept_prefix))
            
            pushStringAnnotation(tag = "terms", annotation = "https://maps.aitoyz.in/terms.html")
            withStyle(style = SpanStyle(color = CyberBlue, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                append(stringResource(R.string.onboarding_terms))
            }
            pop()

            append(stringResource(R.string.onboarding_and))

            pushStringAnnotation(tag = "privacy", annotation = "https://maps.aitoyz.in/privacypolicy.html")
            withStyle(style = SpanStyle(color = CyberBlue, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                append(stringResource(R.string.onboarding_privacy))
            }
            pop()
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Checkbox(
                checked = isAccepted,
                onCheckedChange = { isAccepted = it },
                colors = CheckboxDefaults.colors(checkedColor = CyberBlue)
            )
            ClickableText(
                text = annotatedString,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                onClick = { offset ->
                    annotatedString.getStringAnnotations(tag = "terms", start = offset, end = offset).firstOrNull()?.let {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
                    }
                    annotatedString.getStringAnnotations(tag = "privacy", start = offset, end = offset).firstOrNull()?.let {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onAccepted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
            shape = RoundedCornerShape(12.dp),
            enabled = isAccepted
        ) {
            Text(stringResource(R.string.next), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OnboardingSlideContent(
    slide: OnboardingSlide,
    isStepPage: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "onboardingMotion")
    val pulseScale by if (!isStepPage) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isStepPage) {
            Spacer(modifier = Modifier.height(40.dp))
            
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    },
                contentAlignment = Alignment.Center
            ) {
                if (slide.imageRes != null) {
                    AsyncImage(
                        model = slide.imageRes,
                        contentDescription = null,
                        modifier = Modifier.size(260.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = slide.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = slide.description,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
                lineHeight = 22.sp
            )
        } else {
            // How to use page
            Spacer(modifier = Modifier.height(20.dp))
            
            AsyncImage(
                model = R.drawable.fourth_page,
                contentDescription = null,
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Fit
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = slide.title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(40.dp))
            
            val steps = listOf(
                StepItem(stringResource(R.string.onboarding_4_step1), Icons.Default.TouchApp, Color(0xFF4CAF50)),
                StepItem(stringResource(R.string.onboarding_4_step2), Icons.Default.Pattern, Color(0xFF2196F3)),
                StepItem(stringResource(R.string.onboarding_4_step3), Icons.Default.Lock, Color(0xFFFF9800)),
                StepItem(stringResource(R.string.onboarding_4_step4), Icons.Default.AdsClick, Color(0xFFE91E63)),
                StepItem(stringResource(R.string.onboarding_4_step5), Icons.Default.AddLocationAlt, Color(0xFF8A21F3)),
            )
            
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                steps.forEach { step ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(step.color.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = step.icon,
                                contentDescription = null,
                                tint = step.color,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(
                            text = step.text,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black.copy(alpha = 0.8f),
                            lineHeight = 24.sp
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

private data class StepItem(val text: String, val icon: ImageVector, val color: Color)

data class OnboardingSlide(
    val title: String,
    val description: String,
    val imageRes: Int?,
    val color: Color
)
