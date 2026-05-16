package com.geovault.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geovault.R
import com.geovault.ui.theme.CyberBlack
import com.geovault.ui.theme.CyberBlue
import com.geovault.ui.theme.CyberDarkBlue
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {

    val slides = listOf(
        OnboardingSlide(
            title = stringResource(R.string.onboarding_1_title),
            description = stringResource(R.string.onboarding_1_desc),
            color = CyberBlue
        ),
        OnboardingSlide(
            title = stringResource(R.string.onboarding_2_title),
            description = stringResource(R.string.onboarding_2_desc),
            color = Color(0xFF00E676)
        ),
        OnboardingSlide(
            title = stringResource(R.string.onboarding_3_title),
            description = stringResource(R.string.onboarding_3_desc),
            color = Color(0xFFFFC107)
        ),
        OnboardingSlide(
            title = stringResource(R.string.onboarding_4_title),
            description = stringResource(R.string.onboarding_4_desc),
            color = Color(0xFFFF5252)
        )
    )

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack)
    ) {

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            OnboardingSlideContent(slides[page])
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Indicators
            Row(
                modifier = Modifier
                    .height(8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(slides.size) { iteration ->

                    val color =
                        if (pagerState.currentPage == iteration)
                            CyberBlue
                        else
                            Color.Gray.copy(alpha = 0.3f)

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(
                                width = if (pagerState.currentPage == iteration) 24.dp else 8.dp,
                                height = 8.dp
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (pagerState.currentPage < slides.size - 1) {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                pagerState.currentPage + 1
                            )
                        }
                    } else {
                        onFinished()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberBlue
                ),
                shape = CircleShape
            ) {

                Text(
                    text =
                        if (pagerState.currentPage == slides.size - 1)
                            stringResource(R.string.initialize_system)
                        else
                            stringResource(R.string.next),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )

                if (pagerState.currentPage < slides.size - 1) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.next),
                        tint = Color.Black,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingSlideContent(slide: OnboardingSlide) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Box(
            modifier = Modifier
                .size(200.dp)
                .background(
                    brush = Brush.radialGradient(
                        listOf(
                            slide.color.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {

            Surface(
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                color = CyberDarkBlue,
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    slide.color
                )
            ) {

                Box(contentAlignment = Alignment.Center) {

                    Text(
                        text = slide.title.take(1),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                        color = slide.color
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = slide.title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = slide.description,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

data class OnboardingSlide(
    val title: String,
    val description: String,
    val color: Color
)