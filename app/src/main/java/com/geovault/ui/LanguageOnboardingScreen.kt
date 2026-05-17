package com.geovault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import com.geovault.R
import com.geovault.ui.theme.*

@Composable
fun LanguageOnboardingScreen(
    onLanguageSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("en") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack),
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
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            
            Text(
                text = "Choose your preferred language to continue",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(48.dp))

            Box {
                Surface(
                    onClick = { expanded = true },
                    color = CyberDarkBlue,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (selectedLanguage == "hi") "हिन्दी (Hindi)" else "English",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(Icons.Default.ArrowDropDown, null, tint = CyberBlue)
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .background(CyberDarkBlue)
                ) {
                    DropdownMenuItem(
                        text = { Text("English", color = Color.White) },
                        onClick = {
                            selectedLanguage = "en"
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("हिन्दी (Hindi)", color = Color.White) },
                        onClick = {
                            selectedLanguage = "hi"
                            expanded = false
                        }
                    )
                }
            }

            Spacer(Modifier.height(64.dp))

            Button(
                onClick = { onLanguageSelected(selectedLanguage) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("CONTINUE", fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        }
    }
}
