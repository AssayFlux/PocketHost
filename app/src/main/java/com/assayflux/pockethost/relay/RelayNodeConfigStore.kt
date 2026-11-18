package com.assayflux.pockethost.relay

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the complete Relay node configuration as authenticated ciphertext in a
 * transactional SQLite record. The encryption key remains non-exportable in
 * Android Keystore; SQLite transactions prevent partially replaced config state.
 */
object RelayNodeConfigStore {
    private const val PREFS = "pockethost_relay_node"
    private const val LEGACY_CONFIG_KEY = "encrypted_config_v1"
    private const val KEY_ALIAS = "pockethost_relay_node_config_v1"
    private const val DATABASE = "pockethost_relay_node.db"
    private const val TABLE = "relay_state"
    private const val ROW_CONFIG = "encrypted_config_v2"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val AAD = "AssayFlux Pocket Host Relay Node Config v2".toByteArray(StandardCharsets.UTF_8)

    private class StateDb(context: Context) : SQLiteOpenHelper(
        context.applicationContext, DATABASE, null, 1
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE (state_key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL, updated_at INTEGER NOT NULL)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Versioned migrations must be explicit and transactional.
            if (oldVersion < 1) onCreate(db)
        }
    }

    @Synchronized
    fun save(context: Context, configJson: String): Boolean {
        require(configJson.isNotBlank() && configJson.length <= 256 * 1024) {
            "Relay configuration is empty or exceeds 256 KiB."
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(configJson.toByteArray(StandardCharsets.UTF_8))
        val packed = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
            .put(cipher.iv.size.toByte()).put(cipher.iv).put(encrypted).array()
        val values = ContentValues().apply {
            put("state_key", ROW_CONFIG)
            put("value", Base64.encodeToString(packed, Base64.NO_WRAP))
            put("updated_at", System.currentTimeMillis())
        }
        val db = StateDb(context).writableDatabase
        db.beginTransaction()
        try {
            check(db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
                "Could not persist encrypted Relay configuration."
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return true
    }

    @Synchronized
    fun read(context: Context): String? {
        val db = StateDb(context).readableDatabase
        val encoded = db.query(
            TABLE, arrayOf("value"), "state_key = ?", arrayOf(ROW_CONFIG),
            null, null, null, "1"
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        if (encoded != null) return decrypt(encoded, authenticated = true)

        // One-time migration from the earlier encrypted SharedPreferences record.
        val legacy = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(LEGACY_CONFIG_KEY, null) ?: return null
        val plaintext = decrypt(legacy, authenticated = false)
        save(context, plaintext)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(LEGACY_CONFIG_KEY).commit()
        return plaintext
    }

    @Synchronized
    fun isConfigured(context: Context): Boolean {
        val db = StateDb(context).readableDatabase
        val present = db.query(
            TABLE, arrayOf("state_key"), "state_key = ?", arrayOf(ROW_CONFIG),
            null, null, null, "1"
        ).use { it.moveToFirst() }
        return present || context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .contains(LEGACY_CONFIG_KEY)
    }

    @Synchronized
    fun clear(context: Context) {
        val db = StateDb(context).writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE, "state_key = ?", arrayOf(ROW_CONFIG))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(LEGACY_CONFIG_KEY).commit()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun decrypt(encoded: String, authenticated: Boolean): String {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > 14) { "Stored Relay configuration is truncated." }
        val buffer = ByteBuffer.wrap(packed)
        val ivLength = buffer.get().toInt() and 0xff
        require(ivLength == 12 && buffer.remaining() > ivLength) {
            "Stored Relay configuration has an invalid GCM nonce."
        }
        val iv = ByteArray(ivLength)
        buffer.get(iv)
        val encrypted = ByteArray(buffer.remaining())
        buffer.get(encrypted)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        if (authenticated) cipher.updateAAD(AAD)
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
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
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
