package com.assayflux.pockethost

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assayflux.pockethost.relay.RelayHostIdentityStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPublicKeySpec
import java.security.spec.ECPoint

@RunWith(AndroidJUnit4::class)
class RelayHostIdentityInstrumentedTest {
    @Test
    fun pairingProofUsesStableKeystoreKeyAndVerifiesDomainSeparatedChallenge() {
        val invite = "0123456789abcdef0123456789abcdef"
        val deviceId = "00000000-0000-4000-8000-000000000001"
        val first = RelayHostIdentityStore.createProof(invite, deviceId)
        val second = RelayHostIdentityStore.createProof(invite, deviceId)
        val publicKey = Base64.decode(first.publicKeyBase64, Base64.NO_WRAP)
        assertArrayEquals(publicKey, Base64.decode(second.publicKeyBase64, Base64.NO_WRAP))
        assertTrue(publicKey.size == 65 && publicKey[0] == 0x04.toByte())

        val params = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
        val x = BigInteger(1, publicKey.copyOfRange(1, 33))
        val y = BigInteger(1, publicKey.copyOfRange(33, 65))
        val decodedKey = KeyFactory.getInstance("EC")
            .generatePublic(ECPublicKeySpec(ECPoint(x, y), params))
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(publicKey)
            .joinToString("") { "%02x".format(it) }
        val message = "AssayFlux Pocket Host Relay Pairing v1:$invite:$deviceId:$fingerprint"
            .toByteArray(Charsets.UTF_8)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(decodedKey)
        verifier.update(message)
        assertTrue(verifier.verify(Base64.decode(first.signatureBase64, Base64.NO_WRAP)))
    }
}
