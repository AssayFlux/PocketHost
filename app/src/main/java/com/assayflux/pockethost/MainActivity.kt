package com.assayflux.pockethost

import android.content.Context
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

private val Teal = Color(0xFF24434A)
private val Bronze = Color(0xFFB18B5E)
private val Ivory = Color(0xFFF6F4EF)
private val Slate = Color(0xFF657174)

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
    var status by remember { mutableStateOf("Ready to configure your first website.") }
    var importedSummary by remember {
        mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
            .getString("site_summary", "Bundled sample website is available.") ?: "Bundled sample website is available.")
    }

    LaunchedEffect(Unit) {
        val preferences = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        while (true) {
            hosting = runCatching { NativeEngine.status() == "RUNNING" }.getOrDefault(false)
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
                                    val intent = Intent(context, HostingService::class.java)
                                    runCatching {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent)
                                        else context.startService(intent)
                                    }.onSuccess {
                                        status = "Starting local server on port 8080…"
                                    }.onFailure {
                                        status = it.message ?: "Could not start hosting service."
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
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEAE3D2))
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("LOCAL PREVIEW", fontWeight = FontWeight.SemiBold, color = Teal)
                        Text("http://127.0.0.1:8080", color = Teal)
                        Text("Loopback only. LAN and public access are not enabled.", color = Slate, fontSize = 12.sp)
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:8080"))) },
                            enabled = hosting
                        ) { Text("Open preview") }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("PHASE 1 · SAFE STATIC SITE IMPORT", color = Bronze, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Cloudflare Tunnel, Python and Node.js remain unavailable until their Android integrations are validated.", color = Slate, fontSize = 11.sp)
                Text("AssayFlux PocketHost · v0.1.0", color = Slate, fontSize = 11.sp)
            }
        }
    }
}
