package com.assayflux.pockethost.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.assayflux.pockethost.engine.NativeEngine
import com.assayflux.pockethost.runtime.PythonHostRuntime
import com.assayflux.pockethost.tunnel.CloudflareTokenStore
import com.assayflux.pockethost.tunnel.CloudflareTunnelRuntime
import java.io.File

class HostingService : Service() {
    private val channelId = "pockethost-hosting"
    @Volatile private var stopping = false
    private val statePreferences by lazy { getSharedPreferences("pockethost_state", MODE_PRIVATE) }

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
            if (statePreferences.getBoolean("tunnel_enabled", false)) {
                Thread({
                    if (stopping) return@Thread
                    val tunnelToken = runCatching { CloudflareTokenStore.read(this) }.getOrNull()
                    if (tunnelToken.isNullOrBlank()) {
                        updateStatus("$baseMessage · Cloudflare Tunnel token is missing or cannot be decrypted.")
                        manager.notify(1, notification("Cloudflare Tunnel token unavailable"))
                        return@Thread
                    }
                    val tunnelResult = runCatching {
                        CloudflareTunnelRuntime.start(this, tunnelToken) { event ->
                            if (!stopping) {
                                updateStatus(event)
                                manager.notify(1, notification(event))
                            }
                        }
                    }.getOrElse { "ERROR: ${it.message ?: "connector failed"}" }
                    if (stopping) {
                        runCatching { CloudflareTunnelRuntime.stop() }
                        return@Thread
                    }
                    val tunnelMessage = "$baseMessage · Cloudflare Tunnel $tunnelResult"
                    updateStatus(tunnelMessage)
                    manager.notify(1, notification(tunnelMessage))
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
        if (!statePreferences.getString("service_status", "").orEmpty().startsWith("Hosting failed:")) {
            updateStatus("Hosting stopped.")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateStatus(value: String) {
        statePreferences.edit().putString("service_status", value).apply()
    }

    private fun notification(text: String): Notification =
        Notification.Builder(this, channelId)
            .setContentTitle("AssayFlux PocketHost")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .build()
}
