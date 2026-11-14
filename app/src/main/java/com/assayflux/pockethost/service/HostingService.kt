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
import com.assayflux.pockethost.runtime.PythonHostRuntime
import com.assayflux.pockethost.tunnel.CloudflareTokenStore
import com.assayflux.pockethost.tunnel.CloudflareTunnelRuntime
import java.io.File

class HostingService : Service() {
    private val channelId = "pockethost-hosting"
    @Volatile private var stopping = false
    @Volatile private var tunnelSupervisorActive = false
    private val statePreferences by lazy { getSharedPreferences("pockethost_state", MODE_PRIVATE) }
    private var hostingWakeLock: PowerManager.WakeLock? = null

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
        acquireWakeLockIfRequested()
        updateStatus("Starting local website server…")

        val site = File(filesDir, "site")
        if (!site.exists()) {
            site.mkdirs()
            assets.open("site/index.html").use { input ->
                File(site, "index.html").outputStream().use { output -> input.copyTo(output) }
            }
        }

        val result = if (NativeEngine.status() == "RUNNING") "OK"
            else NativeEngine.start(site.absolutePath, statePreferences.getInt("port", 8080), statePreferences.getBoolean("allow_lan", false))
        if (result == "OK") {
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
            if (pythonEnabled) {
                Thread({
                    if (stopping) return@Thread
                    val projectRoot = statePreferences.getString("python_project_root", File(filesDir, "python-project").absolutePath)
                        ?: File(filesDir, "python-project").absolutePath
                    val pythonResult = runCatching {
                        PythonHostRuntime.start(
                            this,
                            projectRoot,
                            statePreferences.getInt("python_port", 8081),
                            statePreferences.getBoolean("allow_lan", false)
                        )
                    }.getOrElse { "ERROR: ${it.message ?: "Python runtime failed"}" }
                    if (stopping) {
                        runCatching { PythonHostRuntime.stop(this) }
                        return@Thread
                    }
                    val pythonMessage = when (pythonResult) {
                        "RUNNING", "ALREADY_RUNNING" -> " · Python WSGI on port ${statePreferences.getInt("python_port", 8081)}"
                        else -> " · Python runtime error: $pythonResult"
                    }
                    val updatedMessage = "$baseMessage$pythonMessage"
                    updateStatus(updatedMessage)
                    manager.notify(1, notification(updatedMessage))
                }, "PocketHost-Python-Startup").start()
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
        Thread({ runCatching { CloudflareTunnelRuntime.stop() } }, "PocketHost-Cloudflare-Stop").start()
        runCatching { PythonHostRuntime.stop(this) }
        runCatching { NativeEngine.stop() }
        releaseWakeLock()
        if (!statePreferences.getString("service_status", "").orEmpty().startsWith("Hosting failed:")) {
            updateStatus("Hosting stopped.")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLockIfRequested() {
        if (!statePreferences.getBoolean("keep_awake", false) || hostingWakeLock?.isHeld == true) return
        runCatching {
            val powerManager = getSystemService(PowerManager::class.java)
            hostingWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AssayFlux: PocketHostHosting"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            OperationalLog.record(this, "INFO", "WAKE_LOCK_ACQUIRED", "Optional CPU wake lock enabled while hosting.")
        }.onFailure {
            OperationalLog.record(this, "WARN", "WAKE_LOCK_UNAVAILABLE", it.message ?: "Could not acquire CPU wake lock.")
        }
    }

    private fun releaseWakeLock() {
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
