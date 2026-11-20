package com.assayflux.pockethost.management

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.assayflux.pockethost.diagnostics.OperationalLog
import com.assayflux.pockethost.storage.SiteImporter
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Outbound-only remote control agent. It never opens an inbound network listener.
 * The control-plane token is encrypted with a device-bound Android Keystore key.
 */
object RemoteControlAgent {
    private const val PREFS = "pockethost_remote_control"
    private const val KEY_ALIAS = "assayflux.pockethost.remote-control.v1"
    private const val POLL_INTERVAL_MS = 15_000L
    private const val MAX_RETRY_DELAY_MS = 5 * 60_000L
    private const val MAX_CONTROL_RESPONSE_BYTES = 10 * 1024 * 1024
    @Volatile private var worker: Thread? = null
    @Volatile private var running = false
    @Volatile private var generation = 0L

    fun endpoint(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("endpoint", "") ?: ""

    internal fun relayDeviceToken(context: Context): String? = runCatching {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("token", null) ?: return null
        decryptToken(encoded)
    }.getOrNull()

    fun isConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !prefs.getString("endpoint", null).isNullOrBlank() &&
            !prefs.getString("token", null).isNullOrBlank()
    }

    internal fun isRunningForTest(): Boolean = running

    @Synchronized
    fun configure(context: Context, endpoint: String, token: String) {
        val restartAgent = running
        val uri = URI(endpoint.trim().trimEnd('/'))
        require(uri.scheme == "https" || (uri.scheme == "http" && uri.host == "127.0.0.1")) {
            "Use HTTPS for remote control. HTTP is allowed only for localhost development."
        }
        require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Invalid control-plane URL."
        }
        require(token.length >= 32) { "Device token must contain at least 32 characters." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        if (restartAgent) stop()
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("endpoint", uri.toString())
            .putString("token", Base64.encodeToString(packed, Base64.NO_WRAP))
            .commit()
        check(saved) { "Could not persist remote-control credentials." }
        if (restartAgent) start(context)
    }

    @Synchronized
    fun clear(context: Context) {
        stop()
        val cleared = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        check(cleared) { "Could not persist remote-control credential revocation." }
    }

    @Synchronized
    fun start(context: Context) {
        // Server-only mode must never poll the fleet control plane, even when
        // credentials remain stored from an earlier Command Center session.
        val operatingMode = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
            .getString("operating_mode", "SERVER_ONLY") ?: "SERVER_ONLY"
        if (operatingMode != "COMMAND_CENTER_SERVER") {
            stop()
            return
        }
        if (running || !isConfigured(context)) return
        generation += 1
        val runGeneration = generation
        running = true
        val app = context.applicationContext
        worker = Thread({
            var retryDelay = POLL_INTERVAL_MS
            while (running && generation == runGeneration && !Thread.currentThread().isInterrupted) {
                var succeeded = false
                try {
                    pollOnce(app)
                    succeeded = true
                    retryDelay = POLL_INTERVAL_MS
                } catch (error: Exception) {
                    OperationalLog.record(app, "WARN", "REMOTE_CONTROL_UNAVAILABLE",
                        error.message?.take(180) ?: "Remote control connection unavailable.")
                    retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                }
                val delay = if (succeeded) POLL_INTERVAL_MS else retryDelay
                val jitter = (delay * 0.2).toLong()
                val waitMs = (delay - jitter + java.util.concurrent.ThreadLocalRandom.current().nextLong((jitter * 2 + 1).coerceAtLeast(1))).coerceAtLeast(1)
                try { Thread.sleep(waitMs) } catch (_: InterruptedException) { break }
            }
            if (generation == runGeneration) {
                running = false
                if (worker === Thread.currentThread()) worker = null
            }
        }, "PocketHost-Remote-Control").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        generation += 1
        running = false
        worker?.interrupt()
        worker = null
    }

    private fun pollOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val endpoint = prefs.getString("endpoint", null) ?: return
        val token = decryptToken(prefs.getString("token", null) ?: return)
        val state = JSONObject()
            .put("hosting", if (com.assayflux.pockethost.engine.NativeEngine.status() == "RUNNING") "RUNNING" else "STOPPED")
            .put("python", runCatching { com.assayflux.pockethost.runtime.PythonHostRuntime.status(context) }.getOrDefault("UNKNOWN"))
            .put("node", com.assayflux.pockethost.runtime.NodeHostRuntime.status(context))
            .put("tunnel", com.assayflux.pockethost.tunnel.CloudflareTunnelRuntime.status())
            .put("reportedAt", System.currentTimeMillis())
            .put("config_version", prefs.getLong("config_version", 0L))
        post(endpoint, token, "/v1/device/heartbeat",
            JSONObject().put("state", state).put("config_version", prefs.getLong("config_version", 0L)))
        val commands = get(endpoint, token, "/v1/device/commands")
        for (index in 0 until commands.length()) {
            val command = commands.getJSONObject(index)
            val id = command.optString("id")
            if (!id.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) {
                OperationalLog.record(context, "WARN", "REMOTE_COMMAND_REJECTED",
                    "Control plane supplied a command with an invalid identifier.")
                continue
            }
            val kind = command.optString("kind")
            val payload = command.optJSONObject("payload") ?: JSONObject()
            val commandPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // Corrupt idempotency state is a fail-closed condition: silently resetting
            // it could replay a non-idempotent command after process death.
            val processed = JSONObject(commandPrefs.getString("processed_commands", "{}") ?: "{}")
            val saved = processed.optJSONObject(id)
            val outcome = when {
                saved?.optString("_state") == "started" ->
                    JSONObject().put("status", 409).put("error", "previous_execution_interrupted_operator_retry_required")
                saved != null -> saved
                else -> {
                    // Persist a start marker before side effects. If Android kills the
                    // process mid-command, a lease retry must not execute it twice.
                    processed.put(id, JSONObject().put("_state", "started"))
                    check(commandPrefs.edit().putString("processed_commands", processed.toString()).commit()) {
                        "Could not durably record the remote command start marker; command was not executed."
                    }
                    runCatching {
                        if (kind == "deploy_site") {
                            val artifactId = payload.getString("artifactId")
                            val expectedSha = payload.getString("sha256")
                            val archive = downloadArtifact(endpoint, token, artifactId, expectedSha, context)
                            try {
                                val report = FileInputStream(archive).use { SiteImporter.importZipStream(context, it) }
                                JSONObject().put("status", 200).put("files", report.fileCount)
                                    .put("bytes", report.totalBytes).put("source", report.source)
                            } finally { archive.delete() }
                        } else ManagementApiServer.executeRemoteCommand(context, kind, payload)
                    }.getOrElse { JSONObject().put("status", 500).put("error", it.message ?: "command_failed") }
                }
            }
            processed.put(id, outcome)
            while (processed.length() > 500) {
                val oldest = processed.keys().asSequence().firstOrNull() ?: break
                processed.remove(oldest)
            }
            // Persist the final result before acknowledging; a lost ACK is safe to retry.
            check(commandPrefs.edit().putString("processed_commands", processed.toString()).commit()) {
                "Could not durably save the remote command result; acknowledgement withheld."
            }
            val success = outcome.optInt("status", 500) in 200..299
            if (success && kind == "apply_config") {
                check(commandPrefs.edit().putLong("config_version", payload.optLong("version", 0L)).commit()) {
                    "Could not durably save the applied remote configuration version."
                }
            }
            runCatching {
                post(endpoint, token, "/v1/device/commands/$id/ack",
                    JSONObject().put("success", success).put("result", outcome))
            }.onFailure {
                OperationalLog.record(context, "WARN", "REMOTE_COMMAND_ACK_FAILED", "Remote command acknowledgement failed.")
            }
        }
    }

    private fun downloadArtifact(endpoint: String, token: String, artifactId: String, expectedSha: String, context: Context): File {
        require(artifactId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid deployment artifact ID." }
        require(expectedSha.matches(Regex("[0-9a-f]{64}"))) { "Invalid deployment checksum." }
        val connection = URL(endpoint.trimEnd('/') + "/v1/device/artifacts/" + artifactId).openConnection() as HttpURLConnection
        val target = File(context.cacheDir, "remote-deploy-" + artifactId + ".zip")
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer " + token)
            connection.setRequestProperty("Accept", "application/zip")
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("Artifact download returned HTTP " + status)
            val declared = connection.contentLengthLong
            require(declared in 1..(50L * 1024L * 1024L)) { "Deployment archive exceeds the 50 MiB limit." }
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 50L * 1024L * 1024L) { "Deployment archive exceeds the 50 MiB limit." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(total == declared) { "Deployment archive was truncated." }
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualSha == expectedSha) { "Deployment archive checksum mismatch." }
            return target
        } catch (error: Exception) {
            target.delete()
            throw error
        } finally { connection.disconnect() }
    }

    private fun get(endpoint: String, token: String, path: String): JSONArray {
        val text = request(endpoint, token, path, "GET", null)
        return JSONArray(text)
    }

    private fun post(endpoint: String, token: String, path: String, body: JSONObject): JSONObject {
        val text = request(endpoint, token, path, "POST", body.toString())
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun request(endpoint: String, token: String, path: String, method: String, body: String?): String {
        val connection = URL(endpoint.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 7_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBytes = stream?.use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_CONTROL_RESPONSE_BYTES) { "Control-plane response exceeds the 10 MiB safety limit." }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            } ?: ByteArray(0)
            if (status !in 200..299) throw IllegalStateException("Control plane returned HTTP $status")
            return String(responseBytes, Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun decryptToken(encoded: String): String {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > 12) { "Stored remote token is invalid." }
        val iv = packed.copyOfRange(0, 12)
        val ciphertext = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }
}
