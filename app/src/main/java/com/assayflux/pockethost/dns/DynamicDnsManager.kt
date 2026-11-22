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
    var token by remember { mutableStateOf("") }
    var tokenPresent by remember { mutableStateOf(CloudflareApiTokenStore.hasToken(context)) }
    var domain by remember { mutableStateOf(prefs.getString("domain", "") ?: "") }
    var host by remember { mutableStateOf(prefs.getString("host", "@") ?: "@") }
    var includeIpv4 by remember { mutableStateOf(prefs.getBoolean("ipv4", true)) }
    var includeIpv6 by remember { mutableStateOf(prefs.getBoolean("ipv6", true)) }
    var proxied by remember { mutableStateOf(prefs.getBoolean("proxied", true)) }
    var detected by remember { mutableStateOf<DetectedPublicAddresses?>(null) }
    var status by remember { mutableStateOf("Detect public addresses, then sync selected DNS records.") }
    var busy by remember { mutableStateOf(false) }

    fun currentToken(): String = CloudflareApiTokenStore.read(context)
        ?: throw IllegalStateException("Save your Cloudflare DNS API token in DNS Management first.")

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DYNAMIC DNS", fontWeight = FontWeight.Bold)
                Text("Discover public IPv4/IPv6 addresses and synchronize Cloudflare A/AAAA records independently. This updates records matching the selected hostname; review existing DNS ownership before syncing.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(domain, { domain = it.trim(); prefs.edit().putString("domain", domain).apply() }, label = { Text("Cloudflare zone (e.g. example.com)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(host, { host = it.trim(); prefs.edit().putString("host", host).apply() }, label = { Text("Record name (@ or www)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(includeIpv4, { includeIpv4 = it; prefs.edit().putBoolean("ipv4", it).apply() }); Text("IPv4 (A)")
                    Spacer(Modifier.width(12.dp))
                    Checkbox(includeIpv6, { includeIpv6 = it; prefs.edit().putBoolean("ipv6", it).apply() }); Text("IPv6 (AAAA)")
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(proxied, { proxied = it; prefs.edit().putBoolean("proxied", it).apply() }); Text("Proxy eligible records through Cloudflare")
                }
                OutlinedTextField(token, { token = it }, label = { Text(if (tokenPresent) "Replace saved DNS API token (optional)" else "Cloudflare DNS API token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = token.isNotBlank() && !busy, onClick = {
                    runCatching { CloudflareApiTokenStore.save(context, token) }
                        .onSuccess { token = ""; tokenPresent = true; status = "Encrypted Cloudflare API token saved." }
                        .onFailure { status = it.message ?: "Could not save token." }
                }) { Text("Save API token securely") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PUBLIC ADDRESS", fontWeight = FontWeight.SemiBold)
                Text("IPv4: ${detected?.ipv4 ?: "Not detected"}")
                Text("IPv6: ${detected?.ipv6 ?: "Not detected on this network"}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            val result = withContext(Dispatchers.IO) { PublicAddressDetector.detect() }
                            detected = result
                            status = if (result.ipv4 == null && result.ipv6 == null) "No public address detected. Check internet connectivity and IPv6 availability." else "Address detection completed."
                            busy = false
                        }
                    }) { Text("Detect IP") }
                    Button(enabled = !busy && domain.isNotBlank() && host.isNotBlank() && tokenPresent && (includeIpv4 || includeIpv6), onClick = {
                        scope.launch {
                            busy = true
                            status = "Detecting addresses and synchronizing DNS…"
                            try {
                                val addresses = withContext(Dispatchers.IO) { PublicAddressDetector.detect() }
                                detected = addresses
                                val outcomes = withContext(Dispatchers.IO) {
                                    val apiToken = currentToken()
                                    val zone = CloudflareDnsApi.findZone(apiToken, domain)
                                    val fqdn = if (host == "@") zone.name else "${host.trimEnd('.').lowercase()}.${zone.name}"
                                    val existing = CloudflareDnsApi.records(apiToken, zone.id)
                                    val results = mutableListOf<String>()
                                    fun syncFamily(enabled: Boolean, type: String, address: String?) {
                                        if (!enabled) return
                                        try {
                                            val ip = address ?: throw IllegalStateException("No public address detected; record left unchanged.")
                                            val matches = existing.filter { it.type == type && it.name.equals(fqdn, true) }
                                            require(matches.size <= 1) {
                                                "Found multiple $type records for $fqdn. Resolve the duplicates in Cloudflare; none were changed."
                                            }
                                            val record = matches.singleOrNull()
                                            CloudflareDnsApi.save(apiToken, zone, record?.id, type, host, ip, proxied, "10")
                                            results += "$type updated to $ip"
                                        } catch (failure: Exception) {
                                            results += "$type not updated: ${failure.message ?: "unknown error"}"
                                        }
                                    }
                                    syncFamily(includeIpv4, "A", addresses.ipv4)
                                    syncFamily(includeIpv6, "AAAA", addresses.ipv6)
                                    results
                                }
                                status = outcomes.joinToString("\n").ifBlank { "No address families were selected." }
                            } catch (error: Exception) {
                                status = error.message ?: "DNS synchronization failed."
                            } finally { busy = false }
                        }
                    }) { Text("Sync DNS now") }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("This is on-demand synchronization only. It does not prove inbound reachability or bypass CGNAT, carrier firewalls or blocked ports. Background scheduling, ownership tracking and history remain subsequent work.", style = MaterialTheme.typography.bodySmall)
    }
}
