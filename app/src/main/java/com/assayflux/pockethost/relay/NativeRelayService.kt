package com.assayflux.pockethost.relay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.assayflux.pockethost.diagnostics.OperationalLog
import java.util.concurrent.Executors

/** Owner-started foreground lifecycle for the native public visitor gateway. */
class NativeRelayService : Service() {
    private val channelId = "pockethost-native-relay"
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PocketHost-Native-Relay-Service").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var stopping = false

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "AssayFlux Relay", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopping = true
                worker.execute {
                    val result = runCatching { NativeRelay.stop() }.getOrElse { "ERROR: ${it.message}" }
                    if (result != "OK") OperationalLog.record(this, "WARN", "NATIVE_RELAY_STOP", result)
                    main.post { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId) }
                }
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                stopping = false
                startForeground(NOTIFICATION_ID, notification("Starting native Relay…"))
                worker.execute {
                    val result = runCatching {
                        val config = RelayNodeConfigStore.read(this)
                            ?: throw IllegalStateException("Relay node is not configured. Complete Relay setup first.")
                        if (NativeRelay.status() == "RUNNING") "OK" else NativeRelay.start(config)
                    }.getOrElse { "ERROR: ${it.message ?: "Relay startup failed"}" }
                    main.post {
                        if (stopping) return@post
                        if (result == "OK") {
                            getSystemService(NotificationManager::class.java)
                                .notify(NOTIFICATION_ID, notification("Native Relay active"))
                        } else {
                            OperationalLog.record(this, "ERROR", "NATIVE_RELAY_START", result)
                            getSystemService(NotificationManager::class.java)
                                .notify(NOTIFICATION_ID, notification("Relay stopped: ${result.removePrefix("ERROR: ").take(120)}"))
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf(startId)
                        }
                    }
                }
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        stopping = true
        runCatching { NativeRelay.stop() }
        worker.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(message: String): Notification =
        Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("AssayFlux Relay")
            .setContentText(message)
            .setOngoing(message == "Native Relay active" || message.startsWith("Starting"))
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

    companion object {
        private const val NOTIFICATION_ID = 42
        private const val ACTION_START = "com.assayflux.pockethost.relay.START"
        private const val ACTION_STOP = "com.assayflux.pockethost.relay.STOP"

        fun start(context: Context) {
            val intent = Intent(context, NativeRelayService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, NativeRelayService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
