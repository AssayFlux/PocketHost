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
        val constrained = thermal >= 3 || memory.availMem < memory.totalMem / 8L ||
            freeBytes < 1024L * 1024L * 1024L || (batteryPercent <= 10 && !charging) ||
            (cpuPercent ?: 0.0) >= 80.0
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
        if (state == "PROTECTIVE" || state == "EMERGENCY") suspendOptionalRuntimes(context)
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
        prefs.edit().putBoolean("python_enabled", false).putBoolean("node_enabled", false).commit()
        if (pythonWasEnabled) runCatching { PythonHostRuntime.stop(context) }
        if (nodeWasEnabled) runCatching { NodeHostRuntime.stop(context) }
        OperationalLog.record(context, "WARN", "OPTIONAL_RUNTIMES_SUSPENDED",
            "Python/Node were disabled to preserve static hosting during critical device pressure. Re-enable them manually after recovery.")
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
    @Volatile private var latest = JSONObject().put("state", "NOT_RUN")

    @Synchronized fun start(context: Context, port: Int): JSONObject {
        if (running) return latest
        require(port in 1024..65535)
        running = true
        latest = JSONObject().put("state", "RUNNING").put("startedAt", Instant.now().toString())
        Thread({
            try {
                val levels = listOf(1, 2, 4, 8, 12, 16)
                val results = JSONArray()
                var safe = 1
                var hasSafeLevel = false
                for (level in levels) {
                    val memory = android.app.ActivityManager.MemoryInfo()
                    context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(memory)
                    val thermal = if (android.os.Build.VERSION.SDK_INT >= 29)
                        context.getSystemService(android.os.PowerManager::class.java).currentThermalStatus else 0
                    if (memory.lowMemory || thermal >= 4) break
                    val executor = java.util.concurrent.Executors.newFixedThreadPool(level)
                    val latencies = java.util.Collections.synchronizedList(mutableListOf<Long>())
                    val failures = java.util.concurrent.atomic.AtomicInteger(0)
                    val started = System.nanoTime()
                    try {
                        val futures = (0 until (level * 2).coerceAtMost(32)).map {
                            executor.submit {
                                val begin = System.nanoTime()
                                try {
                                    val connection = java.net.URL("http://127.0.0.1:$port/").openConnection() as java.net.HttpURLConnection
                                    try {
                                        connection.connectTimeout = 1_500
                                        connection.readTimeout = 1_500
                                        connection.requestMethod = "GET"
                                        val code = connection.responseCode
                                        if (code !in 200..299) failures.incrementAndGet()
                                        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                                        stream?.use { input -> val buffer = ByteArray(4096); while (input.read(buffer) >= 0) {} }
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
                    val p95 = if (ordered.isEmpty()) 0L else ordered[((ordered.size - 1) * 95) / 100]
                    val elapsed = (System.nanoTime() - started) / 1_000_000L
                    val ok = failures.get() == 0 && ordered.size == (level * 2).coerceAtMost(32) && p95 <= 750L
                    results.put(JSONObject().put("concurrency", level).put("samples", ordered.size)
                        .put("errors", failures.get()).put("p95LatencyMs", p95).put("elapsedMs", elapsed).put("withinSafeThreshold", ok))
                    if (ok) { safe = level; hasSafeLevel = true } else break
                }
                val result = JSONObject().put("state", if (hasSafeLevel) "COMPLETE" else "INCONCLUSIVE")
                    .put("completedAt", Instant.now().toString()).put("safeConcurrency", if (hasSafeLevel) safe else JSONObject.NULL)
                    .put("levels", results)
                    .put("workload", "Loopback GET / against the active static Rust origin; bounded request sweep.")
                    .put("threshold", "Zero errors and p95 latency <= 750 ms; aborted on Android low-memory or severe thermal status.")
                if (hasSafeLevel) {
                    CapacityController.calibrate(context, safe, result)
                    OperationalLog.record(context, "INFO", "CAPACITY_BENCHMARK_COMPLETED", "Safe concurrency ceiling measured at $safe for the local static GET workload.")
                } else {
                    OperationalLog.record(context, "WARN", "CAPACITY_BENCHMARK_INCONCLUSIVE", "The lowest concurrency level did not meet the benchmark threshold; prior calibration was retained.")
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
