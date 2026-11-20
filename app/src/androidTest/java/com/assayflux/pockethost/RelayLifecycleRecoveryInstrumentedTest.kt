package com.assayflux.pockethost

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.assayflux.pockethost.relay.RelayNodeConfigStore
import com.assayflux.pockethost.relay.RelayRetryPolicy
import com.assayflux.pockethost.service.relayNodeConfigurationExists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelayLifecycleRecoveryInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun bootRecoveryRecognizesTransactionalConfigAfterLegacyPreferenceMigration() {
        RelayNodeConfigStore.clear(context, clearRevocations = true)
        assertFalse(relayNodeConfigurationExists(context))
        RelayNodeConfigStore.save(context,
            """{"hosts":[],"routes":[],"bind_address":"127.0.0.1"}""")
        assertTrue(relayNodeConfigurationExists(context))
        RelayNodeConfigStore.clear(context)
        assertFalse(relayNodeConfigurationExists(context))
    }

    @Test
    fun retryScheduleIsExponentialAndCapped() {
        assertEquals(5L, RelayRetryPolicy.delaySeconds(0))
        assertEquals(10L, RelayRetryPolicy.delaySeconds(1))
        assertEquals(20L, RelayRetryPolicy.delaySeconds(2))
        assertEquals(160L, RelayRetryPolicy.delaySeconds(5))
        assertEquals(300L, RelayRetryPolicy.delaySeconds(6))
        assertEquals(300L, RelayRetryPolicy.delaySeconds(30))
        assertTrue(runCatching { RelayRetryPolicy.delaySeconds(-1) }.isFailure)
    }
}
