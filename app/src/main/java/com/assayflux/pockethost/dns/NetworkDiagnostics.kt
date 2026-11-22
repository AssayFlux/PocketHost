package com.assayflux.pockethost.dns

import android.content.Context
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

internal data class AddressFamilyDiagnostics(
    val family: String,
    val detectedPublicAddress: String?,
    val deviceAddresses: List<String>,
    val assessment: String,
    val inboundReachability: String = "NOT VERIFIED"
)

internal object NetworkDiagnostics {
    fun assess(context: Context, addresses: DetectedPublicAddresses): List<AddressFamilyDiagnostics> {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
        val local4 = interfaces.flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress || it.isAnyLocalAddress }
            .map { it.hostAddress.orEmpty() }.distinct()
        val local6 = interfaces.flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet6Address>()
            .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress || it.isAnyLocalAddress || it.isMulticastAddress }
            .map { it.hostAddress?.substringBefore('%').orEmpty() }.filter { it.isNotBlank() }.distinct()

        val v4 = addresses.ipv4
        val v4Assessment = when {
            v4 == null -> "No globally routable IPv4 address detected."
            local4.any { it == v4 } -> "Public IPv4 is assigned directly to this device. Inbound access still needs an external test."
            local4.any(::isCarrierGradeNat) -> "A device interface uses 100.64.0.0/10. Likely carrier-grade NAT; direct inbound IPv4 is unlikely."
            local4.any(::isPrivateIpv4) -> "Private IPv4 is assigned to this device. Upstream NAT/router exists; carrier CGNAT cannot be distinguished from this alone."
            else -> "Public IPv4 differs from device interface addresses. Upstream NAT or routing is present; inbound access is unverified."
        }
        val v6Assessment = when {
            addresses.ipv6 == null -> "No globally routable IPv6 address detected."
            local6.any { it.equals(addresses.ipv6, true) } -> "Detected global IPv6 is assigned to this device. Firewall and inbound reachability remain unverified."
            local6.isEmpty() -> "No usable IPv6 address is assigned to the device."
            else -> "Public IPv6 differs from device interface addresses. Verify interface selection and routing."
        }
        return listOf(
            AddressFamilyDiagnostics("IPv4", v4, local4, v4Assessment),
            AddressFamilyDiagnostics("IPv6", addresses.ipv6, local6, v6Assessment)
        )
    }

    /**
     * Explicit, opt-in remote TCP probe. The user's public IP and selected port are
     * sent to Check-Host.net measurement nodes; this is not a PocketHost-controlled service.
     */
    fun probeInbound(address: String, port: Int): String {
        require(port in 1024..65535) { "Choose the configured unprivileged gateway port (1024–65535)." }
        val parsed = InetAddress.getByName(address)
        require(PublicAddressDetector.isGloballyRoutable(parsed, parsed is Inet6Address)) {
            "The target is not a globally routable public address."
        }
        val target = if (parsed is Inet6Address) "[${parsed.hostAddress?.substringBefore('%')}]:$port"
            else "${parsed.hostAddress}:$port"
        val encoded = URLEncoder.encode(target, "UTF-8")
        val request = URL("https://check-host.net/check-tcp?host=$encoded&max_nodes=3")
        val initial = request.openConnection() as HttpURLConnection
        var expectedNodes = 1
        val requestId = try {
            initial.connectTimeout = 7000
            initial.readTimeout = 9000
            initial.setRequestProperty("Accept", "application/json")
            require(initial.responseCode in 200..299) { "Remote probe service rejected the request (HTTP ${initial.responseCode})." }
            val json = JSONObject(initial.inputStream.bufferedReader().use { it.readText() })
            require(json.optInt("ok", 0) == 1) { "Remote probe service did not accept the check." }
            expectedNodes = json.optJSONObject("nodes")?.length()?.coerceAtLeast(1) ?: 1
            json.optString("request_id").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Remote probe did not return a request ID.")
        } finally { initial.disconnect() }

        repeat(5) { attempt ->
            val connection = URL("https://check-host.net/check-result/${URLEncoder.encode(requestId, "UTF-8")}")
                .openConnection() as HttpURLConnection
            val results = try {
                connection.connectTimeout = 7000
                connection.readTimeout = 9000
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode !in 200..299) null
                else JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally { connection.disconnect() }
            if (results != null) {
                val keys = results.keys()
                var total = 0
                var reachable = 0
                while (keys.hasNext()) {
                    val node = keys.next()
                    val value = results.opt(node)
                    if (value == null || value == JSONObject.NULL) continue
                    total++
                    val nodeResult = when (value) {
                        is org.json.JSONArray -> value.optJSONObject(0)
                        is JSONObject -> value
                        else -> null
                    }
                    if (nodeResult != null && !nodeResult.has("error") && nodeResult.has("time")) reachable++
                }
                if (total > 0 && (total >= expectedNodes || attempt == 4)) return if (reachable > 0)
                    "Reachable from $reachable/$total external TCP probes (Check-Host.net)."
                else "No external probe could connect ($total probes). CGNAT, firewall, routing or port filtering may be responsible."
            }
            if (attempt < 4) Thread.sleep(1500L)
        }
        return "External probe timed out; inbound reachability remains unverified."
    }

    fun isCarrierGradeNat(value: String): Boolean {
        val parts = value.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        val number = (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
        val start = (100 shl 24) or (64 shl 16)
        val end = (100 shl 24) or (127 shl 16) or (255 shl 8) or 255
        return number in start..end
    }

    private fun isPrivateIpv4(value: String): Boolean {
        val parts = value.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        return parts[0] == 10 || parts[0] == 192 && parts[1] == 168 ||
            parts[0] == 172 && parts[1] in 16..31
    }
}
