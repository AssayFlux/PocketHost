package com.assayflux.pockethost.management

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.content.Intent
import com.assayflux.pockethost.service.HostingService
import java.io.File
import com.assayflux.pockethost.diagnostics.MetricsStore
import com.assayflux.pockethost.diagnostics.OperationalLog
import com.assayflux.pockethost.diagnostics.CapacityController
import com.assayflux.pockethost.diagnostics.CapacityLab
import com.assayflux.pockethost.diagnostics.DailyOperationsReport
import com.assayflux.pockethost.diagnostics.TelemetryStore
import com.assayflux.pockethost.diagnostics.DeviceTelemetry
import com.assayflux.pockethost.tunnel.CloudflareTunnelRuntime
import com.assayflux.pockethost.tunnel.CloudflareTokenStore
import com.assayflux.pockethost.engine.NativeEngine
import com.assayflux.pockethost.runtime.PythonHostRuntime
import com.assayflux.pockethost.runtime.NodeHostRuntime
import com.assayflux.pockethost.sites.SiteRegistry
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore

/**
 * Loopback-only management API. Pairing is explicitly approved on the Android device;
 * bearer credentials are memory-only, expire, and can be revoked independently.
 */
object ManagementApiServer {
    private const val PORT = 17841
    private const val SESSION_SECONDS = 12 * 60 * 60L
    private const val MAX_HEADER = 16 * 1024
    private val random = SecureRandom()
    private val workers = ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(32), ThreadPoolExecutor.AbortPolicy())
    // Keep password derivation from occupying every request worker during login bursts.
    private val loginVerificationSlots = Semaphore(2)
    @Volatile private var listener: ServerSocket? = null
    @Volatile private var acceptThread: Thread? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var pending: Pairing? = null
    private data class Session(val expiresAt: Long, val username: String, val role: String, val deviceApprovedPairing: Boolean = false)
    private val sessions = HashMap<String, Session>()
    private val pairingLock = Any()
    private val loginAttemptsLock = Any()
    private val mutationLock = Any()
    private data class LoginThrottle(var windowStartedAt: Long, var failures: Int = 0, var inFlight: Int = 0)
    private val loginAttempts = HashMap<String, LoginThrottle>()

    private data class Pairing(val id: String, val code: String, val expires: Long, @Volatile var approved: Boolean = false, var attempts: Int = 0)
    @Volatile private var lastChallengeAt = 0L
    @Volatile private var challengeWindowAt = 0L
    @Volatile private var challengeWindowCount = 0

    @Synchronized fun start(context: Context): String {
        if (listener?.isClosed == false) return "RUNNING"
        appContext = context.applicationContext
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(java.net.InetSocketAddress(java.net.InetAddress.getByName("127.0.0.1"), PORT), 16)
        listener = socket
        acceptThread = Thread({
            while (!socket.isClosed) {
                try {
                    val client = socket.accept()
                    try { workers.execute { handle(client) } } catch (_: RejectedExecutionException) { runCatching { client.close() } }
                } catch (_: Exception) {
                    if (!socket.isClosed) Thread.sleep(100)
                }
            }
        }, "PocketHost-Management-API").apply { isDaemon = true; start() }
        return "RUNNING"
    }

    @Synchronized fun stop() {
        val oldListener = listener
        val oldThread = acceptThread
        runCatching { oldListener?.close() }
        if (oldThread != null && oldThread !== Thread.currentThread()) runCatching { oldThread.join(500) }
        listener = null
        acceptThread = null
        synchronized(sessions) { sessions.clear() }
        synchronized(pairingLock) {
            pending = null
            // A stopped listener has no active clients; start a fresh rate-limit window
            // when the local management service is explicitly restarted.
            lastChallengeAt = 0L
            challengeWindowAt = 0L
            challengeWindowCount = 0
        }
        synchronized(loginAttemptsLock) { loginAttempts.clear() }
    }

    fun pendingApprovalCode(): String? = synchronized(pairingLock) {
        val p = pending
        if (p != null && p.expires > Instant.now().epochSecond && !p.approved) p.code else null
    }

    fun approvePairing(code: String): Boolean = synchronized(pairingLock) {
        val p = pending ?: return@synchronized false
        if (p.expires <= Instant.now().epochSecond || p.code != code) return@synchronized false
        p.approved = true
        true
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 5_000
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())
            try {
                val header = readHeader(input) ?: return
                val lines = header.split("\r\n")
                val request = lines.firstOrNull()?.split(' ') ?: return
                if (request.size != 3 || request[2] != "HTTP/1.1" || !request[1].startsWith("/")) {
                    respond(output, 400, JSONObject().put("error", "invalid_request_line")); return
                }
                val method = request[0]
                val path = request[1].substringBefore('?')
                val headerPairs = lines.drop(1).mapNotNull {
                    val i = it.indexOf(':'); if (i <= 0) null else it.substring(0, i).trim().lowercase() to it.substring(i + 1).trim()
                }
                if (headerPairs.groupingBy { it.first }.eachCount().values.any { it > 1 }) {
                    respond(output, 400, JSONObject().put("error", "duplicate_header")); return
                }
                val headers = headerPairs.toMap()
                val requestHost = headers["host"]?.substringBefore(':')?.lowercase()
                if (requestHost != "127.0.0.1" && requestHost != "localhost") {
                    respond(output, 403, JSONObject().put("error", "host_not_allowed")); return
                }
                if (headers.containsKey("transfer-encoding")) {
                    respond(output, 400, JSONObject().put("error", "transfer_encoding_not_supported")); return
                }
                val rawLength = headers["content-length"]
                if (rawLength != null && rawLength.toIntOrNull() == null) {
                    respond(output, 400, JSONObject().put("error", "invalid_content_length")); return
                }
                val length = rawLength?.toIntOrNull() ?: 0
                if (length !in 0..8192) { respond(output, 413, JSONObject().put("error", "body_too_large")); return }
                val bodyBytes = ByteArray(length)
                var offset = 0
                while (offset < length) {
                    val n = input.read(bodyBytes, offset, length - offset)
                    if (n < 0) { respond(output, 400, JSONObject().put("error", "incomplete_body")); return }
                    offset += n
                }
                val body = if (length == 0) JSONObject() else runCatching {
                    JSONObject(String(bodyBytes, Charsets.UTF_8))
                }.getOrElse {
                    respond(output, 400, JSONObject().put("error", "invalid_json_body")); return
                }
                val origin = headers["origin"]
                if (method == "OPTIONS") {
                    if (!allowedOrigin(origin)) { respond(output, 403, JSONObject().put("error", "origin_not_allowed"), origin); return }
                    respond(output, 204, JSONObject(), origin)
                    return
                }
                if (origin != null && !allowedOrigin(origin)) {
                    respond(output, 403, JSONObject().put("error", "origin_not_allowed"), origin)
                    return
                }
                val result = route(method, path, headers["authorization"], body)
                respond(output, result.first, result.second, origin)
            } catch (_: Exception) {
                runCatching { respond(output, 400, JSONObject().put("error", "bad_request")) }
            }
        }
    }

    private fun readHeader(input: BufferedInputStream): String? {
        val bytes = ArrayList<Byte>()
        var matched = 0
        while (bytes.size < MAX_HEADER) {
            val value = input.read()
            if (value < 0) return null
            bytes.add(value.toByte())
            matched = when {
                matched == 0 && value == 13 -> 1
                matched == 1 && value == 10 -> 2
                matched == 2 && value == 13 -> 3
                matched == 3 && value == 10 -> 4
                value == 13 -> 1
                else -> 0
            }
            if (matched == 4) return String(bytes.toByteArray(), Charsets.ISO_8859_1)
        }
        throw IllegalArgumentException("Headers too large")
    }

    private fun route(method: String, path: String, authorization: String?, body: JSONObject): Pair<Int, JSONObject> {
        // Keep expensive authentication and health checks independent from configuration
        // writes. Other mutations remain serialized so route/site state cannot interleave.
        val independentRequest = method == "GET" && path == "/api/v1/health" ||
            path.startsWith("/api/v1/auth/") ||
            path == "/api/v1/pair/challenge" || path == "/api/v1/pair/complete"
        return if (independentRequest) {
            routeUnlocked(method, path, authorization, body)
        } else {
            synchronized(mutationLock) { routeUnlocked(method, path, authorization, body) }
        }
    }

    private fun routeUnlocked(method: String, path: String, authorization: String?, body: JSONObject): Pair<Int, JSONObject> {
        if (method == "GET" && path == "/api/v1/health")
            return 200 to JSONObject().put("status", "ok").put("apiVersion", "v1")
        if (method == "POST" && path == "/api/v1/pair/challenge") {
            return synchronized(pairingLock) {
                val now = Instant.now().epochSecond
                if (now - challengeWindowAt >= 60) { challengeWindowAt = now; challengeWindowCount = 0 }
                challengeWindowCount++
                if (challengeWindowCount > 10 || now - lastChallengeAt < 3)
                    return@synchronized (429 to JSONObject().put("error", "pairing_rate_limited"))
                lastChallengeAt = now
                val id = randomToken(12)
                val code = (100000 + random.nextInt(900000)).toString()
                val pairing = Pairing(id, code, now + 120)
                pending = pairing
                201 to JSONObject().put("challengeId", id).put("expiresAt", pairing.expires)
            }
        }
        if (method == "POST" && path == "/api/v1/pair/complete") {
            return synchronized(pairingLock) {
                val p = pending
                if (p == null || p.expires <= Instant.now().epochSecond || p.id != body.optString("challengeId") ||
                    !p.approved || p.attempts >= 5)
                    return@synchronized (401 to JSONObject().put("error", "pairing_not_approved"))
                if (p.code != body.optString("code")) {
                    p.attempts++
                    return@synchronized (401 to JSONObject().put("error", "invalid_pairing_code"))
                }
                val token = randomToken(32)
                synchronized(sessions) {
                    sessions[tokenHash(token)] = Session(Instant.now().epochSecond + SESSION_SECONDS,
                        "owner", "OWNER", deviceApprovedPairing = true)
                }
                pending = null
                200 to JSONObject().put("accessToken", token)
                    .put("expiresAt", Instant.now().epochSecond + SESSION_SECONDS)
            }
        }
        if (method == "POST" && path == "/api/v1/auth/login") {
            val username = body.optString("username").trim()
            val normalizedUsername = username.lowercase()
            val now = Instant.now().epochSecond
            val context = appContext ?: return 503 to JSONObject().put("error", "service_unavailable")
            val throttle = synchronized(loginAttemptsLock) {
                loginAttempts.entries.removeAll {
                    now - it.value.windowStartedAt >= 60 && it.value.inFlight == 0
                }
                if (username.length > 32) {
                    LoginThrottle(now) // Invalid oversized names are not retained in the bounded map.
                } else {
                    val state = loginAttempts[normalizedUsername] ?: run {
                        if (loginAttempts.size >= 256) {
                            val oldest = loginAttempts.entries
                                .filter { it.value.inFlight == 0 }
                                .minByOrNull { it.value.windowStartedAt }?.key
                            if (oldest != null) loginAttempts.remove(oldest)
                        }
                        if (loginAttempts.size >= 256) null
                        else LoginThrottle(now).also { loginAttempts[normalizedUsername] = it }
                    }
                    if (state == null) {
                        null
                    } else if (now - state.windowStartedAt >= 60 && state.inFlight == 0) {
                        state.windowStartedAt = now
                        state.failures = 0
                        state.inFlight++
                        state
                    } else if (state.failures >= 8) {
                        null
                    } else {
                        state.inFlight++
                        state
                    }
                }
            } ?: return 429 to JSONObject().put("error", "login_rate_limited")
            if (!loginVerificationSlots.tryAcquire()) {
                synchronized(loginAttemptsLock) {
                    throttle.inFlight = (throttle.inFlight - 1).coerceAtLeast(0)
                    if (throttle.inFlight == 0 && throttle.failures == 0 && username.length <= 32) {
                        loginAttempts.remove(normalizedUsername, throttle)
                    }
                }
                return 429 to JSONObject().put("error", "login_verification_busy")
            }
            var account: HostAccountStore.Account? = null
            var verificationFailed = false
            try {
                try {
                    account = HostAccountStore.verify(context, username, body.optString("password").toCharArray())
                } catch (_: Exception) {
                    verificationFailed = true
                }
            } finally {
                loginVerificationSlots.release()
            }
            synchronized(loginAttemptsLock) {
                throttle.inFlight = (throttle.inFlight - 1).coerceAtLeast(0)
                if (account == null) {
                    if (now - throttle.windowStartedAt >= 60 && throttle.inFlight == 0) {
                        throttle.windowStartedAt = now
                        throttle.failures = 0
                    }
                    throttle.failures++
                } else {
                    throttle.failures = 0
                }
                if (throttle.inFlight == 0 && throttle.failures == 0 && username.length <= 32) {
                    loginAttempts.remove(normalizedUsername, throttle)
                }
            }
            if (verificationFailed) return 503 to JSONObject().put("error", "credential_store_unavailable")
            if (account == null) return 401 to JSONObject().put("error", "invalid_credentials")
            val token = randomToken(32)
            val expiry = now + SESSION_SECONDS
            synchronized(sessions) { sessions[tokenHash(token)] = Session(expiry, account.username, account.role) }
            return 200 to JSONObject().put("accessToken", token).put("expiresAt", expiry)
                .put("username", account.username).put("role", account.role)
        }
        val token = authorization?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
        val session = token?.let { raw -> synchronized(sessions) {
            val record = sessions[tokenHash(raw)]
            if (record != null && record.expiresAt > Instant.now().epochSecond) record
            else { sessions.remove(tokenHash(raw)); null }
        } }
        if (session == null) return 401 to JSONObject().put("error", "unauthorized")
        if (session.role == "OPERATOR") {
            val context = appContext ?: return 503 to JSONObject().put("error", "service_unavailable")
            val stillActive = runCatching {
                HostAccountStore.list(context).any { it.role == "OPERATOR" && it.username.equals(session.username, true) }
            }.getOrDefault(false)
            if (!stillActive) {
                if (token != null) synchronized(sessions) { sessions.remove(tokenHash(token)) }
                return 401 to JSONObject().put("error", "operator_account_revoked")
            }
        }
        if (method == "DELETE" && path == "/api/v1/session") {
            synchronized(sessions) { sessions.remove(tokenHash(token!!)) }
            return 204 to JSONObject()
        }
        if (method == "GET" && path == "/api/v1/auth/me")
            return 200 to JSONObject().put("username", session.username).put("role", session.role)
                .put("expiresAt", session.expiresAt).put("deviceApprovedPairing", session.deviceApprovedPairing)
        if (method == "POST" && path == "/api/v1/auth/recover-owner") {
            if (session.role != "OWNER" || !session.deviceApprovedPairing)
                return 403 to JSONObject().put("error", "device_approved_pairing_required")
            val context = appContext ?: return 503 to JSONObject().put("error", "service_unavailable")
            val ownerUsername = runCatching {
                HostAccountStore.recoverOwnerPassword(context, body.optString("newPassword").toCharArray())
            }.getOrElse { return 400 to JSONObject().put("error", it.message ?: "owner_recovery_failed") }
                ?: return 404 to JSONObject().put("error", "owner_account_not_found")
            // Recovery revokes every existing Owner and pairing session before issuing
            // a fresh session for the recovered Owner identity.
            synchronized(sessions) {
                sessions.entries.removeAll { it.value.role == "OWNER" }
            }
            val replacement = randomToken(32)
            val expiry = Instant.now().epochSecond + SESSION_SECONDS
            synchronized(sessions) {
                sessions[tokenHash(replacement)] = Session(expiry, ownerUsername, "OWNER")
            }
            OperationalLog.record(context, "WARN", "HOST_OWNER_PASSWORD_RECOVERED",
                "Owner credentials reset through locally approved device pairing; prior Owner sessions revoked.")
            return 200 to JSONObject().put("accessToken", replacement).put("expiresAt", expiry)
                .put("username", ownerUsername).put("role", "OWNER")
        }
        if (method == "POST" && path == "/api/v1/auth/password") {
            val context = appContext ?: return 503 to JSONObject().put("error", "service_unavailable")
            val changed = runCatching {
                HostAccountStore.changePassword(
                    context,
                    session.username,
                    body.optString("currentPassword").toCharArray(),
                    body.optString("newPassword").toCharArray()
                )
            }.getOrElse { return 400 to JSONObject().put("error", it.message ?: "password_change_failed") }
            if (!changed) return 401 to JSONObject().put("error", "invalid_current_password")
            // A password change revokes every old session for this account, then returns
            // one replacement credential so the current console can continue securely.
            synchronized(sessions) {
                sessions.entries.removeAll { it.value.username.equals(session.username, true) }
            }
            val replacement = randomToken(32)
            val expiry = Instant.now().epochSecond + SESSION_SECONDS
            synchronized(sessions) {
                sessions[tokenHash(replacement)] = Session(expiry, session.username, session.role)
            }
            OperationalLog.record(context, "INFO", "HOST_ACCOUNT_PASSWORD_CHANGED",
                "actor=" + session.username + "; prior sessions revoked")
            return 200 to JSONObject().put("accessToken", replacement).put("expiresAt", expiry)
        }
        if (path == "/api/v1/auth/accounts") {
            if (session.role != "OWNER") return 403 to JSONObject().put("error", "owner_required")
            val context = appContext ?: return 503 to JSONObject().put("error", "service_unavailable")
            if (method == "GET") {
                val items = org.json.JSONArray()
                HostAccountStore.list(context).forEach { items.put(JSONObject().put("username", it.username).put("role", it.role).put("createdAt", it.createdAt)) }
                return 200 to JSONObject().put("items", items)
            }
            if (method == "POST") return runCatching {
                val role = if (!HostAccountStore.hasOwner(context)) "OWNER" else body.optString("role", "OPERATOR").uppercase()
                require(role == "OPERATOR" || (role == "OWNER" && !HostAccountStore.hasOwner(context))) { "only_one_owner_account_supported" }
                val account = HostAccountStore.create(context, body.getString("username"), body.getString("password").toCharArray(), role)
                OperationalLog.record(context, "INFO", "HOST_ACCOUNT_CREATED", "Role=${account.role}; actor=${session.username}")
                201 to JSONObject().put("username", account.username).put("role", account.role)
            }.getOrElse { 400 to JSONObject().put("error", it.message ?: "account_creation_failed") }
        }
        if (method == "DELETE" && path.startsWith("/api/v1/auth/accounts/")) {
            if (session.role != "OWNER") return 403 to JSONObject().put("error", "owner_required")
            val username = path.removePrefix("/api/v1/auth/accounts/")
            if (username.equals(session.username, true)) return 409 to JSONObject().put("error", "cannot_revoke_current_account")
            val context = appContext ?: return 503 to JSONObject().put("error", "service_unavailable")
            val removed = runCatching { HostAccountStore.revokeOperator(context, username) }.getOrDefault(false)
            return if (removed) 204 to JSONObject() else 404 to JSONObject().put("error", "account_not_found")
        }
        if (session.role == "OPERATOR" && !operatorAllowed(method, path))
            return 403 to JSONObject().put("error", "owner_required")
        val context = appContext ?: return 503 to JSONObject().put("error", "service_unavailable")
        if (method == "GET" && path == "/api/v1/sites") {
            val items = org.json.JSONArray()
            SiteRegistry.list(context).forEach { site ->
                val hosts = org.json.JSONArray()
                site.hostnames.forEach(hosts::put)
                items.put(JSONObject().put("id", site.id).put("name", site.name)
                    .put("runtime", site.runtime).put("hostnames", hosts)
                    .put("state", when {
                        !site.enabled -> "DISABLED"
                        site.runtime == "static" -> if (NativeEngine.status() == "RUNNING") "RUNNING" else "STOPPED"
                        site.runtime == "python-wsgi" -> runCatching { PythonHostRuntime.status(context) }.getOrDefault("ERROR")
                        site.runtime == "node" -> NodeHostRuntime.status(context)
                        else -> "UNKNOWN"
                    }))
            }
            return 200 to JSONObject().put("items", items)
        }
        if (method == "POST" && path == "/api/v1/sites") {
            return runCatching {
                val hosts = body.optJSONArray("hostnames") ?: org.json.JSONArray()
                val site = SiteRegistry.Site(body.getString("id"), body.getString("name"),
                    body.optString("runtime", "static"), body.getString("root"),
                    (0 until hosts.length()).map { hosts.getString(it) }, body.optBoolean("enabled", true))
                SiteRegistry.put(context, site)
                restartEngineRoutes(context)
                201 to JSONObject().put("status", "configured").put("id", site.id)
            }.getOrElse { 400 to JSONObject().put("error", it.message ?: "invalid_site") }
        }
        if (method == "DELETE" && path.startsWith("/api/v1/sites/")) {
            val id = path.removePrefix("/api/v1/sites/")
            if (!id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) return 400 to JSONObject().put("error", "invalid_site_id")
            if (SiteRegistry.list(context).none { it.id == id }) return 404 to JSONObject().put("error", "site_not_found")
            SiteRegistry.remove(context, id)
            restartEngineRoutes(context)
            return 204 to JSONObject()
        }
        if (method == "GET" && path == "/api/v1/status") {
            val py = runCatching { PythonHostRuntime.status(context) }.getOrDefault("ERROR")
            return 200 to JSONObject().put("state", if (NativeEngine.status() == "RUNNING") "local" else "stopped")
                .put("apiVersion", "v1")
                .put("runtimes", org.json.JSONArray()
                    .put(JSONObject().put("name", "rust-static").put("state", NativeEngine.status()))
                    .put(JSONObject().put("name", "python").put("state", py))
                    .put(JSONObject().put("name", "node").put("state", NodeHostRuntime.status(context))))
        }
        if (method == "GET" && path == "/api/v1/resources") {
            val prefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
            val memory = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
            val defaultPythonWorkers = (memory.totalMem / (512L * 1024L * 1024L)).toInt().coerceIn(4, 16)
            val defaultNodeHeap = (memory.totalMem / (1024L * 1024L * 20L)).toInt().coerceIn(128, 384)
            return 200 to JSONObject().put("pythonMaxWorkers", prefs.getInt("python_max_workers", defaultPythonWorkers))
                .put("nodeHeapMb", prefs.getInt("node_heap_mb", defaultNodeHeap))
                .put("nodeHeapRangeMb", org.json.JSONArray().put(96).put(512))
                .put("pythonWorkerRange", org.json.JSONArray().put(2).put(16))
                .put("deviceTotalMemoryBytes", memory.totalMem).put("deviceAvailableMemoryBytes", memory.availMem)
                .put("note", "Limits are enforced per runtime process; Android may still terminate processes under system memory pressure.")
        }
        if (method == "POST" && path == "/api/v1/resources") {
            val pythonWorkers = body.optInt("pythonMaxWorkers", -1)
            val nodeHeap = body.optInt("nodeHeapMb", -1)
            if (pythonWorkers !in 2..16 || nodeHeap !in 96..512)
                return 400 to JSONObject().put("error", "resource_limits_out_of_range")
            val pythonWasRunning = runCatching { PythonHostRuntime.status(context) }.getOrDefault("STOPPED") == "RUNNING"
            val nodeWasRunning = NodeHostRuntime.status(context) in setOf("RUNNING", "STARTING")
            context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                .putInt("python_max_workers", pythonWorkers).putInt("node_heap_mb", nodeHeap).commit()
            // Keep the Rust static server online. Runtime supervisors observe the
            // stopped workers and relaunch them with the newly persisted limits.
            if (pythonWasRunning) PythonHostRuntime.stop(context)
            if (nodeWasRunning) NodeHostRuntime.stop(context)
            OperationalLog.record(context, "INFO", "RESOURCE_LIMITS_UPDATED",
                "Python workers=$pythonWorkers; Node V8 heap=$nodeHeap MB; Python restarted=$pythonWasRunning; Node restarted=$nodeWasRunning")
            return 200 to JSONObject().put("pythonMaxWorkers", pythonWorkers).put("nodeHeapMb", nodeHeap)
                .put("pythonRestarted", pythonWasRunning).put("nodeRestarted", nodeWasRunning)
                .put("staticHostingPreserved", NativeEngine.status() == "RUNNING")
        }
        if (method == "GET" && path == "/api/v1/capacity") {
            val profile = CapacityController.tick(context)
                .put("activeConnections", NativeEngine.activeConnections())
                .put("queuedConnections", NativeEngine.queuedConnections())
                .put("rateLimitedRequests", NativeEngine.rateLimitedRequests())
                .put("siteLimitRejections", NativeEngine.siteLimitRejections())
                .put("rejectedConnections", NativeEngine.rejectedConnections())
                .put("engineState", NativeEngine.status())
            return 200 to profile
        }
        if (method == "POST" && path == "/api/v1/capacity") {
            return runCatching {
                val percent = body.getInt("percent")
                val duration = body.optInt("durationMinutes", 0)
                val result = if (duration > 0) CapacityController.schedule(context, percent, duration)
                    else { CapacityController.setPercent(context, percent); CapacityController.snapshot(context) }
                200 to result
            }.getOrElse { 400 to JSONObject().put("error", it.message ?: "invalid_capacity_profile") }
        }
        if (method == "POST" && path == "/api/v1/capacity/benchmark") {
            if (NativeEngine.status() != "RUNNING") return 409 to JSONObject().put("error", "hosting_must_be_running")
            return runCatching {
                val port = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getInt("port", 8080)
                202 to CapacityLab.start(context, port)
            }.getOrElse { 409 to JSONObject().put("error", it.message ?: "benchmark_unavailable") }
        }
        if (method == "GET" && path == "/api/v1/capacity/benchmark") {
            return 200 to CapacityLab.snapshot(context)
        }
        if (method == "GET" && path == "/api/v1/reports") {
            val reports = org.json.JSONArray()
            DailyOperationsReport.list(context).forEach(reports::put)
            return 200 to JSONObject().put("items", reports).put("retentionDays", 90)
        }
        if (method == "GET" && path == "/api/v1/telemetry") {
            val samples = org.json.JSONArray()
            TelemetryStore.history(context, 120).forEach(samples::put)
            val latest = if (NativeEngine.status() == "RUNNING")
                TelemetryStore.latest(context) ?: TelemetryStore.record(context)
            else DeviceTelemetry.snapshot(context)
            return 200 to JSONObject().put("latest", latest)
                .put("samples", samples).put("sampleCount", samples.length())
        }
        if (method == "GET" && path == "/api/v1/metrics") {
            val m = MetricsStore.snapshot(context)
            val sources = org.json.JSONArray().put(JSONObject().put("source", "rust-static")
                .put("requests", m.totalRequests).put("bytesServed", m.totalBytes)
                .put("todayRequests", m.todayRequests).put("todayBytes", m.todayBytes))
            val python = runCatching { PythonHostRuntime.metrics(context) }.getOrNull()
            if (python != null) {
                python.put("processPssKb", Debug.getPss())
                    .put("resourceScope", "Shared Android app process; includes the UI, Python and native engine.")
                sources.put(python)
            }
            NodeHostRuntime.metrics(context)?.let { sources.put(it) }
            val memoryInfo = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(memoryInfo)
            val javaRuntime = Runtime.getRuntime()
            val resources = JSONObject()
                .put("processPssKb", Debug.getPss())
                .put("nativeHeapAllocatedBytes", Debug.getNativeHeapAllocatedSize())
                .put("javaHeapUsedBytes", javaRuntime.totalMemory() - javaRuntime.freeMemory())
                .put("javaHeapMaxBytes", javaRuntime.maxMemory())
                .put("availableDeviceMemoryBytes", memoryInfo.availMem)
                .put("totalDeviceMemoryBytes", memoryInfo.totalMem)
                .put("lowMemory", memoryInfo.lowMemory)
                .put("telemetry", if (NativeEngine.status() == "RUNNING") TelemetryStore.latest(context) ?: TelemetryStore.record(context) else DeviceTelemetry.snapshot(context))
            return 200 to JSONObject().put("sampledAt", Instant.now().toString())
                .put("sources", sources).put("deviceResources", resources)
        }
        if (method == "GET" && path == "/api/v1/logs") {
            val entries = org.json.JSONArray()
            OperationalLog.read(context, body.optInt("limit", 100).coerceIn(1, 500)).forEach { entry ->
                entries.put(JSONObject().put("timestamp", entry.timestamp).put("level", entry.level)
                    .put("event", entry.event).put("message", entry.message))
            }
            return 200 to JSONObject().put("items", entries)
        }
        if (method == "GET" && path == "/api/v1/tunnel") {
            return 200 to JSONObject().put("state", CloudflareTunnelRuntime.status())
                .put("configured", CloudflareTokenStore.hasToken(context))
        }
        if (method == "POST" && path == "/api/v1/tunnel/actions") {
            val prefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
            return when (body.optString("action")) {
                "stop" -> {
                    prefs.edit().putBoolean("tunnel_enabled", false).apply()
                    200 to JSONObject().put("state", CloudflareTunnelRuntime.stop())
                }
                "start" -> {
                    if (!CloudflareTokenStore.hasToken(context)) {
                        409 to JSONObject().put("error", "tunnel_token_not_configured")
                    } else {
                        prefs.edit().putBoolean("tunnel_enabled", true).apply()
                        context.startForegroundService(Intent(context, HostingService::class.java))
                        202 to JSONObject().put("state", "CONNECTING")
                    }
                }
                else -> 400 to JSONObject().put("error", "unsupported_tunnel_action")
            }
        }
        if (method == "POST" && path.startsWith("/api/v1/sites/") && path.endsWith("/actions")) {
            val siteId = path.removePrefix("/api/v1/sites/").removeSuffix("/actions").trim('/')
            val site = SiteRegistry.list(context).firstOrNull { it.id == siteId }
                ?: return 404 to JSONObject().put("error", "site_not_found")
            val action = body.optString("action")
            return when (site.runtime) {
                "static" -> when (action) {
                    "start" -> {
                        if (!site.enabled) {
                            SiteRegistry.put(context, site.copy(enabled = true))
                            if (NativeEngine.status() == "RUNNING") restartEngineRoutes(context)
                        }
                        if (NativeEngine.status() == "RUNNING") {
                            200 to JSONObject().put("state", "RUNNING")
                        } else {
                            val prefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
                            val routes = SiteRegistry.hostRoutingJson(context, prefs.getInt("python_port", 8081), prefs.getInt("node_port", 3000))
                            val port = prefs.getInt("port", 8080)
                            val lan = prefs.getBoolean("allow_lan", false)
                            val result = if (routes.length() > 0) NativeEngine.startMultiSite(
                                File(context.filesDir, "site").absolutePath, routes.toString(), port, lan
                            ) else NativeEngine.start(site.root, port, lan)
                            if (result == "OK") 200 to JSONObject().put("state", "RUNNING")
                            else 503 to JSONObject().put("error", result)
                        }
                    }
                    "stop" -> {
                        SiteRegistry.put(context, site.copy(enabled = false))
                        val prefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
                        val remainingRoutes = SiteRegistry.hostRoutingJson(context, prefs.getInt("python_port", 8081), prefs.getInt("node_port", 3000))
                        if (NativeEngine.status() == "RUNNING") {
                            if (remainingRoutes.length() > 0) restartEngineRoutes(context)
                            else NativeEngine.stop()
                        }
                        200 to JSONObject().put("state", "STOPPED")
                    }
                    else -> 400 to JSONObject().put("error", "unsupported_site_action")
                }
                "python-wsgi" -> when (action) {
                    "start" -> {
                        val prefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("python_enabled", true).putString("python_project_root", site.root).apply()
                        context.startForegroundService(Intent(context, HostingService::class.java))
                        202 to JSONObject().put("state", "STARTING")
                    }
                    "stop" -> {
                        context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit().putBoolean("python_enabled", false).apply()
                        200 to JSONObject().put("state", PythonHostRuntime.stop(context))
                    }
                    else -> 400 to JSONObject().put("error", "unsupported_site_action")
                }
                "node" -> when (action) {
                    "start" -> runCatching {
                        val prefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
                        val port = prefs.getInt("node_port", 3000)
                        prefs.edit().putBoolean("node_enabled", true).putString("node_project_root", site.root).apply()
                        context.startForegroundService(Intent(context, HostingService::class.java))
                        "STARTING"
                    }.fold({ 202 to JSONObject().put("state", it) }, { 503 to JSONObject().put("error", it.message ?: "node_start_failed") })
                    "stop" -> {
                        context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit().putBoolean("node_enabled", false).apply()
                        200 to JSONObject().put("state", NodeHostRuntime.stop(context))
                    }
                    else -> 400 to JSONObject().put("error", "unsupported_site_action")
                }
                else -> 400 to JSONObject().put("error", "unsupported_runtime")
            }
        }
        return 404 to JSONObject().put("error", "not_found")
    }

    /**
     * Executes only the typed remote-control operations accepted by the control plane.
     * The temporary in-process session reuses the same validation and route handlers as
     * the local console; remote requests never get direct filesystem or shell access.
     */
    fun executeRemoteCommand(context: Context, kind: String, payload: JSONObject): JSONObject {
        appContext = context.applicationContext
        val token = randomToken(32)
        synchronized(sessions) { sessions[tokenHash(token)] = Session(Instant.now().epochSecond + 60, "remote-control", "OWNER") }
        try {
            val result = when (kind) {
                "site_action" -> {
                    val siteId = payload.optString("siteId")
                    if (!siteId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) {
                        400 to JSONObject().put("error", "invalid_site_id")
                    } else route("POST", "/api/v1/sites/$siteId/actions", "Bearer $token", payload)
                }
                "tunnel_action" -> route("POST", "/api/v1/tunnel/actions", "Bearer $token", payload)
                "apply_config" -> {
                    val config = payload.optJSONObject("config")
                        ?: return JSONObject().put("status", 400).put("error", "missing_config")
                    val supportedKeys = setOf("pythonMaxWorkers", "nodeHeapMb", "tunnelEnabled")
                    val requestedKeys = config.keys().asSequence().toSet()
                    if (requestedKeys.isEmpty() || requestedKeys.any { it !in supportedKeys }) {
                        return JSONObject().put("status", 400).put("error", "unsupported_config_key")
                    }
                    val prefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
                    val results = org.json.JSONArray()
                    var applied = false
                    if (config.has("pythonMaxWorkers") || config.has("nodeHeapMb")) {
                        val current = route("GET", "/api/v1/resources", "Bearer $token", JSONObject()).second
                        val resourceBody = JSONObject()
                            .put("pythonMaxWorkers", config.optInt("pythonMaxWorkers", current.optInt("pythonMaxWorkers", 8)))
                            .put("nodeHeapMb", config.optInt("nodeHeapMb", current.optInt("nodeHeapMb", 192)))
                        val resourceResult = route("POST", "/api/v1/resources", "Bearer $token", resourceBody)
                        if (resourceResult.first !in 200..299) return JSONObject()
                            .put("status", resourceResult.first).put("body", resourceResult.second)
                        results.put(JSONObject().put("resourceLimits", resourceResult.second))
                        applied = true
                    }
                    if (config.has("tunnelEnabled")) {
                        val action = if (config.optBoolean("tunnelEnabled")) "start" else "stop"
                        val tunnelResult = route("POST", "/api/v1/tunnel/actions", "Bearer $token",
                            JSONObject().put("action", action))
                        if (tunnelResult.first !in 200..299) return JSONObject()
                            .put("status", tunnelResult.first).put("body", tunnelResult.second)
                        results.put(JSONObject().put("tunnel", tunnelResult.second))
                        applied = true
                    }
                    if (!applied) return JSONObject().put("status", 400)
                        .put("error", "config_has_no_supported_device_settings")
                    200 to JSONObject().put("applied", results)
                }
                "restart_runtime" -> {
                    val runtime = payload.optString("runtime")
                    when (runtime) {
                        "python" -> {
                            PythonHostRuntime.stop(context)
                            context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                .putBoolean("python_enabled", true).apply()
                            context.startForegroundService(Intent(context, HostingService::class.java))
                            202 to JSONObject().put("state", "STARTING")
                        }
                        "node" -> {
                            NodeHostRuntime.stop(context)
                            context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                .putBoolean("node_enabled", true).apply()
                            context.startForegroundService(Intent(context, HostingService::class.java))
                            202 to JSONObject().put("state", "STARTING")
                        }
                        "static" -> {
                            NativeEngine.stop()
                            context.startForegroundService(Intent(context, HostingService::class.java))
                            202 to JSONObject().put("state", "STARTING")
                        }
                        else -> 400 to JSONObject().put("error", "unsupported_runtime")
                    }
                }
                else -> 400 to JSONObject().put("error", "unsupported_remote_command")
            }
            return JSONObject().put("status", result.first).put("body", result.second)
        } finally {
            synchronized(sessions) { sessions.remove(tokenHash(token)) }
        }
    }

    private fun operatorAllowed(method: String, path: String): Boolean {
        if (method == "GET") return true
        if (method == "DELETE" && path == "/api/v1/session") return true
        if (method == "POST" && path == "/api/v1/capacity") return true
        if (method == "POST" && path == "/api/v1/capacity/benchmark") return true
        if (method == "POST" && path.matches(Regex("/api/v1/sites/[A-Za-z0-9_-]{1,64}/actions"))) return true
        return false
    }
    private fun restartEngineRoutes(context: Context) {
        val prefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        val engineRunning = NativeEngine.status() == "RUNNING"
        if (engineRunning) {
            runCatching { MetricsStore.recordSample(context, NativeEngine.requestCount(), NativeEngine.bytesServed()) }
            NativeEngine.stop()
        }
        if (prefs.getBoolean("python_enabled", false)) {
            runCatching { PythonHostRuntime.stop(context) }
            val pythonHosts = SiteRegistry.pythonHostnameMap(context)
            val pythonResult = runCatching {
                if (pythonHosts.length() > 0) PythonHostRuntime.startSites(context, pythonHosts.toString(),
                    prefs.getInt("python_port", 8081), prefs.getBoolean("allow_lan", false))
                else PythonHostRuntime.start(context,
                    prefs.getString("python_project_root", File(context.filesDir, "python-project").absolutePath)
                        ?: File(context.filesDir, "python-project").absolutePath,
                    prefs.getInt("python_port", 8081), prefs.getBoolean("allow_lan", false))
            }.getOrElse { "ERROR: " + (it.message ?: "Python route reload failed") }
            if (pythonResult != "RUNNING" && pythonResult != "ALREADY_RUNNING")
                OperationalLog.record(context, "ERROR", "PYTHON_ROUTE_RELOAD_FAILED", pythonResult)
        }
        if (engineRunning) {
            val routes = SiteRegistry.hostRoutingJson(context, prefs.getInt("python_port", 8081), prefs.getInt("node_port", 3000))
            val root = File(context.filesDir, "site")
            val result = NativeEngine.startMultiSite(root.absolutePath, routes.toString(),
                prefs.getInt("port", 8080), prefs.getBoolean("allow_lan", false))
            if (result != "OK") OperationalLog.record(context, "ERROR", "ROUTE_RELOAD_FAILED", result)
        }
    }

    private fun randomToken(size: Int): String = ByteArray(size).also(random::nextBytes).let {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }
    private fun tokenHash(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun allowedOrigin(origin: String?): Boolean = origin != null && (
        origin.matches(Regex("http://localhost:[0-9]{1,5}")) ||
        origin.matches(Regex("http://127\\.0\\.0\\.1:[0-9]{1,5}")) ||
        origin == "https://assayflux.xyz" ||
        origin == "https://www.assayflux.xyz" ||
        origin == "https://pockethost.assayflux.xyz"
    )

    private fun respond(output: BufferedOutputStream, status: Int, body: JSONObject, origin: String? = null) {
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        val reason = when (status) { 200 -> "OK"; 201 -> "Created"; 204 -> "No Content"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"; 404 -> "Not Found"; 409 -> "Conflict"; 413 -> "Payload Too Large"; 429 -> "Too Many Requests"; 501 -> "Not Implemented"; 503 -> "Service Unavailable"; else -> "Internal Server Error" }
        val cors = if (allowedOrigin(origin)) "Access-Control-Allow-Origin: $origin\r\nVary: Origin\r\nAccess-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\nAccess-Control-Allow-Headers: Authorization, Content-Type\r\nAccess-Control-Max-Age: 300\r\nAccess-Control-Allow-Private-Network: true\r\n" else ""
        val header = "HTTP/1.1 $status $reason\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${if (status == 204) 0 else bytes.size}\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n$cors" + "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.US_ASCII))
        if (status != 204) output.write(bytes)
        output.flush()
    }
}
