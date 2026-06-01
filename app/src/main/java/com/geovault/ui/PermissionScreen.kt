package com.geovault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geovault.R
import com.geovault.model.VaultState
import com.geovault.ui.theme.CyberBlack
import com.geovault.ui.theme.CyberBlue
import com.geovault.ui.theme.CyberDarkBlue

import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun PermissionScreen(
    state: VaultState,
    onGrantUsage: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantLocation: () -> Unit,
    onGrantBattery: () -> Unit,
    onGrantFullStorage: () -> Unit,
) {
    val allGranted = state.hasUsageStatsPermission &&
                state.hasOverlayPermission &&
                state.hasLocationPermission &&
                state.hasBatteryOptimizationPermission &&
                (android.os.Build.VERSION.SDK_INT < 30 || state.hasFullStoragePermission)

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { 
                        if (!allGranted) {
                            if (!state.hasUsageStatsPermission) onGrantUsage()
                            else if (!state.hasOverlayPermission) onGrantOverlay()
                            else if (!state.hasLocationPermission) onGrantLocation()
                            else if (!state.hasBatteryOptimizationPermission) onGrantBattery()
                            else if (android.os.Build.VERSION.SDK_INT >= 30 && !state.hasFullStoragePermission) onGrantFullStorage()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0047FF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.next), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                
                Text(
                    text = stringResource(R.string.ads_note),
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Top Illustration Placeholder
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(Color(0xFFE8EFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.size(70.dp),
                    tint = Color(0xFF0047FF)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.security_clearance),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Text(
                text = stringResource(R.string.security_clearance_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            PermissionGuideRow(
                title = stringResource(R.string.location_access),
                desc = stringResource(R.string.location_access_desc),
                icon = Icons.Default.LocationOn,
                iconBgColor = Color(0xFFFFEBEE),
                iconColor = Color(0xFFF44336),
                granted = state.hasLocationPermission,
                onClick = onGrantLocation
            )

            PermissionGuideRow(
                title = stringResource(R.string.usage_access),
                desc = stringResource(R.string.usage_access_desc),
                icon = Icons.Default.Visibility,
                iconBgColor = Color(0xFFE0F2F1),
                iconColor = Color(0xFF009688),
                granted = state.hasUsageStatsPermission,
                onClick = onGrantUsage
            )

            PermissionGuideRow(
                title = stringResource(R.string.overlay_access),
                desc = stringResource(R.string.overlay_access_desc),
                icon = Icons.Default.Layers,
                iconBgColor = Color(0xFFF3E5F5),
                iconColor = Color(0xFF9C27B0),
                granted = state.hasOverlayPermission,
                onClick = onGrantOverlay
            )

            PermissionGuideRow(
                title = stringResource(R.string.background_activity),
                desc = stringResource(R.string.background_activity_desc),
                icon = Icons.Default.BatteryChargingFull,
                iconBgColor = Color(0xFFFFF8E1),
                iconColor = Color(0xFFFFC107),
                granted = state.hasBatteryOptimizationPermission,
                onClick = onGrantBattery
            )

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                PermissionGuideRow(
                    title = stringResource(R.string.full_storage_access),
                    desc = stringResource(R.string.full_storage_access_desc),
                    icon = Icons.Default.Storage,
                    iconBgColor = Color(0xFFE1F5FE),
                    iconColor = Color(0xFF03A9F4),
                    granted = state.hasFullStoragePermission,
                    onClick = onGrantFullStorage
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Information Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFFDE7),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFF9C4))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFFBC02D),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF827717),
                        lineHeight = 16.sp
                    )
                }
            }

            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PermissionGuideRow(
    title: String,
    desc: String,
    icon: ImageVector,
    iconBgColor: Color,
    iconColor: Color,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!granted) onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(iconBgColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = iconColor
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        if (granted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Granted",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
