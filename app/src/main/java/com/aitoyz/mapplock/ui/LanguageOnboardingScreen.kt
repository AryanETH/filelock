package com.aitoyz.mapplock.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aitoyz.mapplock.R
import com.aitoyz.mapplock.model.supportedLanguages
import com.aitoyz.mapplock.ui.theme.*

@Composable
fun LanguageOnboardingScreen(
    onLanguageSelected: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedLanguageCode by remember { mutableStateOf("en") }
    val selectedLanguage = remember(selectedLanguageCode) {
        supportedLanguages.find { it.code == selectedLanguageCode } ?: supportedLanguages.first()
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(top = 32.dp, start = 8.dp).statusBarsPadding()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.Black
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    Icons.Default.Language,
                    contentDescription = null,
                    tint = CyberBlue,
                    modifier = Modifier.size(64.dp)
                )
                
                Spacer(Modifier.height(24.dp))
                
                Text(
                    text = "SELECT LANGUAGE",
                    color = LightTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                
                Text(
                    text = "Choose your preferred language to continue",
                    color = LightTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(Modifier.height(48.dp))

                Box {
                    Surface(
                        onClick = { expanded = true },
                        color = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, LightOutline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = CyberBlue.copy(alpha = 0.1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = selectedLanguage.prefix,
                                            color = CyberBlue,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "${selectedLanguage.name} (${selectedLanguage.nativeName})",
                                    color = LightTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(Icons.Default.ArrowDropDown, null, tint = CyberBlue)
                        }
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .heightIn(max = 400.dp)
                            .background(Color.White)
                    ) {
                        supportedLanguages.forEach { language ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = language.name,
                                            color = if (language.code == selectedLanguageCode) CyberBlue else LightTextPrimary,
                                            fontWeight = if (language.code == selectedLanguageCode) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (language.code == selectedLanguageCode) {
                                            Icon(Icons.Default.Check, null, tint = CyberBlue, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    selectedLanguageCode = language.code
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(64.dp))

                Button(
                    onClick = { onLanguageSelected(selectedLanguageCode) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CONTINUE", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White)
                }
            }
        }
    }
}
