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
import java.io.File

class HostingService : Service() {
    private val channelId = "pockethost-hosting"
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
            val pythonResult = if (pythonEnabled) runCatching {
                val projectRoot = statePreferences.getString("python_project_root", File(filesDir, "python-project").absolutePath)
                    ?: File(filesDir, "python-project").absolutePath
                PythonHostRuntime.start(this, projectRoot, statePreferences.getInt("python_port", 8081), statePreferences.getBoolean("allow_lan", false))
            }.getOrElse { "ERROR: ${it.message ?: "Python runtime failed"}" } else "DISABLED"
            val host = if (statePreferences.getBoolean("allow_lan", false)) "LAN" else "loopback"
            val pythonMessage = when (pythonResult) {
                "RUNNING", "ALREADY_RUNNING" -> " · Python WSGI on port ${statePreferences.getInt("python_port", 8081)}"
                "DISABLED" -> ""
                else -> " · Python runtime error: $pythonResult"
            }
            val readyMessage = "$host static server ready on port ${statePreferences.getInt("port", 8080)}$pythonMessage"
            val notificationHidden = Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            updateStatus(if (notificationHidden) "$readyMessage. Notification permission is off; Android may hide the service notification from the notification drawer." else readyMessage)
            getSystemService(NotificationManager::class.java).notify(
                1, notification("$host hosting · port ${statePreferences.getInt("port", 8080)}$pythonMessage")
            )
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
