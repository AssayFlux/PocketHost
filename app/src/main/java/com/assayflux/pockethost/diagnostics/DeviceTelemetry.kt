package com.assayflux.pockethost.diagnostics

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.StatFs
import android.app.ActivityManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Low-cost device profile and live health sample. Unavailable values remain JSON null. */
object DeviceTelemetry {
    private var previousCpu: Pair<Long, Long>? = null
    @Volatile private var latestCpuPercent: Double? = null
    @Volatile private var latestCpuAtElapsed: Long = 0L

    fun latestCpuUsagePercent(): Double? {
        val age = android.os.SystemClock.elapsedRealtime() - latestCpuAtElapsed
        return if (latestCpuAtElapsed > 0L && age in 0..90_000L) latestCpuPercent else null
    }

    @Synchronized fun snapshot(context: Context): JSONObject {
        val app = context.applicationContext
        val memory = ActivityManager.MemoryInfo()
        app.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        val runtime = Runtime.getRuntime()
        val stat = StatFs(app.filesDir.absolutePath)
        val battery = runCatching {
            app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val temperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val power = app.getSystemService(PowerManager::class.java)
        val thermal = if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else -1
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        val caps = runCatching { connectivity.getNetworkCapabilities(connectivity.activeNetwork) }.getOrNull()
        val network = when {
            caps == null -> "unknown"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
        val cpu = cpuUsagePercent()
        if (cpu != null) {
            latestCpuPercent = cpu
            latestCpuAtElapsed = android.os.SystemClock.elapsedRealtime()
        }
        return JSONObject()
            .put("schemaVersion", 1)
            .put("sampledAt", Instant.now().toString())
            .put("manufacturer", Build.MANUFACTURER.orEmpty())
            .put("model", Build.MODEL.orEmpty())
            .put("androidApi", Build.VERSION.SDK_INT)
            .put("abis", JSONArray().also { array -> Build.SUPPORTED_ABIS.forEach(array::put) })
            .put("cpuCores", Runtime.getRuntime().availableProcessors())
            .put("cpuUsagePercent", if (cpu == null) JSONObject.NULL else cpu)
            .put("totalMemoryBytes", memory.totalMem)
            .put("availableMemoryBytes", memory.availMem)
            .put("lowMemory", memory.lowMemory)
            .put("processPssKb", Debug.getPss())
            .put("nativeHeapBytes", Debug.getNativeHeapAllocatedSize())
            .put("javaHeapUsedBytes", runtime.totalMemory() - runtime.freeMemory())
            .put("javaHeapMaxBytes", runtime.maxMemory())
            .put("storageTotalBytes", stat.blockCountLong * stat.blockSizeLong)
            .put("storageAvailableBytes", stat.availableBlocksLong * stat.blockSizeLong)
            .put("batteryPercent", if (batteryPercent < 0) JSONObject.NULL else batteryPercent)
            .put("charging", charging)
            .put("batteryTemperatureC", if (temperature < 0) JSONObject.NULL else temperature / 10.0)
            .put("thermalStatus", if (thermal < 0) JSONObject.NULL else thermal)
            .put("thermalLabel", thermalLabel(thermal))
            .put("networkTransport", network)
            .put("networkValidated", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false)
    }

    private fun thermalLabel(value: Int): String = when (value) {
        0 -> "NONE"
        1 -> "LIGHT"
        2 -> "MODERATE"
        3 -> "SEVERE"
        4 -> "CRITICAL"
        5 -> "EMERGENCY"
        6 -> "SHUTDOWN"
        else -> "UNKNOWN"
    }

    private fun cpuUsagePercent(): Double? {
        return runCatching {
            val fields = File("/proc/stat").useLines { lines ->
                lines.first().trim().split(Regex("\\s+")).drop(1).map(String::toLong)
            }
            if (fields.size < 4) null else {
                val idle = fields[3] + (fields.getOrNull(4) ?: 0L)
                val total = fields.sum()
                val previous = previousCpu
                previousCpu = total to idle
                if (previous == null) null else {
                    val totalDelta = total - previous.first
                    val idleDelta = idle - previous.second
                    if (totalDelta <= 0L) null
                    else ((totalDelta - idleDelta).toDouble() * 100.0 / totalDelta).coerceIn(0.0, 100.0)
                }
            }
        }.getOrNull()
    }
}

/** Two-file, bounded telemetry history. Samples contain no request URLs, IPs or credentials. */
object TelemetryStore {
    private const val FILE_NAME = "telemetry.jsonl"
    private const val MAX_BYTES = 512L * 1024L

    @Synchronized fun record(context: Context): JSONObject {
        val sample = DeviceTelemetry.snapshot(context)
        runCatching {
            val directory = File(context.applicationContext.filesDir, "operations")
            if (!directory.exists()) directory.mkdirs()
            val current = File(directory, FILE_NAME)
            if (current.exists() && current.length() >= MAX_BYTES) {
                val previous = File(directory, "$FILE_NAME.1")
                previous.delete()
                current.renameTo(previous)
            }
            current.appendText(sample.toString() + "\n", Charsets.UTF_8)
        }.onFailure {
            OperationalLog.record(context, "WARN", "TELEMETRY_WRITE_FAILED", it.message ?: "Could not persist telemetry sample.")
        }
        return sample
    }

    fun latest(context: Context): JSONObject? = runCatching {
        val directory = File(context.applicationContext.filesDir, "operations")
        val files = listOf(File(directory, "$FILE_NAME.1"), File(directory, FILE_NAME))
        files.asReversed().firstNotNullOfOrNull { file ->
            if (!file.isFile) null else file.useLines { lines -> lines.toList().lastOrNull()?.let { JSONObject(it) } }
        }
    }.getOrNull()

    fun history(context: Context, limit: Int = 120): List<JSONObject> {
        require(limit in 1..500)
        return runCatching {
            val directory = File(context.applicationContext.filesDir, "operations")
            (listOf(File(directory, "$FILE_NAME.1"), File(directory, FILE_NAME)))
                .filter { it.isFile }.flatMap { it.readLines(Charsets.UTF_8) }
                .takeLast(limit).mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }
        }.getOrDefault(emptyList())
    }
}
