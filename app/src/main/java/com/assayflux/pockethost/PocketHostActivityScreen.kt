package com.assayflux.pockethost

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assayflux.pockethost.diagnostics.OperationalLog
import java.time.LocalDate

@Composable
internal fun PocketHostActivityScreen(
    events: List<OperationalLog.Entry>,
    filterMode: String,
    onFilterModeChange: (String) -> Unit,
    filterText: String,
    onFilterTextChange: (String) -> Unit,
    showClearConfirmation: Boolean,
    onShowClearConfirmationChange: (Boolean) -> Unit,
    onExportLogs: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onClearLogs: () -> Unit
) {
    val palette = MaterialTheme.colorScheme

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("RECENT OPERATIONS", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("All", "Errors", "Today").forEach { filter ->
                                FilterChip(selected = filterMode == filter, onClick = { onFilterModeChange(filter) }, label = { Text(filter) })
                            }
                        }
                        OutlinedTextField(
                            value = filterText,
                            onValueChange = onFilterTextChange,
                            label = { Text("Filter events, levels or messages") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val filteredEvents = events.filter { event ->
                            val matchesText = logFilter.isBlank() ||
                                event.level.contains(logFilter, ignoreCase = true) ||
                                event.event.contains(logFilter, ignoreCase = true) ||
                                event.message.contains(logFilter, ignoreCase = true)
                            val matchesMode = when (logFilterMode) {
                                "Errors" -> event.level.equals("ERROR", ignoreCase = true)
                                "Today" -> event.timestamp.startsWith(LocalDate.now().toString())
                                else -> true
                            }
                            matchesText && matchesMode
                        }.take(100)
                        if (filteredEvents.isEmpty()) {
                            Text(if (events.isEmpty()) "No operational events recorded yet." else "No events match this filter.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                        } else {
                            filteredEvents.forEach { event ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(event.level, color = if (event.level == "ERROR") Color(0xFF9C3D35) else palette.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(event.event.replace('_', ' '), color = palette.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.weight(1f))
                                        Text(event.timestamp.take(19).replace('T', ' '), color = palette.onSurfaceVariant, fontSize = 14.sp)
                                    }
                                    if (event.message.isNotBlank()) Text(event.message, color = palette.onSurfaceVariant, fontSize = 14.sp)
                                }
                            }
                        }
                        Text("App-private rotating log · credentials and bearer tokens are redacted.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                        OutlinedButton(
                            onClick = { onExportLogs() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Export redacted logs") }
                        OutlinedButton(
                            onClick = { onExportDiagnostics() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Export diagnostics bundle") }
                        OutlinedButton(onClick = { onShowClearConfirmationChange(true) },
                            enabled = recentEvents.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text("Clear logs")
                        }
                        if (showClearLogsConfirmation) {
                            AlertDialog(
                                onDismissRequest = { onShowClearConfirmationChange(false) },
                                title = { Text("Clear operational logs?") },
                                text = { Text("This permanently removes the app's retained operational event log files from this device. Export them first if you need a copy.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        onClearLogs()
                                        onShowClearConfirmationChange(false)
                                    }) { Text("Clear logs") }
                                },
                                dismissButton = { TextButton(onClick = { onShowClearConfirmationChange(false) }) { Text("Cancel") } }
                            )
                        }
                    }
                }

}
