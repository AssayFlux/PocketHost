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
    fun capabilityIsBoundToLeaseObservedUidAndRegisteredProjectRoot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val hostUid = context.applicationInfo.uid
        val leases = RuntimeCellLeaseRegistry(hostUid, capacity = 2)
        val lease = leases.reserve("site-a")
        val uid = hostUid + 10_001
        leases.activate(lease.id, uid)

        val projectA = File(context.cacheDir, "cell-capability-a-${System.nanoTime()}").apply { mkdirs() }
        val projectB = File(context.cacheDir, "cell-capability-b-${System.nanoTime()}").apply { mkdirs() }
        File(projectA, "index.html").writeText("site-a")
        File(projectB, "index.html").writeText("site-b")
        val roots = mutableMapOf("site-a" to projectA, "site-b" to projectB)
        val broker = RuntimeCellCapabilityBroker(leases) { projectId -> roots[projectId] }
        val token = broker.issue(lease.id)
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

            // Changing the external registry mapping cannot retarget an already issued grant.
            roots["site-a"] = projectB
            val stillA = broker.openReadOnly(lease.id, token, uid, "index.html")
            try {
                assertEquals("site-a", FileInputStream(stillA.fileDescriptor).bufferedReader().readText())
            } finally {
                stillA.close()
            }

            leases.markProcessDied(lease.id)
            assertThrows(SecurityException::class.java) {
                broker.openReadOnly(lease.id, token, uid, "index.html")
            }
            assertEquals(0, broker.activeGrantCount())
            assertTrue(leases.release(lease.id))
        } finally {
            broker.revokeLease(lease.id)
            projectA.deleteRecursively()
            projectB.deleteRecursively()
        }
    }

    @Test
    fun capabilityCannotBeIssuedWithoutRegisteredProjectRoot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val hostUid = context.applicationInfo.uid
        val leases = RuntimeCellLeaseRegistry(hostUid, capacity = 1)
        val lease = leases.reserve("unmapped")
        leases.activate(lease.id, hostUid + 10_002)
        val broker = RuntimeCellCapabilityBroker(leases) { null }
        try {
            assertThrows(IllegalArgumentException::class.java) {
                broker.issue(lease.id)
            }
        } finally {
            leases.markProcessDied(lease.id)
            leases.release(lease.id)
        }
    }
}
