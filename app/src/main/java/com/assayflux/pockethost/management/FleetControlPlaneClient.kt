package com.assayflux.pockethost.management

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Small, HTTPS-only mobile owner client for worker registration and signed command delivery. */
object FleetControlPlaneClient {
    data class RegisteredWorker(val controlPlaneId: String, val oneTimeDeviceToken: String)
    private const val MAX_RESPONSE = 256 * 1024

    fun registerWorker(endpoint: String, ownerCredential: String, name: String, grant: JSONObject, ownerPublicKeySpki: String): RegisteredWorker {
        require(name.isNotBlank() && name.length <= 120) { "worker_name_invalid" }
        val base = validateEndpoint(endpoint)
        val session = exchangeSession(base, ownerCredential)
        try {
            val registered = request(base, session, "/v1/admin/devices", "POST", JSONObject().put("name", name))
            val id = registered.getString("id")
            val token = registered.getString("device_token")
            require(id.matches(Regex("[0-9a-fA-F-]{36}")) && token.length >= 32) { "invalid_device_registration_response" }
            queueCommand(base, session, id, "install_grant", JSONObject().put("grant", grant).put("owner_public_key_spki", ownerPublicKeySpki))
            return RegisteredWorker(id, token)
        } finally {
            runCatching { request(base, session, "/v1/admin/session", "DELETE", null) }
        }
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
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
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
