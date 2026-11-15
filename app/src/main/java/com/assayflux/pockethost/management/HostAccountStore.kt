package com.assayflux.pockethost.management

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/** Encrypted app-private Owner/Operator registry; passwords are salted PBKDF2 verifiers. */
object HostAccountStore {
    private const val KEY_ALIAS = "assayflux.pockethost.host-accounts.v1"
    private const val ITERATIONS = 210_000
    private const val FILE_NAME = "host-accounts.enc"
    private val random = SecureRandom()
    data class Account(val username: String, val role: String, val createdAt: Long)

    @Synchronized fun list(context: Context): List<Account> = read(context).let { array ->
        (0 until array.length()).map { array.getJSONObject(it) }.map {
            Account(it.getString("username"), it.getString("role"), it.getLong("createdAt"))
        }
    }

    @Synchronized fun hasOwner(context: Context): Boolean =
        read(context).let { array -> (0 until array.length()).any { array.getJSONObject(it).optString("role") == "OWNER" } }

    @Synchronized fun create(context: Context, usernameInput: String, password: CharArray, roleInput: String): Account {
        val username = usernameInput.trim()
        require(username.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{2,31}"))) { "username_must_be_3_to_32_safe_characters" }
        require(password.size in 12..128) { "password_must_be_12_to_128_characters" }
        val role = roleInput.uppercase()
        require(role == "OWNER" || role == "OPERATOR") { "unsupported_account_role" }
        val accounts = read(context)
        require((0 until accounts.length()).none { accounts.getJSONObject(it).optString("username").equals(username, true) }) { "username_already_exists" }
        if (role == "OWNER") require((0 until accounts.length()).none { accounts.getJSONObject(it).optString("role") == "OWNER" }) { "owner_account_already_exists" }
        val salt = ByteArray(16).also(random::nextBytes)
        val verifier = derive(password, salt)
        password.fill('\u0000')
        accounts.put(JSONObject().put("username", username).put("role", role)
            .put("createdAt", System.currentTimeMillis())
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("verifier", Base64.encodeToString(verifier, Base64.NO_WRAP))
            .put("iterations", ITERATIONS))
        write(context, accounts)
        verifier.fill(0)
        return Account(username, role, System.currentTimeMillis())
    }

    @Synchronized fun verify(context: Context, usernameInput: String, password: CharArray): Account? {
        val username = usernameInput.trim()
        val item = read(context).let { array ->
            (0 until array.length()).map { array.getJSONObject(it) }.firstOrNull { it.optString("username").equals(username, true) }
        } ?: run { password.fill('\u0000'); return null }
        val salt = Base64.decode(item.getString("salt"), Base64.NO_WRAP)
        val expected = Base64.decode(item.getString("verifier"), Base64.NO_WRAP)
        val actual = derive(password, salt)
        password.fill('\u0000')
        val valid = MessageDigest.isEqual(expected, actual)
        actual.fill(0)
        expected.fill(0)
        return if (valid) Account(item.getString("username"), item.getString("role"), item.getLong("createdAt")) else null
    }

    @Synchronized fun revokeOperator(context: Context, username: String): Boolean {
        val source = read(context)
        val result = JSONArray()
        var removed = false
        for (i in 0 until source.length()) {
            val item = source.getJSONObject(i)
            if (item.optString("username").equals(username, true)) {
                require(item.optString("role") == "OPERATOR") { "owner_account_cannot_be_revoked_here" }
                removed = true
            } else result.put(item)
        }
        if (removed) write(context, result)
        return removed
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    private fun read(context: Context): JSONArray {
        val file = File(context.noBackupFilesDir, FILE_NAME)
        if (!file.exists()) return JSONArray()
        val bytes = file.readBytes()
        require(bytes.size in 29..256_000) { "host_account_store_invalid_size" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val plain = cipher.doFinal(bytes.copyOfRange(12, bytes.size))
        return try { JSONArray(String(plain, Charsets.UTF_8)) } finally { plain.fill(0) }
    }

    private fun write(context: Context, accounts: JSONArray) {
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, nonce))
        val plain = accounts.toString().toByteArray(Charsets.UTF_8)
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        val file = File(context.noBackupFilesDir, FILE_NAME)
        val temp = File(context.noBackupFilesDir, "$FILE_NAME.tmp")
        temp.outputStream().use { stream -> stream.write(nonce); stream.write(encrypted); stream.fd.sync() }
        if (file.exists() && !file.delete()) { temp.delete(); error("host_account_store_replace_failed") }
        if (!temp.renameTo(file)) { temp.delete(); error("host_account_store_commit_failed") }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
}
