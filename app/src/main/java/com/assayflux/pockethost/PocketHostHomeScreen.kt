package com.assayflux.pockethost

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class HomeService(val title: String, val subtitle: String, val metric: String, val state: String, val icon: ImageVector, val destination: String)

@Composable
internal fun PocketHostHomeScreen(
    hosting: Boolean, status: String, requests: Long, activeConnections: Long, uptimeText: String,
    importedSummary: String, tunnelStatus: String, pythonHosting: Boolean, nodeHosting: Boolean,
    deviceTelemetry: JSONObject, recentEvents: List<com.assayflux.pockethost.diagnostics.OperationalLog.Entry>,
    onOpen: (String) -> Unit
) {
    val palette = MaterialTheme.colorScheme
    val services = listOf(
        HomeService("Hosting", "Local website origin", if (hosting) "$requests requests" else "Not serving requests", if (hosting) "Running · $uptimeText" else "Stopped", Icons.Outlined.Language, "Dashboard"),
        HomeService("Websites", "Projects and publishing", importedSummary, "Site workspace", Icons.Outlined.FolderOpen, "Sites"),
        HomeService("Relay & tunnels", "External connectivity", tunnelStatus, "Connector status", Icons.Outlined.NetworkCheck, "Settings:Network"),
        HomeService("Runtimes", "Python and Node.js", "Python: ${if (pythonHosting) "running" else "stopped"} · Node: ${if (nodeHosting) "running" else "stopped"}", "Trusted code only", Icons.Outlined.Code, "Settings:Runtimes"),
        HomeService("Security", "Access and protection", "Owner and visitor controls", "Review settings", Icons.Outlined.Security, "Settings:Network"),
        HomeService("Capacity", "Device and workload", "$activeConnections active connections", "Live engine snapshot", Icons.Outlined.Memory, "Dashboard"),
        HomeService("Backups", "Protect and recover", "Encrypted backup tools", "Open site operations", Icons.Outlined.Backup, "Sites")
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Your services", color = palette.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Swipe to explore Pocket Host", color = palette.onSurfaceVariant, fontSize = 12.sp)
            }
            Surface(color = palette.primaryContainer, shape = RoundedCornerShape(50)) {
                Text("7 modules", modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = palette.onPrimaryContainer, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = maxWidth * 0.78f
            val scrollState = rememberScrollState()
            val scope = rememberCoroutineScope()
            val stepPx = with(LocalDensity.current) { (cardWidth + 12.dp).roundToPx() }.coerceAtLeast(1)
            val activePage = (scrollState.value / stepPx).coerceIn(0, services.lastIndex)
            Row(Modifier.fillMaxWidth().horizontalScroll(scrollState), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.width((maxWidth - cardWidth - 12.dp) / 2))
                services.forEachIndexed { index, service ->
                    val cardScale by animateFloatAsState(
                        targetValue = if (index == activePage) 1.04f else 0.94f,
                        label = "service-card-scale"
                    )
                    Card(onClick = { onOpen(service.destination) },
                        modifier = Modifier.width(cardWidth).heightIn(min = 224.dp)
                            .graphicsLayer { scaleX = cardScale; scaleY = cardScale },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = if (index == 0) palette.surface else palette.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (index == 0) 3.dp else 1.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(color = palette.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                        Icon(service.icon, contentDescription = null, tint = palette.onPrimaryContainer)
                                    }
                                }
                                Text(service.title, color = palette.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                                Text(service.subtitle, color = palette.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(service.metric, color = palette.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (index == 0 && hosting) palette.primary else palette.outline))
                                    Text(service.state, color = palette.onSurfaceVariant, fontSize = 11.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text("Open  →", color = palette.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width((maxWidth - cardWidth - 12.dp) / 2))
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                services.indices.forEach { index ->
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(if (index == activePage) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (index == activePage) palette.primary else palette.outlineVariant)
                            .clickable { scope.launch { scrollState.animateScrollTo((index * stepPx).coerceAtMost(scrollState.maxValue)) } }
                    )
                }
            }
        }
        Text("System information", color = palette.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        HomeInfoCard(Icons.Outlined.Dashboard, "Hosting status", status, "Active connections: $activeConnections", onClick = { onOpen("Dashboard") })
        val batteryValue = deviceTelemetry.opt("batteryPercent")
        val battery = if (batteryValue == null || batteryValue == JSONObject.NULL) "Unavailable" else "$batteryValue%"
        val memory = deviceTelemetry.optLong("availableMemoryBytes", -1L).takeIf { it >= 0L }?.let { "${it / (1024L * 1024L)} MB available" } ?: "Unavailable"
        HomeInfoCard(Icons.Outlined.Memory, "Device health", "Battery $battery · $memory",
            "${deviceTelemetry.optString("manufacturer")} ${deviceTelemetry.optString("model")} · Android API ${deviceTelemetry.optInt("androidApi")}", onClick = { onOpen("Settings:About") })
        HomeInfoCard(Icons.Outlined.NetworkCheck, "Network", "Transport: ${deviceTelemetry.optString("networkTransport", "unknown")}",
            "Validated internet: ${if (deviceTelemetry.optBoolean("networkValidated")) "Yes" else "No / unknown"}", onClick = { onOpen("Settings:Network") })
        HomeInfoCard(Icons.Outlined.Storage, "Runtime & backup safety", "Python ${if (pythonHosting) "running" else "stopped"} · Node ${if (nodeHosting) "running" else "stopped"}",
            "Runtime projects remain trusted-code-only; backup tools are available in Sites.", onClick = { onOpen("Settings:Runtimes") })
        Text("Recent activity", color = palette.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (recentEvents.isEmpty()) {
            HomeInfoCard(Icons.Outlined.Timeline, "No recent events", "There are no recorded operational events yet.",
                "New service and system events will appear here.", onClick = { onOpen("Activity") })
        } else recentEvents.take(3).forEach { event ->
            HomeInfoCard(Icons.Outlined.Timeline, event.event, event.message, event.level, onClick = { onOpen("Activity") })
        }
    }
}

@Composable
private fun HomeInfoCard(icon: ImageVector, title: String, primary: String, secondary: String, onClick: () -> Unit) {
    val palette = MaterialTheme.colorScheme
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = palette.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = palette.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(primary, color = palette.onSurface, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(secondary, color = palette.onSurfaceVariant, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = palette.onSurfaceVariant, fontSize = 22.sp)
        }
    }
}
