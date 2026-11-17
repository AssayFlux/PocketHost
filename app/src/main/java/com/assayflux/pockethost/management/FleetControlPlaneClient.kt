package com.assayflux.pockethost.management

import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Small, HTTPS-only mobile owner client for worker registration and signed command delivery. */
object FleetControlPlaneClient {
    data class RegisteredWorker(val controlPlaneId: String, val oneTimeDeviceToken: String, val grantQueued: Boolean)
    private const val MAX_RESPONSE = 256 * 1024
    private const val MAX_REQUEST = 256 * 1024

    fun registerWorker(endpoint: String, ownerCredential: String, name: String, grant: JSONObject, ownerPublicKeySpki: String): RegisteredWorker {
        require(name.isNotBlank() && name.length <= 120) { "worker_name_invalid" }
        val base = validateEndpoint(endpoint)
        val session = exchangeSession(base, ownerCredential)
        try {
            val registered = request(base, session, "/v1/admin/devices", "POST", JSONObject().put("name", name))
            val id = registered.getString("id")
            val token = registered.getString("device_token")
            require(id.matches(Regex("[0-9a-fA-F-]{36}")) && token.length >= 32) { "invalid_device_registration_response" }
            val grantQueued = runCatching {
                queueCommand(base, session, id, "install_grant", JSONObject().put("grant", grant).put("owner_public_key_spki", ownerPublicKeySpki))
            }.isSuccess
            return RegisteredWorker(id, token, grantQueued)
        } finally {
            runCatching { request(base, session, "/v1/admin/session", "DELETE", null) }
        }
    }

    fun queueRoleGrant(endpoint: String, ownerCredential: String, controlPlaneDeviceId: String, grant: JSONObject, ownerPublicKeySpki: String) {
        val base = validateEndpoint(endpoint)
        require(controlPlaneDeviceId.matches(Regex("[0-9a-fA-F-]{36}"))) { "invalid_control_plane_device_id" }
        val session = exchangeSession(base, ownerCredential)
        try {
            queueCommand(base, session, controlPlaneDeviceId, "install_grant",
                JSONObject().put("grant", grant).put("owner_public_key_spki", ownerPublicKeySpki))
        } finally { runCatching { request(base, session, "/v1/admin/session", "DELETE", null) } }
    }

    /** Reads the owner-visible heartbeat inventory for linked workers. */
    fun deviceInventory(endpoint: String, ownerCredential: String): JSONArray {
        val base = validateEndpoint(endpoint)
        val session = exchangeSession(base, ownerCredential)
        try {
            return requestArray(base, session, "/v1/admin/devices")
        } finally {
            runCatching { request(base, session, "/v1/admin/session", "DELETE", null) }
        }
    }

    /** Reads the latest 50 command states and worker acknowledgements for one device. */
    fun commandHistory(endpoint: String, ownerCredential: String, controlPlaneDeviceId: String): JSONArray {
        val base = validateEndpoint(endpoint)
        require(controlPlaneDeviceId.matches(Regex("[0-9a-fA-F-]{36}"))) { "invalid_control_plane_device_id" }
        val session = exchangeSession(base, ownerCredential)
        try {
            return requestArray(base, session, "/v1/admin/devices/$controlPlaneDeviceId/commands")
        } finally {
            runCatching { request(base, session, "/v1/admin/session", "DELETE", null) }
        }
    }

    fun revokeWorker(endpoint: String, ownerCredential: String, controlPlaneDeviceId: String) {
        val base = validateEndpoint(endpoint)
        require(controlPlaneDeviceId.matches(Regex("[0-9a-fA-F-]{36}"))) { "invalid_control_plane_device_id" }
        val session = exchangeSession(base, ownerCredential)
        try { request(base, session, "/v1/admin/devices/$controlPlaneDeviceId", "DELETE", null) }
        finally { runCatching { request(base, session, "/v1/admin/session", "DELETE", null) } }
    }

    fun queueSignedJob(endpoint: String, ownerCredential: String, controlPlaneDeviceId: String, envelope: JSONObject) {
        val base = validateEndpoint(endpoint)
        require(controlPlaneDeviceId.matches(Regex("[0-9a-fA-F-]{36}"))) { "invalid_control_plane_device_id" }
        val session = exchangeSession(base, ownerCredential)
        try { queueCommand(base, session, controlPlaneDeviceId, "agent_job", envelope) }
        finally { runCatching { request(base, session, "/v1/admin/session", "DELETE", null) } }
    }

    private fun validateEndpoint(endpoint: String): String {
        val uri = URI(endpoint.trim().trimEnd('/'))
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null && (uri.path.isNullOrEmpty() || uri.path == "/")) {
            "Use a valid HTTPS control-plane origin."
        }
        return "https://${uri.rawAuthority}"
    }

    private fun exchangeSession(base: String, credential: String): String {
        require(credential.length >= 32 && credential.length <= 512) { "owner_credential_invalid" }
        val response = request(base, credential, "/v1/admin/session", "POST", null)
        return response.getString("accessToken").also { require(it.length >= 32) { "admin_session_invalid" } }
    }

    private fun queueCommand(base: String, session: String, deviceId: String, kind: String, payload: JSONObject) {
        request(base, session, "/v1/admin/devices/$deviceId/commands", "POST", JSONObject().put("kind", kind).put("payload", payload))
    }

    private fun requestArray(base: String, bearer: String, path: String): JSONArray {
        require(path.startsWith("/v1/") && !path.startsWith("//")) { "invalid_control_plane_path" }
        val connection = URL(base + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $bearer")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input -> ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_RESPONSE) { "control_plane_response_too_large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } } ?: ByteArray(0)
            if (status !in 200..299) throw IllegalStateException("Control plane returned HTTP $status.")
            return JSONArray(String(bytes, Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }

    private fun request(base: String, bearer: String, path: String, method: String, body: JSONObject?): JSONObject {
        require(path.startsWith("/v1/") && !path.startsWith("//")) { "invalid_control_plane_path" }
        val connection = URL(base + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $bearer")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            if (body != null) {
                val requestBytes = body.toString().toByteArray(Charsets.UTF_8)
                require(requestBytes.size <= MAX_REQUEST) { "control_plane_request_too_large" }
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setFixedLengthStreamingMode(requestBytes.size)
                connection.outputStream.use { it.write(requestBytes) }
                requestBytes.fill(0)
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input -> ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_RESPONSE) { "control_plane_response_too_large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } } ?: ByteArray(0)
            if (status !in 200..299) throw IllegalStateException("Control plane returned HTTP $status: ${String(bytes).take(200)}")
            return if (bytes.isEmpty()) JSONObject() else JSONObject(String(bytes, Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}
