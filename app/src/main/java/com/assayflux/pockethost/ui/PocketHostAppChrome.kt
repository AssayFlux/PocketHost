package com.assayflux.pockethost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
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
    hosting: Boolean,
    onMenuClick: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val palette = MaterialTheme.colorScheme
    var accountExpanded by remember { mutableStateOf(false) }
    Surface(color = palette.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(13.dp), color = palette.primary) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("AF", color = palette.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Pocket Host", color = palette.onSurface, fontSize = 22.sp, fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold)
                    Text("ASSAYFLUX · YOUR DEVICE, YOUR CONTROL", color = palette.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Surface(color = palette.surface, shape = RoundedCornerShape(17.dp), shadowElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMenuClick, modifier = Modifier.semantics { contentDescription = "Open app navigation" }) {
                        Icon(Icons.Outlined.Menu, contentDescription = null, tint = palette.primary)
                    }
                    Column(Modifier.weight(1f).padding(start = 5.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(if (selectedTab == "Home") "Home" else selectedTab, color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(if (hosting) palette.primary else palette.outline))
                            Text(if (hosting) "Hosting running" else "Hosting stopped", color = palette.onSurfaceVariant, fontSize = 10.sp)
                        }
                    }
                    Box {
                        IconButton(onClick = { accountExpanded = true }, modifier = Modifier.semantics { contentDescription = "Open account and app menu" }) {
                            Icon(Icons.Outlined.Person, contentDescription = null, tint = palette.primary)
                        }
                        DropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                            DropdownMenuItem(text = { Text("App settings") }, leadingIcon = { Icon(Icons.Outlined.Settings, null) }, onClick = { accountExpanded = false; onNavigate("Settings:General") })
                            DropdownMenuItem(text = { Text("Security settings") }, leadingIcon = { Icon(Icons.Outlined.Security, null) }, onClick = { accountExpanded = false; onNavigate("Settings:Network") })
                            DropdownMenuItem(text = { Text("About Pocket Host") }, leadingIcon = { Icon(Icons.Outlined.Info, null) }, onClick = { accountExpanded = false; onNavigate("Settings:About") })
                        }
                    }
                }
            }
        }
    }
}

internal data class PocketHostNavItem(val title: String, val subtitle: String, val icon: ImageVector, val destination: String)

private val pocketHostNavItems = listOf(
    PocketHostNavItem("Home", "Service overview and device centre", Icons.Outlined.Dashboard, "Home"),
    PocketHostNavItem("Devices", "Verified devices, roles and revocation", Icons.Outlined.Memory, "Devices"),
    PocketHostNavItem("Hosting", "Server lifecycle, port and local access", Icons.Outlined.Language, "Dashboard"),
    PocketHostNavItem("Websites & files", "Projects, import, publish and rollback", Icons.Outlined.FolderOpen, "Sites"),
    PocketHostNavItem("Workbench", "Edit website code, review changes and commit drafts", Icons.Outlined.Code, "Workbench"),
    PocketHostNavItem("Relay & networking", "Cloudflare tunnel and network settings", Icons.Outlined.NetworkCheck, "Settings:Network"),
    PocketHostNavItem("Python & Node runtimes", "Runtime projects and trusted-code controls", Icons.Outlined.Code, "Settings:Runtimes"),
    PocketHostNavItem("Security & access", "Biometric app lock and visitor access controls", Icons.Outlined.Security, "Settings:Network"),
    PocketHostNavItem("Capacity & performance", "Live admission, capacity and device metrics", Icons.Outlined.Memory, "Dashboard"),
    PocketHostNavItem("Backups & recovery", "Encrypted export and restore operations", Icons.Outlined.Backup, "Sites"),
    PocketHostNavItem("Activity & logs", "Events, diagnostics and operational reports", Icons.Outlined.Timeline, "Activity"),
    PocketHostNavItem("Learn", "Guides, workflows and limitations", Icons.Outlined.School, "Learn"),
    PocketHostNavItem("Settings", "Device preferences and app configuration", Icons.Outlined.Settings, "Settings:General"),
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
            Surface(color = palette.primary, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("AF", color = palette.onPrimary, fontWeight = FontWeight.Bold) }
            }
            Column {
                Text("Pocket Host", color = palette.onSurface, fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
