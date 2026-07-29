package com.aitoyz.mapplock.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aitoyz.mapplock.R
import com.aitoyz.mapplock.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectionGuideScreen(
    isDark: Boolean,
    onOpenBatterySettings: () -> Unit,
    onOpenAutoStartSettings: () -> Unit,
    onBack: () -> Unit
) {
    val manufacturer = remember { android.os.Build.MANUFACTURER.lowercase() }
    
    val backgroundColor = if (isDark) CyberBlack else CreamWhite
    val textPrimary = if (isDark) Color.White else LightTextPrimary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.protection_guide_title), fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = CyberBlue,
                    navigationIconContentColor = textPrimary
                )
            )
        },
        containerColor = backgroundColor
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))
            
            Text(
                stringResource(R.string.protection_guide_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDark) Color.Gray else Color.DarkGray,
                lineHeight = 24.sp
            )
            
            Spacer(Modifier.height(32.dp))

            // Step 1: Battery Optimization
            ProtectionStepItem(
                number = 1,
                title = stringResource(R.string.step_battery_title),
                description = stringResource(R.string.step_battery_desc),
                icon = Icons.Default.BatteryChargingFull,
                guideText = when {
                    manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> stringResource(R.string.oem_guide_xiaomi)
                    manufacturer.contains("oppo") || manufacturer.contains("realme") -> stringResource(R.string.oem_guide_oppo)
                    manufacturer.contains("vivo") -> stringResource(R.string.oem_guide_vivo)
                    manufacturer.contains("samsung") -> stringResource(R.string.oem_guide_samsung)
                    else -> stringResource(R.string.oem_guide_generic)
                },
                isDark = isDark,
                onClick = onOpenBatterySettings
            )

            Spacer(Modifier.height(24.dp))

            // Step 2: Auto-Start
            ProtectionStepItem(
                number = 2,
                title = stringResource(R.string.step_autostart_title),
                description = stringResource(R.string.step_autostart_desc),
                icon = Icons.Default.FlashOn,
                guideText = "Required for Xiaomi, Oppo, Vivo, and Realme devices.",
                isDark = isDark,
                onClick = onOpenAutoStartSettings
            )

            Spacer(Modifier.height(24.dp))

            // Step 3: Lock in Recents
            ProtectionStepItem(
                number = 3,
                title = stringResource(R.string.step_recents_title),
                description = stringResource(R.string.step_recents_desc),
                icon = Icons.AutoMirrored.Filled.Launch,
                guideText = stringResource(R.string.lock_recents_instruction),
                isDark = isDark,
                onClick = null // Non-clickable, just info
            )

            Spacer(Modifier.height(40.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CyberBlue.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberBlue.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VerifiedUser, null, tint = CyberBlue)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "Once completed, Mapplock will be able to protect your privacy without interruptions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun ProtectionStepItem(
    number: Int,
    title: String,
    description: String,
    icon: ImageVector,
    guideText: String,
    isDark: Boolean,
    onClick: (() -> Unit)?
) {
    val textPrimary = if (isDark) Color.White else LightTextPrimary
    val surfaceColor = if (isDark) CyberDarkBlue else Color.White
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = CyberBlue
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(number.toString(), color = Color.White, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = textPrimary, fontWeight = FontWeight.Black)
        }
        
        Spacer(Modifier.height(12.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = surfaceColor,
            shadowElevation = if (isDark) 0.dp else 4.dp,
            onClick = onClick ?: {}
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(icon, null, tint = CyberBlue, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = textPrimary)
                }
                
                Spacer(Modifier.height(12.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.05f)
                ) {
                    Text(
                        guideText,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color.Gray else Color.DarkGray,
                        lineHeight = 18.sp
                    )
                }
                
                if (onClick != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("FIX NOW", color = CyberBlue, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Icon(Icons.Default.ChevronRight, null, tint = CyberBlue, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
