package com.assayflux.pockethost

import java.net.Inet4Address
import java.net.NetworkInterface

internal fun localLanAddress(): String? = runCatching {
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

internal fun formatUptime(totalSeconds: Long): String {
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

internal fun formatBytes(value: Long): String = when {
    value < 1024L -> "$value B"
    value < 1024L * 1024L -> "${value / 1024L} KB"
    value < 1024L * 1024L * 1024L -> "${value / (1024L * 1024L)} MB"
    else -> "${value / (1024L * 1024L * 1024L)} GB"
}
