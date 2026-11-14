package com.assayflux.pockethost.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.assayflux.pockethost.engine.NativeEngine
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
            val readyMessage = if (statePreferences.getBoolean("allow_lan", false)) "LAN server ready on port ${statePreferences.getInt("port", 8080)}. All devices on this Wi-Fi can access the website." else "Local server ready at 127.0.0.1:${statePreferences.getInt("port", 8080)}"
            val notificationHidden = Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            updateStatus(if (notificationHidden) "$readyMessage. Notification permission is off; Android may hide the service notification from the notification drawer." else readyMessage)
            getSystemService(NotificationManager::class.java).notify(
                1, notification(if (statePreferences.getBoolean("allow_lan", false)) "LAN hosting · port ${statePreferences.getInt("port", 8080)}" else "Local hosting · 127.0.0.1:${statePreferences.getInt("port", 8080)}")
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
