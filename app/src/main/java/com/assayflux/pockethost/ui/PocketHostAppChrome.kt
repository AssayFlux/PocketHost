package com.assayflux.pockethost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PocketHostTopBar(selectedTab: String, hosting: Boolean) {
    val palette = MaterialTheme.colorScheme
    Surface(color = palette.surface, shadowElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = palette.primary
            ) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("AF", color = palette.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Pocket Host", color = palette.onSurface,
                    fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("ASSAYFLUX  /  $selectedTab", color = palette.onSurfaceVariant,
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (hosting) Color(0xFFE7F0E8) else palette.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape)
                        .background(if (hosting) Color(0xFF34764A) else palette.onSurfaceVariant))
                    Text(if (hosting) "Running" else "Stopped",
                        color = if (hosting) Color(0xFF285D3A) else palette.onSurfaceVariant,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun PocketHostNavigationBar(
    selectedTab: String,
    onDestinationSelected: (String) -> Unit
) {
    val palette = MaterialTheme.colorScheme
    NavigationBar(
        modifier = Modifier.imePadding(),
        containerColor = palette.surface,
        contentColor = palette.onSurface,
        tonalElevation = 3.dp
    ) {
        listOf("Dashboard", "Sites", "Activity", "Learn", "Settings").forEach { destination ->
            val symbol = when (destination) {
                "Dashboard" -> Icons.Outlined.Dashboard
                "Sites" -> Icons.Outlined.FolderOpen
                "Activity" -> Icons.Outlined.Timeline
                "Learn" -> Icons.Outlined.School
                else -> Icons.Outlined.Settings
            }
            NavigationBarItem(
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = destination },
                selected = selectedTab == destination,
                onClick = { onDestinationSelected(destination) },
                icon = { Icon(symbol, contentDescription = destination) },
                label = { Text(destination, fontSize = 11.sp, maxLines = 1) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = palette.onPrimaryContainer,
                    selectedTextColor = palette.primary,
                    indicatorColor = palette.primaryContainer,
                    unselectedIconColor = palette.onSurfaceVariant,
                    unselectedTextColor = palette.onSurfaceVariant
                )
            )
        }
    }
}
