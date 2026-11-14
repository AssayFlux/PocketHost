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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.assayflux.pockethost.engine.NativeEngine
import com.assayflux.pockethost.service.HostingService
import com.assayflux.pockethost.storage.SiteImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var portText by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getInt("port", 8080).toString()) }
    var allowLan by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getBoolean("allow_lan", false)) }
    val lanAddress = remember(allowLan, hosting) { if (allowLan) localLanAddress() else null }
    var status by remember { mutableStateOf("Ready to configure your first website.") }
    var requests by remember { mutableLongStateOf(0L) }
    var bytesServed by remember { mutableLongStateOf(0L) }
    var activeConnections by remember { mutableLongStateOf(0L) }
    var importedSummary by remember {
        mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
            .getString("site_summary", "Bundled sample website is available.") ?: "Bundled sample website is available.")
    }
    var previousReleases by remember { mutableStateOf(SiteImporter.listReleases(context)) }

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
            requests = runCatching { NativeEngine.requestCount() }.getOrDefault(0L)
            bytesServed = runCatching { NativeEngine.bytesServed() }.getOrDefault(0L)
            activeConnections = runCatching { NativeEngine.activeConnections() }.getOrDefault(0L)
            preferences.getString("service_status", null)?.let { status = it }
            delay(1_000)
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

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Ivory) {
            Column(
                Modifier.fillMaxSize().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("DESIGNED BY ASSAYFLUX", color = Bronze, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("PocketHost", color = Teal, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Your website. Your device. Your control.", color = Slate)
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
                            Text("Engine limits: 32 concurrent connections · 50 MiB maximum file · two Tokio workers.", color = Slate, fontSize = 11.sp)
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
                        Text("LIVE TRAFFIC · THIS ENGINE SESSION", color = Teal, fontWeight = FontWeight.SemiBold)
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
                                Text(if (bytesServed < 1024 * 1024) "${bytesServed / 1024} KB" else "${bytesServed / (1024 * 1024)} MB", color = Teal, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("Counters reset when the hosting engine restarts. Requests are counted inside the Rust server.", color = Slate, fontSize = 10.sp)
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
                Spacer(Modifier.weight(1f))
                Text(if (advancedMode) "ADVANCED MODE · ENGINE DETAILS" else "SIMPLE MODE · SAFE STATIC SITE HOSTING", color = Bronze, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Public tunnel, Python and Node.js are not available in this build. LAN access is optional and limited to your local network.", color = Slate, fontSize = 11.sp)
                Text("AssayFlux PocketHost · v0.1.0", color = Slate, fontSize = 11.sp)
            }
        }
    }
}
