package com.assayflux.pockethost.relay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
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
    private lateinit var connectivity: ConnectivityManager
    @Volatile private var stopping = false
    @Volatile private var ownerRequestedRunning = false
    @Volatile private var activeNetwork: Network? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val previous = activeNetwork
            activeNetwork = network
            if (ownerRequestedRunning && previous != network) {
                worker.execute { restartAfterNetworkChange("network became available") }
            }
        }

        override fun onLost(network: Network) {
            if (activeNetwork == network) {
                activeNetwork = null
                if (ownerRequestedRunning) {
                    worker.execute {
                        if (!ownerRequestedRunning || stopping) return@execute
                        val result = runCatching { NativeRelay.stop() }.getOrElse { "ERROR: ${it.message}" }
                        OperationalLog.record(this@NativeRelayService, "WARN", "NATIVE_RELAY_NETWORK_LOST",
                            "Network lost; listener stopped until a usable network returns. $result")
                        main.post {
                            if (ownerRequestedRunning && !stopping) {
                                getSystemService(NotificationManager::class.java)
                                    .notify(NOTIFICATION_ID, notification("Waiting for network…"))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "AssayFlux Relay", NotificationManager.IMPORTANCE_LOW)
            )
        }
        connectivity = getSystemService(ConnectivityManager::class.java)
        runCatching { connectivity.registerDefaultNetworkCallback(networkCallback) }
            .onFailure {
                OperationalLog.record(this, "WARN", "NATIVE_RELAY_NETWORK_MONITOR",
                    it.message ?: "Could not register Relay network recovery callback.")
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopping = true
                ownerRequestedRunning = false
                worker.execute {
                    val result = runCatching { NativeRelay.stop() }.getOrElse { "ERROR: ${it.message}" }
                    if (result != "OK") OperationalLog.record(this, "WARN", "NATIVE_RELAY_STOP", result)
                    main.post {
                        if (!ownerRequestedRunning) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf(startId)
                        } else {
                            getSystemService(NotificationManager::class.java)
                                .notify(NOTIFICATION_ID, notification("Starting native Relay…"))
                        }
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                stopping = false
                ownerRequestedRunning = true
                startForeground(NOTIFICATION_ID, notification("Starting native Relay…"))
                worker.execute { startRelay("owner/service start") }
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    private fun restartAfterNetworkChange(reason: String) {
        if (!ownerRequestedRunning || stopping) return
        val stopResult = runCatching { NativeRelay.stop() }.getOrElse { "ERROR: ${it.message}" }
        OperationalLog.record(this, "INFO", "NATIVE_RELAY_NETWORK_RECOVERY",
            "Reinitializing native Relay after $reason. $stopResult")
        startRelay(reason)
    }

    private fun startRelay(reason: String) {
        if (!ownerRequestedRunning || stopping) return
        val result = runCatching {
            val config = RelayNodeConfigStore.read(this)
                ?: throw IllegalStateException("Relay node is not configured. Complete Relay setup first.")
            val identity = RelayNodeIdentityStore.getOrCreate()
            OperationalLog.record(this, "INFO", "NATIVE_RELAY_NODE_IDENTITY", identity.nodeId)
            if (NativeRelay.status() == "RUNNING") "OK" else NativeRelay.start(config)
        }.getOrElse { "ERROR: ${it.message ?: "Relay startup failed"}" }

        main.post {
            if (stopping || !ownerRequestedRunning) return@post
            if (result == "OK") {
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification("Native Relay active"))
                OperationalLog.record(this, "INFO", "NATIVE_RELAY_READY", "Native Relay is active after $reason.")
            } else {
                OperationalLog.record(this, "ERROR", "NATIVE_RELAY_START", result)
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification("Relay unavailable: ${result.removePrefix("ERROR: ").take(100)}"))
            }
        }
    }

    override fun onDestroy() {
        stopping = true
        ownerRequestedRunning = false
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
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
