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
    var selectedPage by remember { mutableStateOf("Overview") }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("Overview", "Schedule", "Traffic & capacity").forEach { page ->
            FilterChip(selected = selectedPage == page, onClick = { selectedPage = page },
                label = { Text(page, fontSize = 12.sp, maxLines = 1) })
        }
    }
    if (selectedPage == "Schedule") HostingScheduleCard(context)

    if (selectedPage == "Overview") {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.primary)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("HOSTING STATUS", color = palette.onPrimary.copy(alpha = 0.78f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(if (state.hosting) "Local hosting active" else "Stopped", color = palette.onPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (state.serviceStatus.length > 90) state.serviceStatus.take(87) + "…" else state.serviceStatus,
                    color = palette.onPrimary.copy(alpha = 0.9f),
                    fontSize = 13.sp
                )
                if (state.status.isNotBlank() && state.status != state.serviceStatus) {
                    Text(
                        if (state.status.length > 90) state.status.take(87) + "…" else state.status,
                        color = palette.onPrimary.copy(alpha = 0.76f),
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = {
                        if (state.hosting) {
                            actions.onStopHosting()
                            actions.onStatusChange("Stopping hosting service…")
                        } else if (state.notificationPermissionRequired) {
                            actions.onRequestNotificationPermission()
                        } else {
                            actions.onStartHosting()
                        }
                    },
                    enabled = state.hosting || !state.importing,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.surface, contentColor = palette.primary),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (state.hosting) "Stop hosting" else "Start hosting") }
            }
        }
    }

    if (selectedPage == "Overview") {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Your website", color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text(if (state.importedSummary.isBlank()) "No website deployed yet" else state.importedSummary,
                            color = palette.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(if (state.hosting) "LIVE" else "OFFLINE",
                        color = if (state.hosting) Color(0xFF2E7D32) else palette.onSurfaceVariant,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                val websitePort = state.portText.toIntOrNull() ?: 8080
                val websiteUrl = if (state.allowLan && !state.lanAddress.isNullOrBlank())
                    "http://${state.lanAddress}:$websitePort" else "http://127.0.0.1:$websitePort"
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = palette.background
                ) {
                    Text(websiteUrl, modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        color = palette.onSurface, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl)))
                            }.onFailure { actions.onStatusChange(it.message ?: "Could not open the website preview.") }
                        },
                        enabled = state.hosting,
                        modifier = Modifier.weight(1f)
                    ) { Text("Open preview") }
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Pocket Host website URL", websiteUrl))
                            actions.onStatusChange("Website address copied.")
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Copy address") }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape)
                        .background(if (state.tunnelStatus == "CONNECTED") Color(0xFF2E7D32) else palette.outline))
                    Spacer(Modifier.width(7.dp))
                    Text(if (state.tunnelStatus == "CONNECTED") "Cloudflare tunnel connected" else "Cloudflare tunnel not connected",
                        color = palette.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { actions.onOpenSites() }) { Text("Manage site") }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Live activity", color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text("Current server counters", color = palette.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Surface(
                        color = if (state.hosting) palette.primaryContainer else palette.surfaceVariant,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            if (state.hosting) "ACTIVE" else "IDLE",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = if (state.hosting) palette.onPrimaryContainer else palette.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(palette.background).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("Requests", color = palette.onSurfaceVariant, fontSize = 11.sp)
                        Text(state.requests.toString(), color = palette.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(palette.background).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("Active connections", color = palette.onSurfaceVariant, fontSize = 11.sp)
                        Text(state.activeConnections.toString(), color = palette.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(palette.background).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("Data served", color = palette.onSurfaceVariant, fontSize = 11.sp)
                        Text(formatBytes(state.bytesServed), color = palette.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(palette.background).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("Uptime", color = palette.onSurfaceVariant, fontSize = 11.sp)
                        Text(state.uptimeText.ifBlank { "—" }, color = palette.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "Today · ${state.todayRequests} requests · ${formatBytes(state.todayBytes)} transferred",
                    color = palette.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }

    if (selectedPage == "Traffic & capacity") {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Traffic", color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Requests", color = palette.onSurfaceVariant, fontSize = 14.sp)
                        Text(state.requests.toString(), color = palette.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Active", color = palette.onSurfaceVariant, fontSize = 14.sp)
                        Text(state.activeConnections.toString(), color = palette.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Transferred", color = palette.onSurfaceVariant, fontSize = 14.sp)
                        Text(formatBytes(state.bytesServed), color = palette.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("Uptime ${state.uptimeText}  ·  Today ${state.todayRequests} requests  ·  ${formatBytes(state.todayBytes)}", color = palette.onSurfaceVariant, fontSize = 13.sp)
                Text("Static website traffic only", color = palette.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }

    if (selectedPage == "Traffic & capacity") {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Capacity", color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text("Safe limit ${state.capacityInfo.optInt("safeConcurrency", 16)}  ·  Active ${state.capacityInfo.optLong("activeConnections")}  ·  Queued ${state.capacityInfo.optLong("queuedConnections")}", color = palette.onSurfaceVariant, fontSize = 13.sp)
                TextButton(onClick = { actions.onCapacityDetailsExpandedChange(!state.capacityDetailsExpanded) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.capacityDetailsExpanded) "Hide capacity controls" else "Show capacity controls and benchmark")
                }
                if (state.capacityDetailsExpanded) {
                Text("Admission budget: ${state.capacityInfo.optInt("effectiveConcurrencyLimit", 16)} active connections · ${state.capacityInfo.optInt("percent", 100)}% of ${if (state.capacityInfo.optLong("calibratedAt") > 0L) "measured" else "provisional"} concurrency ceiling",
                    color = palette.onSurfaceVariant, fontSize = 14.sp)
                Slider(value = state.capacityPercent, onValueChange = { actions.onCapacityPercentChange(it) }, valueRange = 1f..100f)
                Text("${state.capacityPercent.toInt()}% capacity profile", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = {
                    runCatching { CapacityController.setPercent(context, state.capacityPercent.toInt()) }
                        .onSuccess { actions.onCapacityInfoChange(CapacityController.snapshot(context)); actions.onStatusChange("Capacity profile applied.") }
                        .onFailure { actions.onStatusChange(it.message ?: "Capacity profile rejected.") }
                }, modifier = Modifier.fillMaxWidth()) { Text("Apply capacity profile") }
                OutlinedTextField(value = state.capacityDuration, onValueChange = { actions.onCapacityDurationChange(it.filter(Char::isDigit).take(4)) },
                    label = { Text("Schedule duration in minutes (max 1440)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = {
                    runCatching { CapacityController.schedule(context, 30, state.capacityDuration.toInt()) }
                        .onSuccess { actions.onCapacityInfoChange(it); actions.onStatusChange("30% capacity schedule started.") }
                        .onFailure { actions.onStatusChange(it.message ?: "Capacity schedule rejected.") }
                }, modifier = Modifier.fillMaxWidth()) { Text("Schedule 30% capacity") }
                OutlinedButton(onClick = {
                    runCatching { CapacityController.schedule(context, state.capacityPercent.toInt(), state.capacityDuration.toInt()) }
                        .onSuccess { actions.onCapacityInfoChange(it); actions.onStatusChange("Selected capacity profile scheduled.") }
                        .onFailure { actions.onStatusChange(it.message ?: "Capacity schedule rejected.") }
                }, modifier = Modifier.fillMaxWidth()) { Text("Schedule selected profile") }
                Button(onClick = {
                    runCatching { actions.onCapacityBenchmarkChange(CapacityLab.start(context, state.portText.toIntOrNull() ?: 8080)) }
                        .onFailure { actions.onStatusChange(it.message ?: "Benchmark could not start.") }
                }, enabled = state.hosting && state.capacityBenchmark.optString("state") != "RUNNING",
                    modifier = Modifier.fillMaxWidth()) { Text(if (state.capacityBenchmark.optString("state") == "RUNNING") "Benchmark running…" else "Measure safe capacity") }
                if (state.capacityBenchmark.optString("state") == "RUNNING" || state.capacityBenchmark.optString("state") == "CANCELLING") {
                    OutlinedButton(onClick = {
                        actions.onCapacityBenchmarkChange(CapacityLab.cancel())
                        actions.onStatusChange("Stopping the capacity benchmark safely…")
                    }, modifier = Modifier.fillMaxWidth()) { Text("Cancel benchmark") }
                }
                Text("Benchmark: ${state.capacityBenchmark.optString("state", "NOT_RUN")} · safe ceiling ${state.capacityInfo.optInt("safeConcurrency", 16)} · active ${state.capacityInfo.optLong("activeConnections")} · queued ${state.capacityInfo.optLong("queuedConnections")} · rate-limited ${state.capacityInfo.optLong("rateLimitedRequests")} · site-cap rejected ${state.capacityInfo.optLong("siteLimitRejections")} · rejected ${state.capacityInfo.optLong("rejectedConnections")}",
                    color = palette.onSurfaceVariant, fontSize = 14.sp)
                val benchmarkLevels = state.capacityBenchmark.optJSONArray("levels")
                val chartRows = (0 until (benchmarkLevels?.length() ?: 0)).mapNotNull { index ->
                    benchmarkLevels?.optJSONObject(index)?.let {
                        Triple(it.optInt("concurrency"), it.optLong("p95LatencyMs"), if (it.has("p99LatencyMs")) it.optLong("p99LatencyMs") else null)
                    }
                }
                if (chartRows.isNotEmpty()) {
                    Text("P95 response latency by concurrency", color = palette.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Canvas(Modifier.fillMaxWidth().height(112.dp)) {
                        val maxLatency = maxOf(750L, chartRows.maxOf { it.second }).toFloat()
                        val rowHeight = size.height / chartRows.size
                        chartRows.forEachIndexed { index, row ->
                            val top = index * rowHeight + rowHeight * 0.2f
                            val barWidth = size.width * (row.second.toFloat() / maxLatency).coerceIn(0f, 1f)
                            drawRect(color = palette.primary, topLeft = Offset(0f, top), size = Size(barWidth, rowHeight * 0.48f))
                            drawLine(color = palette.onSurface.copy(alpha = 0.55f),
                                start = Offset(size.width * (750f / maxLatency), top - 2f),
                                end = Offset(size.width * (750f / maxLatency), top + rowHeight * 0.7f), strokeWidth = 2f)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chartRows.forEach { row ->
                            FilterChip(selected = state.selectedCapacityConcurrency == row.first,
                                onClick = { actions.onSelectedCapacityConcurrencyChange(if (state.selectedCapacityConcurrency == row.first) -1 else row.first) },
                                label = { Text("${row.first}x", fontSize = 12.sp) })
                        }
                    }
                    val selectedPoint = chartRows.firstOrNull { it.first == state.selectedCapacityConcurrency }
                    Text(selectedPoint?.let { point -> "Concurrency ${point.first} · p95 ${point.second} ms · ${point.third?.let { p99 -> "p99 $p99 ms" } ?: "p99 not recorded"}" }
                        ?: "Select a concurrency point to inspect p95 and p99 latency. Bars show p95 milliseconds; marker = 750 ms benchmark threshold.",
                        color = palette.onSurfaceVariant, fontSize = 14.sp)
                }
                Text("Device health · ${state.deviceTelemetry.optString("thermalLabel", "UNKNOWN")}  ·  ${state.deviceTelemetry.optString("networkTransport", "Network unknown")}  ·  ${if (state.deviceTelemetry.isNull("batteryPercent")) "Battery unknown" else state.deviceTelemetry.optInt("batteryPercent").toString() + "% battery"}", color = palette.onSurfaceVariant, fontSize = 13.sp)
                Text("App RAM ${formatBytes(state.deviceTelemetry.optLong("processPssKb") * 1024L)}  ·  Free storage ${formatBytes(state.deviceTelemetry.optLong("storageAvailableBytes"))}", color = palette.onSurfaceVariant, fontSize = 12.sp)
                OutlinedButton(onClick = { actions.onExportDailyReport() },
                    enabled = DailyOperationsReport.list(context).isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text("Export latest daily report")
                }
                }
            }
        }
    }

}
