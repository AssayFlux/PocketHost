package com.assayflux.pockethost.management

import android.content.Context
import com.assayflux.pockethost.diagnostics.MetricsStore
import com.assayflux.pockethost.engine.NativeEngine
import com.assayflux.pockethost.runtime.PythonHostRuntime
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Loopback-only management API. Pairing is explicitly approved on the Android device;
 * bearer credentials are memory-only, expire, and can be revoked independently.
 */
object ManagementApiServer {
    private const val PORT = 17841
    private const val SESSION_SECONDS = 12 * 60 * 60L
    private const val MAX_HEADER = 16 * 1024
    private val random = SecureRandom()
    private val workers = Executors.newFixedThreadPool(4)
    @Volatile private var listener: ServerSocket? = null
    @Volatile private var acceptThread: Thread? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var pending: Pairing? = null
    private val sessions = HashMap<String, Long>()

    private data class Pairing(val id: String, val code: String, val expires: Long, @Volatile var approved: Boolean = false, var attempts: Int = 0)
    @Volatile private var lastChallengeAt = 0L
    @Volatile private var challengeWindowAt = 0L
    @Volatile private var challengeWindowCount = 0

    @Synchronized fun start(context: Context): String {
        if (listener?.isClosed == false) return "RUNNING"
        appContext = context.applicationContext
        val socket = ServerSocket(PORT, 16, java.net.InetAddress.getByName("127.0.0.1"))
        listener = socket
        acceptThread = Thread({
            while (!socket.isClosed) {
                try {
                    val client = socket.accept()
                    workers.execute { handle(client) }
                } catch (_: Exception) {
                    if (!socket.isClosed) Thread.sleep(100)
                }
            }
        }, "PocketHost-Management-API").apply { isDaemon = true; start() }
        return "RUNNING"
    }

    @Synchronized fun stop() {
        runCatching { listener?.close() }
        listener = null
        acceptThread = null
        synchronized(sessions) { sessions.clear() }
        pending = null
    }

    @Synchronized fun pendingApprovalCode(): String? {
        val p = pending ?: return null
        return if (p.expires > Instant.now().epochSecond && !p.approved) p.code else null
    }

    @Synchronized fun approvePairing(code: String): Boolean {
        val p = pending ?: return false
        if (p.expires <= Instant.now().epochSecond || p.code != code) return false
        p.approved = true
        return true
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
                if (request.size < 2) return
                val method = request[0]
                val path = request[1].substringBefore('?')
                val headers = lines.drop(1).mapNotNull {
                    val i = it.indexOf(':'); if (i <= 0) null else it.substring(0, i).trim().lowercase() to it.substring(i + 1).trim()
                }.toMap()
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                if (length !in 0..8192) { respond(output, 413, JSONObject().put("error", "body_too_large")); return }
                val bodyBytes = ByteArray(length)
                var offset = 0
                while (offset < length) {
                    val n = input.read(bodyBytes, offset, length - offset)
                    if (n < 0) { respond(output, 400, JSONObject().put("error", "incomplete_body")); return }
                    offset += n
                }
                val body = runCatching { JSONObject(String(bodyBytes, Charsets.UTF_8)) }.getOrDefault(JSONObject())
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

    @Synchronized private fun route(method: String, path: String, authorization: String?, body: JSONObject): Pair<Int, JSONObject> {
        if (method == "GET" && path == "/api/v1/health")
            return 200 to JSONObject().put("status", "ok").put("apiVersion", "v1")
        if (method == "POST" && path == "/api/v1/pair/challenge") {
            val now = Instant.now().epochSecond
            if (now - challengeWindowAt >= 60) { challengeWindowAt = now; challengeWindowCount = 0 }
            challengeWindowCount++
            if (challengeWindowCount > 10 || now - lastChallengeAt < 3)
                return 429 to JSONObject().put("error", "pairing_rate_limited")
            lastChallengeAt = now
            val id = randomToken(12)
            val code = (100000 + random.nextInt(900000)).toString()
            pending = Pairing(id, code, Instant.now().epochSecond + 120)
            return 201 to JSONObject().put("challengeId", id).put("expiresAt", pending!!.expires)
        }
        if (method == "POST" && path == "/api/v1/pair/complete") {
            val p = pending
            if (p == null || p.expires <= Instant.now().epochSecond || p.id != body.optString("challengeId") ||
                !p.approved || p.attempts >= 5) return 401 to JSONObject().put("error", "pairing_not_approved")
            if (p.code != body.optString("code")) {
                p.attempts++
                return 401 to JSONObject().put("error", "invalid_pairing_code")
            }
            val token = randomToken(32)
            synchronized(sessions) { sessions[tokenHash(token)] = Instant.now().epochSecond + SESSION_SECONDS }
            pending = null
            return 200 to JSONObject().put("accessToken", token).put("expiresAt", Instant.now().epochSecond + SESSION_SECONDS)
        }
        val token = authorization?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
        val valid = token != null && synchronized(sessions) {
            val expiry = sessions[tokenHash(token)]
            if (expiry != null && expiry > Instant.now().epochSecond) true
            else { sessions.remove(tokenHash(token)); false }
        }
        if (!valid) return 401 to JSONObject().put("error", "unauthorized")
        if (method == "DELETE" && path == "/api/v1/session") {
            synchronized(sessions) { sessions.remove(tokenHash(token!!)) }
            return 204 to JSONObject()
        }
        val context = appContext ?: return 503 to JSONObject().put("error", "service_unavailable")
        if (method == "GET" && path == "/api/v1/status") {
            val py = runCatching { PythonHostRuntime.status(context) }.getOrDefault("ERROR")
            return 200 to JSONObject().put("state", if (NativeEngine.status() == "RUNNING") "local" else "stopped")
                .put("apiVersion", "v1")
                .put("runtimes", org.json.JSONArray()
                    .put(JSONObject().put("name", "rust-static").put("state", NativeEngine.status()))
                    .put(JSONObject().put("name", "python").put("state", py))
                    .put(JSONObject().put("name", "node").put("state", "UNAVAILABLE")))
        }
        if (method == "GET" && path == "/api/v1/metrics") {
            val m = MetricsStore.snapshot(context)
            return 200 to JSONObject().put("sampledAt", Instant.now().toString())
                .put("sources", org.json.JSONArray().put(JSONObject().put("source", "rust-static")
                    .put("requests", m.totalRequests).put("bytesServed", m.totalBytes)
                    .put("scopeNote", "Persisted Rust static-server counters; Python and Node metrics are not yet available.")))
        }
        return 404 to JSONObject().put("error", "not_found")
    }

    private fun randomToken(size: Int): String = ByteArray(size).also(random::nextBytes).let {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }
    private fun tokenHash(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun allowedOrigin(origin: String?): Boolean = origin != null && (
        origin.matches(Regex("http://localhost:[0-9]{1,5}")) ||
        origin.matches(Regex("http://127\\.0\\.0\\.1:[0-9]{1,5}")) ||
        origin.matches(Regex("https://[a-zA-Z0-9.-]+\\.assayflux\\.xyz"))
    )

    private fun respond(output: BufferedOutputStream, status: Int, body: JSONObject, origin: String? = null) {
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        val reason = when (status) { 200 -> "OK"; 201 -> "Created"; 204 -> "No Content"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"; 404 -> "Not Found"; 413 -> "Payload Too Large"; else -> "Service Unavailable" }
        val cors = if (allowedOrigin(origin)) "Access-Control-Allow-Origin: $origin\r\nVary: Origin\r\nAccess-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\nAccess-Control-Allow-Headers: Authorization, Content-Type\r\nAccess-Control-Max-Age: 300\r\n" else ""
        val header = "HTTP/1.1 $status $reason\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${if (status == 204) 0 else bytes.size}\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n$cors" + "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.US_ASCII))
        if (status != 204) output.write(bytes)
        output.flush()
    }
}
