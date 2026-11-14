package com.assayflux.pockethost

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.assayflux.pockethost.service.HostingService

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
    var hosting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready to start local hosting") }
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Ivory) {
            Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
                        Text(if (hosting) "Starting / local hosting" else "Stopped", color = Teal, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Text(status, color = Slate, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = {
                                val intent = Intent(context, HostingService::class.java)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent)
                                else context.startService(intent)
                                hosting = true
                                status = "Starting local server on port 8080"
                            }, enabled = !hosting) { Text("Start hosting") }
                            OutlinedButton(onClick = {
                                context.stopService(Intent(context, HostingService::class.java))
                                hosting = false
                                status = "Hosting stopped"
                            }, enabled = hosting) { Text("Stop") }
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
                        Text("http://127.0.0.1:8080", color = Teal)
                        Text("Loopback only. LAN and public access are not enabled in this prototype.", color = Slate, fontSize = 12.sp)
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:8080")))
                        }, enabled = hosting) { Text("Open preview") }
                    }
                }
                Text("PHASE 0 · RUST ENGINE + ANDROID BRIDGE", color = Bronze, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Cloudflare Tunnel, Python and Node.js are not integrated yet; each requires Android compatibility validation.", color = Slate, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text("AssayFlux PocketHost · v0.1.0 prototype", color = Slate, fontSize = 11.sp)
            }
        }
    }
}
