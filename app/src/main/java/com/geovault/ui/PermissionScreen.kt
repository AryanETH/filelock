package com.geovault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geovault.model.VaultState
import com.geovault.ui.theme.CyberBlack
import com.geovault.ui.theme.CyberBlue
import com.geovault.ui.theme.CyberDarkBlue

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun PermissionScreen(
    state: VaultState,
    onGrantUsage: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantLocation: () -> Unit,
    onGrantBattery: () -> Unit,
) {
    val allGranted = state.hasUsageStatsPermission && 
                     state.hasOverlayPermission &&
                     state.hasLocationPermission &&
                     state.hasBatteryOptimizationPermission

    Column(
        modifier = Modifier.fillMaxSize().background(CyberBlack).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = CyberBlue,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "SECURITY CLEARANCE",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 2.sp
        )
        
        Text(
            "Mandatory permissions required for system operation. Access denied until authorization is complete.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PermissionRow("Usage Access", "Monitor app launches for stealth mode.", state.hasUsageStatsPermission, onGrantUsage)
            PermissionRow("Overlay Access", "Required to show the security interface.", state.hasOverlayPermission, onGrantOverlay)
            PermissionRow("Location Access", "Detect vault proximity for auto-locking.", state.hasLocationPermission, onGrantLocation)
            PermissionRow("Background Activity", "Essential for 24/7 background security monitoring.", state.hasBatteryOptimizationPermission, onGrantBattery)
        }

        if (allGranted) {
            Text(
                "SYSTEM READY",
                color = Color(0xFF00E676),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp).padding(bottom = 16.dp),
                color = CyberBlue,
                trackColor = Color.Gray.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
fun PermissionRow(title: String, desc: String, granted: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = if (!granted) onClick else ({}),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CyberDarkBlue.copy(alpha = if (granted) 0.3f else 1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (granted) Color(0xFF00E676).copy(alpha = 0.3f) else CyberBlue.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (granted) Color(0xFF00E676) else Color(0xFFFF5252)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White)
                Text(desc, fontSize = 11.sp, color = Color.Gray)
            }
            if (!granted) {
                Text("AUTHORIZE", color = CyberBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
