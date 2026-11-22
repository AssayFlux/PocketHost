package com.assayflux.pockethost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

private data class HomeService(
    val title: String, val subtitle: String, val metric: String,
    val state: String, val icon: ImageVector, val destination: String,
    val available: Boolean = true
)

@Composable
internal fun PocketHostHomeScreen(
    hosting: Boolean, status: String, requests: Long, activeConnections: Long, uptimeText: String,
    importedSummary: String, tunnelStatus: String, pythonHosting: Boolean, nodeHosting: Boolean,
    deviceTelemetry: JSONObject, recentEvents: List<com.assayflux.pockethost.diagnostics.OperationalLog.Entry>,
    operatingMode: String,
    onOpen: (String) -> Unit
) {
    val palette = MaterialTheme.colorScheme
    val services = listOf(
        HomeService("Hosting", "Static website server", if (hosting) "$requests total requests" else "Not serving requests",
            if (hosting) "Running · $uptimeText" else "Stopped", Icons.Outlined.Language, "Dashboard"),
        HomeService("Websites", "Sites, files and backups", importedSummary, "Website workspace", Icons.Outlined.FolderOpen, "Sites"),
        HomeService("Workbench", "Edit a safe private draft", "Files, changes and local snapshots",
            "Live site remains unchanged", Icons.Outlined.Code, "Workbench"),
        HomeService("Deployments", "Publish and rollback", "Reviewed production releases",
            "Coming soon", Icons.Outlined.Code, "Deployments", false),
        HomeService("Network", "LAN, Relay and Cloudflare", tunnelStatus,
            "Connection status", Icons.Outlined.NetworkCheck, "Settings:Network"),
        HomeService("Activity", "Events and diagnostics", "$activeConnections active connections",
            "Operational history", Icons.Outlined.Timeline, "Activity"),
        HomeService("Learn", "Guides and operating limits", "Setup and troubleshooting",
            "Documentation", Icons.Outlined.School, "Learn"),
        HomeService("Settings", "Security and preferences",
            "Python ${if (pythonHosting) "running" else "stopped"} · Node ${if (nodeHosting) "running" else "stopped"}",
            "Device preferences", Icons.Outlined.Settings, "Settings:General")
    ) + if (operatingMode == "COMMAND_CENTER_SERVER") listOf(
        HomeService("Devices", "Trusted fleet and workers", "Device enrollment and status",
            "Command Center enabled", Icons.Outlined.Memory, "Devices")
    ) else emptyList()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Your services", color = palette.onSurface, style = MaterialTheme.typography.headlineMedium)
            Text("Your hosting operations, at a glance.", color = palette.onSurfaceVariant, fontSize = 13.sp)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = if (hosting) palette.primaryContainer else palette.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)) {
                        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Language, null,
                                tint = if (hosting) palette.onPrimaryContainer else palette.onSurfaceVariant)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("HOSTING STATUS", color = palette.onSurfaceVariant, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                        Text(if (hosting) "Your server is running" else "Your server is stopped",
                            color = palette.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Surface(color = if (hosting) palette.primaryContainer else palette.surfaceVariant,
                        shape = RoundedCornerShape(50)) {
                        Text(if (hosting) "RUNNING" else "OFFLINE",
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            color = if (hosting) palette.onPrimaryContainer else palette.onSurfaceVariant,
                            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Text(status, color = palette.onSurfaceVariant, fontSize = 13.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    HomeMetric("REQUESTS", requests.toString(), Modifier.weight(1f))
                    HomeMetric("CONNECTIONS", activeConnections.toString(), Modifier.weight(1f))
                    HomeMetric("UPTIME", uptimeText, Modifier.weight(1f))
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = { onOpen("Dashboard") }, modifier = Modifier.fillMaxWidth()
                ) { Text("Open hosting controls") }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Workspace", color = palette.onSurface, style = MaterialTheme.typography.titleLarge)
            Text("${services.size} areas", color = palette.onSurfaceVariant, fontSize = 12.sp)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when {
                maxWidth >= 760.dp -> 3
                maxWidth >= 480.dp -> 2
                else -> 1
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                services.chunked(columns).forEach { serviceRow ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        serviceRow.forEach { service ->
                            Card(
                                onClick = { onOpen(service.destination) },
                                modifier = Modifier.weight(1f).heightIn(min = 154.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = palette.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 120.dp),
                                    verticalArrangement = Arrangement.SpaceBetween) {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Surface(color = palette.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                                            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                                Icon(service.icon, null, tint = palette.onPrimaryContainer)
                                            }
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(service.title, color = palette.onSurface,
                                                fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text(service.subtitle, color = palette.onSurfaceVariant,
                                                fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(service.metric, color = palette.onSurface, fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box(Modifier.size(7.dp).clip(CircleShape).background(
                                                if (service.title == "Hosting" && hosting) palette.primary else palette.outline))
                                            Text(service.state, color = palette.onSurfaceVariant, fontSize = 10.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f))
                                            if (!service.available) Text("SOON", color = palette.onSurfaceVariant,
                                                fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        repeat(columns - serviceRow.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        Text("Device health", color = palette.onSurface, style = MaterialTheme.typography.titleLarge)
        val batteryValue = deviceTelemetry.opt("batteryPercent")
        val battery = if (batteryValue == null || batteryValue == JSONObject.NULL) "Unavailable" else "$batteryValue%"
        val memory = deviceTelemetry.optLong("availableMemoryBytes", -1L).takeIf { it >= 0L }
            ?.let { "${it / (1024L * 1024L)} MB available" } ?: "Unavailable"
        HomeInfoCard(Icons.Outlined.Memory, "This device",
            "Battery $battery · $memory",
            "${deviceTelemetry.optString("manufacturer")} ${deviceTelemetry.optString("model")} · Android API ${deviceTelemetry.optInt("androidApi")}",
            onClick = { onOpen("Settings:About") })
        HomeInfoCard(Icons.Outlined.NetworkCheck, "Network",
            "Transport: ${deviceTelemetry.optString("networkTransport", "unknown")}",
            "Validated internet: ${if (deviceTelemetry.optBoolean("networkValidated")) "Yes" else "No / unknown"}",
            onClick = { onOpen("Settings:Network") })
        HomeInfoCard(Icons.Outlined.Storage, "Runtime & backup safety",
            "Python ${if (pythonHosting) "running" else "stopped"} · Node ${if (nodeHosting) "running" else "stopped"}",
            "Runtime projects remain trusted-code-only; backups are available in Websites.",
            onClick = { onOpen("Settings:Runtimes") })

        Text("Recent activity", color = palette.onSurface, style = MaterialTheme.typography.titleLarge)
        if (recentEvents.isEmpty()) {
            HomeInfoCard(Icons.Outlined.Timeline, "No recent events",
                "There are no recorded operational events yet.",
                "New service and system events will appear here.", onClick = { onOpen("Activity") })
        } else recentEvents.take(3).forEach { event ->
            HomeInfoCard(Icons.Outlined.Timeline, event.event, event.message, event.level,
                onClick = { onOpen("Activity") })
        }
    }
}

@Composable
private fun HomeMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = MaterialTheme.colorScheme
    Column(modifier.background(palette.surfaceVariant, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = palette.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = palette.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HomeInfoCard(icon: ImageVector, title: String, primary: String, secondary: String, onClick: () -> Unit) {
    val palette = MaterialTheme.colorScheme
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = palette.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = palette.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(primary, color = palette.onSurface, fontSize = 13.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(secondary, color = palette.onSurfaceVariant, fontSize = 11.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = palette.onSurfaceVariant, fontSize = 22.sp)
        }
    }
}
