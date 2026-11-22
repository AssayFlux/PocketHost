package com.assayflux.pockethost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PocketHostOwnerHeader(
    currentUserId: String,
    onMenuClick: () -> Unit,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val palette = MaterialTheme.colorScheme
    var accountExpanded by remember { mutableStateOf(false) }
    Surface(color = palette.background, shadowElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PocketHostBrandMark(Modifier.size(34.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text("PocketHost", color = palette.onSurface, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                Text("PRIVATE WEB HOSTING", color = palette.onSurfaceVariant,
                    fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            }
            Box {
                Surface(
                    color = palette.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.clickable { accountExpanded = true }
                        .semantics { contentDescription = "Open account and app menu" }
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = palette.onSurface, modifier = Modifier.size(18.dp))
                        Text(currentUserId, color = palette.onSurface, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = palette.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
                DropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                    DropdownMenuItem(
                        text = { Column {
                            Text("Signed in as", fontSize = 10.sp, color = palette.onSurfaceVariant)
                            Text(currentUserId, fontWeight = FontWeight.SemiBold)
                        } },
                        onClick = { accountExpanded = false; onNavigate("Profile") }
                    )
                    DropdownMenuItem(text = { Text("Profile & password") },
                        leadingIcon = { Icon(Icons.Outlined.Person, null) },
                        onClick = { accountExpanded = false; onNavigate("Profile") })
                    DropdownMenuItem(text = { Text("Fingerprint & security") },
                        leadingIcon = { Icon(Icons.Outlined.Security, null) },
                        onClick = { accountExpanded = false; onNavigate("Settings:General") })
                    DropdownMenuItem(text = { Text("App settings") },
                        leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                        onClick = { accountExpanded = false; onNavigate("Settings:General") })
                    DropdownMenuItem(text = { Text("Network & access") },
                        leadingIcon = { Icon(Icons.Outlined.Security, null) },
                        onClick = { accountExpanded = false; onNavigate("Settings:Network") })
                    DropdownMenuItem(text = { Text("About Pocket Host") },
                        leadingIcon = { Icon(Icons.Outlined.Info, null) },
                        onClick = { accountExpanded = false; onNavigate("Settings:About") })
                    DropdownMenuItem(text = { Text("Sign out") },
                        leadingIcon = { Icon(Icons.Outlined.Person, null) },
                        onClick = { accountExpanded = false; onSignOut() })
                }
            }
            IconButton(onClick = onMenuClick,
                modifier = Modifier.semantics { contentDescription = "Open app navigation" }) {
                Icon(Icons.Outlined.Menu, contentDescription = "Open app navigation", tint = palette.onSurface)
            }
        }
    }
}

internal data class PocketHostNavItem(val title: String, val subtitle: String, val icon: ImageVector, val destination: String)

private val pocketHostNavGroups = listOf(
    "OVERVIEW" to listOf(
        PocketHostNavItem("Dashboard", "Server health, traffic, logs and alerts", Icons.Outlined.Dashboard, "Dashboard")
    ),
    "HOSTING" to listOf(
        PocketHostNavItem("Websites & files", "Import and manage hosted websites and files", Icons.Outlined.FolderOpen, "Sites"),
        PocketHostNavItem("Workbench", "Edit drafts and preview changes", Icons.Outlined.Code, "Workbench"),
        PocketHostNavItem("Deployments", "Releases, snapshots and recovery", Icons.Outlined.Timeline, "Deployments"),
        PocketHostNavItem("Server settings", "Ports, limits and power behavior", Icons.Outlined.Settings, "Settings:Server")
    ),
    "NETWORK & ACCESS" to listOf(
        PocketHostNavItem("Devices", "Trusted devices and fleet access", Icons.Outlined.Memory, "Devices"),
        PocketHostNavItem("Network settings", "Ports, LAN and connectivity", Icons.Outlined.NetworkCheck, "Settings:Network"),
        PocketHostNavItem("Local access", "Local network and private access", Icons.Outlined.NetworkCheck, "Settings:Local access"),
        PocketHostNavItem("Remote control", "Remote administration connection", Icons.Outlined.Security, "Settings:Remote"),
        PocketHostNavItem("Cloudflare Tunnel", "Public tunnel connection", Icons.Outlined.Language, "Settings:Cloudflare"),
        PocketHostNavItem("Cloudflare Access", "Identity-aware access protection", Icons.Outlined.Security, "Settings:Cloudflare Access"),
        PocketHostNavItem("Relay Node", "Operate this device as a relay node", Icons.Outlined.NetworkCheck, "Settings:Relay node"),
        PocketHostNavItem("Relay Host", "Connect this host to a relay", Icons.Outlined.NetworkCheck, "Settings:Relay host"),
        PocketHostNavItem("Hostnames & access", "Domains, visitor access and policy", Icons.Outlined.Security, "Settings:Hostnames & access"),
        PocketHostNavItem("DNS management", "Manage DNS records and routing", Icons.Outlined.Language, "Settings:DNS management")
    ),
    "RUNTIMES" to listOf(
        PocketHostNavItem("Runtime overview", "Runtime health and resource controls", Icons.Outlined.Memory, "Settings:Runtimes"),
        PocketHostNavItem("Python runtime", "Configure and run Python apps", Icons.Outlined.Code, "Settings:Python"),
        PocketHostNavItem("Node.js runtime", "Configure and run Node.js apps", Icons.Outlined.Code, "Settings:Node.js")
    ),
    "SYSTEM" to listOf(
        PocketHostNavItem("Activity & logs", "Events and diagnostic exports", Icons.Outlined.Timeline, "Activity"),
        PocketHostNavItem("Learn", "Guides and operating limits", Icons.Outlined.School, "Learn"),
        PocketHostNavItem("Application settings", "Appearance and device preferences", Icons.Outlined.Settings, "Settings:General"),
        PocketHostNavItem("About PocketHost", "Version and platform details", Icons.Outlined.Info, "Settings:About")
    )
)

@Composable
internal fun PocketHostSidebar(
    selectedTab: String,
    selectedSection: String,
    operatingMode: String,
    onNavigate: (String) -> Unit
) {
    val palette = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PocketHostBrandMark(Modifier.size(42.dp))
            Column {
                Text("POCKET HOST", color = palette.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = 1.sp)
                Text("ASSAYFLUX", color = palette.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        pocketHostNavGroups.forEach { (groupTitle, groupItems) ->
            val visibleItems = groupItems.filter { it.destination != "Devices" || operatingMode == "COMMAND_CENTER_SERVER" }
            if (visibleItems.isNotEmpty()) {
                Text(groupTitle, color = palette.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 3.dp))
                visibleItems.forEach { item ->
                    val selected = when {
                        item.destination.startsWith("Settings:") -> selectedTab == "Settings" && selectedSection == item.destination.substringAfter(":")
                        else -> selectedTab == item.destination
                    }
                    NavigationDrawerItem(
                        label = { Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(item.title, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp)
                            Text(item.subtitle, fontSize = 10.sp, color = palette.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } },
                        selected = selected,
                        onClick = { onNavigate(item.destination) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Spacer(Modifier.padding(bottom = 4.dp))
        Text("Static hosting remains independent of optional runtimes and tunnel services.", color = palette.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}
