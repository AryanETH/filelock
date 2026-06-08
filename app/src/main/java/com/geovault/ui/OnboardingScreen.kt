package com.geovault.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.geovault.ui.theme.*
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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.text.ClickableText
import android.net.Uri
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onStartAction: () -> Unit = {},
    onEndAction: () -> Unit = {}
) {
    var isAccepted by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onEndAction()
        onFinished()
    }

    val slides = listOf(
        OnboardingSlide(
            title = stringResource(R.string.onboarding_1_title),
            description = stringResource(R.string.onboarding_1_desc),
            icon = Icons.Default.Map,
            imageRes = null,
            color = Color(0xFF2962FF)
        ),
        OnboardingSlide(
            title = stringResource(R.string.onboarding_2_title),
            description = stringResource(R.string.onboarding_2_desc),
            icon = null,
            imageRes = R.drawable.mascot_secret,
            color = Color(0xFF2962FF)
        ),
        OnboardingSlide(
            title = stringResource(R.string.onboarding_3_title),
            description = stringResource(R.string.onboarding_3_desc),
            icon = null,
            imageRes = R.drawable.slide_3_calf,
            color = Color(0xFF2962FF)
        ),
        OnboardingSlide(
            title = stringResource(R.string.onboarding_4_title),
            description = stringResource(R.string.onboarding_4_desc),
            icon = Icons.Default.Public,
            imageRes = null,
            color = Color(0xFF2962FF)
        ),
        OnboardingSlide(
            title = "Map Data Disclaimer",
            description = "Map data and borders are provided as-is by map providers. We do not control or modify geographic representations. Your privacy is our priority.",
            icon = Icons.Default.Security,
            imageRes = null,
            color = CyberBlue,
            isDisclaimer = true
        )
    )

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

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

                val isLastPage = pagerState.currentPage == slides.size - 1
                val buttonEnabled = !isLastPage || isAccepted

                Button(
                    onClick = {
                        if (pagerState.currentPage < slides.size - 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            onStartAction()
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberBlue,
                        disabledContainerColor = CyberBlue.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = buttonEnabled
                ) {
                    Text(
                        text = if (isLastPage) "Get Started" else "Continue",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { page ->
            OnboardingSlideContent(
                slide = slides[page],
                isAccepted = isAccepted,
                onAcceptChange = { isAccepted = it },
                onStartAction = onStartAction
            )
        }
    }
}

@Composable
fun OnboardingSlideContent(
    slide: OnboardingSlide,
    isAccepted: Boolean = false,
    onAcceptChange: (Boolean) -> Unit = {},
    onStartAction: () -> Unit = {}
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        
        Box(
            modifier = Modifier
                .size(280.dp)
                .background(Color(0xFFF5F7FF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (slide.imageRes != null) {
                androidx.compose.foundation.Image(
                    painter = painterResource(slide.imageRes),
                    contentDescription = null,
                    modifier = Modifier.size(220.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else if (slide.icon != null) {
                Icon(
                    imageVector = slide.icon,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = CyberBlue
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        Text(
            text = slide.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (slide.isDisclaimer) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = slide.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                val annotatedString = buildAnnotatedString {
                    append("I understand and accept the ")
                    
                    pushStringAnnotation(tag = "terms", annotation = "https://maps.aitoyz.in/terms.html")
                    withStyle(style = SpanStyle(color = CyberBlue, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                        append("Terms of Service")
                    }
                    pop()

                    append(" and ")

                    pushStringAnnotation(tag = "privacy", annotation = "https://maps.aitoyz.in/privacypolicy.html")
                    withStyle(style = SpanStyle(color = CyberBlue, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                        append("Privacy Policy")
                    }
                    pop()
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Checkbox(
                        checked = isAccepted,
                        onCheckedChange = onAcceptChange,
                        colors = CheckboxDefaults.colors(checkedColor = CyberBlue)
                    )
                    ClickableText(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                        onClick = { offset ->
                            annotatedString.getStringAnnotations(tag = "terms", start = offset, end = offset).firstOrNull()?.let {
                                onStartAction()
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
                            }
                            annotatedString.getStringAnnotations(tag = "privacy", start = offset, end = offset).firstOrNull()?.let {
                                onStartAction()
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
                            }
                        }
                    )
                }
            }
        } else {
            Text(
                text = slide.description,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
                lineHeight = 22.sp
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

data class OnboardingSlide(
    val title: String,
    val description: String,
    val icon: ImageVector?,
    val imageRes: Int?,
    val color: Color,
    val isDisclaimer: Boolean = false
)

