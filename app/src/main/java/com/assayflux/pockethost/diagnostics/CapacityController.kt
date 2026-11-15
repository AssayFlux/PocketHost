package com.assayflux.pockethost.diagnostics

import android.content.Context
import com.assayflux.pockethost.engine.NativeEngine
import com.assayflux.pockethost.runtime.PythonHostRuntime
import com.assayflux.pockethost.runtime.NodeHostRuntime
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Persistent, bounded admission profile. Percentages apply to a measured safe concurrency ceiling. */
object CapacityController {
    private const val PREFS = "pockethost_capacity"
    private const val DEFAULT_SAFE_LIMIT = 4
    private const val KEY_OPTIONAL_RUNTIMES_SUSPENDED = "capacity_optional_runtimes_suspended"
    @Volatile private var lastHealthState = "NORMAL"
    @Volatile private var recoveryUntilElapsed = 0L

    private fun healthFactor(context: Context): Pair<Double, String> {
        val memory = android.app.ActivityManager.MemoryInfo()
        context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(memory)
        val thermal = if (android.os.Build.VERSION.SDK_INT >= 29)
            context.getSystemService(android.os.PowerManager::class.java).currentThermalStatus else 0
        val storage = android.os.StatFs(context.filesDir.absolutePath)
        val freeBytes = storage.availableBlocksLong * storage.blockSizeLong
        val battery = runCatching {
            context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = battery?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else 100
        val batteryStatus = battery?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = batteryStatus == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
            batteryStatus == android.os.BatteryManager.BATTERY_STATUS_FULL
        val cpuPercent = DeviceTelemetry.latestCpuUsagePercent()
        val emergency = thermal >= 5 || freeBytes < 128L * 1024L * 1024L || (cpuPercent ?: 0.0) >= 98.0
        val critical = memory.lowMemory || memory.availMem < memory.totalMem / 20L ||
            thermal >= 4 || freeBytes < 256L * 1024L * 1024L || (cpuPercent ?: 0.0) >= 95.0
        // Missing or stale CPU telemetry is not evidence of idle CPU. Apply a
        // conservative admission derating until a fresh sample is available.
        val constrained = thermal >= 3 || memory.availMem < memory.totalMem / 8L ||
            freeBytes < 1024L * 1024L * 1024L || (batteryPercent <= 10 && !charging) ||
            cpuPercent == null || cpuPercent >= 80.0
        if (emergency) { recoveryUntilElapsed = 0L; return 0.05 to "EMERGENCY" }
        if (critical) { recoveryUntilElapsed = 0L; return 0.1 to "PROTECTIVE" }
        if (constrained) { recoveryUntilElapsed = 0L; return 0.5 to "CONSTRAINED" }
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastHealthState in setOf("CONSTRAINED", "PROTECTIVE", "EMERGENCY") && recoveryUntilElapsed == 0L) {
            recoveryUntilElapsed = now + 30_000L
        }
        if (recoveryUntilElapsed > now) return 0.75 to "RECOVERY"
        recoveryUntilElapsed = 0L
        return 1.0 to "NORMAL"
    }

    @Synchronized fun tick(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val expiry = prefs.getLong("schedule_expires", 0L)
        if (expiry > 0L && expiry <= Instant.now().epochSecond) {
            val previous = prefs.getInt("previous_percent", 100).coerceIn(1, 100)
            prefs.edit().putInt("percent", previous).putLong("schedule_expires", 0L)
                .remove("previous_percent").apply()
            OperationalLog.record(context, "INFO", "CAPACITY_SCHEDULE_EXPIRED", "Previous capacity profile restored.")
        }
        apply(context)
        return snapshot(context)
    }

    @Synchronized fun setPercent(context: Context, percent: Int) {
        require(percent in 1..100) { "Capacity percentage must be between 1 and 100." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("percent", percent).putLong("schedule_expires", 0L).remove("previous_percent").apply()
        apply(context)
        OperationalLog.record(context, "INFO", "CAPACITY_PROFILE_CHANGED", "Admission budget set to $percent% of the calibrated safe concurrency ceiling.")
    }

    @Synchronized fun schedule(context: Context, percent: Int, durationMinutes: Int): JSONObject {
        require(percent in 1..100) { "Capacity percentage must be between 1 and 100." }
        require(durationMinutes in 1..1440) { "Schedule duration must be between 1 and 1440 minutes." }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val expiry = prefs.getLong("schedule_expires", 0L)
        val current = if (expiry > Instant.now().epochSecond)
            prefs.getInt("previous_percent", prefs.getInt("percent", 100))
        else prefs.getInt("percent", 100)
        prefs.edit().putInt("previous_percent", current).putInt("percent", percent)
            .putLong("schedule_expires", Instant.now().epochSecond + durationMinutes * 60L).apply()
        apply(context)
        OperationalLog.record(context, "INFO", "CAPACITY_SCHEDULE_STARTED", "$percent% admission profile scheduled for $durationMinutes minutes.")
        return snapshot(context)
    }

    @Synchronized fun calibrate(context: Context, safeConcurrency: Int, result: JSONObject) {
        require(safeConcurrency in 1..16)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("safe_concurrency", safeConcurrency)
            .putLong("calibrated_at", Instant.now().epochSecond)
            .putString("benchmark_json", result.toString()).apply()
        apply(context)
        OperationalLog.record(context, "INFO", "CAPACITY_CALIBRATED", "Benchmark safe concurrency ceiling recorded as $safeConcurrency. Results are device/workload specific.")
    }

    @Synchronized fun apply(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val safe = prefs.getInt("safe_concurrency", DEFAULT_SAFE_LIMIT).coerceIn(1, 16)
        val percent = prefs.getInt("percent", 100).coerceIn(1, 100)
        val baseLimit = ((safe * percent) / 100.0).toInt().coerceAtLeast(1).coerceAtMost(16)
        val (factor, state) = healthFactor(context)
        val limit = (baseLimit * factor).toInt().coerceAtLeast(1).coerceAtMost(16)
        // Enforce protective policy on every tick, not only on state transitions: a user
        // must not be able to restart an optional runtime while the device remains critical.
        if (state == "PROTECTIVE" || state == "EMERGENCY") {
            suspendOptionalRuntimes(context)
        } else if (state == "NORMAL") {
            resumeOptionalRuntimes(context)
        }
        if (state != lastHealthState) {
            lastHealthState = state
            OperationalLog.record(context, if (state == "NORMAL" || state == "RECOVERY") "INFO" else "WARN",
                "CAPACITY_HEALTH_STATE", "Automatic admission protection state changed to $state.")
        }
        runCatching { NativeEngine.setCapacityLimit(limit) }
    }

    private fun suspendOptionalRuntimes(context: Context) {
        val prefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        val pythonWasEnabled = prefs.getBoolean("python_enabled", false)
        val nodeWasEnabled = prefs.getBoolean("node_enabled", false)
        if (!pythonWasEnabled && !nodeWasEnabled) return
        // Preserve owner intent; this marker distinguishes an automatic safety stop
        // from a deliberate user stop.
        val firstSuspension = !prefs.getBoolean(KEY_OPTIONAL_RUNTIMES_SUSPENDED, false)
        prefs.edit().putBoolean(KEY_OPTIONAL_RUNTIMES_SUSPENDED, true).commit()
        if (pythonWasEnabled && runCatching { PythonHostRuntime.status(context) }.getOrNull() != "STOPPED") {
            runCatching { PythonHostRuntime.stop(context) }
        }
        if (nodeWasEnabled && runCatching { NodeHostRuntime.status(context) }.getOrNull() !in setOf("STOPPED", "ERROR")) {
            runCatching { NodeHostRuntime.stop(context) }
        }
        if (firstSuspension) OperationalLog.record(context, "WARN", "OPTIONAL_RUNTIMES_SUSPENDED",
            "Python/Node were temporarily stopped to preserve static hosting during critical device pressure. Their enabled preferences are retained for automatic recovery.")
    }

    private fun resumeOptionalRuntimes(context: Context) {
        val prefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_OPTIONAL_RUNTIMES_SUSPENDED, false)) return
        val pythonWanted = prefs.getBoolean("python_enabled", false)
        val nodeWanted = prefs.getBoolean("node_enabled", false)
        var allRecovered = true

        if (pythonWanted) {
            val current = runCatching { PythonHostRuntime.status(context) }.getOrNull()
            if (current == null) {
                allRecovered = false
            } else if (current == "STOPPED") {
                val root = prefs.getString("python_project_root",
                    java.io.File(context.filesDir, "python-project").absolutePath)
                    ?: java.io.File(context.filesDir, "python-project").absolutePath
                val routes = runCatching { com.assayflux.pockethost.sites.SiteRegistry.pythonHostnameMap(context) }
                    .getOrElse { allRecovered = false; org.json.JSONObject() }
                val started = runCatching {
                    if (routes.length() > 0) PythonHostRuntime.startSites(context, routes.toString(),
                        prefs.getInt("python_port", 8081), prefs.getBoolean("allow_lan", false))
                    else PythonHostRuntime.start(context, root, prefs.getInt("python_port", 8081),
                        prefs.getBoolean("allow_lan", false))
                }
                if (started.isFailure) {
                    allRecovered = false
                    OperationalLog.record(context, "ERROR", "PYTHON_RECOVERY_FAILED",
                        started.exceptionOrNull()?.message ?: "Python could not be restarted after capacity recovery.")
                }
            } else if (current != "RUNNING") {
                allRecovered = false
            }
        }

        if (nodeWanted) {
            val current = runCatching { NodeHostRuntime.status(context) }.getOrNull()
            if (current == null) {
                allRecovered = false
            } else if (current == "STOPPED" || current == "ERROR") {
                val root = prefs.getString("node_project_root",
                    java.io.File(context.filesDir, "node-project-default").absolutePath)
                    ?: java.io.File(context.filesDir, "node-project-default").absolutePath
                val started = runCatching { NodeHostRuntime.start(context, root, prefs.getInt("node_port", 3000)) }
                if (started.isFailure) {
                    allRecovered = false
                    OperationalLog.record(context, "ERROR", "NODE_RECOVERY_FAILED",
                        started.exceptionOrNull()?.message ?: "Node.js could not be restarted after capacity recovery.")
                } else {
                    allRecovered = false // STARTING is not proof of readiness; retry on the next health tick.
                }
            } else if (current != "RUNNING") {
                allRecovered = false
            }
        }

        if (allRecovered) {
            prefs.edit().putBoolean(KEY_OPTIONAL_RUNTIMES_SUSPENDED, false).commit()
            OperationalLog.record(context, "INFO", "OPTIONAL_RUNTIMES_RECOVERED",
                "Previously enabled optional runtimes have recovered after device health returned to NORMAL.")
        }
    }

    fun snapshot(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val safe = prefs.getInt("safe_concurrency", DEFAULT_SAFE_LIMIT).coerceIn(1, 16)
        val percent = prefs.getInt("percent", 100).coerceIn(1, 100)
        val expiry = prefs.getLong("schedule_expires", 0L)
        val (factor, healthState) = healthFactor(context)
        return JSONObject().put("percent", percent).put("safeConcurrency", safe)
            .put("effectiveConcurrencyLimit", (((safe * percent) / 100.0).toInt().coerceAtLeast(1) * factor).toInt().coerceAtLeast(1))
            .put("healthState", healthState).put("healthFactor", factor)
            .put("calibratedAt", prefs.getLong("calibrated_at", 0L))
            .put("scheduleExpiresAt", expiry)
            .put("scheduled", expiry > Instant.now().epochSecond)
            .put("benchmark", runCatching { JSONObject(prefs.getString("benchmark_json", "{}") ?: "{}") }.getOrDefault(JSONObject()))
            .put("semantics", "Admission concurrency ceiling derived from this device's measured workload; not CPU utilization or a guaranteed visitor count.")
    }
}

/** A bounded local HTTP workload sweep. It never runs while hosting is stopped. */
object CapacityLab {
    @Volatile private var running = false
    @Volatile private var cancelRequested = false
    @Volatile private var latest = JSONObject().put("state", "NOT_RUN")

    @Synchronized fun cancel(): JSONObject {
        if (running) {
            cancelRequested = true
            latest = JSONObject().put("state", "CANCELLING")
        }
        return latest
    }

    @Synchronized fun start(context: Context, port: Int): JSONObject {
        if (running) return latest
        require(port in 1024..65535)
        running = true
        cancelRequested = false
        latest = JSONObject().put("state", "RUNNING").put("startedAt", Instant.now().toString())
        Thread({
            try {
                val levels = listOf(1, 2, 4, 8, 12, 16)
                val results = JSONArray()
                var safe = 1
                var hasSafeLevel = false
                var stopReason = "completed"
                for (level in levels) {
                    if (cancelRequested) { stopReason = "user_cancelled"; break }
                    val memory = android.app.ActivityManager.MemoryInfo()
                    context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(memory)
                    val thermal = if (android.os.Build.VERSION.SDK_INT >= 29)
                        context.getSystemService(android.os.PowerManager::class.java).currentThermalStatus else 0
                    val battery = runCatching {
                        context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                    }.getOrNull()
                    val batteryLevel = battery?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val batteryScale = battery?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val batteryPercent = if (batteryLevel >= 0 && batteryScale > 0) batteryLevel * 100 / batteryScale else -1
                    val batteryStatus = battery?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val charging = batteryStatus == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                        batteryStatus == android.os.BatteryManager.BATTERY_STATUS_FULL
                    if (memory.lowMemory) { stopReason = "low_memory"; break }
                    if (thermal >= 4) { stopReason = "severe_thermal"; break }
                    if (batteryPercent in 0..10 && !charging) { stopReason = "low_battery"; break }
                    val deviceBefore = DeviceTelemetry.snapshot(context)
                    val executor = java.util.concurrent.Executors.newFixedThreadPool(level)
                    val requestCount = (level * 2).coerceAtMost(32)
                    val latencies = java.util.Collections.synchronizedList(mutableListOf<Long>())
                    val failures = java.util.concurrent.atomic.AtomicInteger(0)
                    val successes = java.util.concurrent.atomic.AtomicInteger(0)
                    val bytesServed = java.util.concurrent.atomic.AtomicLong(0L)
                    val started = System.nanoTime()
                    try {
                        val futures = (0 until requestCount).map { sample ->
                            executor.submit {
                                val begin = System.nanoTime()
                                try {
                                    // Unique query strings and no-cache request headers make each
                                    // sample an explicit origin request rather than a browser-cache test.
                                    val connection = java.net.URL("http://127.0.0.1:$port/?__pockethost_capacity=$sample").openConnection() as java.net.HttpURLConnection
                                    try {
                                        connection.connectTimeout = 1_500
                                        connection.readTimeout = 1_500
                                        connection.requestMethod = "GET"
                                        connection.setRequestProperty("Cache-Control", "no-cache, no-store")
                                        connection.setRequestProperty("Pragma", "no-cache")
                                        val code = connection.responseCode
                                        if (code in 200..299) successes.incrementAndGet()
                                        else failures.incrementAndGet()
                                        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                                        stream?.use { input ->
                                            val buffer = ByteArray(4096)
                                            while (true) {
                                                val count = input.read(buffer)
                                                if (count < 0) break
                                                if (count > 0) bytesServed.addAndGet(count.toLong())
                                            }
                                        }
                                    } finally {
                                        connection.disconnect()
                                    }
                                } catch (_: Exception) { failures.incrementAndGet() }
                                finally { latencies.add((System.nanoTime() - begin) / 1_000_000L) }
                            }
                        }
                        futures.forEach { it.get(4, java.util.concurrent.TimeUnit.SECONDS) }
                    } finally {
                        executor.shutdownNow()
                    }
                    val ordered = latencies.sorted()
                    val percentile = { p: Int ->
                        if (ordered.isEmpty()) 0L else ordered[((ordered.size - 1) * p) / 100]
                    }
                    val p50 = percentile(50)
                    val p95 = percentile(95)
                    val maxLatency = ordered.lastOrNull() ?: 0L
                    val elapsed = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L)
                    val throughputBytesPerSecond = bytesServed.get() * 1_000L / elapsed
                    val requestsPerSecond = successes.get() * 1_000.0 / elapsed
                    val averageLatency = if (ordered.isEmpty()) 0L else ordered.sum() / ordered.size
                    val deviceAfter = DeviceTelemetry.snapshot(context)
                    val afterThermal = deviceAfter.optInt("thermalStatus", -1)
                    val afterBattery = deviceAfter.optInt("batteryPercent", -1)
                    val afterCpu = if (deviceAfter.isNull("cpuUsagePercent")) null else deviceAfter.optDouble("cpuUsagePercent")
                    val environmentSafe = !deviceAfter.optBoolean("lowMemory", false) &&
                        afterThermal < 4 && !(afterBattery in 0..10 && !deviceAfter.optBoolean("charging", false)) &&
                        (afterCpu == null || afterCpu < 95.0)
                    val ok = failures.get() == 0 && ordered.size == requestCount && p95 <= 750L && environmentSafe
                    results.put(JSONObject().put("concurrency", level).put("samples", ordered.size)
                        .put("successfulRequests", successes.get()).put("errors", failures.get())
                        .put("bytesServed", bytesServed.get()).put("requestsPerSecond", requestsPerSecond)
                        .put("throughputBytesPerSecond", throughputBytesPerSecond)
                        .put("averageLatencyMs", averageLatency).put("p50LatencyMs", p50)
                        .put("p95LatencyMs", p95).put("maxLatencyMs", maxLatency)
                        .put("elapsedMs", elapsed).put("withinSafeThreshold", ok)
                        .put("environmentSafe", environmentSafe)
                        .put("deviceBefore", deviceBefore).put("deviceAfter", deviceAfter))
                    if (ok) { safe = level; hasSafeLevel = true } else {
                        if (!environmentSafe) stopReason = when {
                            deviceAfter.optBoolean("lowMemory", false) -> "low_memory_after_load"
                            afterThermal >= 4 -> "severe_thermal_after_load"
                            afterBattery in 0..10 && !deviceAfter.optBoolean("charging", false) -> "low_battery_after_load"
                            afterCpu != null && afterCpu >= 95.0 -> "high_cpu_after_load"
                            else -> "environment_guard"
                        }
                        break
                    }
                }
                val cancelled = cancelRequested || stopReason == "user_cancelled"
                val result = JSONObject().put("state", when {
                    cancelled -> "CANCELLED"
                    hasSafeLevel -> "COMPLETE"
                    else -> "INCONCLUSIVE"
                })
                    .put("completedAt", Instant.now().toString())
                    .put("safeConcurrency", if (hasSafeLevel && !cancelled) safe else JSONObject.NULL)
                    .put("stopReason", stopReason)
                    .put("levels", results)
                    .put("workload", "Loopback static-origin GET / with unique query strings and no-cache headers; reports request rate, byte throughput, latency distribution and before/after device health snapshots for each concurrency level.")
                    .put("threshold", "Zero errors, p95 latency <= 750 ms and safe post-load memory, CPU, thermal and battery state; stopped on cancellation or device health guards.")
                if (cancelled) {
                    OperationalLog.record(context, "INFO", "CAPACITY_BENCHMARK_CANCELLED", "Capacity benchmark cancelled by the owner; previous calibration retained.")
                } else if (hasSafeLevel) {
                    CapacityController.calibrate(context, safe, result)
                    OperationalLog.record(context, "INFO", "CAPACITY_BENCHMARK_COMPLETED", "Safe concurrency ceiling measured at $safe for the local static GET workload.")
                } else {
                    OperationalLog.record(context, "WARN", "CAPACITY_BENCHMARK_INCONCLUSIVE", "The workload did not establish a safe concurrency ceiling; prior calibration was retained.")
                }
                latest = result
            } catch (error: Exception) {
                latest = JSONObject().put("state", "FAILED").put("error", error.message ?: "benchmark_failed")
                OperationalLog.record(context, "WARN", "CAPACITY_BENCHMARK_FAILED", error.message ?: "Capacity benchmark failed.")
            } finally { running = false }
        }, "PocketHost-Capacity-Lab").start()
        return latest
    }

    fun snapshot(context: Context): JSONObject {
        if (running) return latest
        val persisted = context.getSharedPreferences("pockethost_capacity", Context.MODE_PRIVATE)
            .getString("benchmark_json", null)
        return persisted?.let { runCatching { JSONObject(it) }.getOrNull() } ?: latest
    }
}
