package com.assayflux.pockethost

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assayflux.pockethost.service.HostingSchedule
import java.time.ZoneId

/** Daily start/stop controls for the local website hosting service. */
@Composable
internal fun HostingScheduleCard(context: Context) {
    val initial = remember { HostingSchedule.settings(context) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var start by remember { mutableStateOf(initial.start) }
    var stop by remember { mutableStateOf(initial.stop) }
    var message by remember { mutableStateOf("Schedule is saved on this device.") }
    val palette = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AUTOMATIC HOSTING HOURS", color = palette.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 11.sp)
            Text("Turn your local website server on and off every day", color = palette.onSurface, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (enabled) "Schedule enabled" else "Schedule disabled", color = palette.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text("Uses device time · " + ZoneId.systemDefault().id, color = palette.onSurfaceVariant, fontSize = 12.sp)
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = start, onValueChange = { start = it.take(5) },
                    label = { Text("Server ON") }, placeholder = { Text("08:00") },
                    singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = stop, onValueChange = { stop = it.take(5) },
                    label = { Text("Server OFF") }, placeholder = { Text("22:00") },
                    singleLine = true, modifier = Modifier.weight(1f),
                )
            }
            Button(onClick = {
                runCatching { HostingSchedule.save(context, enabled, start.trim(), stop.trim()) }
                    .onSuccess {
                        message = if (enabled) "Daily schedule saved. Hosting will start at $start and stop at $stop."
                            else "Daily schedule disabled. Manual server controls remain available."
                    }
                    .onFailure { message = it.message ?: "Could not save hosting schedule." }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save hosting schedule") }
            Text(message, color = palette.onSurfaceVariant, fontSize = 12.sp)
            Text(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    "Android may delay alarms while idle unless exact-alarm access is allowed. The schedule is restored after reboot."
                else "The schedule is restored after reboot. Battery restrictions may delay background alarms.",
                color = palette.onSurfaceVariant, fontSize = 11.sp
            )
        }
    }
}
