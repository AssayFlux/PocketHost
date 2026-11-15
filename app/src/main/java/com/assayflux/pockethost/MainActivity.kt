package com.assayflux.pockethost

import android.Manifest
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import java.time.LocalDate
import com.assayflux.pockethost.ui.theme.PocketHostTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.assayflux.pockethost.engine.NativeEngine
import com.assayflux.pockethost.diagnostics.OperationalLog
import com.assayflux.pockethost.diagnostics.MetricsStore
import com.assayflux.pockethost.diagnostics.DiagnosticsBundle
import com.assayflux.pockethost.diagnostics.CapacityController
import com.assayflux.pockethost.diagnostics.CapacityLab
import com.assayflux.pockethost.diagnostics.DailyOperationsReport
import com.assayflux.pockethost.diagnostics.TelemetryStore
import com.assayflux.pockethost.diagnostics.DeviceTelemetry
import com.assayflux.pockethost.service.HostingService
import com.assayflux.pockethost.storage.SiteImporter
import com.assayflux.pockethost.storage.SiteFileManager
import com.assayflux.pockethost.storage.PythonProjectImporter
import com.assayflux.pockethost.storage.NodeProjectImporter
import com.assayflux.pockethost.storage.SiteBackupManager
import com.assayflux.pockethost.runtime.PythonHostRuntime
import com.assayflux.pockethost.runtime.NodeHostRuntime
import com.assayflux.pockethost.sites.SiteRegistry
import com.assayflux.pockethost.management.ManagementApiServer
import com.assayflux.pockethost.management.RemoteControlAgent
import com.assayflux.pockethost.tunnel.CloudflareTokenStore
import com.assayflux.pockethost.tunnel.CloudflareTunnelRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface


private fun localLanAddress(): String? = runCatching {
    val interfaces = NetworkInterface.getNetworkInterfaces()
    val candidates = mutableListOf<String>()
    while (interfaces.hasMoreElements()) {
        val network = interfaces.nextElement()
        if (network.isUp && !network.isLoopback) {
            val addresses = network.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                    candidates.add(address.hostAddress ?: "")
                }
            }
        }
    }
    candidates.firstOrNull { it.isNotBlank() }
}.getOrNull()

private fun formatBytes(value: Long): String = when {
    value < 1024L -> "$value B"
    value < 1024L * 1024L -> "${value / 1024L} KB"
    value < 1024L * 1024L * 1024L -> "${value / (1024L * 1024L)} MB"
    else -> "${value / (1024L * 1024L * 1024L)} GB"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PocketHostDashboard() }
    }
}

@Composable
private fun PocketHostDashboard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf("Dashboard") }
    var darkTheme by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getBoolean("dark_theme", false)) }
    var autoStartOnBoot by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getBoolean("auto_start_on_boot", false)) }
    var logFilterMode by remember { mutableStateOf("All") }
    var showClearLogsConfirmation by remember { mutableStateOf(false) }
    var siteFileRefresh by remember { mutableIntStateOf(0) }
    var showCreateSiteFile by remember { mutableStateOf(false) }
    var newSiteFilePath by remember { mutableStateOf("new-page.html") }
    var newSiteFileContent by remember { mutableStateOf("") }
    var editingSiteFilePath by remember { mutableStateOf<String?>(null) }
    var editingSiteFileContent by remember { mutableStateOf("") }
    var pendingDeleteSiteFile by remember { mutableStateOf<String?>(null) }
    var pendingIndexSiteFile by remember { mutableStateOf<String?>(null) }
    var hosting by remember { mutableStateOf(runCatching { NativeEngine.status() == "RUNNING" }.getOrDefault(false)) }
    var importing by remember { mutableStateOf(false) }
    var advancedMode by remember { mutableStateOf(false) }
    var keepAwake by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getBoolean("keep_awake", false)) }
    var portText by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getInt("port", 8080).toString()) }
    var allowLan by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getBoolean("allow_lan", false)) }
    val lanAddress = remember(allowLan, hosting) { if (allowLan) localLanAddress() else null }
    var status by remember { mutableStateOf("Ready to configure your first website.") }
    var requests by remember { mutableLongStateOf(0L) }
    var bytesServed by remember { mutableLongStateOf(0L) }
    var todayRequests by remember { mutableLongStateOf(0L) }
    var todayBytes by remember { mutableLongStateOf(0L) }
    var activeConnections by remember { mutableLongStateOf(0L) }
    var recentEvents by remember { mutableStateOf(emptyList<OperationalLog.Entry>()) }
    var logFilter by remember { mutableStateOf("") }
    var importedSummary by remember {
        mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
            .getString("site_summary", "Bundled sample website is available.") ?: "Bundled sample website is available.")
    }
    var previousReleases by remember { mutableStateOf(SiteImporter.listReleases(context)) }
    var pythonPortText by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getInt("python_port", 8081).toString()) }
    var pythonProjectSummary by remember { mutableStateOf(if (File(context.filesDir, "python-project/wsgi.py").isFile) "Python WSGI project imported." else "No Python project imported.") }
    var pythonHosting by remember { mutableStateOf(runCatching { PythonHostRuntime.status(context) == "RUNNING" }.getOrDefault(false)) }
    var nodePortText by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getInt("node_port", 3000).toString()) }
    var nodeProjectSummary by remember { mutableStateOf(if (File(context.filesDir, "node-project-default/package.json").isFile) "Node project imported." else "No Node project imported.") }
    var nodeHosting by remember { mutableStateOf(NodeHostRuntime.status(context) == "RUNNING") }
    var trustedRuntimeCodeAck by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getBoolean("trusted_runtime_code_ack", false)) }
    var hostnameText by remember { mutableStateOf("") }
    var hostnameRuntime by remember { mutableStateOf("static") }
    var cloudflareTokenText by remember { mutableStateOf("") }
    var backupPassphrase by remember { mutableStateOf("") }
    var cloudflareTokenSaved by remember { mutableStateOf(CloudflareTokenStore.hasToken(context)) }
    var tunnelStatus by remember { mutableStateOf(CloudflareTunnelRuntime.status()) }
    var pendingPairingCode by remember { mutableStateOf<String?>(null) }
    var controlPlaneEndpoint by remember { mutableStateOf(RemoteControlAgent.endpoint(context)) }
    var controlPlaneToken by remember { mutableStateOf("") }
    var remoteControlConfigured by remember { mutableStateOf(RemoteControlAgent.isConfigured(context)) }
    var capacityPercent by remember { mutableFloatStateOf(CapacityController.snapshot(context).optInt("percent", 100).toFloat()) }
    var capacityInfo by remember { mutableStateOf(CapacityController.snapshot(context)) }
    var capacityBenchmark by remember { mutableStateOf(CapacityLab.snapshot(context)) }
    var capacityDuration by remember { mutableStateOf("180") }
    var deviceTelemetry by remember { mutableStateOf(DeviceTelemetry.snapshot(context)) }

    val startHosting: (Boolean) -> Unit = { notificationDenied ->
        val intent = Intent(context, HostingService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }.onSuccess {
            status = if (notificationDenied) "Starting hosting. Android notification permission is denied, so the ongoing service notification may be hidden from the notification drawer." else "Starting local server on port ${portText.toIntOrNull() ?: 8080}…"
        }.onFailure {
            status = it.message ?: "Could not start hosting service."
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        startHosting(!granted)
    }

    LaunchedEffect(Unit) {
        val preferences = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        var lastIdleTelemetryAt = 0L
        while (true) {
            hosting = runCatching { NativeEngine.status() == "RUNNING" }.getOrDefault(false)
            val metrics = withContext(Dispatchers.IO) { MetricsStore.snapshot(context) }
            requests = metrics.totalRequests
            bytesServed = metrics.totalBytes
            todayRequests = metrics.todayRequests
            todayBytes = metrics.todayBytes
            activeConnections = runCatching { NativeEngine.activeConnections() }.getOrDefault(0L)
            pythonHosting = runCatching { PythonHostRuntime.status(context) == "RUNNING" }.getOrDefault(false)
            nodeHosting = runCatching { NodeHostRuntime.status(context) == "RUNNING" }.getOrDefault(false)
            tunnelStatus = CloudflareTunnelRuntime.status()
            pendingPairingCode = ManagementApiServer.pendingApprovalCode()
            preferences.getString("service_status", null)?.let { status = it }
            recentEvents = withContext(Dispatchers.IO) { OperationalLog.read(context, 500) }
            capacityInfo = withContext(Dispatchers.IO) { CapacityController.tick(context)
                .put("activeConnections", NativeEngine.activeConnections())
                .put("rejectedConnections", NativeEngine.rejectedConnections()) }
            capacityBenchmark = CapacityLab.snapshot(context)
            val telemetryNow = android.os.SystemClock.elapsedRealtime()
            if (hosting) {
                deviceTelemetry = TelemetryStore.latest(context) ?: DeviceTelemetry.snapshot(context)
            } else if (telemetryNow - lastIdleTelemetryAt >= 30_000L) {
                // Keep the device panel live while the web server is stopped, without writing
                // a persistent history row every UI refresh.
                deviceTelemetry = DeviceTelemetry.snapshot(context)
                lastIdleTelemetryAt = telemetryNow
            }
            delay(1_000)
        }
    }

    val logExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-ndjson")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val exported = withContext(Dispatchers.IO) { OperationalLog.export(context, uri) }
                status = if (exported) "Redacted operational logs exported." else "Could not export operational logs."
            }
        }
    }

    val dailyReportExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val exported = withContext(Dispatchers.IO) { DailyOperationsReport.exportLatest(context, uri) }
                status = if (exported) "Latest daily operations report exported." else "Could not export the daily report."
            }
        }
    }

    val diagnosticsExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val exported = withContext(Dispatchers.IO) { DiagnosticsBundle.export(context, uri) }
                status = if (exported) "Diagnostics bundle exported. Review it before sharing." else "Could not export diagnostics bundle."
            }
        }
    }

    val backupExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            val passphrase = backupPassphrase.toCharArray()
            backupPassphrase = ""
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { SiteBackupManager.export(context, uri, passphrase) }
                }
                status = if (result.getOrDefault(false)) "Encrypted website backup exported. Keep the passphrase safe; it cannot be recovered." else result.exceptionOrNull()?.message ?: "Backup export failed."
            }
        }
    }

    val backupRestorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val passphrase = backupPassphrase.toCharArray()
            backupPassphrase = ""
            importing = true
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { SiteBackupManager.restore(context, uri, passphrase) }
                }
                result.onSuccess { report ->
                    importedSummary = "Encrypted backup restored · ${report.fileCount} files · ${report.totalBytes / 1024} KB"
                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                        .putString("site_summary", importedSummary).apply()
                    previousReleases = SiteImporter.listReleases(context)
                    status = "Backup authenticated and restored. Restart hosting to load the restored website."
                }.onFailure { error -> status = error.message ?: "Backup restore failed." }
                importing = false
            }
        }
    }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            importing = true
            status = "Inspecting ZIP archive…"
            scope.launch {
                val result = runCatching { withContext(Dispatchers.IO) { SiteImporter.importZip(context, uri) } }
                result.onSuccess { report ->
                    importedSummary = "ZIP imported · ${report.fileCount} files · ${report.totalBytes / 1024} KB"
                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                        .putString("site_summary", importedSummary).apply()
                    previousReleases = SiteImporter.listReleases(context)
                    status = "ZIP website deployed locally. Start hosting to preview it."
                }.onFailure { error -> status = error.message ?: "ZIP import failed." }
                importing = false
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            importing = true
            status = "Validating and importing website…"
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { SiteImporter.importTree(context, uri) }
                }
                result.onSuccess { report ->
                    importedSummary = "Folder imported · ${report.fileCount} files · ${report.totalBytes / 1024} KB"
                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                        .putString("site_summary", importedSummary).apply()
                    previousReleases = SiteImporter.listReleases(context)
                    status = "Website deployed locally. Start hosting to preview it."
                }.onFailure { error ->
                    status = error.message ?: "Website import failed."
                }
                importing = false
            }
        }
    }

    val pythonProjectPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            importing = true
            status = "Validating Python WSGI project…"
            scope.launch {
                val result = runCatching { withContext(Dispatchers.IO) { PythonProjectImporter.importTree(context, uri) } }
                result.onSuccess { report ->
                    pythonProjectSummary = "Python project imported · ${report.fileCount} files · ${report.totalBytes / 1024} KB"
                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                        .putString("python_project_root", report.projectDirectory.absolutePath).apply()
                    runCatching { SiteRegistry.put(context, SiteRegistry.Site("python-default", "Python application", "python-wsgi",
                        report.projectDirectory.absolutePath, emptyList(), true)) }
                    status = "Python project is ready. Start the Python backend to run its wsgi.py application."
                }.onFailure { error -> status = error.message ?: "Python project import failed." }
                importing = false
            }
        }
    }

    val nodeProjectPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            importing = true
            status = "Importing Node.js project and validating package.json…"
            scope.launch {
                val result = runCatching { withContext(Dispatchers.IO) { NodeProjectImporter.importTree(context, uri, "default") } }
                result.onSuccess { report ->
                    nodeProjectSummary = "Node.js project imported · ${report.fileCount} files · ${report.totalBytes / 1024} KB · ${report.entryFile.name}"
                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                        .putString("node_project_root", report.projectDirectory.absolutePath).apply()
                    runCatching {
                        SiteRegistry.put(context, SiteRegistry.Site("node-default", "Node application", "node",
                            report.projectDirectory.absolutePath, emptyList(), true))
                    }.onFailure { OperationalLog.record(context, "WARN", "NODE_SITE_REGISTRY", it.message ?: "Could not register Node project.") }
                    status = "Node.js project is ready. Dependencies must already be included in node_modules; npm install scripts are not run on-device."
                }.onFailure { error -> status = error.message ?: "Node project import failed." }
                importing = false
            }
        }
    }

    PocketHostTheme(darkTheme = darkTheme) {
        val palette = MaterialTheme.colorScheme
        Scaffold(
            containerColor = palette.background,
            topBar = {
                Surface(color = palette.surface, tonalElevation = 1.dp) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("AssayFlux Pocket Host", color = palette.onSurface,
                                fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(selectedTab, color = palette.onSurfaceVariant, fontSize = 14.sp)
                        }
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (hosting) Color(0xFF2E7D32) else Color(0xFFC62828)))
                        Text(if (hosting) "Running" else "Stopped", color = palette.onSurface,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            bottomBar = {
                NavigationBar(modifier = Modifier.imePadding(), containerColor = palette.surface) {
                    listOf("Dashboard", "Files", "Logs", "Settings").forEach { destination ->
                        val symbol = when (destination) { "Dashboard" -> "⌂"; "Files" -> "▤"; "Logs" -> "≡"; else -> "⚙" }
                        NavigationBarItem(
                            selected = selectedTab == destination,
                            onClick = { selectedTab = destination },
                            icon = { Text(symbol, fontSize = 18.sp) },
                            label = { Text(destination, fontSize = 14.sp) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        ) { innerPadding ->
            Surface(Modifier.fillMaxSize(), color = palette.background) {
                Column(
                    Modifier.fillMaxSize().padding(innerPadding).imePadding()
                        .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                if (selectedTab == "Dashboard") {
                if (pendingPairingCode != null) {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = palette.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("CONSOLE PAIRING REQUEST", color = palette.onSurface, fontWeight = FontWeight.Bold)
                            Text("Code: $pendingPairingCode", color = palette.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("Only approve this if you initiated pairing from your own trusted browser.", color = palette.onSurfaceVariant)
                            Button(onClick = {
                                val approved = ManagementApiServer.approvePairing(pendingPairingCode.orEmpty())
                                if (approved) { status = "Management console pairing approved."; pendingPairingCode = null }
                                else status = "Pairing request expired. Start pairing again."
                            }) { Text("Approve console pairing") }
                        }
                    }
                }
                }
                if (selectedTab == "Settings") {
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
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Auto-start hosting on boot", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                                    Text("Starts the local hosting service after device restart when Android permits it.", color = palette.onSurfaceVariant)
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
                                }.onFailure { status = it.message ?: "Could not open battery optimization settings." }
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
                                value = portText,
                                onValueChange = { value -> if (value.length <= 5 && value.all(Char::isDigit)) portText = value },
                                label = { Text("HTTP port") },
                                enabled = !hosting,
                                singleLine = true
                            )
                            OutlinedButton(
                                onClick = {
                                    val port = portText.toIntOrNull()
                                    if (port == null || port !in 1024..65535) {
                                        status = "Choose a port between 1024 and 65535."
                                    } else {
                                        context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                            .putInt("port", port).apply()
                                        status = "Server port saved. Start hosting to apply it."
                                    }
                                },
                                enabled = !hosting
                            ) { Text("Save server settings") }
                            Text("Engine limits: 16 concurrent connections · 50 MiB maximum file · two Tokio workers.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Keep CPU awake while hosting", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                                    Text("Optional screen-off reliability aid. Uses more battery and cannot override every OEM network policy.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                                }
                                Switch(
                                    checked = keepAwake,
                                    onCheckedChange = { enabled ->
                                        keepAwake = enabled
                                        context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                            .putBoolean("keep_awake", enabled).apply()
                                        status = if (enabled) "CPU wake lock will be used the next time hosting starts." else "CPU wake lock disabled for the next hosting session."
                                    },
                                    enabled = !hosting
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
                                        if (hosting) RemoteControlAgent.start(context)
                                        status = if (hosting) "Remote control connected. This phone will poll the backend while hosting is active." else "Remote connection saved securely. The agent starts when hosting starts."
                                    }.onFailure { status = it.message ?: "Could not configure remote control." }
                                }, enabled = controlPlaneEndpoint.isNotBlank() && controlPlaneToken.length >= 32) {
                                    Text("Save remote connection")
                                }
                                OutlinedButton(onClick = {
                                    RemoteControlAgent.clear(context)
                                    remoteControlConfigured = false
                                    controlPlaneToken = ""
                                    status = "Remote control credentials removed from this device."
                                }, enabled = remoteControlConfigured) { Text("Disconnect") }
                            }
                            Text("The device makes outbound HTTPS requests only. The backend cannot open inbound ports or execute arbitrary shell commands on this phone.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                }

                }
                if (selectedTab == "Dashboard") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("HOSTING STATUS", color = palette.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(if (hosting) "Local hosting active" else "Stopped", color = palette.onSurface, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Text(status, color = palette.onSurfaceVariant, fontSize = 14.sp)
                        Button(
                            onClick = {
                                if (hosting) {
                                    context.stopService(Intent(context, HostingService::class.java))
                                    status = "Stopping hosting service…"
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    startHosting(false)
                                }
                            },
                            enabled = hosting || !importing,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (hosting) "Stop hosting" else "Start hosting") }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("STATIC ENGINE TRAFFIC · ALL HOSTING SESSIONS", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Requests", color = palette.onSurfaceVariant, fontSize = 14.sp)
                                Text(requests.toString(), color = palette.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Active", color = palette.onSurfaceVariant, fontSize = 14.sp)
                                Text(activeConnections.toString(), color = palette.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Transferred", color = palette.onSurfaceVariant, fontSize = 14.sp)
                                Text(formatBytes(bytesServed), color = palette.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("Today: $todayRequests requests · ${formatBytes(todayBytes)} transferred.", color = palette.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Static-engine totals persist across service restarts. Python WSGI and tunnel traffic are not included yet.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("CAPACITY & AUTOMATIC PROTECTION", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        Text("Admission budget: ${capacityInfo.optInt("effectiveConcurrencyLimit", 16)} active connections · ${capacityInfo.optInt("percent", 100)}% of ${if (capacityInfo.optLong("calibratedAt") > 0L) "measured" else "provisional"} concurrency ceiling",
                            color = palette.onSurfaceVariant, fontSize = 14.sp)
                        Slider(value = capacityPercent, onValueChange = { capacityPercent = it }, valueRange = 1f..100f)
                        Text("${capacityPercent.toInt()}% capacity profile", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        OutlinedButton(onClick = {
                            runCatching { CapacityController.setPercent(context, capacityPercent.toInt()) }
                                .onSuccess { capacityInfo = CapacityController.snapshot(context); status = "Capacity profile applied." }
                                .onFailure { status = it.message ?: "Capacity profile rejected." }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Apply capacity profile") }
                        OutlinedTextField(value = capacityDuration, onValueChange = { capacityDuration = it.filter(Char::isDigit).take(4) },
                            label = { Text("Schedule duration in minutes (max 1440)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = {
                            runCatching { CapacityController.schedule(context, 30, capacityDuration.toInt()) }
                                .onSuccess { capacityInfo = it; status = "30% capacity schedule started." }
                                .onFailure { status = it.message ?: "Capacity schedule rejected." }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Schedule 30% capacity") }
                        OutlinedButton(onClick = {
                            runCatching { CapacityController.schedule(context, capacityPercent.toInt(), capacityDuration.toInt()) }
                                .onSuccess { capacityInfo = it; status = "Selected capacity profile scheduled." }
                                .onFailure { status = it.message ?: "Capacity schedule rejected." }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Schedule selected profile") }
                        Button(onClick = {
                            runCatching { capacityBenchmark = CapacityLab.start(context, portText.toIntOrNull() ?: 8080) }
                                .onFailure { status = it.message ?: "Benchmark could not start." }
                        }, enabled = hosting && capacityBenchmark.optString("state") != "RUNNING",
                            modifier = Modifier.fillMaxWidth()) { Text(if (capacityBenchmark.optString("state") == "RUNNING") "Benchmark running…" else "Measure safe capacity") }
                        Text("Benchmark: ${capacityBenchmark.optString("state", "NOT_RUN")} · safe ceiling ${capacityInfo.optInt("safeConcurrency", 16)} · active ${capacityInfo.optLong("activeConnections")} · rejected ${capacityInfo.optLong("rejectedConnections")}",
                            color = palette.onSurfaceVariant, fontSize = 14.sp)
                        val benchmarkLevels = capacityBenchmark.optJSONArray("levels")
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
                            Text("Bars show p95 milliseconds; vertical marker = 750 ms acceptance threshold. Concurrency levels: ${chartRows.joinToString { it.first.toString() }}.",
                                color = palette.onSurfaceVariant, fontSize = 14.sp)
                        }
                        Text("Device: ${deviceTelemetry.optString("manufacturer")} ${deviceTelemetry.optString("model")} · Android API ${deviceTelemetry.optInt("androidApi")} · ${deviceTelemetry.optInt("cpuCores")} CPU cores",
                            color = palette.onSurfaceVariant, fontSize = 14.sp)
                        Text("CPU load: ${if (deviceTelemetry.isNull("cpuUsagePercent")) "warming up / unavailable" else deviceTelemetry.optDouble("cpuUsagePercent").toInt().toString() + "%"} · app PSS: ${formatBytes(deviceTelemetry.optLong("processPssKb") * 1024L)} · RAM available: ${formatBytes(deviceTelemetry.optLong("availableMemoryBytes"))} · storage free: ${formatBytes(deviceTelemetry.optLong("storageAvailableBytes"))} · battery: ${if (deviceTelemetry.isNull("batteryPercent")) "unknown" else deviceTelemetry.optInt("batteryPercent").toString() + "%"} · thermal: ${deviceTelemetry.optString("thermalLabel", "UNKNOWN")} · network: ${deviceTelemetry.optString("networkTransport", "unknown")}",
                            color = palette.onSurfaceVariant, fontSize = 14.sp)
                        Text("Automatic health state: ${capacityInfo.optString("healthState", "NORMAL")}. Daily reports retained: ${DailyOperationsReport.list(context).size}. The percentage controls admission concurrency, not CPU usage or a guaranteed visitor count. Android can still stop the app.",
                            color = palette.onSurfaceVariant, fontSize = 14.sp)
                        OutlinedButton(onClick = { dailyReportExportPicker.launch("pockethost-daily-report.json") },
                            enabled = DailyOperationsReport.list(context).isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text("Export latest daily report")
                        }
                    }
                }

                }
                if (selectedTab == "Logs") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("RECENT OPERATIONS", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("All", "Errors", "Today").forEach { filter ->
                                FilterChip(selected = logFilterMode == filter, onClick = { logFilterMode = filter }, label = { Text(filter) })
                            }
                        }
                        OutlinedTextField(
                            value = logFilter,
                            onValueChange = { logFilter = it },
                            label = { Text("Filter events, levels or messages") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val filteredEvents = recentEvents.filter { event ->
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
                            Text(if (recentEvents.isEmpty()) "No operational events recorded yet." else "No events match this filter.", color = palette.onSurfaceVariant, fontSize = 14.sp)
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
                            onClick = { logExportPicker.launch("pockethost-operations.jsonl") },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Export redacted logs") }
                        OutlinedButton(
                            onClick = { diagnosticsExportPicker.launch("pockethost-diagnostics.zip") },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Export diagnostics bundle") }
                        OutlinedButton(onClick = { showClearLogsConfirmation = true },
                            enabled = recentEvents.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text("Clear logs")
                        }
                        if (showClearLogsConfirmation) {
                            AlertDialog(
                                onDismissRequest = { showClearLogsConfirmation = false },
                                title = { Text("Clear operational logs?") },
                                text = { Text("This permanently removes the app's retained operational event log files from this device. Export them first if you need a copy.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        OperationalLog.clear(context)
                                        recentEvents = emptyList()
                                        logFilter = ""
                                        logFilterMode = "All"
                                        status = "Operational logs cleared."
                                        showClearLogsConfirmation = false
                                    }) { Text("Clear logs") }
                                },
                                dismissButton = { TextButton(onClick = { showClearLogsConfirmation = false }) { Text("Cancel") } }
                            )
                        }
                    }
                }

                }
                if (selectedTab == "Files") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("WEBSITE", fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                        Text(importedSummary, color = palette.onSurfaceVariant, fontSize = 14.sp)
                        val siteRoot = File(context.filesDir, "site")
                        val siteFiles = remember(importedSummary, siteFileRefresh) { SiteFileManager.list(context) }
                        Text("Site files and folders", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        OutlinedButton(onClick = { showCreateSiteFile = true }, enabled = !hosting && !importing,
                            modifier = Modifier.fillMaxWidth()) { Text("New text file") }
                        if (siteFiles.isEmpty()) {
                            Text("No imported site files yet. Choose a folder or ZIP to deploy your website.", color = palette.onSurfaceVariant)
                        } else {
                            siteFiles.forEach { entry ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Text(if (entry.isDirectory) "▣" else "▤", Modifier.padding(end = 8.dp), color = palette.onSurfaceVariant)
                                        Text(entry.relativePath, Modifier.weight(1f), color = palette.onSurface,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(if (entry.isDirectory) "Folder" else formatBytes(entry.size), color = palette.onSurfaceVariant)
                                    }
                                    if (!entry.isDirectory) {
                                        Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entry.lastModified)),
                                            color = palette.onSurfaceVariant)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(onClick = {
                                                runCatching { SiteFileManager.readText(context, entry.relativePath) }
                                                    .onSuccess { editingSiteFilePath = entry.relativePath; editingSiteFileContent = it }
                                                    .onFailure { status = it.message ?: "This file cannot be edited as text." }
                                            }, enabled = !hosting && entry.isEditable) { Text("Edit") }
                                            if (entry.relativePath.endsWith(".html", true) || entry.relativePath.endsWith(".htm", true)) {
                                                OutlinedButton(onClick = { pendingIndexSiteFile = entry.relativePath },
                                                    enabled = !hosting) { Text("Set as index") }
                                            }
                                            OutlinedButton(onClick = { pendingDeleteSiteFile = entry.relativePath },
                                                enabled = !hosting) { Text("Delete") }
                                        }
                                    }
                                }
                                Divider(color = palette.outline)
                            }
                        }
                        Text(if (SiteFileManager.hasIndex(context)) "Entry page: index.html" else "No index.html found at the site root yet.", color = palette.onSurfaceVariant)
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(context, SitePreviewActivity::class.java)) },
                            enabled = hosting,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Preview site") }
                        Button(
                            onClick = { folderPicker.launch(null) },
                            enabled = !hosting && !importing,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (importing) "Importing website…" else "Choose website folder") }
                        OutlinedButton(
                            onClick = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                            enabled = !hosting && !importing,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Import website ZIP") }
                        Text("Imports are staged and validated before replacing the current website. Maximum import: 50 MB, 5,000 files and 10,000 total entries.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                        OutlinedTextField(
                            value = backupPassphrase,
                            onValueChange = { backupPassphrase = it },
                            label = { Text("Backup passphrase (12+ characters)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = !hosting && !importing,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = { backupExportPicker.launch("pockethost-site.afbackup") },
                            enabled = !hosting && !importing && backupPassphrase.length >= 12,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Export encrypted website backup") }
                        OutlinedButton(
                            onClick = { backupRestorePicker.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) },
                            enabled = !hosting && !importing && backupPassphrase.length >= 12,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Restore encrypted website backup") }
                        Text("Backups use AES-256-GCM with a passphrase-derived key. Only the static website is included; Python projects, tunnel credentials and device settings are excluded. Restores are authenticated and validated before deployment.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                        if (previousReleases.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    val result = runCatching { SiteImporter.rollbackLatest(context) }
                                    result.onSuccess {
                                        previousReleases = SiteImporter.listReleases(context)
                                        importedSummary = "Restored previous deployment"
                                        status = "Rollback completed. Restart hosting to load the restored site."
                                        context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                            .putString("site_summary", importedSummary).apply()
                                    }.onFailure { status = it.message ?: "Rollback failed." }
                                },
                                enabled = !hosting && !importing,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Rollback previous deployment") }
                            Text("${previousReleases.size} previous release(s) retained · latest first", color = palette.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                }

                    if (showCreateSiteFile) {
                        AlertDialog(
                            onDismissRequest = { showCreateSiteFile = false },
                            title = { Text("Create text file") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(value = newSiteFilePath, onValueChange = { newSiteFilePath = it },
                                        label = { Text("Relative file path") }, singleLine = true)
                                    OutlinedTextField(value = newSiteFileContent, onValueChange = { newSiteFileContent = it },
                                        label = { Text("File contents") }, minLines = 4, maxLines = 8)
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    runCatching { SiteFileManager.createText(context, newSiteFilePath, newSiteFileContent) }
                                        .onSuccess {
                                            siteFileRefresh++
                                            newSiteFilePath = "new-page.html"
                                            newSiteFileContent = ""
                                            status = "Text file created."
                                        }
                                        .onFailure { status = it.message ?: "Could not create the file." }
                                    showCreateSiteFile = false
                                }) { Text("Create") }
                            },
                            dismissButton = { TextButton(onClick = { showCreateSiteFile = false }) { Text("Cancel") } }
                        )
                    }
                    editingSiteFilePath?.let { path ->
                        AlertDialog(
                            onDismissRequest = { editingSiteFilePath = null },
                            title = { Text("Edit $path") },
                            text = {
                                OutlinedTextField(value = editingSiteFileContent, onValueChange = { editingSiteFileContent = it },
                                    modifier = Modifier.fillMaxWidth(), minLines = 6, maxLines = 12)
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    runCatching { SiteFileManager.writeText(context, path, editingSiteFileContent) }
                                        .onSuccess { siteFileRefresh++; status = "File saved." }
                                        .onFailure { status = it.message ?: "Could not save the file." }
                                    editingSiteFilePath = null
                                }) { Text("Save") }
                            },
                            dismissButton = { TextButton(onClick = { editingSiteFilePath = null }) { Text("Cancel") } }
                        )
                    }
                    pendingDeleteSiteFile?.let { path ->
                        AlertDialog(
                            onDismissRequest = { pendingDeleteSiteFile = null },
                            title = { Text("Delete file?") },
                            text = { Text("Delete $path permanently from the imported website?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    runCatching { SiteFileManager.deleteFile(context, path) }
                                        .onSuccess { siteFileRefresh++; status = "File deleted." }
                                        .onFailure { status = it.message ?: "Could not delete the file." }
                                    pendingDeleteSiteFile = null
                                }) { Text("Delete") }
                            },
                            dismissButton = { TextButton(onClick = { pendingDeleteSiteFile = null }) { Text("Cancel") } }
                        )
                    }
                    pendingIndexSiteFile?.let { path ->
                        AlertDialog(
                            onDismissRequest = { pendingIndexSiteFile = null },
                            title = { Text("Set website entry page?") },
                            text = { Text("Copy $path to the website root as index.html? An existing index.html will be replaced.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    runCatching { SiteFileManager.setIndex(context, path) }
                                        .onSuccess { siteFileRefresh++; status = "Website entry page updated to index.html." }
                                        .onFailure { status = it.message ?: "Could not set the entry page." }
                                    pendingIndexSiteFile = null
                                }) { Text("Set index.html") }
                            },
                            dismissButton = { TextButton(onClick = { pendingIndexSiteFile = null }) { Text("Cancel") } }
                        )
                    }

                }
                if (selectedTab == "Settings") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("CLOUDFLARE TUNNEL", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (tunnelStatus) {
                                "CONNECTED" -> "Tunnel connected to Cloudflare."
                                "CONNECTING" -> "Tunnel process started; waiting for an active edge connection."
                                "ERROR" -> "Tunnel process stopped with an error. Check the service status."
                                else -> if (cloudflareTokenSaved) "Named tunnel token is encrypted on this device." else "No tunnel token saved."
                            },
                            color = palette.onSurfaceVariant, fontSize = 14.sp
                        )
                        OutlinedTextField(
                            value = cloudflareTokenText,
                            onValueChange = { cloudflareTokenText = it },
                            label = { Text("Cloudflare named-tunnel token") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = tunnelStatus != "CONNECTED" && tunnelStatus != "CONNECTING",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    runCatching { CloudflareTokenStore.save(context, cloudflareTokenText) }
                                        .onSuccess {
                                            cloudflareTokenText = ""
                                            cloudflareTokenSaved = true
                                            status = "Cloudflare token encrypted with Android Keystore."
                                        }
                                        .onFailure { status = it.message ?: "Could not save Cloudflare token." }
                                },
                                enabled = cloudflareTokenText.isNotBlank() && tunnelStatus != "CONNECTED" && tunnelStatus != "CONNECTING"
                            ) { Text("Save token securely") }
                            OutlinedButton(
                                onClick = {
                                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                        .putBoolean("tunnel_enabled", true).apply()
                                    if (cloudflareTokenSaved) {
                                        if (hosting) {
                                            // Re-enter the foreground service command path so the
                                            // bounded reconnect supervisor owns this tunnel session.
                                            ContextCompat.startForegroundService(context, Intent(context, HostingService::class.java))
                                        } else {
                                            startHosting(false)
                                        }
                                    } else {
                                        status = "Save a Cloudflare tunnel token first."
                                    }
                                },
                                enabled = tunnelStatus != "CONNECTED" && tunnelStatus != "CONNECTING"
                            ) { Text("Start tunnel") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                        .putBoolean("tunnel_enabled", false).apply()
                                    scope.launch {
                                        tunnelStatus = withContext(Dispatchers.IO) { CloudflareTunnelRuntime.stop() }
                                        status = "Cloudflare Tunnel stopped. Static hosting remains available."
                                    }
                                },
                                enabled = tunnelStatus == "CONNECTED" || tunnelStatus == "CONNECTING" || tunnelStatus == "ERROR"
                            ) { Text("Stop tunnel") }
                            OutlinedButton(
                                onClick = {
                                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                        .putBoolean("tunnel_enabled", false).apply()
                                    CloudflareTokenStore.clear(context)
                                    cloudflareTokenSaved = false
                                    status = "Encrypted Cloudflare token removed from this device."
                                },
                                enabled = cloudflareTokenSaved && tunnelStatus == "STOPPED"
                            ) { Text("Remove token") }
                        }
                        Text(
                            "Reuse your existing named Cloudflare Tunnel and Public Hostname. Do not create another tunnel. Confirm its Public Hostname origin is http://127.0.0.1:${portText.toIntOrNull() ?: 8080}. DNS and hostname setup remain in the Cloudflare dashboard.",
                            color = palette.onSurfaceVariant, fontSize = 14.sp
                        )
                        Text(
                            "Public exposure is not visitor-authenticated by PocketHost. Add Cloudflare Access before exposing private sites. Tunnel connector and ABI support still require physical-device validation.",
                            color = palette.onSurfaceVariant, fontSize = 14.sp
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("TRUSTED APPLICATION CODE ONLY", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        Text("Python and Node applications execute with PocketHost's Android app privileges. A separate process or V8 heap limit is NOT a security sandbox. Never run code you do not own or trust. Strong OS-backed sandboxing is not implemented yet.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("I understand and will run trusted code only", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                                Text("Required before either optional runtime can start.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                            }
                            Switch(checked = trustedRuntimeCodeAck, onCheckedChange = { accepted ->
                                trustedRuntimeCodeAck = accepted
                                val prefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("trusted_runtime_code_ack", accepted).apply()
                                if (!accepted) {
                                    prefs.edit().putBoolean("python_enabled", false).putBoolean("node_enabled", false).apply()
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            runCatching { PythonHostRuntime.stop(context) }
                                            runCatching { NodeHostRuntime.stop(context) }
                                        }
                                        pythonHosting = false
                                        nodeHosting = false
                                        if (hosting) {
                                            runCatching { NativeEngine.stop() }
                                            startHosting(false)
                                        }
                                    }
                                    status = "Optional runtimes stopped. Static hosting remains available."
                                }
                            })
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("PYTHON BACKEND · CPYTHON 3.11", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(pythonProjectSummary, color = palette.onSurfaceVariant, fontSize = 14.sp)
                        OutlinedButton(
                            onClick = { pythonProjectPicker.launch(null) },
                            enabled = !importing && !pythonHosting,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Import Python WSGI project") }
                        OutlinedTextField(
                            value = pythonPortText,
                            onValueChange = { value -> if (value.length <= 5 && value.all(Char::isDigit)) pythonPortText = value },
                            label = { Text("Python HTTP port") },
                            enabled = !pythonHosting,
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val port = pythonPortText.toIntOrNull()
                                    val preferences = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
                                    // Compare against the port actually persisted for the running static engine,
                                    // not an unsaved value currently typed into the Advanced Mode field.
                                    val staticPort = preferences.getInt("port", 8080)
                                    val root = preferences.getString("python_project_root", File(context.filesDir, "python-project").absolutePath)
                                        ?: File(context.filesDir, "python-project").absolutePath
                                    if (port == null || port !in 1024..65535 || port == staticPort) {
                                        status = "Choose a Python port from 1024 to 65535, different from the static server port."
                                    } else if (!File(root, "wsgi.py").isFile) {
                                        status = "Import a Python project containing wsgi.py first."
                                    } else {
                                        context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                            .putBoolean("python_enabled", true).putInt("python_port", port).apply()
                                        startHosting(false)
                                    }
                                },
                                enabled = !pythonHosting && !importing
                            ) { Text("Start Python") }
                            OutlinedButton(
                                onClick = {
                                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit().putBoolean("python_enabled", false).apply()
                                    scope.launch {
                                        withContext(Dispatchers.IO) { runCatching { PythonHostRuntime.stop(context) } }
                                        pythonHosting = false
                                        status = "Python backend stopped. Static hosting remains available."
                                    }
                                },
                                enabled = pythonHosting
                            ) { Text("Stop Python") }
                        }
                        if (pythonHosting) Text(
                            if (allowLan && lanAddress != null) "Python preview: http://$lanAddress:$pythonPortText" else "Python preview: http://127.0.0.1:$pythonPortText",
                            color = palette.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text("Runs trusted WSGI applications from wsgi.py. Loopback by default; LAN access follows the explicit hosting setting. Python project code runs with app privileges.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("CUSTOM HOSTNAME ROUTING", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        Text("Map a domain to an imported website or backend. Configure Cloudflare's public hostname origin to the Rust static server port.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                        OutlinedTextField(value = hostnameText, onValueChange = { hostnameText = it.trim().lowercase() },
                            label = { Text("Hostname, e.g. app.example.com") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("static", "python-wsgi", "node").forEach { runtime ->
                                FilterChip(selected = hostnameRuntime == runtime, onClick = { hostnameRuntime = runtime },
                                    label = { Text(if (runtime == "python-wsgi") "Python" else runtime.replaceFirstChar { it.uppercase() }) })
                            }
                        }
                        Button(onClick = {
                            val host = hostnameText.trim().trimEnd('.').lowercase()
                            val preferences = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
                            val root = when (hostnameRuntime) {
                                "static" -> File(context.filesDir, "site").absolutePath
                                "python-wsgi" -> preferences.getString("python_project_root", File(context.filesDir, "python-project").absolutePath) ?: ""
                                else -> preferences.getString("node_project_root", File(context.filesDir, "node-project-default").absolutePath) ?: ""
                            }
                            val id = when (hostnameRuntime) { "static" -> "static-default"; "python-wsgi" -> "python-default"; else -> "node-default" }
                            val name = when (hostnameRuntime) { "static" -> "Static website"; "python-wsgi" -> "Python application"; else -> "Node application" }
                            runCatching {
                                val current = SiteRegistry.list(context).firstOrNull { it.id == id }
                                SiteRegistry.put(context, SiteRegistry.Site(id, name, hostnameRuntime, root,
                                    (current?.hostnames.orEmpty() + host).distinct(), true))
                                if (preferences.getBoolean("python_enabled", false)) {
                                    runCatching { PythonHostRuntime.stop(context) }
                                    val pythonHosts = SiteRegistry.pythonHostnameMap(context)
                                    val pythonRoot = preferences.getString("python_project_root", File(context.filesDir, "python-project").absolutePath)
                                        ?: File(context.filesDir, "python-project").absolutePath
                                    if (pythonHosts.length() > 0) PythonHostRuntime.startSites(context, pythonHosts.toString(),
                                        preferences.getInt("python_port", 8081), preferences.getBoolean("allow_lan", false))
                                    else PythonHostRuntime.start(context, pythonRoot, preferences.getInt("python_port", 8081),
                                        preferences.getBoolean("allow_lan", false))
                                }
                                if (hosting) {
                                    MetricsStore.recordSample(context, NativeEngine.requestCount(), NativeEngine.bytesServed())
                                    NativeEngine.stop()
                                    val routes = SiteRegistry.hostRoutingJson(context, preferences.getInt("python_port", 8081), preferences.getInt("node_port", 3000))
                                    val result = if (routes.length() == 0) NativeEngine.start(File(context.filesDir, "site").absolutePath,
                                        preferences.getInt("port", 8080), preferences.getBoolean("allow_lan", false))
                                    else NativeEngine.startMultiSite(File(context.filesDir, "site").absolutePath, routes.toString(),
                                        preferences.getInt("port", 8080), preferences.getBoolean("allow_lan", false))
                                    check(result == "OK") { result }
                                }
                            }.onSuccess {
                                hostnameText = ""
                                status = "Hostname route saved. It is active now if hosting is running."
                            }.onFailure { status = it.message ?: "Could not save hostname route." }
                        }, enabled = hostnameText.isNotBlank() && !importing) { Text("Save hostname route") }
                        SiteRegistry.list(context).filter { it.hostnames.isNotEmpty() }.forEach { site ->
                            Text(site.name + " · " + site.hostnames.joinToString(), color = palette.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("NODE.JS 24 LTS · EMBEDDED RUNTIME", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(nodeProjectSummary, color = palette.onSurfaceVariant, fontSize = 14.sp)
                        OutlinedButton(onClick = { nodeProjectPicker.launch(null) }, enabled = !importing && !nodeHosting,
                            modifier = Modifier.fillMaxWidth()) { Text("Import Node.js project") }
                        OutlinedTextField(value = nodePortText,
                            onValueChange = { value -> if (value.length <= 5 && value.all(Char::isDigit)) nodePortText = value },
                            label = { Text("Node.js HTTP port") }, enabled = !nodeHosting, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = {
                                val port = nodePortText.toIntOrNull()
                                val root = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
                                    .getString("node_project_root", File(context.filesDir, "node-project-default").absolutePath)
                                    ?: File(context.filesDir, "node-project-default").absolutePath
                                val preferences = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
                                val staticPort = preferences.getInt("port", 8080)
                                val pythonPort = preferences.getInt("python_port", 8081)
                                if (port == null || port !in 1024..65535 || port == staticPort || port == pythonPort) {
                                    status = "Choose a valid Node port different from the static and Python ports."
                                } else {
                                    preferences.edit().putInt("node_port", port).putBoolean("node_enabled", true)
                                        .putString("node_project_root", root).apply()
                                    startHosting(false)
                                    status = "Node.js runtime requested. The foreground supervisor will launch the isolated process."
                                }
                            }, enabled = !nodeHosting && !importing) { Text("Start Node.js") }
                            OutlinedButton(onClick = {
                                context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit().putBoolean("node_enabled", false).apply()
                                NodeHostRuntime.stop(context)
                                nodeHosting = false
                                status = "Stopping the isolated Node.js process."
                            }, enabled = nodeHosting) { Text("Stop Node.js") }
                        }
                        if (nodeHosting) Text("Node.js preview: http://127.0.0.1:$nodePortText", color = palette.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Runs one Node.js application in a separate Android process under the same app security identity. This is not a sandbox. Include preinstalled node_modules; subprocess-based npm scripts and native addons are not guaranteed. User code runs with app privileges.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                    }
                }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.surface)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("CONNECTION & SECURITY", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            Text("Public HTTPS is provided at the Cloudflare edge for your existing configured hostname; the phone's local origin remains HTTP.", color = palette.onSurfaceVariant)
                            Text("Pocket Host does not currently provide built-in visitor password protection. Use Cloudflare Access for private sites before sharing the public hostname.", color = palette.onSurfaceVariant)
                            Text("Automatic start on boot is not available in this build. Android background and OEM power policies still apply.", color = palette.onSurfaceVariant)
                            OutlinedButton(onClick = { selectedTab = "Files" }, modifier = Modifier.fillMaxWidth()) {
                                Text("Open backup and restore")
                            }
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.surface)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("ABOUT", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            Text("AssayFlux Pocket Host", color = palette.onSurface, fontWeight = FontWeight.Bold)
                            Text("Version 0.1.0 · Designed by AssayFlux", color = palette.onSurfaceVariant)
                            Text("Native Android website hosting powered by Rust, Kotlin and optional trusted Python/Node runtimes.", color = palette.onSurfaceVariant)
                        }
                    }
                }
                if (selectedTab == "Dashboard") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("DEVICE HEALTH & LOCAL PREVIEW", fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                        Text("Battery: ${if (deviceTelemetry.isNull("batteryPercent")) "Unknown" else deviceTelemetry.optInt("batteryPercent").toString() + "%"} · Temperature: ${deviceTelemetry.optString("batteryTemperatureC", "Unavailable")} · Network: ${deviceTelemetry.optString("networkTransport", "Unknown")}", color = palette.onSurfaceVariant)
                        if ((!deviceTelemetry.isNull("batteryPercent") && deviceTelemetry.optInt("batteryPercent") < 20) ||
                            deviceTelemetry.optString("thermalLabel").uppercase() in listOf("SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN")) {
                            Text("Device protection warning: battery is low or thermal pressure is high. Reduce hosting load and allow the phone to cool.", color = palette.error, fontWeight = FontWeight.SemiBold)
                        }
                        val localSiteUrl = if (allowLan && lanAddress != null) "http://$lanAddress:${portText.toIntOrNull() ?: 8080}/" else "http://127.0.0.1:${portText.toIntOrNull() ?: 8080}/"
                        Text(localSiteUrl, color = palette.onSurface, fontFamily = FontFamily.Monospace)
                        val configuredPublicHost = SiteRegistry.list(context).flatMap { it.hostnames }.firstOrNull()
                        if (tunnelStatus == "CONNECTED" && configuredPublicHost != null) {
                            Text("Public URL: https://$configuredPublicHost", color = palette.onSurface,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Pocket Host site URL", localSiteUrl))
                                status = "Local site URL copied."
                            }) { Text("Copy URL") }
                            OutlinedButton(onClick = {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, localSiteUrl)
                                }
                                context.startActivity(Intent.createChooser(share, "Share site URL"))
                            }) { Text("Share URL") }
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Allow local network access", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                                Text("Lets devices on your current Wi-Fi open this site. No visitor authentication is provided.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                            }
                            Switch(checked = allowLan, onCheckedChange = { enabled ->
                                allowLan = enabled
                                context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                    .putBoolean("allow_lan", enabled).apply()
                            }, enabled = !hosting)
                        }
                        Text(if (allowLan) "LAN mode enabled · port $portText · ${lanAddress ?: "Connect to Wi-Fi to view this phone’s address"} · restart hosting after changing this setting." else "Loopback only · LAN and public access disabled.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(context, SitePreviewActivity::class.java)) },
                            enabled = hosting
                        ) { Text("Preview site") }
                    }
                }
                }
            }
        }
    }
}
}
