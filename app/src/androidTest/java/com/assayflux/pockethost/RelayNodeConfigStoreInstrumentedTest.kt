package com.assayflux.pockethost

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.assayflux.pockethost.relay.RelayNodeConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelayNodeConfigStoreInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun encryptedConfigurationSurvivesStoreReopenAndReplacement() {
        RelayNodeConfigStore.clear(context)
        val first = """{"schema":1,"marker":"first","tls_private_key_pem":"not-a-real-key"}"""
        val second = """{"schema":1,"marker":"second","hosts":[],"routes":[]}"""

        assertTrue(RelayNodeConfigStore.save(context, first))
        assertTrue(RelayNodeConfigStore.isConfigured(context))
        assertEquals(first, RelayNodeConfigStore.read(context))

        assertTrue(RelayNodeConfigStore.save(context, second))
        assertEquals(second, RelayNodeConfigStore.read(context))
    }

    @Test
    fun clearRemovesDurableConfigurationAndIsIdempotent() {
        RelayNodeConfigStore.clear(context)
        assertNull(RelayNodeConfigStore.read(context))
        assertFalse(RelayNodeConfigStore.isConfigured(context))

        RelayNodeConfigStore.save(context, """{"schema":1,"marker":"clear-me"}""")
        RelayNodeConfigStore.clear(context)
        RelayNodeConfigStore.clear(context)

        assertNull(RelayNodeConfigStore.read(context))
        assertFalse(RelayNodeConfigStore.isConfigured(context))
    }
}
