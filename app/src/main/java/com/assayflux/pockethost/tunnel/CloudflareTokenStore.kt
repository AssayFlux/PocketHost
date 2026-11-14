package com.assayflux.pockethost.tunnel

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the named-tunnel token as AES-256-GCM ciphertext using Android Keystore. */
object CloudflareTokenStore {
    private const val KEY_ALIAS = "assayflux.pockethost.cloudflare.token.v1"
    private const val PREFS = "pockethost_secure"
    private const val TOKEN_KEY = "cloudflare_tunnel_token"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    @Synchronized
    fun save(context: Context, token: String) {
        val normalized = token.trim()
        require(normalized.length in 32..4096 && normalized.none(Char::isWhitespace)) {
            "Enter a valid Cloudflare named-tunnel token."
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(TOKEN_KEY, Base64.encodeToString(payload, Base64.NO_WRAP))
            .commit()
        check(saved) { "Could not save the encrypted Cloudflare token." }
    }

    @Synchronized
    fun read(context: Context): String? {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(TOKEN_KEY, null) ?: return null
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > 12 + 16) { "Stored Cloudflare token is invalid." }
        val iv = payload.copyOfRange(0, 12)
        val ciphertext = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    fun hasToken(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(TOKEN_KEY)

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(TOKEN_KEY).commit()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey {
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
