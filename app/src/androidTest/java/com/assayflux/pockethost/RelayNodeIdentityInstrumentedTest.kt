package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assayflux.pockethost.relay.RelayNodeIdentityStore
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelayNodeIdentityInstrumentedTest {
    @Test
    fun identityIsStableAndPrivateKeySignsWithoutBeingExported() {
        RelayNodeIdentityStore.delete()
        val first = RelayNodeIdentityStore.getOrCreate()
        val second = RelayNodeIdentityStore.getOrCreate()
        assertEquals(first.nodeId, second.nodeId)
        assertEquals(first.publicKeySpkiBase64, second.publicKeySpkiBase64)
        assertTrue(first.nodeId.startsWith("ph_"))

        val challenge = ByteArray(32) { (it * 7 + 3).toByte() }
        val signatureBytes = RelayNodeIdentityStore.sign(challenge)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(Base64.decode(first.publicKeySpkiBase64, Base64.NO_WRAP))
        )
        val valid = Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(challenge)
            verify(signatureBytes)
        }
        assertTrue("Keystore identity must sign the supplied challenge", valid)
        assertTrue(RelayNodeIdentityStore.exists())
    }

    @Test
    fun identityRejectsShortChallengesAndResetCreatesNewIdentity() {
        RelayNodeIdentityStore.delete()
        val original = RelayNodeIdentityStore.getOrCreate()
        assertTrue(runCatching { RelayNodeIdentityStore.sign(ByteArray(16)) }.isFailure)
        RelayNodeIdentityStore.delete()
        assertTrue(!RelayNodeIdentityStore.exists())
        val replacement = RelayNodeIdentityStore.getOrCreate()
        assertNotEquals(original.nodeId, replacement.nodeId)
    }
}
