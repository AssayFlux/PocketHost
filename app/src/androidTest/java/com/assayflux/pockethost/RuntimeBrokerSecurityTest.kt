package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.runtime.RuntimeCellLeaseRegistry
import com.assayflux.pockethost.runtime.RuntimeProjectCapabilityBroker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RuntimeBrokerSecurityTest {
    @Test
    fun cellLeaseRegistryNeverSharesActiveUidOrRecyclesLiveSlot() {
        val hostUid = InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo.uid
        val broker = RuntimeCellLeaseRegistry(hostUid, capacity = 2)
        val first = broker.reserve("project-a")
        val second = broker.reserve("project-b")
        assertNotEquals(first.slot, second.slot)
        val active = broker.activate(first.id, hostUid + 10_000)
        assertEquals(RuntimeCellLeaseRegistry.State.ACTIVE, active.state)
        assertThrows(IllegalStateException::class.java) { broker.reserve("project-a") }
        assertThrows(IllegalStateException::class.java) { broker.release(active.id) }
        assertThrows(IllegalStateException::class.java) { broker.activate(second.id, active.isolatedUid!!) }
        broker.activate(second.id, hostUid + 10_001)
        assertThrows(IllegalStateException::class.java) { broker.reserve("project-c") }
        broker.markProcessDied(active.id)
        assertTrue(broker.release(active.id))
        assertEquals(0, broker.reserve("project-c").slot)
    }

    @Test
    fun filesystemCapabilitiesAreProjectScopedAndRevocable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val base = File(context.cacheDir, "capability-test-${System.nanoTime()}").apply { mkdirs() }
        val a = File(base, "a").apply { mkdirs() }
        val b = File(base, "b").apply { mkdirs() }
        File(a, "index.html").writeText("project-a")
        File(b, "secret.txt").writeText("project-b-secret")
        val broker = RuntimeProjectCapabilityBroker()
        val token = broker.registerProject("project-a", a)
        try {
            val input = broker.openReadOnly(token, "index.html")
            try {
                assertEquals("project-a", input.fileDescriptor.let { java.io.FileInputStream(it).bufferedReader().readText() })
            } finally { input.close() }
            val written = "broker-write-only".toByteArray()
            assertEquals(written.size.toLong(), broker.writeBytes(token, "created.txt", written))
            assertEquals("broker-write-only", File(a, "created.txt").readText())
            assertThrows(IllegalArgumentException::class.java) {
                broker.writeBytes(token, "oversized.bin", ByteArray(1024 * 1024 + 1))
            }
            assertThrows(Exception::class.java) { broker.openReadOnly(token, "../b/secret.txt") }
            assertThrows(SecurityException::class.java) { broker.openReadOnly("0".repeat(64), "index.html") }
            assertTrue(broker.revoke(token))
            assertThrows(SecurityException::class.java) { broker.openReadOnly(token, "index.html") }
        } finally {
            broker.revokeProject("project-a")
            base.deleteRecursively()
        }
    }
}
