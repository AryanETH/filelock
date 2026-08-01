package com.aitoyz.mapplock.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import com.aitoyz.mapplock.model.Language
import com.aitoyz.mapplock.model.supportedLanguages
import com.aitoyz.mapplock.ui.theme.CyberBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionScreen(
    currentLanguageCode: String,
    onLanguageSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isEmpty()) {
            supportedLanguages
        } else {
            supportedLanguages.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.nativeName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // "Choose Language" title with minimal top whitespace
        Text(
            text = stringResource(R.string.select_language),
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = CyberBlue
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = {
                Text(stringResource(R.string.search_languages), color = Color.Gray)
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.Gray
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.Gray
                        )
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberBlue,
                unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f),
                cursorColor = CyberBlue
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredLanguages) { language ->
                LanguageItem(
                    language = language,
                    isSelected = language.code == currentLanguageCode,
                    onClick = {
                        onLanguageSelected(language.code)
                    }
                )
            }
        }
    }
}

@Composable
fun LanguageItem(
    language: Language,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) CyberBlue else Color.LightGray.copy(alpha = 0.4f)
        ),
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Square Prefix Icon
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) CyberBlue.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.05f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = language.prefix,
                        color = if (isSelected) CyberBlue else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = language.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) CyberBlue else Color.Black
                )
                Text(
                    text = language.nativeName,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            // Radio Button style selection indicator
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = if (isSelected) CyberBlue else Color.Transparent,
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) CyberBlue else Color.LightGray,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
