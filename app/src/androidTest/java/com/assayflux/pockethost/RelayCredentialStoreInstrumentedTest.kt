package com.assayflux.pockethost

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assayflux.pockethost.management.RelayCredentialStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelayCredentialStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun dedicatedRelayCredentialIsEncryptedAndIndependent() {
        RelayCredentialStore.clear(context)
        val endpoint = "https://relay.example.test"
        val token = "relay-host-token-" + "a".repeat(48)
        RelayCredentialStore.configure(context, endpoint, token)

        assertTrue(RelayCredentialStore.isConfigured(context))
        assertEquals(endpoint, RelayCredentialStore.endpoint(context))
        assertEquals(token, RelayCredentialStore.readToken(context))
        val raw = context.getSharedPreferences("pockethost_relay", Context.MODE_PRIVATE)
            .getString("token", "")
        assertFalse("Raw Relay credential must not be stored in preferences", raw.orEmpty().contains(token))
        assertFalse("Relay configuration must not create fleet credentials",
            context.getSharedPreferences("pockethost_remote_control", Context.MODE_PRIVATE).contains("token"))

        RelayCredentialStore.clear(context)
        assertFalse(RelayCredentialStore.isConfigured(context))
        assertEquals(null, RelayCredentialStore.readToken(context))
    }

    @Test
    fun publicRelayEndpointRequiresHttpsAndCleanBaseUrl() {
        val token = "relay-host-token-" + "b".repeat(48)
        assertTrue(runCatching {
            RelayCredentialStore.configure(context, "http://relay.example.test", token)
        }.isFailure)
        assertTrue(runCatching {
            RelayCredentialStore.configure(context, "https://user:pass@relay.example.test", token)
        }.isFailure)
        assertTrue(runCatching {
            RelayCredentialStore.configure(context, "https://relay.example.test/path", token)
        }.isFailure)
        RelayCredentialStore.configure(context, "http://127.0.0.1:8080", token)
        assertTrue(RelayCredentialStore.isConfigured(context))
        assertTrue(runCatching { RelayCredentialStore.configure(context, "http://localhost:8080", token) }.isFailure)
        assertTrue(runCatching { RelayCredentialStore.configure(context, "http://[::1]:8080", token) }.isFailure)
        RelayCredentialStore.clear(context)
    }
}
