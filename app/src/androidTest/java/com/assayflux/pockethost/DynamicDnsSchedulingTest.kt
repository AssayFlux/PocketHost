package com.assayflux.pockethost

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.assayflux.pockethost.dns.DynamicDnsSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DynamicDnsSchedulingTest {
    @Test
    fun periodicDdnsUsesUniqueWorkAndCanBeCancelled() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = WorkManager.getInstance(context)
        DynamicDnsSync.schedule(context, true)
        val scheduled = manager.getWorkInfosForUniqueWork("pockethost-dynamic-dns-periodic")
            .get(10, TimeUnit.SECONDS)
        assertEquals(1, scheduled.size)
        assertEquals(androidx.work.WorkInfo.State.ENQUEUED, scheduled.single().state)

        manager.cancelUniqueWork("pockethost-dynamic-dns-periodic").result.get(10, TimeUnit.SECONDS)
        val cancelled = manager.getWorkInfosForUniqueWork("pockethost-dynamic-dns-periodic")
            .get(10, TimeUnit.SECONDS)
        assertTrue(cancelled.isEmpty() || cancelled.all { it.state == androidx.work.WorkInfo.State.CANCELLED })
    }
}
