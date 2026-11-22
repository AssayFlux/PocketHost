package com.assayflux.pockethost.dns

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class CloudflareZone(val id: String, val name: String)
internal data class CloudflareDnsRecord(
    val id: String, val type: String, val name: String, val content: String,
    val ttl: Int, val proxied: Boolean, val priority: Int?
)

/** DNS API credentials are distinct from the Cloudflare Tunnel token. */
internal object CloudflareApiTokenStore {
    private const val PREFS = "pockethost_secure"
    private const val TOKEN = "cloudflare_dns_api_token"
    private const val ALIAS = "assayflux.pockethost.cloudflare.dns-token.v1"

    fun hasToken(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(TOKEN)

    @Synchronized fun save(context: Context, token: String) {
        val value = token.trim()
        require(value.length in 20..4096 && !value.any(Char::isWhitespace)) { "Enter a valid scoped Cloudflare API token." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val packed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(TOKEN, Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) {
            "Could not save the encrypted Cloudflare API token."
        }
    }

    @Synchronized fun read(context: Context): String? {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TOKEN, null) ?: return null
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > 28) { "Stored Cloudflare API token is invalid." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        return cipher.doFinal(packed.copyOfRange(12, packed.size)).toString(Charsets.UTF_8)
    }

    @Synchronized fun clear(context: Context) {
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(TOKEN).commit())
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
}

internal object CloudflareDnsApi {
    private const val BASE = "https://api.cloudflare.com/client/v4"
    fun supportedRecordTypes() = listOf("A", "AAAA", "CNAME", "TXT", "MX")
    fun isSupportedRecordType(type: String) = type.uppercase() in supportedRecordTypes()
    fun proxyEligible(type: String) = type.uppercase() in setOf("A", "AAAA", "CNAME")

    fun normalizeDomain(raw: String): String {
        val value = raw.trim().trimEnd('.').lowercase()
        require(value.length in 3..253 && value.contains('.') && value.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 && label.first().isLetterOrDigit() &&
                label.last().isLetterOrDigit() && label.all { it.isLetterOrDigit() || it == '-' }
        }) { "Enter a valid domain name, such as example.com." }
        return value
    }

    fun validate(type: String, rawName: String, rawContent: String, zone: String, priority: String) {
        val name = if (rawName.trim() == "@") zone else rawName.trim().trimEnd('.').lowercase()
        require(name == zone || name.endsWith(".$zone")) { "Record name must belong to the selected zone." }
        require(name.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                (label == "*" || (label.first().let { it.isLetterOrDigit() || it == '_' } &&
                    label.last().let { it.isLetterOrDigit() || it == '_' } &&
                    label.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' }))
        }) { "Record name contains an invalid DNS label." }
        val content = rawContent.trim()
        require(content.isNotEmpty() && content.length <= 4096) { "Record content is required (maximum 4,096 characters)." }
        when (type.uppercase()) {
            "A" -> require(content.split('.').let { parts ->
                parts.size == 4 && parts.all { octet ->
                    octet.matches(Regex("0|[1-9][0-9]{0,2}") ) && octet.toIntOrNull()?.let { it in 0..255 } == true
                }
            }) { "Enter a valid IPv4 address in dotted-decimal notation." }
            "AAAA" -> require(content.contains(":") && !content.contains("%") && runCatching {
                InetAddress.getByName(content) is Inet6Address
            }.getOrDefault(false)) { "Enter a valid IPv6 address without an interface scope suffix." }
            "CNAME" -> normalizeDomain(content)
            "MX" -> {
                normalizeDomain(content)
                require(priority.toIntOrNull()?.let { it in 0..65535 } == true) {
                    "MX priority must be between 0 and 65,535."
                }
            }
            "TXT" -> Unit
            else -> throw IllegalArgumentException("Supported types are A, AAAA, CNAME, TXT and MX.")
        }
    }

    fun findZone(token: String, rawDomain: String): CloudflareZone {
        val domain = normalizeDomain(rawDomain)
        val json = request("GET", "/zones?name=${enc(domain)}&status=active", token)
        val items = json.optJSONArray("result") ?: JSONArray()
        val zones = (0 until items.length()).mapNotNull { items.optJSONObject(it) }
            .map { CloudflareZone(it.optString("id"), it.optString("name").lowercase()) }
            .filter { it.id.isNotBlank() && it.name == domain }
        require(zones.size == 1) {
            if (zones.isEmpty()) "No active Cloudflare zone found for $domain. Check the domain and token permissions."
            else "Multiple matching zones were returned; resolve the duplicate zone before continuing."
        }
        return zones.single()
    }

    fun records(token: String, zoneId: String): List<CloudflareDnsRecord> {
        val output = mutableListOf<CloudflareDnsRecord>()
        var page = 1
        var pages = 1
        while (page <= pages && page <= 5) {
            val json = request("GET", "/zones/${enc(zoneId)}/dns_records?per_page=100&page=$page", token)
            val items = json.optJSONArray("result") ?: JSONArray()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                output += CloudflareDnsRecord(
                    item.optString("id"), item.optString("type"), item.optString("name"),
                    item.optString("content"), item.optInt("ttl", 1), item.optBoolean("proxied", false),
                    if (item.has("priority") && !item.isNull("priority")) item.optInt("priority") else null
                )
            }
            pages = json.optJSONObject("result_info")?.optInt("total_pages", 1) ?: 1
            page++
        }
        require(pages <= 5) { "This zone has more than 500 records; manage the larger inventory in Cloudflare." }
        return output.sortedWith(compareBy<CloudflareDnsRecord> { it.name }.thenBy { it.type })
    }

    fun save(token: String, zone: CloudflareZone, id: String?, type: String, name: String,
        content: String, proxied: Boolean, priority: String) {
        val recordType = type.uppercase()
        validate(recordType, name, content, zone.name, priority)
        val recordName = if (name.trim() == "@") zone.name else name.trim().trimEnd('.').lowercase()
        val payload = JSONObject().put("type", recordType).put("name", recordName)
            .put("content", content.trim()).put("ttl", 1)
            .put("proxied", proxyEligible(recordType) && proxied)
        if (recordType == "MX") payload.put("priority", priority.toInt())
        val suffix = if (id.isNullOrBlank()) "" else "/${enc(id)}"
        request(if (id.isNullOrBlank()) "POST" else "PATCH",
            "/zones/${enc(zone.id)}/dns_records$suffix", token, payload)
    }

    fun delete(token: String, zoneId: String, id: String) {
        request("DELETE", "/zones/${enc(zoneId)}/dns_records/${enc(id)}", token)
    }

    private fun request(method: String, path: String, token: String, body: JSONObject? = null): JSONObject {
        val connection = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 12000
            connection.readTimeout = 18000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrElse {
                throw IllegalStateException("Cloudflare returned an unreadable response (HTTP $status).")
            }
            if (status !in 200..299 || !json.optBoolean("success", false)) {
                val reason = json.optJSONArray("errors")?.optJSONObject(0)?.optString("message").orEmpty()
                throw IllegalStateException(reason.takeIf { it.isNotBlank() }?.take(240)
                    ?: "Cloudflare DNS request failed (HTTP $status). Check token permissions and zone.")
            }
            return json
        } finally { connection.disconnect() }
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}

@Composable
internal fun CloudflareDnsManagerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = MaterialTheme.colorScheme
    var tokenInput by remember { mutableStateOf("") }
    var tokenSaved by remember { mutableStateOf(CloudflareApiTokenStore.hasToken(context)) }
    var domain by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf<CloudflareZone?>(null) }
    var records by remember { mutableStateOf<List<CloudflareDnsRecord>>(emptyList()) }
    var type by remember { mutableStateOf("A") }
    var name by remember { mutableStateOf("@") }
    var content by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("10") }
    var proxied by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<CloudflareDnsRecord?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Use a scoped token with Zone:Read and DNS:Edit permissions.") }

    fun apiToken(): String = tokenInput.trim().takeIf { it.isNotEmpty() }
        ?: CloudflareApiTokenStore.read(context)
        ?: throw IllegalStateException("Save a Cloudflare API token first.")

    fun refresh(selected: CloudflareZone) {
        scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) { CloudflareDnsApi.records(apiToken(), selected.id) } }
                .onSuccess { records = it; message = "Loaded ${it.size} DNS records for ${selected.name}." }
                .onFailure { message = it.message ?: "Could not load DNS records." }
            busy = false
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CLOUDFLARE DNS", color = palette.onSurface, fontWeight = FontWeight.Bold)
                Text("Manage records in a zone you control. This API token is encrypted with Android Keystore and is separate from your Tunnel token.",
                    color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(tokenInput, { tokenInput = it },
                    label = { Text(if (tokenSaved) "Replace saved API token (optional)" else "Cloudflare API token") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = tokenInput.isNotBlank() && !busy, onClick = {
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { CloudflareApiTokenStore.save(context, tokenInput) } }
                                .onSuccess { tokenInput = ""; tokenSaved = true; message = "Scoped API token saved securely." }
                                .onFailure { message = it.message ?: "Could not save API token." }
                        }
                    }) { Text("Save token") }
                    OutlinedButton(enabled = tokenSaved && !busy, onClick = {
                        runCatching { CloudflareApiTokenStore.clear(context) }
                            .onSuccess { tokenSaved = false; tokenInput = ""; zone = null; records = emptyList(); message = "API token removed." }
                            .onFailure { message = it.message ?: "Could not remove API token." }
                    }) { Text("Remove token") }
                }
                OutlinedTextField(domain, { value ->
                    if (value != domain) {
                        domain = value
                        zone = null
                        records = emptyList()
                        editingId = null
                        deleting = null
                        message = "Domain changed. Find the zone again before editing DNS records."
                    }
                }, label = { Text("Zone domain (e.g. assayflux.org)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = tokenSaved && domain.isNotBlank() && !busy, onClick = {
                    scope.launch {
                        busy = true
                        runCatching { withContext(Dispatchers.IO) { CloudflareDnsApi.findZone(apiToken(), domain) } }
                            .onSuccess { zone = it; message = "Selected zone ${it.name}."; refresh(it) }
                            .onFailure { message = it.message ?: "Could not find Cloudflare zone." }
                        busy = false
                    }
                }) { Text(if (busy) "Loading…" else "Find zone and load records") }
            }
        }

        zone?.let { selected ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DNS RECORDS · ${selected.name}", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Type", color = palette.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { menu = true }) { Text(type) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            CloudflareDnsApi.supportedRecordTypes().forEach { option ->
                                DropdownMenuItem(text = { Text(option) }, onClick = {
                                    type = option; proxied = CloudflareDnsApi.proxyEligible(option); menu = false
                                })
                            }
                        }
                    }
                    OutlinedTextField(name, { name = it }, label = { Text("Record name (@ for apex)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(content, { content = it }, label = { Text("Content / target") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (type == "MX") OutlinedTextField(priority, { priority = it.filter(Char::isDigit).take(5) },
                        label = { Text("MX priority") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (CloudflareDnsApi.proxyEligible(type)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(proxied, { proxied = it })
                            Text("Proxied through Cloudflare", color = palette.onSurface)
                        }
                    } else Text("This record type is DNS-only.", color = palette.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy && name.isNotBlank() && content.isNotBlank(), onClick = {
                            scope.launch {
                                busy = true
                                runCatching { withContext(Dispatchers.IO) {
                                    CloudflareDnsApi.save(apiToken(), selected, editingId, type, name, content, proxied, priority)
                                } }.onSuccess {
                                    editingId = null; name = "@"; content = ""; refresh(selected); message = "DNS record saved."
                                }.onFailure { message = it.message ?: "Could not save DNS record." }
                                busy = false
                            }
                        }) { Text(if (editingId == null) "Create record" else "Save changes") }
                        if (editingId != null) TextButton(onClick = { editingId = null; name = "@"; content = "" }) { Text("Cancel") }
                        OutlinedButton(enabled = !busy, onClick = { refresh(selected) }) { Text("Refresh") }
                    }
                    records.forEach { record ->
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant)) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${record.type}  ${record.name}", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                                Text(record.content, color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                Text("TTL ${if (record.ttl == 1) "Auto" else record.ttl} · ${if (record.proxied) "Proxied" else "DNS only"}",
                                    color = palette.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (CloudflareDnsApi.isSupportedRecordType(record.type)) {
                                        TextButton(onClick = {
                                            editingId = record.id; type = record.type; name = record.name
                                            content = record.content; priority = (record.priority ?: 10).toString(); proxied = record.proxied
                                        }) { Text("Edit") }
                                    } else {
                                        Text("Edit this record type in Cloudflare.", color = palette.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton(onClick = { deleting = record }) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(message, color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }

    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete DNS record?") },
            text = { Text("Delete ${record.type} ${record.name}? This is applied immediately in Cloudflare.") },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    val selected = zone
                    if (selected != null) scope.launch {
                        busy = true
                        runCatching { withContext(Dispatchers.IO) { CloudflareDnsApi.delete(apiToken(), selected.id, record.id) } }
                            .onSuccess { deleting = null; refresh(selected); message = "DNS record deleted." }
                            .onFailure { message = it.message ?: "Could not delete DNS record." }
                        busy = false
                    }
                }) { Text("Delete record") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}
