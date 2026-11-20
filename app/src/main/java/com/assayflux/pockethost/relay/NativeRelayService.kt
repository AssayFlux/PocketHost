package com.assayflux.pockethost.relay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import org.json.JSONObject
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
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

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
    private val renewalScheduler = Executors.newSingleThreadScheduledExecutor()
    private var renewalTask: ScheduledFuture<*>? = null
    private var retryTask: ScheduledFuture<*>? = null
    private var retryAttempt = 0
    @Volatile private var activeNetwork: Network? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val previous = activeNetwork
            activeNetwork = network
            if (ownerRequestedRunning && previous != network) {
                worker.execute {
                    cancelRetry(resetAttempts = true)
                    restartAfterNetworkChange("network became available")
                }
            }
        }

        override fun onLost(network: Network) {
            if (activeNetwork == network) {
                activeNetwork = null
                if (ownerRequestedRunning) {
                    worker.execute {
                        cancelRetry(resetAttempts = false)
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
        renewalTask = renewalScheduler.scheduleWithFixedDelay({
            if (ownerRequestedRunning && !stopping) {
                worker.execute { startRelay("scheduled ACME renewal check") }
            }
        }, 12, 12, TimeUnit.HOURS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopping = true
                ownerRequestedRunning = false
                cancelRetry(resetAttempts = false)
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
        cancelRetry(resetAttempts = true)
        val stopResult = runCatching { NativeRelay.stop() }.getOrElse { "ERROR: ${it.message}" }
        OperationalLog.record(this, "INFO", "NATIVE_RELAY_NETWORK_RECOVERY",
            "Reinitializing native Relay after $reason. $stopResult")
        startRelay(reason)
    }

    private fun certificateNeedsRenewal(configJson: String): Boolean {
        val config = JSONObject(configJson)
        val bindAddress = config.optString("bind_address", "127.0.0.1")
        if (InetAddress.getByName(bindAddress).isLoopbackAddress) return false
        val certificatePem = config.optString("tls_certificate_pem")
        val privateKeyPem = config.optString("tls_private_key_pem")
        if (certificatePem.isBlank() || privateKeyPem.isBlank()) return true
        return runCatching {
            val certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certificatePem.toByteArray(Charsets.UTF_8))) as X509Certificate
            certificate.checkValidity()
            if (certificate.notAfter.time <= System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30)) {
                return@runCatching true
            }

            // A parseable certificate with a mismatched private key would otherwise
            // fail every TLS handshake indefinitely without entering the ACME path.
            val keyBody = privateKeyPem
                .substringAfter("-----BEGIN PRIVATE KEY-----", "")
                .substringBefore("-----END PRIVATE KEY-----")
            require(keyBody.isNotBlank()) { "Relay private key must use an unencrypted PKCS#8 PEM block." }
            val keyBytes = Base64.getMimeDecoder().decode(keyBody)
            val privateKey = KeyFactory.getInstance(certificate.publicKey.algorithm)
                .generatePrivate(PKCS8EncodedKeySpec(keyBytes))
            val signatureAlgorithm = when (certificate.publicKey.algorithm.uppercase()) {
                "EC" -> "SHA256withECDSA"
                "RSA" -> "SHA256withRSA"
                else -> return@runCatching true
            }
            val challenge = "PocketHost Relay certificate key match".toByteArray(Charsets.UTF_8)
            val proof = Signature.getInstance(signatureAlgorithm).run {
                initSign(privateKey)
                update(challenge)
                sign()
            }
            val keyMatches = Signature.getInstance(signatureAlgorithm).run {
                initVerify(certificate.publicKey)
                update(challenge)
                verify(proof)
            }
            if (!keyMatches) return@runCatching true

            val configuredHosts = JSONObject(configJson).optJSONArray("routes")
                ?.let { routes -> (0 until routes.length()).mapNotNull { routes.optJSONObject(it)?.optString("hostname")?.lowercase() } }
                .orEmpty()
            val certificateHosts = certificate.subjectAlternativeNames
                ?.mapNotNull { entry -> if (entry.size >= 2 && entry[0] == 2) entry[1] as? String else null }
                ?.map { it.lowercase() }
                .orEmpty()
            fun covers(pattern: String, hostname: String): Boolean =
                pattern == hostname || (pattern.startsWith("*.") &&
                    hostname.count { it == '.' } == pattern.count { it == '.' } &&
                    hostname.substringAfter('.') == pattern.removePrefix("*."))
            configuredHosts.any { host -> certificateHosts.none { san -> covers(san, host) } }
        }.getOrDefault(true)
    }

    private fun startRelay(reason: String) {
        if (!ownerRequestedRunning || stopping) return
        val result = runCatching {
            var config = RelayNodeConfigStore.read(this)
                ?: throw IllegalStateException("Relay node is not configured. Complete Relay setup first.")
            val configObject = JSONObject(config)
            if (certificateNeedsRenewal(config)) {
                if (configObject.optString("acme_contact_email").isBlank()) {
                    throw IllegalStateException("Automatic TLS needs an ACME contact email and accepted Let's Encrypt Terms in Relay settings.")
                }
                if (NativeRelay.status() == "RUNNING") {
                    val stopped = NativeRelay.stop()
                    if (stopped != "OK") throw IllegalStateException("Could not stop Relay before ACME renewal: $stopped")
                }
                OperationalLog.record(this, "INFO", "RELAY_ACME_CERTIFICATE_REQUESTED", "Starting HTTP-01 certificate issuance or renewal.")
                val issued = NativeRelay.issueCertificate(config)
                if (issued.startsWith("ERROR:")) throw IllegalStateException(issued.removePrefix("ERROR:").trim())
                val bundle = JSONObject(issued)
                configObject.put("tls_certificate_pem", bundle.getString("tls_certificate_pem"))
                configObject.put("tls_private_key_pem", bundle.getString("tls_private_key_pem"))
                configObject.put("acme_account_credentials", bundle.getJSONObject("acme_account_credentials"))
                check(RelayNodeConfigStore.save(this, configObject.toString())) {
                    "Could not securely persist the TLS certificate and ACME account credentials."
                }
                config = configObject.toString()
                OperationalLog.record(this, "INFO", "RELAY_ACME_CERTIFICATE_SAVED", "Certificate and account credentials stored in encrypted Relay configuration.")
            }
            val identity = RelayNodeIdentityStore.getOrCreate()
            OperationalLog.record(this, "INFO", "NATIVE_RELAY_NODE_IDENTITY", identity.nodeId)
            if (NativeRelay.status() == "RUNNING") "OK" else NativeRelay.start(config, getDatabasePath("pockethost_relay_node.db").absolutePath)
        }.getOrElse { "ERROR: ${it.message ?: "Relay startup failed"}" }

        main.post {
            if (stopping || !ownerRequestedRunning) return@post
            if (result == "OK") {
                cancelRetry(resetAttempts = true)
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification("Native Relay active"))
                OperationalLog.record(this, "INFO", "NATIVE_RELAY_READY", "Native Relay is active after $reason.")
            } else {
                scheduleRetry()
                OperationalLog.record(this, "ERROR", "NATIVE_RELAY_START", result)
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification("Relay unavailable: ${result.removePrefix("ERROR: ").take(100)}"))
            }
        }
    }

    override fun onDestroy() {
        stopping = true
        ownerRequestedRunning = false
        renewalTask?.cancel(true)
        cancelRetry(resetAttempts = false)
        renewalScheduler.shutdownNow()
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        runCatching { NativeRelay.stop() }
        worker.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null


    /** Retry transient DNS, network and ACME failures without requiring an app reopen. */
    @Synchronized
    private fun scheduleRetry() {
        if (!ownerRequestedRunning || stopping || retryTask?.isDone == false) return
        val delaySeconds = RelayRetryPolicy.delaySeconds(retryAttempt++)
        retryTask = renewalScheduler.schedule({
            synchronized(this) { retryTask = null }
            if (ownerRequestedRunning && !stopping) {
                worker.execute { startRelay("automatic recovery retry") }
            }
        }, delaySeconds, TimeUnit.SECONDS)
        OperationalLog.record(this, "WARN", "NATIVE_RELAY_RETRY_SCHEDULED",
            "Relay startup will retry in $delaySeconds seconds.")
    }

    @Synchronized
    private fun cancelRetry(resetAttempts: Boolean) {
        retryTask?.cancel(false)
        retryTask = null
        if (resetAttempts) retryAttempt = 0
    }

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
