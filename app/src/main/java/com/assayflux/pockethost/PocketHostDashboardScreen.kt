package com.assayflux.pockethost

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.Manifest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.os.Build
import org.json.JSONObject
import com.assayflux.pockethost.diagnostics.CapacityController
import com.assayflux.pockethost.diagnostics.CapacityLab
import com.assayflux.pockethost.diagnostics.DailyOperationsReport

internal data class PocketHostDashboardState(
    val hosting: Boolean,
    val importing: Boolean,
    val status: String,
    val serviceStatus: String,
    val importedSummary: String,
    val allowLan: Boolean,
    val lanAddress: String?,
    val portText: String,
    val tunnelStatus: String,
    val requests: Long,
    val activeConnections: Long,
    val bytesServed: Long,
    val uptimeText: String,
    val todayRequests: Long,
    val todayBytes: Long,
    val capacityInfo: JSONObject,
    val capacityDetailsExpanded: Boolean,
    val capacityPercent: Float,
    val capacityDuration: String,
    val capacityBenchmark: JSONObject,
    val deviceTelemetry: JSONObject,
    val selectedCapacityConcurrency: Int,
    val dailyReports: List<JSONObject>,
    val recentEvents: List<com.assayflux.pockethost.diagnostics.OperationalLog.Entry>,
    val notificationPermissionRequired: Boolean
)

internal data class PocketHostDashboardActions(
    val onStopHosting: () -> Unit,
    val onStartHosting: () -> Unit,
    val onRequestNotificationPermission: () -> Unit,
    val onStatusChange: (String) -> Unit,
    val onOpenSites: () -> Unit,
    val onCapacityDetailsExpandedChange: (Boolean) -> Unit,
    val onCapacityPercentChange: (Float) -> Unit,
    val onCapacityDurationChange: (String) -> Unit,
    val onCapacityInfoChange: (JSONObject) -> Unit,
    val onCapacityBenchmarkChange: (JSONObject) -> Unit,
    val onSelectedCapacityConcurrencyChange: (Int) -> Unit,
    val onExportDailyReport: () -> Unit
)

@Composable
internal fun PocketHostDashboardScreen(
    context: Context,
    state: PocketHostDashboardState,
    actions: PocketHostDashboardActions
) {
    val palette = MaterialTheme.colorScheme
    val positive = Color(0xFF63B94F)
    val negative = Color(0xFFB44738)
    val websitePort = state.portText.toIntOrNull() ?: 8080
    val websiteUrl = if (state.allowLan && !state.lanAddress.isNullOrBlank())
        "http://${state.lanAddress}:$websitePort" else "http://127.0.0.1:$websitePort"
    val reports = state.dailyReports.associateBy { it.optString("date") }
    val today = java.time.LocalDate.now()
    val lastSevenDays = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val chartValues = lastSevenDays.map { day -> day to (reports[day.toString()]?.optLong("totalRequests") ?: 0L) }
    val maximum = (chartValues.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    val alerts = state.recentEvents.filter { it.level.equals("ERROR", true) || it.level.equals("WARN", true) }.take(4)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Dashboard", color = palette.onSurface, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Text("Server health and recent activity", color = palette.onSurfaceVariant)
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = if (state.hosting) Color(0xFFE3EEDB) else Color(0xFFF4E4DD))) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("WEB SERVER", color = if (state.hosting) Color(0xFF3E6338) else Color(0xFF8D3D32),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                        Text(if (state.hosting) "Server is running" else "Server is stopped",
                            color = if (state.hosting) Color(0xFF31572D) else Color(0xFF8F382D),
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(if (state.hosting) "Your hosting service is online." else "Start the server when you are ready to host.",
                            color = if (state.hosting) Color(0xFF496B43) else Color(0xFF86564C),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Surface(color = if (state.hosting) positive else negative, shape = CircleShape) {
                        Box(Modifier.size(16.dp))
                    }
                }
                Button(onClick = {
                    if (state.hosting) {
                        actions.onStopHosting()
                        actions.onStatusChange("Stopping hosting service…")
                    } else if (state.notificationPermissionRequired) actions.onRequestNotificationPermission()
                    else actions.onStartHosting()
                }, enabled = state.hosting || !state.importing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.hosting) negative else Color(0xFF557B4B),
                        contentColor = Color.White
                    ), modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.hosting) "Stop server" else "Start server")
                }
                if (state.serviceStatus.isNotBlank()) Text(state.serviceStatus,
                    color = if (state.hosting) Color(0xFF496B43) else Color(0xFF86564C),
                    fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = palette.surface),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("UPTIME", color = palette.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(state.uptimeText.ifBlank { "—" }, color = palette.onSurface,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = palette.surface),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ACTIVE CONNECTIONS", color = palette.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(state.activeConnections.toString(), color = palette.onSurface,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = palette.surface),
            shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Traffic", color = palette.onSurface, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold)
                        Text("Requests per day · last 7 days", color = palette.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Text(state.requests.toString(), color = palette.primary, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth().height(126.dp), horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                    chartValues.forEach { (day, value) ->
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Text(value.toString(), color = palette.onSurfaceVariant, fontSize = 9.sp, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth(0.72f)
                                .height((if (value == 0L) 3f else (value.toFloat() / maximum.toFloat() * 78f).coerceAtLeast(5f)).dp)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(Color(0xFF76956A)))
                            Spacer(Modifier.height(5.dp))
                            Text(day.dayOfMonth.toString(), color = palette.onSurfaceVariant, fontSize = 10.sp)
                        }
                    }
                }
                Text("Date · ${today.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${today.year}",
                    color = palette.onSurfaceVariant, fontSize = 10.sp)
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = palette.surface),
            shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Notifications", color = palette.onSurface, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                if (alerts.isEmpty()) Text("No warnings or errors to report.", color = palette.onSurfaceVariant, fontSize = 13.sp)
                else alerts.forEach { event ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                        Surface(color = if (event.level.equals("ERROR", true)) negative else Color(0xFFB87832),
                            shape = RoundedCornerShape(6.dp)) {
                            Text(event.level, color = Color.White, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(event.event, color = palette.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(event.message, color = palette.onSurfaceVariant, fontSize = 11.sp, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = palette.surface),
            shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Recent logs", color = palette.onSurface, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                if (state.recentEvents.isEmpty()) Text("No server events have been recorded yet.",
                    color = palette.onSurfaceVariant, fontSize = 13.sp)
                else state.recentEvents.take(5).forEach { event ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(event.level, color = when (event.level.uppercase()) {
                                "ERROR" -> negative
                                "WARN" -> Color(0xFFB87832)
                                else -> palette.primary
                            }, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text(event.event, color = palette.onSurface, fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(event.message, color = palette.onSurfaceVariant, fontSize = 11.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = palette.surface),
            shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Local address", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                Text(websiteUrl, color = palette.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("PocketHost address", websiteUrl))
                        actions.onStatusChange("Server address copied.")
                    }, modifier = Modifier.weight(1f)) { Text("Copy address") }
                    OutlinedButton(onClick = actions.onOpenSites, modifier = Modifier.weight(1f)) { Text("Manage websites") }
                }
            }
        }
    }
}
