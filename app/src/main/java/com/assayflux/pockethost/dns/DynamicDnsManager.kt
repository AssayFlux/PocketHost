package com.assayflux.pockethost.dns

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL

internal data class DetectedPublicAddresses(val ipv4: String?, val ipv6: String?)

internal object PublicAddressDetector {
    private val ipv4Endpoints = listOf("https://api.ipify.org", "https://ipv4.icanhazip.com")
    private val ipv6Endpoints = listOf("https://api6.ipify.org", "https://ipv6.icanhazip.com")

    fun detect(): DetectedPublicAddresses = DetectedPublicAddresses(
        ipv4 = detectOne(ipv4Endpoints, false),
        ipv6 = detectOne(ipv6Endpoints, true)
    )

    private fun detectOne(endpoints: List<String>, ipv6: Boolean): String? {
        val observed = endpoints.mapNotNull { endpoint ->
            runCatching {
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    instanceFollowRedirects = false
                    setRequestProperty("Accept", "text/plain")
                }
                try {
                    if (connection.responseCode !in 200..299) return@runCatching null
                    val value = connection.inputStream.bufferedReader().use { it.readText().trim() }
                    if (value.isBlank() || value.length > 64) return@runCatching null
                    val address = InetAddress.getByName(value)
                    if (!isGloballyRoutable(address, ipv6)) return@runCatching null
                    address.hostAddress?.substringBefore('%')
                } finally { connection.disconnect() }
            }.getOrNull()
        }.distinct()
        return observed.singleOrNull()
    }

    internal fun isGloballyRoutable(address: InetAddress, ipv6: Boolean): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        if (ipv6) {
            if (address !is Inet6Address) return false
            val bytes = address.address
            val first = bytes[0].toInt() and 0xff
            if ((first and 0xe0) != 0x20) return false
            if ((bytes[0].toInt() and 0xff) == 0x20 && (bytes[1].toInt() and 0xff) == 0x01 &&
                (bytes[2].toInt() and 0xff) == 0x0d && (bytes[3].toInt() and 0xff) == 0xb8) return false
            return true
        }
        if (address !is java.net.Inet4Address) return false
        val p = address.address.map { it.toInt() and 0xff }
        if (p[0] == 0 || p[0] == 10 || p[0] == 127 || p[0] >= 224) return false
        if (p[0] == 100 && p[1] in 64..127) return false
        if (p[0] == 169 && p[1] == 254) return false
        if (p[0] == 172 && p[1] in 16..31) return false
        if (p[0] == 192 && p[1] == 168) return false
        if (p[0] == 192 && p[1] == 0 && p[2] == 2) return false
        if (p[0] == 198 && p[1] in 18..19) return false
        if (p[0] == 198 && p[1] == 51 && p[2] == 100) return false
        if (p[0] == 203 && p[1] == 0 && p[2] == 113) return false
        return true
    }
}

@Composable
internal fun DynamicDnsManagerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("pockethost_ddns", 0) }
    var provider by remember { mutableStateOf(prefs.getString("provider", "cloudflare") ?: "cloudflare") }
    var providerMenu by remember { mutableStateOf(false) }
    var dynadotPassword by remember { mutableStateOf("") }
    var dynadotPasswordPresent by remember { mutableStateOf(DynadotDdnsCredentialStore.hasPassword(context)) }
    var token by remember { mutableStateOf("") }
    var tokenPresent by remember { mutableStateOf(CloudflareApiTokenStore.hasToken(context)) }
    var domain by remember { mutableStateOf(prefs.getString("domain", "") ?: "") }
    var host by remember { mutableStateOf(prefs.getString("host", "@") ?: "@") }
    var includeIpv4 by remember { mutableStateOf(prefs.getBoolean("ipv4", true)) }
    var includeIpv6 by remember { mutableStateOf(prefs.getBoolean("ipv6", true)) }
    var proxied by remember { mutableStateOf(prefs.getBoolean("proxied", true)) }
    var autoSync by remember { mutableStateOf(DynamicDnsSync.isScheduled(context)) }
    var adoptExisting by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf<DetectedPublicAddresses?>(null) }
    var assessments by remember { mutableStateOf<List<AddressFamilyDiagnostics>>(emptyList()) }
    var allowExternalProbe by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(DynamicDnsSync.history(context).take(5)) }
    var status by remember { mutableStateOf("Detect both address families independently. Public IP detection does not prove inbound reachability.") }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DYNAMIC DNS", fontWeight = FontWeight.Bold)
                Text("Choose the authoritative DNS provider for this domain. Dynadot DDNS works only while the domain uses Dynadot nameservers; Cloudflare API updates work only while the domain is an active Cloudflare zone.", style = MaterialTheme.typography.bodySmall)
                ExposedDropdownMenuBox(expanded = providerMenu, onExpandedChange = { providerMenu = !providerMenu }) {
                    OutlinedTextField(
                        value = if (provider == "dynadot") "Dynadot DNS / DDNS" else "Cloudflare DNS API",
                        onValueChange = {}, readOnly = true, label = { Text("DNS provider") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenu) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                        DropdownMenuItem(text = { Text("Dynadot DNS / DDNS") }, onClick = {
                            provider = "dynadot"; prefs.edit().putString("provider", provider).putBoolean("auto_sync", false).apply(); autoSync = false; DynamicDnsSync.schedule(context, false); providerMenu = false
                        })
                        DropdownMenuItem(text = { Text("Cloudflare DNS API") }, onClick = {
                            provider = "cloudflare"; prefs.edit().putString("provider", provider).putBoolean("auto_sync", false).apply(); autoSync = false; DynamicDnsSync.schedule(context, false); providerMenu = false
                        })
                    }
                }
                if (provider == "dynadot") {
                    Text("In Dynadot: enable Dynadot DNS and Dynamic DNS for this domain, then generate its DDNS password. This is not your Dynadot account password. DDNS may replace conflicting DNS records for the selected hostname.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(dynadotPassword, { dynadotPassword = it },
                        label = { Text(if (dynadotPasswordPresent) "Replace saved Dynadot DDNS password (optional)" else "Dynadot domain DDNS password") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = dynadotPassword.isNotBlank() && !busy, onClick = {
                            runCatching { DynadotDdnsCredentialStore.save(context, dynadotPassword) }
                                .onSuccess { dynadotPassword = ""; dynadotPasswordPresent = true; status = "Encrypted Dynadot DDNS password saved." }
                                .onFailure { status = it.message ?: "Could not save Dynadot DDNS password." }
                        }) { Text("Save Dynadot password") }
                        OutlinedButton(enabled = dynadotPasswordPresent && !busy, onClick = {
                            runCatching { DynadotDdnsCredentialStore.clear(context) }
                                .onSuccess { dynadotPasswordPresent = false; dynadotPassword = ""; status = "Dynadot DDNS password removed." }
                                .onFailure { status = it.message ?: "Could not remove Dynadot password." }
                        }) { Text("Remove") }
                    }
                } else {
                    Text("Cloudflare API mode requires an active Cloudflare zone and a scoped token with Zone:Read and DNS:Edit. It is independent of the Tunnel token.", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(domain, {
                    domain = it.trim()
                    prefs.edit().putString("domain", domain).apply()
                }, label = { Text("Domain (e.g. example.com)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(host, {
                    host = it.trim()
                    prefs.edit().putString("host", host).apply()
                }, label = { Text("Record name (@ or www)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(includeIpv4, { includeIpv4 = it; prefs.edit().putBoolean("ipv4", it).apply() })
                    Text("IPv4 (A)")
                    Spacer(Modifier.width(12.dp))
                    Checkbox(includeIpv6, { includeIpv6 = it; prefs.edit().putBoolean("ipv6", it).apply() })
                    Text("IPv6 (AAAA)")
                }
                if (provider == "cloudflare") {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(proxied, { proxied = it; prefs.edit().putBoolean("proxied", it).apply() })
                        Text("Proxy eligible records through Cloudflare")
                    }
                }
                if (provider == "cloudflare") {
                    OutlinedTextField(token, { token = it }, label = {
                        Text(if (tokenPresent) "Replace saved DNS API token (optional)" else "Cloudflare DNS API token")
                    }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(enabled = token.isNotBlank() && !busy, onClick = {
                        runCatching { CloudflareApiTokenStore.save(context, token) }
                            .onSuccess { token = ""; tokenPresent = true; status = "Encrypted Cloudflare DNS API token saved." }
                            .onFailure { status = it.message ?: "Could not save token." }
                    }) { Text("Save API token securely") }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DUAL-STACK NETWORK DIAGNOSTICS", fontWeight = FontWeight.SemiBold)
                Text("IPv4: ${detected?.ipv4 ?: "Not detected"}")
                Text("IPv6: ${detected?.ipv6 ?: "Not detected"}")
                assessments.forEach { item ->
                    Divider()
                    Text(item.family, fontWeight = FontWeight.SemiBold)
                    Text(item.assessment, style = MaterialTheme.typography.bodySmall)
                    Text("Inbound reachability: ${item.inboundReachability}", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(allowExternalProbe, { allowExternalProbe = it })
                    Text("I consent to sending my public IP and gateway port to Check-Host.net for remote TCP probes")
                }
                Button(enabled = !busy && allowExternalProbe && detected != null &&
                    (detected?.ipv4 != null || detected?.ipv6 != null), onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val probePrefs = context.getSharedPreferences("pockethost_ddns", 0)
                            val now = System.currentTimeMillis()
                            val lastProbe = probePrefs.getLong("last_external_probe_ms", 0L)
                            require(now - lastProbe >= 60_000L) { "External probes are rate-limited to one run per minute." }
                            probePrefs.edit().putLong("last_external_probe_ms", now).apply()
                            val gatewayPort = context.getSharedPreferences("pockethost_state", 0).getInt("port", 8080)
                            val base = assessments.ifEmpty {
                                withContext(Dispatchers.IO) { NetworkDiagnostics.assess(context, detected!!) }
                            }
                            val checked = withContext(Dispatchers.IO) {
                                base.map { item ->
                                    val address = item.detectedPublicAddress
                                    val result = if (address == null) "No public address; probe skipped."
                                        else runCatching { NetworkDiagnostics.probeInbound(address, gatewayPort) }
                                            .getOrElse { "Probe failed: ${it.message ?: "unknown error"}" }
                                    item.copy(inboundReachability = result)
                                }
                            }
                            assessments = checked
                            status = "External TCP probes completed. Results are point-in-time and do not guarantee reachability from every network."
                        } catch (error: Exception) {
                            status = error.message ?: "External reachability probe failed."
                        } finally { busy = false }
                    }
                }) { Text("Probe inbound access externally") }
                Text("This sends each detected public address and the configured gateway port to Check-Host.net. Only run it if you consent. A failed probe may indicate CGNAT, firewall, routing or filtering; a successful probe confirms only the tested TCP port.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val result = withContext(Dispatchers.IO) { PublicAddressDetector.detect() }
                            detected = result
                            assessments = withContext(Dispatchers.IO) { NetworkDiagnostics.assess(context, result) }
                            status = "Address detection complete. Inbound access remains unverified without an independent external probe."
                        } catch (error: Exception) {
                            status = error.message ?: "Network diagnostics failed."
                        } finally { busy = false }
                    }
                }) { Text("Detect & assess IPv4 / IPv6") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AUTOMATIC DDNS", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(autoSync, enabled = domain.isNotBlank() && host.isNotBlank() &&
                        (if (provider == "dynadot") dynadotPasswordPresent else tokenPresent) && (includeIpv4 || includeIpv6), onCheckedChange = { enabled ->
                        autoSync = enabled
                        prefs.edit().putBoolean("auto_sync", enabled).apply()
                        DynamicDnsSync.schedule(context, enabled)
                        status = if (enabled) "Automatic DDNS scheduled every 15 minutes when network is available." else "Automatic DDNS paused."
                    })
                    Text("Enable background synchronization (15-minute minimum)")
                }
                Text("Background updates use Android WorkManager with network constraints and exponential retry. Android battery restrictions may delay execution.", style = MaterialTheme.typography.bodySmall)
                Text("Last successful A update: ${prefs.getString("last_success_A", "Never")}", style = MaterialTheme.typography.bodySmall)
                Text("Last successful AAAA update: ${prefs.getString("last_success_AAAA", "Never")}", style = MaterialTheme.typography.bodySmall)
                if (provider == "cloudflare") {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(adoptExisting, { adoptExisting = it })
                        Text("I explicitly authorize PocketHost to adopt one existing matching A/AAAA record if present")
                    }
                    Text("Leave this off to protect records created or managed outside PocketHost. Background sync never adopts records automatically.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Dynadot's DDNS endpoint directly updates the selected root/subdomain A or AAAA record. Review existing records first; its service may replace conflicting records.", style = MaterialTheme.typography.bodySmall)
                }
                Button(enabled = !busy && domain.isNotBlank() && host.isNotBlank() &&
                    (if (provider == "dynadot") dynadotPasswordPresent else tokenPresent) &&
                    (includeIpv4 || includeIpv6), onClick = {
                    scope.launch {
                        busy = true
                        status = "Detecting addresses, reconciling owned records and checking public DNS…"
                        try {
                            val outcomes = withContext(Dispatchers.IO) {
                                DynamicDnsSync.run(context, allowAdoptExisting = adoptExisting)
                            }
                            status = outcomes.joinToString("\n") { "${it.type}: ${it.message}" }
                            history = DynamicDnsSync.history(context).take(5)
                            detected = withContext(Dispatchers.IO) { PublicAddressDetector.detect() }
                            assessments = withContext(Dispatchers.IO) { NetworkDiagnostics.assess(context, detected!!) }
                        } catch (error: Exception) {
                            status = error.message ?: "DNS synchronization failed."
                        } finally { busy = false }
                    }
                }) { Text("Sync DNS now") }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("RECENT DDNS HISTORY", fontWeight = FontWeight.SemiBold)
                if (history.isEmpty()) Text("No synchronization history yet.", style = MaterialTheme.typography.bodySmall)
                history.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("History is stored locally and bounded to the latest 100 family results.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("A detected IP is not proof that visitors can reach this phone. IPv4 behind 100.64.0.0/10 is flagged as likely CGNAT; private IPv4 indicates upstream NAT but cannot identify whether the NAT is carrier-managed. IPv6 firewall policy also requires an external test. DNS answers are checked independently after each update.", style = MaterialTheme.typography.bodySmall)
    }
}
