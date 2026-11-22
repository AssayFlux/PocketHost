package com.assayflux.pockethost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.clickable
import androidx.compose.foundation.layout.navigationBars
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
    selectedTab: String,
    selectedSection: String,
    hosting: Boolean,
    onMenuClick: () -> Unit,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val palette = MaterialTheme.colorScheme
    val headerTitle = when (selectedTab) {
        "Dashboard" -> "Hosting"
        "Sites" -> "Websites"
        "Activity" -> "Activity"
        "Settings" -> when (selectedSection) {
            "Network" -> "Network & tunnels"
            "Server" -> "Server settings"
            "Remote" -> "Remote control"
            "Local access" -> "Local access"
            "Relay node" -> "Native Relay Node"
            "Relay host" -> "Relay host connection"
            "Cloudflare" -> "Cloudflare Tunnel"
            "Cloudflare Access" -> "Cloudflare Access"
            "DNS management" -> "DNS management"
            "Hostnames & access" -> "Hostnames & access"
            "Runtimes" -> "Runtimes"
            "Python" -> "Python runtime"
            "Node.js" -> "Node.js runtime"
            "About" -> "About Pocket Host"
            else -> "Settings"
        }
        else -> selectedTab
    }
    var accountExpanded by remember { mutableStateOf(false) }
    Surface(color = palette.background) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PocketHostBrandMark(Modifier.size(42.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("POCKET HOST", color = palette.onSurface, fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                Text(headerTitle, color = palette.onSurface, fontSize = 19.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(
                color = if (hosting) palette.primaryContainer else palette.surfaceVariant,
                shape = RoundedCornerShape(50)
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape)
                        .background(if (hosting) palette.primary else palette.outline))
                    Text(if (hosting) "RUNNING" else "OFFLINE",
                        color = if (hosting) palette.onPrimaryContainer else palette.onSurfaceVariant,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            IconButton(onClick = onMenuClick,
                modifier = Modifier.semantics { contentDescription = "Open app navigation" }) {
                Icon(Icons.Outlined.Menu, contentDescription = null, tint = palette.onSurface)
            }
            Box {
                IconButton(onClick = { accountExpanded = true },
                    modifier = Modifier.semantics { contentDescription = "Open account and app menu" }) {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = palette.onSurface)
                }
                DropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
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
        }
    }
}

@Composable
internal fun PocketHostBottomNavigation(
    selectedTab: String,
    selectedSection: String,
    onNavigate: (String) -> Unit
) {
    val palette = MaterialTheme.colorScheme
    val items = listOf(
        Triple("Home", "Home", Icons.Outlined.Dashboard),
        Triple("Hosting", "Dashboard", Icons.Outlined.Language),
        Triple("Sites", "Sites", Icons.Outlined.FolderOpen),
        Triple("Workbench", "Workbench", Icons.Outlined.Code),
        Triple("More", "Settings:General", Icons.Outlined.Settings)
    )
    Surface(color = palette.surface, tonalElevation = 3.dp) {
        androidx.compose.material3.NavigationBar(
            containerColor = palette.surface,
            contentColor = palette.onSurface,
            windowInsets = androidx.compose.foundation.layout.WindowInsets.navigationBars
        ) {
            items.forEach { (label, destination, icon) ->
                val selected = when (destination) {
                    "Settings:General" -> selectedTab == "Settings"
                    else -> selectedTab == destination
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onNavigate(destination) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (selected) palette.primary else palette.onSurfaceVariant
                    )
                    Text(
                        label,
                        color = if (selected) palette.primary else palette.onSurfaceVariant,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

internal data class PocketHostNavItem(val title: String, val subtitle: String, val icon: ImageVector, val destination: String)

private val pocketHostNavItems = listOf(
    PocketHostNavItem("Home", "Service overview and quick status", Icons.Outlined.Dashboard, "Home"),
    PocketHostNavItem("Hosting", "Start, stop and monitor the web server", Icons.Outlined.Language, "Dashboard"),
    PocketHostNavItem("Websites & files", "Import, edit, publish and recover sites", Icons.Outlined.FolderOpen, "Sites"),
    PocketHostNavItem("Workbench", "Edit site files and preview changes", Icons.Outlined.Code, "Workbench"),
    PocketHostNavItem("Deployments", "Production publish and rollback", Icons.Outlined.Code, "Deployments"),
    PocketHostNavItem("Devices", "Trusted devices and fleet administration", Icons.Outlined.Memory, "Devices"),
    PocketHostNavItem("Network & tunnels", "LAN access, Relay and Cloudflare", Icons.Outlined.NetworkCheck, "Settings:Network"),
    PocketHostNavItem("Runtimes", "Optional Python and Node.js services", Icons.Outlined.Code, "Settings:Runtimes"),
    PocketHostNavItem("Activity & logs", "Operational events and diagnostics", Icons.Outlined.Timeline, "Activity"),
    PocketHostNavItem("Learn", "Guides and operating limits", Icons.Outlined.School, "Learn"),
    PocketHostNavItem("Settings", "Appearance and device preferences", Icons.Outlined.Settings, "Settings:General"),
    PocketHostNavItem("About", "Version and platform information", Icons.Outlined.Info, "Settings:About")
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
        Text("WORKSPACE", color = palette.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 4.dp))
        pocketHostNavItems.filter { it.destination != "Devices" || operatingMode == "COMMAND_CENTER_SERVER" }.forEach { item ->
            val selected = when {
                item.destination == "Home" -> selectedTab == "Home"
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
        Spacer(Modifier.padding(bottom = 4.dp))
        Text("Static hosting remains independent of optional runtimes and tunnel services.", color = palette.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}
