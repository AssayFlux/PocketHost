package com.assayflux.pockethost

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.assayflux.pockethost.relay.RelayCredentialIssuer
import com.assayflux.pockethost.relay.RelayNodeConfigStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class RelayCredentialIssuerInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val hostId = "00000000-0000-4000-8000-000000000001"

    private fun seed() {
        RelayNodeConfigStore.clear(context, clearRevocations = true)
        RelayNodeConfigStore.save(context, """
            {
              "bind_address":"127.0.0.1",
              "http_port":8082,
              "https_port":8444,
              "dns_resolver":"1.1.1.1:53",
              "hosts":[{"device_id":"$hostId","credential_sha256":"0000000000000000000000000000000000000000000000000000000000000000"}],
              "routes":[{"hostname":"example.test","site_id":"site-a","device_id":"$hostId","dns_txt_name":"_pockethost.example.test","dns_txt_value":"test-challenge-value-123456"}]
            }
        """.trimIndent())
    }

    @Test
    fun credentialRotationStoresOnlyVerifierAndChangesCredential() {
        seed()
        val first = RelayCredentialIssuer.issueOrRotate(context, hostId)
        val second = RelayCredentialIssuer.issueOrRotate(context, hostId)
        assertNotEquals(first.token, second.token)
        val config = JSONObject(RelayNodeConfigStore.read(context)!!)
        val verifier = config.getJSONArray("hosts").getJSONObject(0).getString("credential_sha256")
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(second.token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, verifier)
        assertFalse(RelayNodeConfigStore.read(context)!!.contains(second.token))
    }

    @Test
    fun revocationRemovesHostAndRoutesAndPersistsTombstone() {
        seed()
        val preRevocationConfig = RelayNodeConfigStore.read(context)!!
        assertTrue(RelayCredentialIssuer.revoke(context, hostId))
        val config = JSONObject(RelayNodeConfigStore.read(context)!!)
        assertEquals(0, config.getJSONArray("hosts").length())
        assertEquals(0, config.getJSONArray("routes").length())
        assertTrue(hostId in RelayNodeConfigStore.revokedDeviceIds(context))
        assertFalse(RelayCredentialIssuer.revoke(context, hostId))
        assertTrue(runCatching {
            RelayCredentialIssuer.issueOrRotate(context, hostId)
        }.isFailure)
        assertTrue("A revoked host must not be resurrected by importing an old config", runCatching {
            RelayNodeConfigStore.save(context, preRevocationConfig)
        }.isFailure)
        RelayNodeConfigStore.clear(context)
        assertTrue("Removing node config must not erase revocation history",
            hostId in RelayNodeConfigStore.revokedDeviceIds(context))
        assertTrue("Old configuration must remain blocked after config removal", runCatching {
            RelayNodeConfigStore.save(context, preRevocationConfig)
        }.isFailure)
    }
}
