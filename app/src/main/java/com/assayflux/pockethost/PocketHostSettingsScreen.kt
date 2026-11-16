package com.assayflux.pockethost

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PocketHostSettingsSectionNavigation(
    selectedSection: String,
    onSectionSelected: (String) -> Unit
) {
    val palette = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("General", "Network", "Runtimes", "About").forEach { section ->
            FilterChip(
                selected = selectedSection == section,
                onClick = { onSectionSelected(section) },
                label = { Text(section, fontSize = 12.sp) }
            )
        }
    }
}

internal data class PocketHostGeneralSettingsState(
    val darkTheme: Boolean,
    val biometricUnlockEnabled: Boolean,
    val autoStartOnBoot: Boolean
)

internal data class PocketHostGeneralSettingsActions(
    val onDarkThemeChange: (Boolean) -> Unit,
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dark theme", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                Text("Use the darker Pocket Host appearance.", color = palette.onSurfaceVariant)
            }
            Switch(checked = state.darkTheme, onCheckedChange = actions.onDarkThemeChange)
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Biometric app unlock", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                Text("Require a strong biometric when Pocket Host is opened; Android 11+ can also use a secure device credential. This protects the local app UI, not Owner/Operator API authentication.",
                    color = palette.onSurfaceVariant, fontSize = 13.sp)
            }
            Switch(checked = state.biometricUnlockEnabled, onCheckedChange = actions.onBiometricUnlockChange)
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-start hosting on boot", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("Starts the local hosting service after device restart when Android permits it.", color = palette.onSurfaceVariant)
                }
                Switch(checked = state.autoStartOnBoot, onCheckedChange = actions.onAutoStartOnBootChange)
            }
            OutlinedButton(onClick = actions.onBatteryOptimizationSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Battery optimization settings")
            }
        }
    }
}
