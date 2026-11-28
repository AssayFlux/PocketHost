package com.assayflux.pockethost.dns

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Dynadot DDNS password is domain-scoped and is not the Dynadot account password. */
internal object DynadotDdnsCredentialStore {
    private const val PREFS = "pockethost_secure"
    private const val KEY = "dynadot_ddns_password"
    private const val ALIAS = "assayflux.pockethost.dynadot-ddns.v1"

    fun hasPassword(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY)

    @Synchronized fun save(context: Context, password: String) {
        val value = password.trim()
        require(value.length in 8..512 && !value.any(Char::isWhitespace)) {
            "Enter the domain-specific Dynadot DDNS password, not your account password."
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val packed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) {
            "Could not save the encrypted Dynadot DDNS password."
        }
    }

    @Synchronized fun read(context: Context): String? {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > 28) { "Stored Dynadot credential is invalid." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        return cipher.doFinal(packed.copyOfRange(12, packed.size)).toString(Charsets.UTF_8)
    }

    @Synchronized fun clear(context: Context) {
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).commit())
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
}

internal object DynadotDdnsApi {
    private const val ENDPOINT = "https://www.dynadot.com/set_ddns"

    fun update(domainRaw: String, hostRaw: String, type: String, ip: String, password: String): String {
        val domain = CloudflareDnsApi.normalizeDomain(domainRaw)
        val host = hostRaw.trim().trimEnd('.').lowercase()
        require(host == "@" || host.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 && label.all { it.isLetterOrDigit() || it == '-' }
        }) { "Enter @ for the root domain or a valid subdomain label." }
        require(type == "A" || type == "AAAA") { "Dynadot DDNS supports A and AAAA updates only." }
        val address = java.net.InetAddress.getByName(ip)
        require(if (type == "A") address is java.net.Inet4Address else address is java.net.Inet6Address) {
            "The detected address does not match the selected record family."
        }
        require(PublicAddressDetector.isGloballyRoutable(address, type == "AAAA")) {
            "Refusing to publish a non-global $type address."
        }
        val query = listOf(
            "containRoot" to if (host == "@") "true" else "false",
            "domain" to domain,
            "ip" to ip,
            "pwd" to password,
            "subDomain" to if (host == "@") "" else host,
            "ttl" to "300",
            "type" to type
        ).joinToString("&") { (key, value) ->
            "${enc(key)}=${enc(value)}"
        }
        val connection = (URL("$ENDPOINT?$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "text/plain")
            setRequestProperty("User-Agent", "PocketHost/0.1.0 Android-Dynadot-DDNS")
        }
        try {
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty().take(1000)
            require(status in 200..299) {
                "Dynadot DDNS rejected the update (HTTP $status). Check that Dynadot DNS and DDNS are enabled for this domain."
            }
            // Dynadot's documented/ddns-go-compatible response is JSON with
            // error_code; -1 is failure and every other code is success. Never
            // interpret an arbitrary non-empty HTML/login/challenge page as success.
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: throw IllegalStateException("Dynadot returned an unexpected response instead of its DDNS JSON result.")
            require(json.has("error_code")) {
                "Dynadot response did not contain error_code; the update cannot be verified."
            }
            require(json.optInt("error_code", -1) != -1) {
                val details = json.optJSONArray("content")?.let { items ->
                    (0 until items.length()).mapNotNull { items.optString(it).takeIf(String::isNotBlank) }.joinToString("; ")
                }.orEmpty()
                details.takeIf(String::isNotBlank)
                    ?: "Dynadot reported an unsuccessful DDNS update. Check the domain-specific password and Dynadot DNS configuration."
            }
            return body.ifBlank { "Dynadot accepted the HTTPS update request." }
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}
