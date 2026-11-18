package com.assayflux.pockethost.relay

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.MessageDigest

/**
 * Stable per-install Relay node identity backed by Android Keystore.
 *
 * The private key is generated inside Android Keystore and is never exported.
 * Pairing protocols exchange the public key and use sign() to prove possession.
 * Clearing the identity is an explicit destructive operation; it does not silently
 * regenerate a key when the Keystore entry is missing or invalid.
 */
object RelayNodeIdentityStore {
    private const val KEY_ALIAS = "assayflux.pockethost.relay-node-identity.p256.v1"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    data class Identity(
        val nodeId: String,
        val publicKeySpkiBase64: String
    )

    @Synchronized
    fun getOrCreate(): Identity {
        val keyStore = loadKeyStore()
        val existing = keyStore.getKey(KEY_ALIAS, null)
        val pair = if (existing != null) {
            val privateKey = existing as? java.security.PrivateKey
                ?: throw IllegalStateException("Relay identity key has an unexpected type.")
            val certificate = keyStore.getCertificate(KEY_ALIAS)
                ?: throw IllegalStateException("Relay identity certificate is missing; reset the identity explicitly.")
            KeyPair(certificate.publicKey, privateKey)
        } else {
            generateKeyPair()
        }
        val encodedPublicKey = pair.public.encoded
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(encodedPublicKey)
            .joinToString("") { "%02x".format(it) }
        return Identity(
            nodeId = "ph_" + fingerprint.take(32),
            publicKeySpkiBase64 = Base64.encodeToString(encodedPublicKey, Base64.NO_WRAP)
        )
    }

    /** Sign a server-provided, domain-separated pairing challenge. */
    @Synchronized
    fun sign(challenge: ByteArray): ByteArray {
        require(challenge.size in 32..4096) { "Relay identity challenge must be 32–4,096 bytes." }
        val keyStore = loadKeyStore()
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as? java.security.PrivateKey
            ?: throw IllegalStateException("Relay node identity has not been created.")
        return Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(privateKey)
            update(challenge)
            sign()
        }
    }

    @Synchronized
    fun exists(): Boolean = loadKeyStore().containsAlias(KEY_ALIAS)

    /** Explicit node reset; existing pairings must be revoked server-side first. */
    @Synchronized
    fun delete() {
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            KEYSTORE
        )
        generator.initialize(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKeyPair()
    }

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(KEYSTORE).apply { load(null) }
}
