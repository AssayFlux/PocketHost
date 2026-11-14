package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.diagnostics.MetricsStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetricsStoreSmokeTest {
    @Test
    fun aggregatesTrafficAcrossEngineSessions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MetricsStore.reset(context)

        MetricsStore.beginSession(context)
        MetricsStore.recordSample(context, 10L, 1_000L)
        MetricsStore.recordSample(context, 15L, 2_000L)

        var snapshot = MetricsStore.snapshot(context)
        assertEquals(15L, snapshot.totalRequests)
        assertEquals(2_000L, snapshot.totalBytes)
        assertEquals(15L, snapshot.todayRequests)
        assertEquals(2_000L, snapshot.todayBytes)

        MetricsStore.beginSession(context)
        MetricsStore.recordSample(context, 2L, 300L)

        snapshot = MetricsStore.snapshot(context)
        assertEquals(17L, snapshot.totalRequests)
        assertEquals(2_300L, snapshot.totalBytes)
        assertEquals(17L, snapshot.todayRequests)
        assertEquals(2_300L, snapshot.todayBytes)

        MetricsStore.reset(context)
    }
}
