package com.assayflux.pockethost.relay

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/** Per-install host proof-of-possession key. Its private half never leaves Android Keystore. */
object RelayHostIdentityStore {
    private const val KEY_ALIAS = "assayflux.pockethost.relay-host-identity.p256.v1"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val SIGNING_CONTEXT = "AssayFlux Pocket Host Relay Pairing v1:"

    data class PairingProof(
        val publicKeyBase64: String,
        val signatureBase64: String
    )

    @Synchronized
    fun createProof(inviteCode: String, deviceId: String): PairingProof {
        require(inviteCode.length == 32 && inviteCode.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "Pairing invite is malformed or expired."
        }
        val pair = getOrCreate()
        val publicKey = (pair.public as? ECPublicKey)
            ?: throw IllegalStateException("Relay host identity is not an EC P-256 key.")
        val uncompressed = byteArrayOf(0x04) + fixed32(publicKey.w.affineX) + fixed32(publicKey.w.affineY)
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(uncompressed)
            .joinToString("") { "%02x".format(it) }
        val message = "$SIGNING_CONTEXT$inviteCode:$deviceId:$fingerprint"
            .toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(pair.private)
            update(message)
            sign()
        }
        return PairingProof(
            publicKeyBase64 = Base64.encodeToString(uncompressed, Base64.NO_WRAP),
            signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP)
        )
    }

    @Synchronized
    fun exists(): Boolean = loadKeyStore().containsAlias(KEY_ALIAS)

    private fun getOrCreate(): KeyPair {
        val keyStore = loadKeyStore()
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as? java.security.PrivateKey
        if (privateKey != null) {
            val certificate = keyStore.getCertificate(KEY_ALIAS)
                ?: throw IllegalStateException("Relay host identity certificate is missing.")
            return KeyPair(certificate.publicKey, privateKey)
        }
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
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

    private fun fixed32(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        val unsigned = if (raw.size > 32 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
        require(unsigned.size <= 32) { "Relay host public key coordinate is out of range." }
        return ByteArray(32).also { unsigned.copyInto(it, destinationOffset = 32 - unsigned.size) }
    }

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(KEYSTORE).apply { load(null) }
}
