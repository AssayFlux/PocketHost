package com.assayflux.pockethost.dns

import android.content.Context
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

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
