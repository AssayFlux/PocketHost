package com.assayflux.pockethost

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class LearnTopic(val number: String, val title: String, val summary: String, val steps: List<String>, val note: String? = null)

@Composable
fun PocketHostLearnScreen(onOpenDashboard: () -> Unit, onOpenFiles: () -> Unit, onOpenSettings: () -> Unit) {
    val topics = listOf(
        LearnTopic("01", "Start your first website", "Get a static website online from your phone.", listOf("Open Files and choose Import website.", "Select a website folder or ZIP archive containing index.html.", "Review the imported file list and confirm the entry page.", "Return to Dashboard and tap Start hosting.", "Use Preview site to check the local website.")),
        LearnTopic("02", "Understand your website address", "Know the difference between local, LAN and public access.", listOf("Loopback addresses are only reachable on this phone.", "LAN access lets devices on the same local network reach the phone when enabled.", "A public hostname requires your existing Cloudflare Tunnel to be connected and routed to the Rust website listener.", "Never point a public hostname at the local management API."), "Cloudflare edge HTTPS does not automatically make a website private. Use Cloudflare Access for private sites."),
        LearnTopic("03", "Read the hosting dashboard", "Understand live status, traffic and device health.", listOf("Running reflects the native server status, not just a saved preference.", "Requests and bytes are static-engine totals; optional runtime and tunnel metrics may not be included.", "Battery, thermal and network readings describe this phone at the time of sampling.", "Android power management, connectivity and OEM policies can interrupt hosting.")),
        LearnTopic("04", "Measure safe capacity", "Use Capacity Lab to find a conservative device-specific connection budget.", listOf("Start hosting before running the local static GET benchmark.", "Run the benchmark when the phone is cool, adequately charged and otherwise idle.", "Review measured latency and the selected safe concurrency ceiling.", "Use a temporary capacity profile when you want to limit admission.", "Cancel a benchmark if the phone heats up or becomes unresponsive."), "A capacity percentage changes the admission budget. It is not CPU usage, a visitor-count promise or a guarantee of sustained throughput."),
        LearnTopic("05", "Use Python and Node carefully", "Optional runtimes are for trusted application code.", listOf("Import a compatible Python WSGI project or a prebundled Node project.", "Review runtime configuration and dependencies before starting it.", "Keep optional application routes private until their security requirements are met.", "Stop unused runtimes to conserve memory, battery and thermal headroom."), "Python and Node run under the separate-UID companion package, not the host app UID. Both runtimes still share the companion UID; per-project filesystem isolation and enforceable outbound network restrictions are incomplete. This is not a hostile-code sandbox. Run trusted owner-controlled code only."),
        LearnTopic("06", "Inspect activity and diagnose issues", "Use operational events and exports to understand problems.", listOf("Open Activity to inspect recent operational events.", "Filter for errors and search relevant event names or messages.", "Export redacted logs or a diagnostics bundle before requesting support.", "Do not share secrets, tunnel tokens, passwords or private website data.")),
        LearnTopic("07", "Back up and restore", "Protect your website and trusted runtime projects.", listOf("Open Files and find the backup and restore controls.", "Choose a strong passphrase and store it somewhere safe.", "Keep the exported encrypted backup in a separate trusted location.", "Restore only a backup you trust and verify the website before restarting hosting."), "The encrypted backup includes static website files, optional Python/Node project roots, hostname routes and selected non-secret settings. It excludes tunnel/remote credentials, accounts, session tokens and device-bound keys. Restored runtimes remain stopped until you review and enable them."),
        LearnTopic("08", "Keep the phone safe", "A phone is a small server with real power and thermal limits.", listOf("Keep the phone ventilated and avoid direct sunlight.", "Monitor battery, temperature, free storage and available memory.", "Use conservative capacity settings for sustained workloads.", "Expect hosting interruptions from Android, OEM power management or network changes.", "Do not promise 24/7 uptime or unlimited traffic."))
    )
    val palette = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Learn Pocket Host", color = palette.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Quick guides for running a website from your phone.", color = palette.onSurfaceVariant, fontSize = 14.sp)
        }
        Card(colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("QUICK START", color = palette.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Import a website → Start hosting → Preview it → Configure your existing public hostname when ready.", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton(onClick = onOpenFiles) { Text("Open Files") }; TextButton(onClick = onOpenDashboard) { Text("Open Dashboard") } }
            }
        }
        topics.forEach { topic ->
            val isExpanded = expanded == topic.number
            Card(onClick = { expanded = if (isExpanded) "" else topic.number }, colors = CardDefaults.cardColors(containerColor = palette.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(topic.number, color = palette.primary, fontWeight = FontWeight.Bold)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(topic.title, color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            Text(topic.summary, color = palette.onSurfaceVariant, fontSize = 13.sp)
                        }
                        Text(if (isExpanded) "−" else "+", color = palette.onSurfaceVariant, fontSize = 20.sp)
                    }
                    if (isExpanded) {
                        topic.steps.forEachIndexed { index, step ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Text("${index + 1}.", color = palette.onSurfaceVariant, fontWeight = FontWeight.SemiBold); Text(step, color = palette.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f)) }
                        }
                        topic.note?.let { Text(it, color = palette.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("NEED TO CHANGE A SETTING?", color = palette.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Open Settings for hosting options, connection information, backup guidance and app details.", color = palette.onSurface)
                TextButton(onClick = onOpenSettings) { Text("Open Settings") }
            }
        }
    }
}
