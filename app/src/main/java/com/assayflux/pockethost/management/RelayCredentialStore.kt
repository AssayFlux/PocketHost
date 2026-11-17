package com.assayflux.pockethost.management

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Dedicated credentials for website visitor ingress.
 * These credentials are intentionally independent from fleet-management credentials.
 */
object RelayCredentialStore {
    private const val PREFS = "pockethost_relay"
    private const val KEY_ALIAS = "assayflux.pockethost.relay-credential.v1"
    private const val ENDPOINT_KEY = "endpoint"
    private const val TOKEN_KEY = "token"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun endpoint(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ENDPOINT_KEY, "") ?: ""

    fun isConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !prefs.getString(ENDPOINT_KEY, null).isNullOrBlank() &&
            !prefs.getString(TOKEN_KEY, null).isNullOrBlank()
    }

    @Synchronized
    fun configure(context: Context, endpoint: String, token: String) {
        val normalizedEndpoint = endpoint.trim().trimEnd('/')
        val uri = URI(normalizedEndpoint)
        require(uri.scheme == "https" ||
            (uri.scheme == "http" && uri.host in setOf("127.0.0.1", "localhost", "::1"))) {
            "Use HTTPS for the public AssayFlux Relay endpoint. HTTP is allowed only for localhost development."
        }
        require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null &&
            (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")) {
            "Enter a Relay base URL without credentials, query, fragment or path."
        }
        require(normalizedEndpoint.length <= 2_048) { "Relay endpoint is too long." }
        val normalizedToken = token.trim()
        require(normalizedToken.length in 32..4_096 && !normalizedToken.any(Char::isWhitespace)) {
            "Relay credential must contain 32–4,096 non-whitespace characters."
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(normalizedToken.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + ciphertext
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ENDPOINT_KEY, normalizedEndpoint)
            .putString(TOKEN_KEY, Base64.encodeToString(packed, Base64.NO_WRAP))
            .commit()
        check(saved) { "Could not save the encrypted Relay credential." }
    }

    @Synchronized
    fun readToken(context: Context): String? {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(TOKEN_KEY, null) ?: return null
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > 12 + 16) { "Stored Relay credential is invalid." }
        val iv = packed.copyOfRange(0, 12)
        val ciphertext = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    @Synchronized
    fun clear(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(ENDPOINT_KEY).remove(TOKEN_KEY).commit()
        check(saved) { "Could not remove the saved Relay credential." }
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
