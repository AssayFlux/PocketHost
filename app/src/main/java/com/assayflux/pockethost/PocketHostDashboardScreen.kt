package com.assayflux.pockethost

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import org.json.JSONObject
import com.assayflux.pockethost.diagnostics.CapacityController
import com.assayflux.pockethost.diagnostics.CapacityLab
import com.assayflux.pockethost.diagnostics.DailyOperationsReport

internal data class PocketHostDashboardState(
    val hosting: Boolean,
    val importing: Boolean,
    val status: String,
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

                if (pendingPairingCode != null) {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = palette.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("CONSOLE PAIRING REQUEST", color = palette.onSurface, fontWeight = FontWeight.Bold)
                            Text("Code: $pendingPairingCode", color = palette.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("Only approve this if you initiated pairing from your own trusted browser.", color = palette.onSurfaceVariant)
                            Button(onClick = {
                                val approved = ManagementApiServer.approvePairing(pendingPairingCode.orEmpty())
                                if (approved) { state.status = "Management console pairing approved."; pendingPairingCode = null }
                                else state.status = "Pairing request expired. Start pairing again."
                            }) { Text("Approve console pairing") }
                        }
                    }
                }
                }
                if (selectedTab == "Settings") {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("General", "Network", "Runtimes", "About").forEach { section ->
                            FilterChip(
                                selected = settingsSection == section,
                                onClick = { settingsSection = section },
                                label = { Text(section, fontSize = 12.sp) }
                            )
                        }
                    }
                }
                if (selectedTab == "Settings" && settingsSection == "General") {
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
                            Switch(checked = darkTheme, onCheckedChange = { enabled ->
                                darkTheme = enabled
                                context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit().putBoolean("dark_theme", enabled).apply()
                            })
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
                                Text("Require a strong biometric when Pocket Host is opened; Android 11+ can also use a secure device credential. This protects the local app UI, not Owner/Operator API authentication.", color = palette.onSurfaceVariant, fontSize = 13.sp)
                            }
                            Switch(checked = biometricUnlockEnabled, onCheckedChange = { enabled ->
                                if (!enabled) {
                                    biometricUnlockEnabled = false
                                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                        .putBoolean("biometric_unlock_enabled", false).apply()
                                    state.status = "Biometric app unlock disabled."
                                } else {
                                    val activity = context as? MainActivity
                                    if (activity == null) {
                                        state.status = "Could not open biometric confirmation."
                                    } else {
                                        activity.confirmBiometricUnlock { success ->
                                            if (success) {
                                                biometricUnlockEnabled = true
                                                context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                                    .putBoolean("biometric_unlock_enabled", true).apply()
                                                state.status = "Biometric app unlock enabled for the next app launch."
                                            } else {
                                                state.status = "Biometric confirmation was not completed; the setting remains off."
                                            }
                                        }
                                    }
                                }
                            })
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
                                    Text("Auto-start state.hosting on boot", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                                    Text("Starts the local state.hosting service after device restart when Android permits it.", color = palette.onSurfaceVariant)
                                }
                                Switch(checked = autoStartOnBoot, onCheckedChange = { enabled ->
                                    autoStartOnBoot = enabled
                                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                        .putBoolean("auto_start_on_boot", enabled).apply()
                                })
                            }
                            OutlinedButton(onClick = {
                                runCatching {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }.onFailure { state.status = it.message ?: "Could not open battery optimization settings." }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Battery optimization settings") }
                        }
                    }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !advancedMode, onClick = { advancedMode = false }, label = { Text("Simple") })
                    FilterChip(selected = advancedMode, onClick = { advancedMode = true }, label = { Text("Advanced") })
                }
                if (advancedMode) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = palette.surface)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("SERVER CONFIGURATION", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = state.portText,
                                onValueChange = { value -> if (value.length <= 5 && value.all(Char::isDigit)) state.portText = value },
                                label = { Text("HTTP port") },
                                enabled = !state.hosting,
                                singleLine = true
                            )
                            OutlinedButton(
                                onClick = {
                                    val port = state.portText.toIntOrNull()
                                    if (port == null || port !in 1024..65535) {
                                        state.status = "Choose a port between 1024 and 65535."
                                    } else {
                                        context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                            .putInt("port", port).apply()
                                        state.status = "Server port saved. Start state.hosting to apply it."
                                    }
                                },
                                enabled = !state.hosting
                            ) { Text("Save server settings") }
                            Text("Engine limits: 16 concurrent connections · 50 MiB maximum file · two Tokio workers.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Keep CPU awake while state.hosting", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                                    Text("Optional screen-off reliability aid. Uses more battery and cannot override every OEM network policy.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                                }
                                Switch(
                                    checked = keepAwake,
                                    onCheckedChange = { enabled ->
                                        keepAwake = enabled
                                        context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                            .putBoolean("keep_awake", enabled).apply()
                                        state.status = if (enabled) "CPU wake lock will be used the next time state.hosting starts." else "CPU wake lock disabled for the next state.hosting session."
                                    },
                                    enabled = !state.hosting
                                )
                            }
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = palette.surface)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("REMOTE CONTROL PLANE", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            Text(if (remoteControlConfigured) "Device is configured for outbound remote management." else "Connect this phone to your independently hosted PocketHost backend.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                            OutlinedTextField(
                                value = controlPlaneEndpoint,
                                onValueChange = { controlPlaneEndpoint = it },
                                label = { Text("Control-plane HTTPS URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = controlPlaneToken,
                                onValueChange = { controlPlaneToken = it },
                                label = { Text("One-time device token") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    runCatching {
                                        RemoteControlAgent.configure(context, controlPlaneEndpoint, controlPlaneToken)
                                        remoteControlConfigured = true
                                        controlPlaneToken = ""
                                        if (state.hosting) RemoteControlAgent.start(context)
                                        state.status = if (state.hosting) "Remote control connected. This phone will poll the backend while state.hosting is active." else "Remote connection saved securely. The agent starts when state.hosting starts."
                                    }.onFailure { state.status = it.message ?: "Could not configure remote control." }
                                }, enabled = controlPlaneEndpoint.isNotBlank() && controlPlaneToken.length >= 32) {
                                    Text("Save remote connection")
                                }
                                OutlinedButton(onClick = {
                                    RemoteControlAgent.clear(context)
                                    remoteControlConfigured = false
                                    controlPlaneToken = ""
                                    state.status = "Remote control credentials removed from this device."
                                }, enabled = remoteControlConfigured) { Text("Disconnect") }
                            }
                            Text("The device makes outbound HTTPS state.requests only. The backend cannot open inbound ports or execute arbitrary shell commands on this phone.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                }

                }
                if (selectedTab == "Dashboard") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.primary)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("HOSTING STATUS", color = palette.onPrimary.copy(alpha = 0.78f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(if (state.hosting) "Local state.hosting active" else "Stopped", color = palette.onPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (state.status.length > 90) state.status.take(87) + "…" else state.status, color = palette.onPrimary.copy(alpha = 0.82f), fontSize = 13.sp)
                        Button(
                            onClick = {
                                if (state.hosting) {
                                    actions.onStopHosting()
                                    state.status = "Stopping state.hosting service…"
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    actions.onRequestNotificationPermission()
                                } else {
                                    actions.onStartHosting()
                                }
                            },
                            enabled = state.hosting || !state.importing,
                            colors = ButtonDefaults.buttonColors(containerColor = palette.surface, contentColor = palette.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (state.hosting) "Stop state.hosting" else "Start state.hosting") }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
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
                            "http://$state.lanAddress:$websitePort" else "http://127.0.0.1:$websitePort"
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
                    shape = RoundedCornerShape(12.dp),
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
                        Text("Uptime $state.uptimeText  ·  Today $state.todayRequests state.requests  ·  ${formatBytes(state.todayBytes)}", color = palette.onSurfaceVariant, fontSize = 13.sp)
                        Text("Static website traffic only", color = palette.onSurfaceVariant, fontSize = 12.sp)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Capacity", color = palette.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text("Safe limit ${state.capacityInfo.optInt("safeConcurrency", 16)}  ·  Active ${state.capacityInfo.optLong("state.activeConnections")}  ·  Queued ${state.capacityInfo.optLong("queuedConnections")}", color = palette.onSurfaceVariant, fontSize = 13.sp)
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
                        Text("Benchmark: ${state.capacityBenchmark.optString("state", "NOT_RUN")} · safe ceiling ${state.capacityInfo.optInt("safeConcurrency", 16)} · active ${state.capacityInfo.optLong("state.activeConnections")} · queued ${state.capacityInfo.optLong("queuedConnections")} · rate-limited ${state.capacityInfo.optLong("rateLimitedRequests")} · site-cap rejected ${state.capacityInfo.optLong("siteLimitRejections")} · rejected ${state.capacityInfo.optLong("rejectedConnections")}",
                            color = palette.onSurfaceVariant, fontSize = 14.sp)
                        val benchmarkLevels = state.capacityBenchmark.optJSONArray("levels")
                        val chartRows = (0 until (benchmarkLevels?.length() ?: 0)).mapNotNull { index ->
                            benchmarkLevels?.optJSONObject(index)?.let { it.optInt("concurrency") to it.optLong("p95LatencyMs") }
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
                            Text(selectedPoint?.let { "Concurrency ${it.first} · p95 latency ${it.second} ms" }
                                ?: "Select a concurrency point to inspect its p95 latency. Bars show measured milliseconds; marker = 750 ms benchmark threshold.",
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
