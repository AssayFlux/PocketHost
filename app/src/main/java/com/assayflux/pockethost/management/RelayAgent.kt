package com.assayflux.pockethost.management

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import com.assayflux.pockethost.sites.SiteRegistry
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
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
    @Volatile private var running = false
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
        if (running || !RemoteControlAgent.isConfigured(context)) return
        val endpoint = RemoteControlAgent.endpoint(context).trimEnd('/')
        val token = RemoteControlAgent.relayDeviceToken(context) ?: return
        generation += 1
        val runGeneration = generation
        running = true
        val app = context.applicationContext
        worker = Thread({
            var retry = 1_000L
            while (running && generation == runGeneration && !Thread.currentThread().isInterrupted) {
                try {
                    streamOnce(app, endpoint, token)
                    retry = 1_000L
                } catch (error: Exception) {
                    if (running) {
                        com.assayflux.pockethost.diagnostics.OperationalLog.record(
                            app, "WARN", "RELAY_DISCONNECTED",
                            error.message?.take(160) ?: "Relay connection disconnected."
                        )
                    }
                    try { Thread.sleep(retry) } catch (_: InterruptedException) { break }
                    retry = (retry * 2).coerceAtMost(60_000L)
                }
            }
            if (generation == runGeneration) {
                running = false
                streamConnection = null
                if (worker === Thread.currentThread()) worker = null
            }
        }, "PocketHost-Relay-Stream").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        generation += 1
        running = false
        streamConnection?.disconnect()
        streamConnection = null
        worker?.interrupt()
        worker = null
    }

    private fun streamOnce(context: Context, endpoint: String, token: String) {
        val connection = URL("$endpoint/v1/device/relay/stream").openConnection() as HttpURLConnection
        streamConnection = connection
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
            val contentType = connection.contentType.orEmpty()
            require(contentType.startsWith("text/event-stream", ignoreCase = true)) {
                "Relay endpoint did not return an event stream."
            }
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8), 16 * 1024).use { reader ->
                var data = StringBuilder()
                while (running) {
                    val line = reader.readLine() ?: throw IllegalStateException("Relay event stream ended.")
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
        try {
            require(id.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid Relay request ID." }
            val method = message.getString("method")
            require(method in setOf("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS")) { "Unsupported Relay method." }
            val hostname = message.getString("hostname").trimEnd('.').lowercase()
            require(hostname.length in 1..253 && hostname.none { it.isWhitespace() }) { "Invalid Relay hostname." }
            val siteId = message.getString("site_id")
            val registeredSite = SiteRegistry.list(context).firstOrNull { site ->
                site.enabled && site.id == siteId && hostname in site.hostnames
            }
            require(registeredSite != null) { "Relay hostname is not mapped to the expected local site ID." }
            val path = message.getString("path")
            require(path.startsWith("/") && path.length <= 8_192 && !path.startsWith("//")) { "Invalid Relay path." }
            val body = Base64.decode(message.optString("body_base64", ""), Base64.DEFAULT)
            require(body.size <= MAX_BODY_BYTES) { "Relay request body exceeds 8 MiB." }

            val port = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getInt("port", 8080)
            val local = URL("http://127.0.0.1:$port$path")
            val connection = local.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 5_000
                connection.readTimeout = 35_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Host", hostname)
                connection.setRequestProperty("X-Forwarded-Proto", "https")
                connection.setRequestProperty("Connection", "close")
                val incomingHeaders = message.optJSONObject("headers") ?: JSONObject()
                val allowed = setOf(
                    "accept", "accept-encoding", "accept-language", "authorization", "cache-control", "cf-connecting-ip",
                    "content-type", "cookie", "if-match", "if-modified-since", "if-none-match",
                    "origin", "range", "referer", "user-agent"
                )
                val keys = incomingHeaders.keys()
                while (keys.hasNext()) {
                    val name = keys.next()
                    if (name.lowercase() in allowed) {
                        val value = incomingHeaders.optString(name)
                        if (value.length <= 8_192) connection.setRequestProperty(name, value)
                    }
                }
                if (body.isNotEmpty()) {
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.outputStream.use { it.write(body) }
                }
                val status = connection.responseCode
                val stream = if (status >= 400) connection.errorStream else connection.inputStream
                val responseBytes = stream?.use { input ->
                    ByteArrayOutputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_BODY_BYTES) { "Relay response body exceeds 8 MiB." }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                } ?: ByteArray(0)
                val responseHeaders = JSONObject()
                connection.headerFields.forEach { (name, values) ->
                    if (name != null && values != null && name.lowercase() in setOf(
                            "cache-control", "content-disposition", "content-encoding", "content-language",
                            "content-range", "content-type", "etag", "expires", "last-modified", "location", "set-cookie", "vary"
                        )) {
                        responseHeaders.put(name.lowercase(), values.firstOrNull().orEmpty())
                    }
                }
                postResponse(endpoint, token, id, JSONObject()
                    .put("id", id)
                    .put("status", status)
                    .put("headers", responseHeaders)
                    .put("body_base64", Base64.encodeToString(responseBytes, Base64.NO_WRAP)))
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            runCatching {
                postResponse(endpoint, token, id, JSONObject()
                    .put("id", id)
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
