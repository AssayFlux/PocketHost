package com.assayflux.pockethost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assayflux.pockethost.ui.theme.PocketHostPalettes

@Composable
internal fun PocketHostSettingsSectionNavigation(
    selectedSection: String,
    onSectionSelected: (String) -> Unit
) {
    val sections = listOf(
        "General", "Server", "Remote", "Local access",
        "Network", "Relay node", "Relay host", "Cloudflare", "Cloudflare Access", "DNS management", "Hostnames & access",
        "Runtimes", "Python", "Node.js", "About"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                "SETTINGS PAGES",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            sections.chunked(2).forEach { rowSections ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowSections.forEach { section ->
                        FilterChip(
                            selected = selectedSection == section,
                            onClick = { onSectionSelected(section) },
                            label = { Text(section, fontSize = 12.sp, maxLines = 2) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowSections.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

internal data class PocketHostGeneralSettingsState(
    val appearanceMode: String,
    val selectedTheme: String,
    val biometricUnlockEnabled: Boolean,
    val autoStartOnBoot: Boolean
)

internal data class PocketHostGeneralSettingsActions(
    val onAppearanceModeChange: (String) -> Unit,
    val onThemeChange: (String) -> Unit,
    val onBiometricUnlockChange: (Boolean) -> Unit,
    val onAutoStartOnBootChange: (Boolean) -> Unit,
    val onBatteryOptimizationSettings: () -> Unit
)

/** Device-level preferences kept separate from server, network and runtime configuration. */
@Composable
internal fun PocketHostSettingsScreen(
    state: PocketHostGeneralSettingsState,
    actions: PocketHostGeneralSettingsActions
) {
    val palette = MaterialTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("APPEARANCE", color = palette.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                Text("Make Pocket Host feel like yours", color = palette.onSurface, style = MaterialTheme.typography.titleLarge)
                Text("Choose how the app follows your device and select a classic colour collection.",
                    color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text("Display mode", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("System", "Light", "Dark").forEach { mode ->
                    FilterChip(selected = state.appearanceMode == mode,
                        onClick = { actions.onAppearanceModeChange(mode) },
                        label = { Text(mode) })
                }
            }
            Text("Classic themes", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
            PocketHostPalettes.names.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { theme ->
                        val selected = state.selectedTheme == theme
                        FilterChip(
                            selected = selected,
                            onClick = { actions.onThemeChange(theme) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Box(Modifier.size(13.dp).clip(CircleShape).background(themeAccent(theme)))
                                    Text(theme)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant),
                shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(palette.primary))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Pocket Host", color = palette.onSurface, style = MaterialTheme.typography.titleLarge)
                        Text("${state.selectedTheme} · ${state.appearanceMode} appearance",
                            color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Biometric app unlock", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                Text("Require a strong biometric when Pocket Host is opened; Android 11+ can also use a secure device credential. This protects the local app UI, not Owner/Operator API authentication.",
                    color = palette.onSurfaceVariant, fontSize = 13.sp)
            }
            Switch(checked = state.biometricUnlockEnabled, onCheckedChange = actions.onBiometricUnlockChange)
        }
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-start hosting on boot", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("Starts the local hosting service after device restart when Android permits it.",
                        color = palette.onSurfaceVariant)
                }
                Switch(checked = state.autoStartOnBoot, onCheckedChange = actions.onAutoStartOnBootChange)
            }
            OutlinedButton(onClick = actions.onBatteryOptimizationSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Battery optimization settings")
            }
        }
    }
}

private fun themeAccent(name: String): Color = when (name) {
    "Midnight" -> Color(0xFFD5A84B)
    "Forest" -> Color(0xFF52734F)
    "Burgundy" -> Color(0xFF873F4C)
    "Ocean" -> Color(0xFF286D7A)
    "Graphite" -> Color(0xFFC6A15B)
    "Sandstone" -> Color(0xFF9A6B35)
    "Lavender" -> Color(0xFF75618F)
    "Copper" -> Color(0xFF9A5838)
    "Arctic" -> Color(0xFF426F91)
    else -> Color(0xFFC29B38)
}
