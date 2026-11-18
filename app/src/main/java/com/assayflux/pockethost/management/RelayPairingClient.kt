package com.assayflux.pockethost.management

import android.content.Context
import android.util.Base64
import com.assayflux.pockethost.relay.RelayHostIdentityStore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/** Completes an owner-issued, two-minute Relay pairing invite over verified HTTPS. */
object RelayPairingClient {
    private val random = SecureRandom()

    fun pair(context: Context, endpoint: String, inviteCode: String): String {
        val normalizedEndpoint = endpoint.trim().trimEnd('/')
        val uri = java.net.URI(normalizedEndpoint)
        require(uri.scheme == "https" ||
            (uri.scheme == "http" && uri.host == "127.0.0.1")) {
            "Relay pairing requires HTTPS except for localhost development."
        }
        require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null &&
            (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")) {
            "Enter a Relay base URL without credentials, query, fragment or path."
        }

        val deviceId = RelayCredentialStore.hostDeviceId(context)
        val proof = RelayHostIdentityStore.createProof(inviteCode.trim(), deviceId)
        val tokenBytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.encodeToString(
            tokenBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        // Save before sending: if the server commits but the HTTP response is lost,
        // the host still retains the only bearer that was registered on the node.
        RelayCredentialStore.configure(context, normalizedEndpoint, token)
        val body = JSONObject()
            .put("invite_code", inviteCode.trim())
            .put("device_id", deviceId)
            .put("credential_token", token)
            .put("public_key_base64", proof.publicKeyBase64)
            .put("signature_base64", proof.signatureBase64)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val connection = URL("$normalizedEndpoint/v1/pair/complete")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 12_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val responseStream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status != HttpURLConnection.HTTP_OK) {
                val reason = runCatching { JSONObject(responseText).optString("error") }.getOrDefault("")
                throw IllegalStateException(
                    if (reason.isNotBlank()) "Relay pairing was rejected: $reason" else "Relay pairing failed (HTTP $status)."
                )
            }
            val response = JSONObject(responseText)
            require(response.optString("status") == "paired" && response.optString("device_id") == deviceId) {
                "Relay returned an invalid pairing confirmation."
            }
            return deviceId
        } finally {
            connection.disconnect()
        }
    }
}
