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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PocketHostTopBar(selectedTab: String, hosting: Boolean) {
    val palette = MaterialTheme.colorScheme
    Surface(color = palette.surface, tonalElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
                color = palette.primary
            ) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("AF", color = palette.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Pocket Host", color = palette.onSurface,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("ASSAYFLUX  ·  $selectedTab", color = palette.onSurfaceVariant,
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (hosting) Color(0xFFE8F3E9) else palette.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape)
                        .background(if (hosting) Color(0xFF2E7D32) else palette.onSurfaceVariant))
                    Text(if (hosting) "Live" else "Offline",
                        color = if (hosting) Color(0xFF246B35) else palette.onSurfaceVariant,
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
    NavigationBar(modifier = Modifier.imePadding(), containerColor = palette.surface) {
        listOf("Dashboard", "Sites", "Activity", "Learn", "Settings").forEach { destination ->
            val symbol = when (destination) {
                "Dashboard" -> Icons.Outlined.Dashboard
                "Sites" -> Icons.Outlined.FolderOpen
                "Activity" -> Icons.Outlined.Timeline
                "Learn" -> Icons.Outlined.School
                else -> Icons.Outlined.Settings
            }
            NavigationBarItem(
                // Keep a stable, explicit accessible name on the whole destination
                // control. Some platform accessibility bridges flatten the icon and
                // visible label differently, so the parent must remain discoverable.
                modifier = Modifier.semantics { contentDescription = destination },
                selected = selectedTab == destination,
                onClick = { onDestinationSelected(destination) },
                icon = { Icon(symbol, contentDescription = destination) },
                label = { Text(destination, fontSize = 12.sp, maxLines = 1) },
                alwaysShowLabel = true
            )
        }
    }
}
