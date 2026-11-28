package com.assayflux.pockethost

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assayflux.pockethost.dns.DynadotDdnsCredentialStore
import com.assayflux.pockethost.dns.parseDynadotResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DynadotDdnsCredentialTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun domainScopedPasswordIsEncryptedAndCanBeRemoved() {
        DynadotDdnsCredentialStore.clear(context)
        val password = "dynadot-domain-ddns-" + "x".repeat(24)
        DynadotDdnsCredentialStore.save(context, password)
        assertTrue(DynadotDdnsCredentialStore.hasPassword(context))
        assertEquals(password, DynadotDdnsCredentialStore.read(context))
        val stored = context.getSharedPreferences("pockethost_secure", Context.MODE_PRIVATE)
            .getString("dynadot_ddns_password", "")
        assertFalse("Raw Dynadot password must not be stored in preferences", stored.orEmpty().contains(password))
        DynadotDdnsCredentialStore.clear(context)
        assertFalse(DynadotDdnsCredentialStore.hasPassword(context))
        assertEquals(null, DynadotDdnsCredentialStore.read(context))
    }

    @Test
    fun rejectsShortOrWhitespaceCredentials() {
        assertTrue(runCatching { DynadotDdnsCredentialStore.save(context, "short") }.isFailure)
        assertTrue(runCatching { DynadotDdnsCredentialStore.save(context, "password with spaces") }.isFailure)
    }
}

