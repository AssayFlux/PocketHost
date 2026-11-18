package com.assayflux.pockethost.relay

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
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
        context.applicationContext, DATABASE, null, 3
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE (state_key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL, updated_at INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE relay_revocations (device_id TEXT PRIMARY KEY NOT NULL, revoked_at INTEGER NOT NULL, reason TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE relay_host_credentials (device_id TEXT PRIMARY KEY NOT NULL, credential_sha256 TEXT NOT NULL, created_at INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE relay_pairing_invites (invite_hash TEXT PRIMARY KEY NOT NULL, device_id TEXT NOT NULL, expires_at INTEGER NOT NULL)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS relay_revocations (device_id TEXT PRIMARY KEY NOT NULL, revoked_at INTEGER NOT NULL, reason TEXT NOT NULL)"
                )
            }
            if (oldVersion < 3) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS relay_host_credentials (device_id TEXT PRIMARY KEY NOT NULL, credential_sha256 TEXT NOT NULL, created_at INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS relay_pairing_invites (invite_hash TEXT PRIMARY KEY NOT NULL, device_id TEXT NOT NULL, expires_at INTEGER NOT NULL)"
                )
            }
        }
    }

    @Synchronized
    fun save(context: Context, configJson: String, revokedDeviceId: String? = null): Boolean {
        require(configJson.isNotBlank() && configJson.length <= 256 * 1024) {
            "Relay configuration is empty or exceeds 256 KiB."
        }
        val parsedConfig = JSONObject(configJson)
        val configuredHosts = parsedConfig.optJSONArray("hosts")
            ?: throw IllegalArgumentException("Relay configuration must include a hosts array.")
        val configuredHostIds = (0 until configuredHosts.length()).mapNotNull { index ->
            configuredHosts.optJSONObject(index)?.optString("device_id")?.takeIf(String::isNotBlank)
        }.toSet()
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
        withDatabase(context, writable = true) { db ->
            db.beginTransaction()
            try {
                val revoked = db.query(
                    "relay_revocations", arrayOf("device_id"), null, null, null, null, null
                ).use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
                require(configuredHostIds.intersect(revoked).isEmpty()) {
                    "A previously revoked Relay host identity cannot be re-imported."
                }
                check(db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
                    "Could not persist encrypted Relay configuration."
                }
                for (index in 0 until configuredHosts.length()) {
                    val host = configuredHosts.optJSONObject(index) ?: continue
                    val deviceId = host.optString("device_id")
                    val verifier = host.optString("credential_sha256")
                    if (deviceId.isBlank() || verifier.length != 64) continue
                    val credential = ContentValues().apply {
                        put("device_id", deviceId)
                        put("credential_sha256", verifier.lowercase())
                        put("created_at", System.currentTimeMillis())
                    }
                    check(db.insertWithOnConflict("relay_host_credentials", null, credential, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
                        "Could not persist Relay host credential verifier."
                    }
                }
                if (!revokedDeviceId.isNullOrBlank()) {
                    val revocation = ContentValues().apply {
                        put("device_id", revokedDeviceId)
                        put("revoked_at", System.currentTimeMillis())
                        put("reason", "owner_revoked")
                    }
                    check(db.insertWithOnConflict("relay_revocations", null, revocation, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
                        "Could not persist Relay host revocation."
                    }
                    db.delete("relay_host_credentials", "device_id = ?", arrayOf(revokedDeviceId))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        return true
    }

    @Synchronized
    fun read(context: Context): String? {
        val encoded = withDatabase(context, writable = false) { db ->
            db.query(
                TABLE, arrayOf("value"), "state_key = ?", arrayOf(ROW_CONFIG),
                null, null, null, "1"
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }
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
        val present = withDatabase(context, writable = false) { db ->
            db.query(
                TABLE, arrayOf("state_key"), "state_key = ?", arrayOf(ROW_CONFIG),
                null, null, null, "1"
            ).use { it.moveToFirst() }
        }
        return present || context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .contains(LEGACY_CONFIG_KEY)
    }

    @Synchronized
    fun activeHostIds(context: Context): Set<String> =
        withDatabase(context, writable = false) { db ->
            db.query("relay_host_credentials", arrayOf("device_id"), null, null, null, null, null)
                .use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
        }

    @Synchronized
    fun revokedDeviceIds(context: Context): Set<String> =
        withDatabase(context, writable = false) { db ->
            db.query("relay_revocations", arrayOf("device_id"), null, null, null, null, null)
                .use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) add(cursor.getString(0))
                    }
                }
        }

    @Synchronized
    fun clear(context: Context, clearRevocations: Boolean = false) {
        withDatabase(context, writable = true) { db ->
            db.beginTransaction()
            try {
                val activeIds = db.query(
                    "relay_host_credentials", arrayOf("device_id"), null, null, null, null, null
                ).use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
                for (deviceId in activeIds) {
                    val revocation = ContentValues().apply {
                        put("device_id", deviceId)
                        put("revoked_at", System.currentTimeMillis())
                        put("reason", "node_config_removed")
                    }
                    db.insertWithOnConflict("relay_revocations", null, revocation, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.delete("relay_host_credentials", null, null)
                db.delete("relay_pairing_invites", null, null)
                db.delete(TABLE, "state_key = ?", arrayOf(ROW_CONFIG))
                if (clearRevocations) db.delete("relay_revocations", null, null)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(LEGACY_CONFIG_KEY).commit()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private inline fun <T> withDatabase(
        context: Context,
        writable: Boolean,
        block: (SQLiteDatabase) -> T
    ): T {
        val helper = StateDb(context)
        try {
            return block(if (writable) helper.writableDatabase else helper.readableDatabase)
        } finally {
            helper.close()
        }
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
