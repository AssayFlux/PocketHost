package com.assayflux.pockethost.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.assayflux.pockethost.engine.NativeEngine
import com.assayflux.pockethost.diagnostics.OperationalLog
import com.assayflux.pockethost.diagnostics.MetricsStore
import com.assayflux.pockethost.runtime.PythonHostRuntime
import com.assayflux.pockethost.runtime.NodeHostRuntime
import com.assayflux.pockethost.management.ManagementApiServer
import com.assayflux.pockethost.sites.SiteRegistry
import com.assayflux.pockethost.tunnel.CloudflareTokenStore
import com.assayflux.pockethost.tunnel.CloudflareTunnelRuntime
import java.io.File

class HostingService : Service() {
    private val channelId = "pockethost-hosting"
    @Volatile private var stopping = false
    @Volatile private var tunnelSupervisorActive = false
    @Volatile private var metricsMonitorActive = false
    @Volatile private var pythonSupervisorActive = false
    @Volatile private var nodeSupervisorActive = false
    @Volatile private var nodeSupervisorThread: Thread? = null
    @Volatile private var pythonSupervisorThread: Thread? = null
    @Volatile private var wakeLockMonitorActive = false
    @Volatile private var wakeLockMonitor: Thread? = null
    private val statePreferences by lazy { getSharedPreferences("pockethost_state", MODE_PRIVATE) }
    private var hostingWakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        private const val WAKE_LOCK_REFRESH_MS = 5 * 60 * 1000L
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "PocketHost", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, notification("Starting local website server…"))
        runCatching { ManagementApiServer.start(this) }
            .onFailure { OperationalLog.record(this, "ERROR", "MANAGEMENT_API_START_FAILED", it.message ?: "Management API failed to start.") }
        acquireWakeLockIfRequested()
        updateStatus("Starting local website server…")

        val site = File(filesDir, "site")
        if (!site.exists()) {
            site.mkdirs()
            assets.open("site/index.html").use { input ->
                File(site, "index.html").outputStream().use { output -> input.copyTo(output) }
            }
        }

        val engineAlreadyRunning = NativeEngine.status() == "RUNNING"
        val registeredRoutes = SiteRegistry.hostRoutingJson(
            this, statePreferences.getInt("python_port", 8081), statePreferences.getInt("node_port", 3000)
        ).toString()
        val result = if (engineAlreadyRunning) "OK"
            else if (registeredRoutes != "{}") NativeEngine.startMultiSite(
                site.absolutePath, registeredRoutes, statePreferences.getInt("port", 8080),
                statePreferences.getBoolean("allow_lan", false)
            ) else NativeEngine.start(
                site.absolutePath, statePreferences.getInt("port", 8080), statePreferences.getBoolean("allow_lan", false)
            )
        if (result == "OK") {
            if (!engineAlreadyRunning) MetricsStore.beginSession(this)
            startMetricsMonitor()
            val pythonEnabled = statePreferences.getBoolean("python_enabled", false)
            val host = if (statePreferences.getBoolean("allow_lan", false)) "LAN" else "loopback"
            val staticPort = statePreferences.getInt("port", 8080)
            val baseMessage = "$host static server ready on port $staticPort"
            val initialMessage = if (pythonEnabled) "$baseMessage · Starting Python WSGI…" else baseMessage
            val notificationHidden = Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            updateStatus(if (notificationHidden) "$initialMessage. Notification permission is off; Android may hide the service notification from the notification drawer." else initialMessage)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(1, notification("$host hosting · port $staticPort"))
            if (statePreferences.getBoolean("tunnel_enabled", false) && !tunnelSupervisorActive) {
                tunnelSupervisorActive = true
                Thread({
                    if (stopping) { tunnelSupervisorActive = false; return@Thread }
                    val tunnelToken = runCatching { CloudflareTokenStore.read(this) }.getOrNull()
                    if (tunnelToken.isNullOrBlank()) {
                        updateStatus("$baseMessage · Cloudflare Tunnel token is missing or cannot be decrypted.")
                        manager.notify(1, notification("Cloudflare Tunnel token unavailable"))
                        tunnelSupervisorActive = false
                        return@Thread
                    }
                    var retry = 0
                    while (!stopping && statePreferences.getBoolean("tunnel_enabled", false) && retry < 5) {
                        val tunnelResult = runCatching {
                            CloudflareTunnelRuntime.start(this, tunnelToken) { event ->
                                if (!stopping) {
                                    updateStatus(event)
                                    manager.notify(1, notification(event))
                                }
                            }
                        }.getOrElse { "ERROR: ${it.message ?: "connector failed"}" }
                        if (tunnelResult.startsWith("ERROR")) {
                            updateStatus("$baseMessage · $tunnelResult")
                        } else {
                            updateStatus("$baseMessage · Cloudflare Tunnel $tunnelResult")
                            manager.notify(1, notification("Cloudflare Tunnel $tunnelResult"))
                        }
                        while (!stopping &&
                            statePreferences.getBoolean("tunnel_enabled", false) &&
                            CloudflareTunnelRuntime.status() in setOf("CONNECTING", "CONNECTED")) {
                            try { Thread.sleep(1_000) } catch (_: InterruptedException) { break }
                        }
                        if (stopping || !statePreferences.getBoolean("tunnel_enabled", false)) break
                        retry++
                        val delayMs = when (retry) { 1 -> 5_000L; 2 -> 10_000L; 3 -> 20_000L; 4 -> 40_000L; else -> 60_000L }
                        val retryMessage = "Cloudflare Tunnel disconnected · retry $retry/5 in ${delayMs / 1_000}s"
                        updateStatus(retryMessage)
                        manager.notify(1, notification(retryMessage))
                        try { Thread.sleep(delayMs) } catch (_: InterruptedException) { break }
                    }
                    if (!stopping && statePreferences.getBoolean("tunnel_enabled", false)) {
                        updateStatus("Cloudflare Tunnel stopped after five restart attempts. Check the token and network.")
                    }
                    tunnelSupervisorActive = false
                }, "PocketHost-Cloudflare-Startup").start()
            }
            if (statePreferences.getBoolean("node_enabled", false) && !nodeSupervisorActive) {
                nodeSupervisorActive = true
                val nodeRoot = statePreferences.getString("node_project_root", File(filesDir, "node-project-default").absolutePath)
                    ?: File(filesDir, "node-project-default").absolutePath
                val nodePort = statePreferences.getInt("node_port", 3000)
                val supervisor = Thread({
                    var failures = 0
                    while (!stopping && statePreferences.getBoolean("node_enabled", false)) {
                        val nodeState = NodeHostRuntime.status(this)
                        if (nodeState == "RUNNING" || nodeState == "STARTING") {
                            failures = 0
                            try { Thread.sleep(3_000) } catch (_: InterruptedException) { break }
                            continue
                        }
                        val result = runCatching { NodeHostRuntime.start(this, nodeRoot, nodePort) }
                            .getOrElse { "ERROR: Node.js runtime failed" }
                        if (result == "STARTING") {
                            try { Thread.sleep(5_000) } catch (_: InterruptedException) { break }
                            if (NodeHostRuntime.status(this) == "RUNNING") continue
                            failures++
                        } else {
                            failures++
                        }
                        OperationalLog.record(this, "WARN", "NODE_RUNTIME_RESTART", "Node.js start attempt $failures failed: $result")
                        if (failures >= 5) break
                        val delayMs = (5_000L shl (failures - 1)).coerceAtMost(60_000L)
                        try { Thread.sleep(delayMs) } catch (_: InterruptedException) { break }
                    }
                    nodeSupervisorActive = false
                    nodeSupervisorThread = null
                }, "PocketHost-Node-Supervisor")
                nodeSupervisorThread = supervisor
                supervisor.start()
            }
            if (pythonEnabled && !pythonSupervisorActive) {
                pythonSupervisorActive = true
                val projectRoot = statePreferences.getString("python_project_root", File(filesDir, "python-project").absolutePath)
                    ?: File(filesDir, "python-project").absolutePath
                val pythonPort = statePreferences.getInt("python_port", 8081)
                val allowLan = statePreferences.getBoolean("allow_lan", false)
                val supervisor = Thread({
                    var failures = 0
                    while (!stopping && statePreferences.getBoolean("python_enabled", false)) {
                        val current = runCatching { PythonHostRuntime.status(this) }.getOrDefault("ERROR")
                        if (current == "RUNNING") {
                            failures = 0
                            try { Thread.sleep(3_000) } catch (_: InterruptedException) { break }
                            continue
                        }
                        val result = runCatching {
                            val pythonHosts = SiteRegistry.pythonHostnameMap(this)
                            if (pythonHosts.length() > 0) PythonHostRuntime.startSites(this, pythonHosts.toString(), pythonPort, allowLan)
                            else PythonHostRuntime.start(this, projectRoot, pythonPort, allowLan)
                        }.getOrElse { "ERROR: " + (it.message ?: "Python runtime failed") }
                        if (result == "RUNNING" || result == "ALREADY_RUNNING") {
                            failures = 0
                            val message = "$baseMessage · Python WSGI supervised on port $pythonPort"
                            updateStatus(message)
                            manager.notify(1, notification(message))
                            try { Thread.sleep(3_000) } catch (_: InterruptedException) { break }
                        } else {
                            failures++
                            OperationalLog.record(this, "WARN", "PYTHON_RUNTIME_RESTART", "Python runtime start attempt $failures failed: $result")
                            val message = "$baseMessage · Python runtime unavailable (attempt $failures/5)"
                            updateStatus(message)
                            manager.notify(1, notification(message))
                            if (failures >= 5) break
                            val delay = (5_000L shl (failures - 1)).coerceAtMost(60_000L)
                            try { Thread.sleep(delay) } catch (_: InterruptedException) { break }
                        }
                    }
                    if (stopping || !statePreferences.getBoolean("python_enabled", false)) {
                        runCatching { PythonHostRuntime.stop(this) }
                    }
                    pythonSupervisorActive = false
                    pythonSupervisorThread = null
                }, "PocketHost-Python-Supervisor")
                pythonSupervisorThread = supervisor
                supervisor.start()
            }
        } else {
            updateStatus("Hosting failed: $result")
            getSystemService(NotificationManager::class.java).notify(
                1, notification("Hosting error: $result")
            )
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopping = true
        ManagementApiServer.stop()
        Thread({ runCatching { CloudflareTunnelRuntime.stop() } }, "PocketHost-Cloudflare-Stop").start()
        runCatching {
            MetricsStore.recordSample(this, NativeEngine.requestCount(), NativeEngine.bytesServed())
        }
        pythonSupervisorThread?.interrupt()
        nodeSupervisorThread?.interrupt()
        runCatching { NodeHostRuntime.stop(this) }
        runCatching { PythonHostRuntime.stop(this) }
        runCatching { NativeEngine.stop() }
        releaseWakeLock()
        if (!statePreferences.getString("service_status", "").orEmpty().startsWith("Hosting failed:")) {
            updateStatus("Hosting stopped.")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMetricsMonitor() {
        if (metricsMonitorActive) return
        metricsMonitorActive = true
        Thread({
            try {
                while (!stopping && NativeEngine.status() == "RUNNING") {
                    MetricsStore.recordSample(this, NativeEngine.requestCount(), NativeEngine.bytesServed())
                    Thread.sleep(2_000)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (error: Exception) {
                OperationalLog.record(this, "WARN", "METRICS_MONITOR_ERROR", error.message ?: "Traffic metrics sampling stopped.")
            } finally {
                metricsMonitorActive = false
            }
        }, "PocketHost-Metrics").start()
    }

    private fun acquireWakeLockIfRequested() {
        if (!statePreferences.getBoolean("keep_awake", false) || hostingWakeLock?.isHeld == true) return
        runCatching {
            val powerManager = getSystemService(PowerManager::class.java)
            hostingWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AssayFlux: PocketHostHosting"
            ).apply {
                setReferenceCounted(false)
                // A bounded lease prevents a leaked lock from holding the CPU forever
                // if service teardown is interrupted. The supervisor renews it only
                // while this foreground service remains active.
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
            startWakeLockRefresh()
            OperationalLog.record(this, "INFO", "WAKE_LOCK_ACQUIRED", "Optional CPU wake lock enabled with a renewable ten-minute lease.")
        }.onFailure {
            OperationalLog.record(this, "WARN", "WAKE_LOCK_UNAVAILABLE", it.message ?: "Could not acquire CPU wake lock.")
        }
    }

    private fun startWakeLockRefresh() {
        if (stopping || wakeLockMonitorActive) return
        wakeLockMonitorActive = true
        val monitor = Thread({
            try {
                while (!stopping && statePreferences.getBoolean("keep_awake", false)) {
                    Thread.sleep(WAKE_LOCK_REFRESH_MS)
                    if (stopping || !statePreferences.getBoolean("keep_awake", false)) break
                    synchronized(this@HostingService) {
                        hostingWakeLock?.let { lock ->
                            if (stopping) return@synchronized
                            runCatching { lock.acquire(WAKE_LOCK_TIMEOUT_MS) }
                                .onFailure {
                                    OperationalLog.record(this, "WARN", "WAKE_LOCK_RENEWAL_FAILED", it.message ?: "Could not renew CPU wake lock.")
                                }
                        }
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                wakeLockMonitorActive = false
                wakeLockMonitor = null
            }
        }, "PocketHost-WakeLock-Lease")
        wakeLockMonitor = monitor
        monitor.start()
    }

    @Synchronized
    private fun releaseWakeLock() {
        wakeLockMonitor?.interrupt()
        wakeLockMonitor = null
        runCatching {
            hostingWakeLock?.let { lock ->
                if (lock.isHeld) lock.release()
            }
            hostingWakeLock = null
            OperationalLog.record(this, "INFO", "WAKE_LOCK_RELEASED", "CPU wake lock released.")
        }
    }

    private fun updateStatus(value: String) {
        statePreferences.edit().putString("service_status", value).apply()
        val level = when {
            value.contains("error", ignoreCase = true) || value.contains("failed", ignoreCase = true) -> "ERROR"
            value.contains("retry", ignoreCase = true) || value.contains("denied", ignoreCase = true) -> "WARN"
            else -> "INFO"
        }
        OperationalLog.record(this, level, "SERVICE_STATUS", value)
    }

    private fun notification(text: String): Notification =
        Notification.Builder(this, channelId)
            .setContentTitle("AssayFlux PocketHost")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .build()
}
