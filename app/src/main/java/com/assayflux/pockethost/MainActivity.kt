package com.assayflux.pockethost

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.assayflux.pockethost.service.HostingService
import com.assayflux.pockethost.storage.SiteImporter
import com.assayflux.pockethost.storage.PythonProjectImporter
import com.assayflux.pockethost.storage.SiteBackupManager
import com.assayflux.pockethost.runtime.PythonHostRuntime
import com.assayflux.pockethost.management.ManagementApiServer
import com.assayflux.pockethost.tunnel.CloudflareTokenStore
import com.assayflux.pockethost.tunnel.CloudflareTunnelRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

private val Teal = Color(0xFF24434A)
private val Bronze = Color(0xFFB18B5E)
private val Ivory = Color(0xFFF6F4EF)
private val Slate = Color(0xFF657174)

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
    var cloudflareTokenText by remember { mutableStateOf("") }
    var backupPassphrase by remember { mutableStateOf("") }
    var cloudflareTokenSaved by remember { mutableStateOf(CloudflareTokenStore.hasToken(context)) }
    var tunnelStatus by remember { mutableStateOf(CloudflareTunnelRuntime.status()) }
    var pendingPairingCode by remember { mutableStateOf<String?>(null) }

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
        while (true) {
            hosting = runCatching { NativeEngine.status() == "RUNNING" }.getOrDefault(false)
            val metrics = withContext(Dispatchers.IO) { MetricsStore.snapshot(context) }
            requests = metrics.totalRequests
            bytesServed = metrics.totalBytes
            todayRequests = metrics.todayRequests
            todayBytes = metrics.todayBytes
            activeConnections = runCatching { NativeEngine.activeConnections() }.getOrDefault(0L)
            pythonHosting = runCatching { PythonHostRuntime.status(context) == "RUNNING" }.getOrDefault(false)
            tunnelStatus = CloudflareTunnelRuntime.status()
            pendingPairingCode = ManagementApiServer.pendingApprovalCode()
            preferences.getString("service_status", null)?.let { status = it }
            recentEvents = withContext(Dispatchers.IO) { OperationalLog.read(context, 500) }
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
                    status = "Python project is ready. Start the Python backend to run its wsgi.py application."
                }.onFailure { error -> status = error.message ?: "Python project import failed." }
                importing = false
            }
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Teal,
            onPrimary = Ivory,
            secondary = Bronze,
            onSecondary = Color.White,
            background = Ivory,
            onBackground = Teal,
            surface = Color.White,
            onSurface = Teal,
            outline = Color(0xFFD9D3C8)
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Ivory) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("DESIGNED BY ASSAYFLUX", color = Bronze, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("PocketHost", color = Teal, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Your website. Your device. Your control.", color = Slate)
                if (pendingPairingCode != null) {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("CONSOLE PAIRING REQUEST", color = Teal, fontWeight = FontWeight.Bold)
                            Text("Code: $pendingPairingCode", color = Teal, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("Only approve this if you initiated pairing from your own trusted browser.", color = Slate)
                            Button(onClick = {
                                val approved = ManagementApiServer.approvePairing(pendingPairingCode.orEmpty())
                                if (approved) { status = "Management console pairing approved."; pendingPairingCode = null }
                                else status = "Pairing request expired. Start pairing again."
                            }) { Text("Approve console pairing") }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !advancedMode, onClick = { advancedMode = false }, label = { Text("Simple") })
                    FilterChip(selected = advancedMode, onClick = { advancedMode = true }, label = { Text("Advanced") })
                }
                if (advancedMode) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("SERVER CONFIGURATION", color = Teal, fontWeight = FontWeight.SemiBold)
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
                            Text("Engine limits: 16 concurrent connections · 50 MiB maximum file · two Tokio workers.", color = Slate, fontSize = 11.sp)
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Keep CPU awake while hosting", color = Teal, fontWeight = FontWeight.SemiBold)
                                    Text("Optional screen-off reliability aid. Uses more battery and cannot override every OEM network policy.", color = Slate, fontSize = 10.sp)
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
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("HOSTING STATUS", color = Slate, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(if (hosting) "Local hosting active" else "Stopped", color = Teal, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Text(status, color = Slate, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        startHosting(false)
                                    }
                                },
                                enabled = !hosting && !importing
                            ) { Text("Start hosting") }
                            OutlinedButton(
                                onClick = {
                                    context.stopService(Intent(context, HostingService::class.java))
                                    status = "Stopping hosting service…"
                                },
                                enabled = hosting
                            ) { Text("Stop") }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("STATIC ENGINE TRAFFIC · ALL HOSTING SESSIONS", color = Teal, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Requests", color = Slate, fontSize = 11.sp)
                                Text(requests.toString(), color = Teal, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Active", color = Slate, fontSize = 11.sp)
                                Text(activeConnections.toString(), color = Teal, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Transferred", color = Slate, fontSize = 11.sp)
                                Text(formatBytes(bytesServed), color = Teal, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("Today: $todayRequests requests · ${formatBytes(todayBytes)} transferred.", color = Slate, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("Static-engine totals persist across service restarts. Python WSGI and tunnel traffic are not included yet.", color = Slate, fontSize = 10.sp)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("RECENT OPERATIONS", color = Teal, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = logFilter,
                            onValueChange = { logFilter = it },
                            label = { Text("Filter events, levels or messages") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val filteredEvents = recentEvents.filter { event ->
                            logFilter.isBlank() ||
                                event.level.contains(logFilter, ignoreCase = true) ||
                                event.event.contains(logFilter, ignoreCase = true) ||
                                event.message.contains(logFilter, ignoreCase = true)
                        }.take(8)
                        if (filteredEvents.isEmpty()) {
                            Text(if (recentEvents.isEmpty()) "No operational events recorded yet." else "No events match this filter.", color = Slate, fontSize = 12.sp)
                        } else {
                            filteredEvents.forEach { event ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(event.level, color = if (event.level == "ERROR") Color(0xFF9C3D35) else Bronze, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(event.event.replace('_', ' '), color = Teal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.weight(1f))
                                        Text(event.timestamp.take(19).replace('T', ' '), color = Slate, fontSize = 9.sp)
                                    }
                                    if (event.message.isNotBlank()) Text(event.message, color = Slate, fontSize = 11.sp)
                                }
                            }
                        }
                        Text("App-private rotating log · credentials and bearer tokens are redacted.", color = Slate, fontSize = 10.sp)
                        OutlinedButton(
                            onClick = { logExportPicker.launch("pockethost-operations.jsonl") },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Export redacted logs") }
                        OutlinedButton(
                            onClick = { diagnosticsExportPicker.launch("pockethost-diagnostics.zip") },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Export diagnostics bundle") }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("WEBSITE", fontWeight = FontWeight.SemiBold, color = Teal)
                        Text(importedSummary, color = Slate, fontSize = 13.sp)
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
                        Text("Imports are staged and validated before replacing the current website. Maximum import: 50 MB, 5,000 files and 10,000 total entries.", color = Slate, fontSize = 11.sp)
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
                        Text("Backups use AES-256-GCM with a passphrase-derived key. Only the static website is included; Python projects, tunnel credentials and device settings are excluded. Restores are authenticated and validated before deployment.", color = Slate, fontSize = 10.sp)
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
                            Text("${previousReleases.size} previous release(s) retained · latest first", color = Slate, fontSize = 11.sp)
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("CLOUDFLARE TUNNEL", color = Teal, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (tunnelStatus) {
                                "CONNECTED" -> "Tunnel connected to Cloudflare."
                                "CONNECTING" -> "Tunnel process started; waiting for an active edge connection."
                                "ERROR" -> "Tunnel process stopped with an error. Check the service status."
                                else -> if (cloudflareTokenSaved) "Named tunnel token is encrypted on this device." else "No tunnel token saved."
                            },
                            color = Slate, fontSize = 12.sp
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
                            "Create a remotely managed tunnel in Cloudflare Zero Trust, then configure its Public Hostname origin as http://127.0.0.1:${portText.toIntOrNull() ?: 8080}. DNS and hostname setup remain in the Cloudflare dashboard.",
                            color = Slate, fontSize = 10.sp
                        )
                        Text(
                            "Public exposure is not visitor-authenticated by PocketHost. Add Cloudflare Access before exposing private sites. Tunnel connector and ABI support still require physical-device validation.",
                            color = Slate, fontSize = 10.sp
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("PYTHON BACKEND · CPYTHON 3.11", color = Teal, fontWeight = FontWeight.SemiBold)
                        Text(pythonProjectSummary, color = Slate, fontSize = 12.sp)
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
                                        if (hosting) {
                                            scope.launch {
                                                val started = runCatching {
                                                    withContext(Dispatchers.IO) { PythonHostRuntime.start(context, root, port, allowLan) }
                                                }
                                                started.onSuccess { status = "Python WSGI backend $it on port $port." }
                                                    .onFailure { status = it.message ?: "Python backend failed to start." }
                                            }
                                        } else {
                                            startHosting(false)
                                        }
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
                            color = Teal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text("Runs trusted WSGI applications from wsgi.py. Loopback by default; LAN access follows the explicit hosting setting. Python project code runs with app privileges.", color = Slate, fontSize = 10.sp)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEAE3D2))
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("LOCAL PREVIEW", fontWeight = FontWeight.SemiBold, color = Teal)
                        Text(if (allowLan && lanAddress != null) "http://$lanAddress:$portText" else "http://127.0.0.1:$portText", color = Teal)
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Allow local network access", color = Teal, fontWeight = FontWeight.SemiBold)
                                Text("Lets devices on your current Wi-Fi open this site. No visitor authentication is provided.", color = Slate, fontSize = 11.sp)
                            }
                            Switch(checked = allowLan, onCheckedChange = { enabled ->
                                allowLan = enabled
                                context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                    .putBoolean("allow_lan", enabled).apply()
                            }, enabled = !hosting)
                        }
                        Text(if (allowLan) "LAN mode enabled · port $portText · ${lanAddress ?: "Connect to Wi-Fi to view this phone’s address"} · restart hosting after changing this setting." else "Loopback only · LAN and public access disabled.", color = Slate, fontSize = 12.sp)
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:${portText.toIntOrNull() ?: 8080}"))) },
                            enabled = hosting
                        ) { Text("Open preview") }
                    }
                }
                Text(if (advancedMode) "ADVANCED MODE · ENGINE DETAILS" else "SIMPLE MODE · SAFE STATIC SITE HOSTING", color = Bronze, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Cloudflare Tunnel integration is under validation. Python WSGI is available; Node.js hosting and remote console control are not yet implemented. Public access requires a configured Cloudflare hostname and an access policy for private content.", color = Slate, fontSize = 11.sp)
                Text("AssayFlux PocketHost · v0.1.0", color = Slate, fontSize = 11.sp)
            }
        }
    }
}
