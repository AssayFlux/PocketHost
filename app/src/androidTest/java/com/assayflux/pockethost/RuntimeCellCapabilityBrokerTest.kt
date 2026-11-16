package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.runtime.RuntimeCellCapabilityBroker
import com.assayflux.pockethost.runtime.RuntimeCellLeaseRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class RuntimeCellCapabilityBrokerTest {
    @Test
    fun capabilityIsBoundToLeaseAndObservedUidAndRevokedOnCellDeath() {
        val hostUid = InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo.uid
        val leases = RuntimeCellLeaseRegistry(hostUid, capacity = 2)
        val lease = leases.reserve("site-a")
        val uid = hostUid + 10_001
        leases.activate(lease.id, uid)

        val root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "cell-capability-${System.nanoTime()}"
        ).apply { mkdirs() }
        File(root, "index.html").writeText("site-a")
        val broker = RuntimeCellCapabilityBroker(leases)
        val token = broker.issue(lease.id, root)
        try {
            val descriptor = broker.openReadOnly(lease.id, token, uid, "index.html")
            try {
                assertEquals("site-a", FileInputStream(descriptor.fileDescriptor).bufferedReader().readText())
            } finally {
                descriptor.close()
            }

            assertThrows(SecurityException::class.java) {
                broker.openReadOnly(lease.id, token, uid + 1, "index.html")
            }
            assertThrows(SecurityException::class.java) {
                broker.openReadOnly("another-lease", token, uid, "index.html")
            }

            leases.markProcessDied(lease.id)
            assertThrows(SecurityException::class.java) {
                broker.openReadOnly(lease.id, token, uid, "index.html")
            }
            assertEquals(0, broker.activeGrantCount())
            assertTrue(leases.release(lease.id))
        } finally {
            broker.revokeLease(lease.id)
            root.deleteRecursively()
        }
    }
}
