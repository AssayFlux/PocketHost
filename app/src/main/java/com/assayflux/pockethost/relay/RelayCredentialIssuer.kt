package com.assayflux.pockethost.relay

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Owner-operated issuance and revocation for the standalone Relay host credentials.
 * Only SHA-256 verifiers are persisted in the encrypted node configuration; the
 * raw high-entropy credential is returned once for transfer to the host device.
 */
object RelayCredentialIssuer {
    private val random = SecureRandom()

    data class IssuedCredential(val deviceId: String, val token: String)

    @Synchronized
    fun issueOrRotate(context: Context, rawDeviceId: String): IssuedCredential {
        val deviceId = UUID.fromString(rawDeviceId.trim()).toString()
        require(deviceId !in RelayNodeConfigStore.revokedDeviceIds(context)) {
            "This host identity was revoked. Pair it again with a new host identity."
        }
        val configText = RelayNodeConfigStore.read(context)
            ?: throw IllegalStateException("Import or create a Relay node configuration first.")
        val config = JSONObject(configText)
        val hosts = config.optJSONArray("hosts")
            ?: throw IllegalArgumentException("Relay configuration has no hosts array.")
        var found = false
        val tokenBytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.encodeToString(
            tokenBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        for (index in 0 until hosts.length()) {
            val host = hosts.optJSONObject(index) ?: continue
            if (host.optString("device_id").equals(deviceId, ignoreCase = true)) {
                host.put("credential_sha256", digest)
                found = true
                break
            }
        }
        if (!found) {
            require(deviceId in RelayNodeConfigStore.activeHostIds(context)) {
                "Host identity is not paired with this Relay node."
            }
            hosts.put(JSONObject().put("device_id", deviceId).put("credential_sha256", digest))
        }
        config.put("hosts", hosts)
        check(RelayNodeConfigStore.save(context, config.toString())) {
            "Could not persist the rotated Relay credential."
        }
        return IssuedCredential(deviceId, token)
    }

    @Synchronized
    fun revoke(context: Context, rawDeviceId: String): Boolean {
        val deviceId = UUID.fromString(rawDeviceId.trim()).toString()
        val configText = RelayNodeConfigStore.read(context) ?: return false
        val config = JSONObject(configText)
        val hosts = config.optJSONArray("hosts") ?: return false
        val keptHosts = JSONArray()
        var found = false
        for (index in 0 until hosts.length()) {
            val host = hosts.optJSONObject(index) ?: continue
            if (host.optString("device_id").equals(deviceId, ignoreCase = true)) {
                found = true
            } else {
                keptHosts.put(host)
            }
        }
        if (!found) return false

        val routes = config.optJSONArray("routes") ?: JSONArray()
        val keptRoutes = JSONArray()
        for (index in 0 until routes.length()) {
            val route = routes.optJSONObject(index) ?: continue
            if (!route.optString("device_id").equals(deviceId, ignoreCase = true)) {
                keptRoutes.put(route)
            }
        }
        config.put("hosts", keptHosts)
        config.put("routes", keptRoutes)
        RelayNodeConfigStore.save(context, config.toString(), revokedDeviceId = deviceId)
        return true
    }

    fun registeredHostIds(context: Context): List<String> {
        val config = RelayNodeConfigStore.read(context)?.let(::JSONObject) ?: return emptyList()
        val hosts = config.optJSONArray("hosts") ?: JSONArray()
        val configured = (0 until hosts.length()).mapNotNull { index ->
            hosts.optJSONObject(index)?.optString("device_id")?.takeIf(String::isNotBlank)
        }
        return (configured + RelayNodeConfigStore.activeHostIds(context)).distinct()
    }
}
