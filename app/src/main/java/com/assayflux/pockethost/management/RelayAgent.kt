package com.assayflux.pockethost.management

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import org.json.JSONArray
import com.assayflux.pockethost.sites.SiteRegistry
import java.io.BufferedReader
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Outbound-only Relay transport. One long-lived authenticated SSE stream carries
 * bounded visitor requests; authenticated POSTs return each response. The device
 * never opens an Internet-facing listener for Relay traffic.
 */
object RelayAgent {
    private const val MAX_BODY_BYTES = 8 * 1024 * 1024
    private const val MAX_EVENT_CHARS = 12 * 1024 * 1024
    private const val MAX_SSE_LINE_CHARS = MAX_EVENT_CHARS + 6
    @Volatile private var running = false
    @Volatile private var currentState = "STOPPED"
    @Volatile private var generation = 0L
    @Volatile private var worker: Thread? = null
    @Volatile private var streamConnection: HttpURLConnection? = null
    private val requests = ThreadPoolExecutor(
        4, 4, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(64),
        { runnable -> Thread(runnable, "PocketHost-Relay-Request").apply { isDaemon = true } },
        ThreadPoolExecutor.CallerRunsPolicy()
    )

    @Synchronized
    fun start(context: Context) {
        if (running) return
        if (!RelayCredentialStore.isConfigured(context)) {
            currentState = "STOPPED"
            return
        }
        val endpoint = RelayCredentialStore.endpoint(context).trimEnd('/')
        val token = RelayCredentialStore.readToken(context)
        if (token.isNullOrBlank()) {
            currentState = "ERROR"
            return
        }
        generation += 1
        val runGeneration = generation
        running = true
        currentState = "CONNECTING"
        val app = context.applicationContext
        worker = Thread({
            var retry = 1_000L
            while (running && generation == runGeneration && !Thread.currentThread().isInterrupted) {
                try {
                    streamOnce(app, endpoint, token, runGeneration)
                    retry = 1_000L
                } catch (error: Exception) {
                    if (running) {
                        currentState = "RETRYING"
                        com.assayflux.pockethost.diagnostics.OperationalLog.record(
                            app, "WARN", "RELAY_DISCONNECTED",
                            error.message?.take(160) ?: "Relay connection disconnected."
                        )
                    }
                    val jitter = (retry * 0.2).toLong()
                    val delay = retry - jitter + java.util.concurrent.ThreadLocalRandom.current().nextLong((jitter * 2 + 1).coerceAtLeast(1))
                    try { Thread.sleep(delay) } catch (_: InterruptedException) { break }
                    retry = (retry * 2).coerceAtMost(60_000L)
                }
            }
            if (generation == runGeneration) {
                running = false
                currentState = "STOPPED"
                streamConnection = null
                if (worker === Thread.currentThread()) worker = null
            }
        }, "PocketHost-Relay-Stream").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        generation += 1
        running = false
        currentState = "STOPPED"
        streamConnection?.disconnect()
        streamConnection = null
        worker?.interrupt()
        worker = null
    }

    fun status(): String = currentState

    private fun streamOnce(context: Context, endpoint: String, token: String, runGeneration: Long) {
        val connection = URL("$endpoint/v1/device/relay/stream").openConnection() as HttpURLConnection
        streamConnection = connection
        if (!running || generation != runGeneration) {
            connection.disconnect()
            return
        }
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 0
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.setRequestProperty("Cache-Control", "no-cache")
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("Relay stream returned HTTP $status")
            currentState = "CONNECTED"
            val contentType = connection.contentType.orEmpty()
            require(contentType.startsWith("text/event-stream", ignoreCase = true)) {
                "Relay endpoint did not return an event stream."
            }
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8), 16 * 1024).use { reader ->
                val sseReader = java.io.PushbackReader(reader, 1)
                var data = StringBuilder()
                while (running) {
                    val line = readBoundedSseLine(sseReader, MAX_SSE_LINE_CHARS)
                        ?: throw IllegalStateException("Relay event stream ended.")
                    if (line.isEmpty()) {
                        if (data.isNotEmpty()) {
                            val payload = data.toString()
                            data = StringBuilder()
                            if (payload.length <= MAX_EVENT_CHARS) {
                                try {
                                    val message = JSONObject(payload)
                                    requests.execute { forwardRequest(context, endpoint, token, message) }
                                } catch (error: Exception) {
                                    com.assayflux.pockethost.diagnostics.OperationalLog.record(
                                        context, "WARN", "RELAY_EVENT_REJECTED",
                                        error.message?.take(160) ?: "Invalid Relay event."
                                    )
                                }
                            }
                        }
                    } else if (line.startsWith("data:")) {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.substring(5).trimStart())
                        if (data.length > MAX_EVENT_CHARS) throw IllegalStateException("Relay event exceeds the 12 MiB limit.")
                    } else if (line.startsWith("retry:")) {
                        // Server retry hints are intentionally bounded by the local backoff.
                    }
                }
            }
        } finally {
            connection.disconnect()
            if (streamConnection === connection) streamConnection = null
        }
    }

    private fun forwardRequest(context: Context, endpoint: String, token: String, message: JSONObject) {
        val id = message.optString("id")
        val siteId = message.optString("site_id")
        try {
            require(id.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid Relay request ID." }
            require(siteId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid Relay site ID." }
            val method = message.getString("method")
            require(method in setOf("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS")) { "Unsupported Relay method." }
            val hostname = message.getString("hostname")
            require(hostname.length in 1..253 && hostname.none { it.isWhitespace() }) { "Invalid Relay hostname." }
            val localHostname = SiteRegistry.localHostnameForRelay(context, siteId)
                ?: throw SecurityException("Relay route does not identify an enabled local site with a hostname.")
            val path = message.getString("path")
            require(path.startsWith("/") && path.length <= 8_192 && !path.startsWith("//")) { "Invalid Relay path." }
            val body = Base64.decode(message.optString("body_base64", ""), Base64.DEFAULT)
            require(body.size <= MAX_BODY_BYTES) { "Relay request body exceeds 8 MiB." }
            val port = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getInt("port", 8080)
            val localResponse = forwardLocalRequest(
                port, localHostname, hostname, path, method, message.optJSONObject("headers") ?: JSONObject(), body
            )
            postResponse(endpoint, token, id, JSONObject()
                .put("id", id)
                .put("site_id", siteId)
                .put("status", localResponse.status)
                .put("headers", localResponse.headers)
                .put("set_cookies", JSONArray(localResponse.setCookies))
                .put("body_base64", Base64.encodeToString(localResponse.body, Base64.NO_WRAP)))
        } catch (error: Exception) {
            runCatching {
                postResponse(endpoint, token, id, JSONObject()
                    .put("id", id)
                    .put("site_id", siteId)
                    .put("status", 502)
                    .put("headers", JSONObject().put("content-type", "text/plain; charset=utf-8").put("cache-control", "no-store"))
                    .put("body_base64", Base64.encodeToString(
                        (error.message ?: "Local site forwarding failed.").take(512).toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP
                    )))
            }.onFailure {
                com.assayflux.pockethost.diagnostics.OperationalLog.record(
                    context, "WARN", "RELAY_RESPONSE_FAILED", "Could not return a visitor response to the Relay gateway."
                )
            }
        }
    }


    /** Reads one SSE line without allowing BufferedReader.readLine() to allocate an unbounded string. */
    internal fun readBoundedSseLine(reader: java.io.PushbackReader, maxChars: Int): String? {
        require(maxChars > 0) { "SSE line limit must be positive." }
        val line = StringBuilder(minOf(maxChars, 256))
        while (true) {
            val next = reader.read()
            if (next < 0) return if (line.isEmpty()) null else line.toString()
            if (next == '\n'.code) return line.toString()
            if (next == '\r'.code) {
                val following = reader.read()
                if (following >= 0 && following != '\n'.code) reader.unread(following)
                return line.toString()
            }
            if (line.length >= maxChars) {
                throw IllegalStateException("Relay SSE line exceeds its ${maxChars}-character limit.")
            }
            line.append(next.toChar())
        }
    }
    internal fun validateChunkTrailer(line: String) {
        if (line.isEmpty()) return
        val separator = line.indexOf(':')
        require(separator > 0) { "Malformed local HTTP chunk trailer." }
        val name = line.substring(0, separator).trim()
        val value = line.substring(separator + 1).trim()
        require(name.isNotEmpty() && value.length <= 8_192 &&
            name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' }) {
            "Invalid local HTTP chunk trailer."
        }
        require(name.lowercase() !in setOf("content-length", "transfer-encoding", "host")) {
            "Forbidden local HTTP chunk trailer."
        }
    }

    internal fun addResponseFramingHeader(headers: MutableMap<String, String>, name: String, value: String) {
        val normalized = name.lowercase()
        require(normalized == "transfer-encoding" || normalized == "content-length") {
            "Unsupported local HTTP framing header."
        }
        require(!headers.containsKey(normalized)) {
            "Duplicate local HTTP $normalized header."
        }
        headers[normalized] = value
    }

    internal data class ResponseFraming(val chunked: Boolean, val contentLength: Long?)

    internal fun validateResponseFraming(transferEncodingInput: String?, contentLengthInput: String?): ResponseFraming {
        val transferEncoding = transferEncodingInput?.trim()?.takeIf { it.isNotEmpty() }
        val contentLengthHeader = contentLengthInput?.trim()
        require(transferEncoding == null || contentLengthHeader == null) {
            "Ambiguous local HTTP response framing (Transfer-Encoding and Content-Length)."
        }
        require(transferEncoding == null || transferEncoding.equals("chunked", ignoreCase = true)) {
            "Unsupported local HTTP transfer encoding."
        }
        val contentLength = contentLengthHeader?.toLongOrNull()
        require(contentLengthHeader == null || contentLength != null) {
            "Invalid local HTTP Content-Length."
        }
        require(contentLength == null || contentLength in 0..MAX_BODY_BYTES.toLong()) {
            "Local response body exceeds 8 MiB."
        }
        return ResponseFraming(transferEncoding != null, contentLength)
    }

    private data class LocalResponse(val status: Int, val headers: JSONObject, val body: ByteArray, val setCookies: List<String>)

    /** Raw loopback HTTP preserves the visitor Host header; HttpURLConnection filters it. */
    private fun forwardLocalRequest(
        port: Int, localHostname: String, originalHostname: String, path: String, method: String,
        incomingHeaders: JSONObject, body: ByteArray
    ): LocalResponse {
        val allowed = setOf("accept", "accept-encoding", "accept-language", "access-control-request-headers",
            "access-control-request-method", "authorization", "cache-control", "content-type", "cookie",
            "cf-connecting-ip", "if-match", "if-modified-since", "if-none-match", "origin", "range",
            "referer", "sec-fetch-dest", "sec-fetch-mode", "sec-fetch-site", "user-agent")
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 5_000)
            socket.soTimeout = 35_000
            val output = BufferedOutputStream(socket.getOutputStream())
            val input = BufferedInputStream(socket.getInputStream())
            fun writeLine(value: String) {
                output.write(value.toByteArray(Charsets.ISO_8859_1))
                output.write(byteArrayOf(13, 10))
            }
            writeLine("$method $path HTTP/1.1")
            writeLine("Host: $localHostname")
            writeLine("X-PocketHost-Original-Host: $originalHostname")
            writeLine("Connection: close")
            writeLine("X-Forwarded-Proto: https")
            val keys = incomingHeaders.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val normalized = name.lowercase()
                if (normalized in allowed) {
                    val value = incomingHeaders.optString(name)
                    require(value.length <= 8_192 && !value.contains('\r') && !value.contains('\n')) {
                        "Invalid Relay request header."
                    }
                    writeLine("$normalized: $value")
                }
            }
            if (body.isNotEmpty()) writeLine("Content-Length: ${body.size}")
            writeLine("")
            if (body.isNotEmpty()) output.write(body)
            output.flush()

            fun readLine(max: Int = 8_192): String {
                val bytes = ByteArrayOutputStream()
                var previous = -1
                while (true) {
                    val next = input.read()
                    if (next < 0) throw IllegalStateException("Local site closed its response unexpectedly.")
                    if (previous == 13 && next == 10) break
                    if (previous >= 0) bytes.write(previous)
                    previous = next
                    if (bytes.size() > max) throw IllegalStateException("Local HTTP line exceeds its limit.")
                }
                return bytes.toString(Charsets.ISO_8859_1.name())
            }

            val statusParts = readLine().split(' ', limit = 3)
            require(statusParts.size >= 2 && statusParts[0].startsWith("HTTP/1.")) { "Invalid local HTTP status line." }
            val status = statusParts[1].toIntOrNull() ?: throw IllegalStateException("Invalid local HTTP status.")
            require(status in 100..599) { "Invalid local HTTP status." }
            val headers = LinkedHashMap<String, String>()
            val setCookies = ArrayList<String>()
            var headerBytes = 0
            while (true) {
                val line = readLine()
                headerBytes += line.length + 2
                require(headerBytes <= 64 * 1024) { "Local HTTP response headers exceed 64 KiB." }
                if (line.isEmpty()) break
                val split = line.indexOf(':')
                require(split > 0) { "Malformed local HTTP response header." }
                val name = line.substring(0, split).trim().lowercase()
                val value = line.substring(split + 1).trim()
                if (name in setOf("access-control-allow-credentials", "access-control-allow-headers",
                        "access-control-allow-methods", "access-control-allow-origin", "access-control-expose-headers",
                        "access-control-max-age", "cache-control", "content-disposition", "content-encoding",
                        "content-language", "content-range", "content-security-policy",
                        "content-security-policy-report-only", "content-type", "cross-origin-embedder-policy",
                        "cross-origin-opener-policy", "cross-origin-resource-policy", "etag", "expires",
                        "last-modified", "location", "permissions-policy", "referrer-policy",
                        "strict-transport-security", "vary", "www-authenticate", "x-frame-options") && value.length <= 8_192) {
                    headers[name] = if (headers.containsKey(name)) "${headers[name]}, $value" else value
                }
                if (name == "set-cookie" && value.length <= 8_192) setCookies.add(value)
                if (name == "transfer-encoding" || name == "content-length") {
                    addResponseFramingHeader(headers, name, value)
                }
            }
            val framing = validateResponseFraming(headers["transfer-encoding"], headers["content-length"])
            val response = ByteArrayOutputStream()
            fun readExact(count: Int) {
                require(count >= 0 && response.size().toLong() + count <= MAX_BODY_BYTES) {
                    "Local response body exceeds 8 MiB."
                }
                var remaining = count
                val buffer = ByteArray(16 * 1024)
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                    if (read < 0) throw IllegalStateException("Truncated local HTTP response body.")
                    response.write(buffer, 0, read)
                    remaining -= read
                }
            }
            if (method != "HEAD" && status !in 100..199 && status != 204 && status != 304) {
                when {
                    framing.chunked -> {
                        while (true) {
                            val size = readLine().substringBefore(';').trim().toLongOrNull(16)
                                ?: throw IllegalStateException("Invalid chunk length.")
                            require(size <= MAX_BODY_BYTES.toLong()) { "Chunked response exceeds 8 MiB." }
                            if (size == 0L) {
                                var trailerBytes = 0
                                var trailerCount = 0
                                while (true) {
                                    val trailer = readLine()
                                    trailerBytes += trailer.length + 2
                                    if (trailerBytes > 64 * 1024 || ++trailerCount > 64) {
                                        throw IllegalStateException("Local HTTP chunk trailers exceed their limit.")
                                    }
                                    if (trailer.isEmpty()) break
                                    validateChunkTrailer(trailer)
                                }
                                break
                            }
                            readExact(size.toInt())
                            require(input.read() == 13 && input.read() == 10) { "Malformed chunk terminator." }
                        }
                    }
                    framing.contentLength != null -> {
                        readExact(framing.contentLength.toInt())
                    }
                    else -> {
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            require(response.size().toLong() + read <= MAX_BODY_BYTES) {
                                "Local response body exceeds 8 MiB."
                            }
                            response.write(buffer, 0, read)
                        }
                    }
                }
            }
            val jsonHeaders = JSONObject()
            headers.filterKeys { it in setOf("access-control-allow-credentials", "access-control-allow-headers",
                "access-control-allow-methods", "access-control-allow-origin", "access-control-expose-headers",
                "access-control-max-age", "cache-control", "content-disposition", "content-encoding",
                "content-language", "content-range", "content-security-policy",
                "content-security-policy-report-only", "content-type", "cross-origin-embedder-policy",
                "cross-origin-opener-policy", "cross-origin-resource-policy", "etag", "expires",
                "last-modified", "location", "permissions-policy", "referrer-policy",
                "strict-transport-security", "vary", "www-authenticate", "x-frame-options") }.forEach { (name, value) -> jsonHeaders.put(name, value) }
            return LocalResponse(status, jsonHeaders, response.toByteArray(), setCookies)
        }
    }

    private fun postResponse(endpoint: String, token: String, id: String, body: JSONObject) {
        require(id.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid Relay response ID." }
        val connection = URL("$endpoint/v1/device/relay/responses/$id").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 7_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            require(bytes.size <= 12 * 1024 * 1024) { "Relay response frame exceeds its limit." }
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("Relay response acknowledgement returned HTTP $status")
        } finally {
            connection.disconnect()
        }
    }
}
