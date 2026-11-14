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
        val site = File(filesDir, "site")
        if (!site.exists()) {
            site.mkdirs()
            assets.open("site/index.html").use { input ->
                File(site, "index.html").outputStream().use { output -> input.copyTo(output) }
            }
        }

        val result = if (NativeEngine.status() == "RUNNING") "OK"
            else NativeEngine.start(site.absolutePath, 8080)
        getSystemService(NotificationManager::class.java).notify(
            1, notification(if (result == "OK") "Local hosting · 127.0.0.1:8080" else "Hosting error: $result")
        )
        if (result != "OK") stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { NativeEngine.stop() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(text: String): Notification =
        Notification.Builder(this, channelId)
            .setContentTitle("AssayFlux PocketHost")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .build()
}
