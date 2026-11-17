package com.assayflux.pockethost

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assayflux.pockethost.runtime.RuntimeCellCapabilityBroker
import com.assayflux.pockethost.runtime.RuntimeCellCapabilityBrokerBinder
import com.assayflux.pockethost.runtime.RuntimeCellLeaseRegistry
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class RuntimeWorkerSeccompTest {
    @Test(timeout = 20_000)
    fun fixedIsolatedWorkerSlotsHaveDistinctUids() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageName = "com.assayflux.pockethost.worker"
        val serviceClasses = listOf(
            "$packageName.RuntimeCellProbeService",
            "$packageName.RuntimeCellProbeServiceTwo"
        )
        val binders = serviceClasses.map { AtomicReference<IBinder?>() }
        val latches = serviceClasses.map { CountDownLatch(1) }
        val connections = serviceClasses.mapIndexed { index, _ ->
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    binders[index].set(service)
                    latches[index].countDown()
                }
                override fun onServiceDisconnected(name: ComponentName) = Unit
            }
        }
        val bound = mutableListOf<ServiceConnection>()
        try {
            serviceClasses.forEachIndexed { index, className ->
                val intent = Intent().setComponent(ComponentName(packageName, className))
                assertTrue("Isolated slot $index must bind",
                    context.bindService(intent, connections[index], Context.BIND_AUTO_CREATE))
                bound += connections[index]
            }
            latches.forEachIndexed { index, latch ->
                assertTrue("Isolated slot $index bind timed out", latch.await(10, TimeUnit.SECONDS))
            }
            val uids = binders.map { reference ->
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    check(requireNotNull(reference.get()).transact(TRANSACTION_UID, data, reply, 0))
                    reply.readException()
                    reply.readInt()
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
            assertNotEquals("First slot must have an isolated UID", context.applicationInfo.uid, uids[0])
            assertNotEquals("Second slot must have an isolated UID", context.applicationInfo.uid, uids[1])
            assertNotEquals("Two simultaneous project slots must not share a UID", uids[0], uids[1])
        } finally {
            bound.forEach { connection -> runCatching { context.unbindService(connection) } }
        }
    }

    @Test(timeout = 30_000)
    fun isolatedCellClosesInheritedSocketsDeniesNewSocketsAndAcceptsHostUnixListener() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val socketName = "pockethost-seccomp-" + System.nanoTime()
        val address = LocalSocketAddress(socketName)
        val listener = LocalServerSocket(socketName)
        val outboundServer = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        // Use the platform Socket implementation to establish the inherited TCP
        // descriptor. Some API 26 emulator images reject the raw Os.connect address
        // conversion with EAFNOSUPPORT even though Java's network stack supports IPv4.
        val outboundSocket = java.net.Socket()
        outboundSocket.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), outboundServer.localPort), 3_000)
        val outboundPeer = outboundServer.accept()
        val outboundPfd = ParcelFileDescriptor.fromSocket(outboundSocket)
        val listenerPfd = ParcelFileDescriptor.dup(listener.getFileDescriptor())
        val connected = CountDownLatch(1)
        val binderRef = AtomicReference<IBinder?>()
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binderRef.set(service)
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        val service = Intent().setComponent(ComponentName(
            "com.assayflux.pockethost.worker",
            "com.assayflux.pockethost.worker.RuntimeCellProbeService"
        ))
        try {
            assertTrue("Runtime cell probe service must bind",
                context.bindService(service, serviceConnection, Context.BIND_AUTO_CREATE))
            assertTrue("Runtime cell probe service bind timed out", connected.await(10, TimeUnit.SECONDS))
            val binder = checkNotNull(binderRef.get())
            val uidData = Parcel.obtain()
            val uidReply = Parcel.obtain()
            try {
                uidData.writeInterfaceToken(DESCRIPTOR)
                check(binder.transact(TRANSACTION_UID, uidData, uidReply, 0))
                uidReply.readException()
                assertNotEquals("Cell must run under a distinct isolated UID",
                    context.applicationInfo.uid, uidReply.readInt())
            } finally {
                uidData.recycle()
                uidReply.recycle()
            }

            val client = LocalSocket()
            try {
                client.connect(address)
                client.soTimeout = 3_000
                // The isolated service must call accept() to make progress on this
                // abstract Unix stream. Write concurrently with the synchronous Binder
                // transaction; writing on this thread first can deadlock on API 26.
                val writerFailure = AtomicReference<Throwable?>()
                val writer = Thread({
                    runCatching {
                        client.getOutputStream().write("probe".toByteArray(Charsets.UTF_8))
                        client.getOutputStream().flush()
                    }.onFailure(writerFailure::set)
                }, "PocketHost-Seccomp-Probe-Writer").apply { start() }

                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                var transactionSucceeded = false
                val probe = try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    listenerPfd.writeToParcel(data, 0)
                    outboundPfd.writeToParcel(data, 0)
                    check(binder.transact(TRANSACTION_INSTALL_POLICY_AND_ACCEPT, data, reply, 0)) {
                        "Isolated cell Binder transaction failed before returning a policy result."
                    }
                    reply.readException()
                    transactionSucceeded = true
                    val values = listOf(reply.readInt(), reply.readInt(), reply.readInt(), reply.readInt(), reply.readInt(), reply.readInt(), reply.readInt())
                    val deniedNetworkIoMask = reply.readInt()
                    values to (deniedNetworkIoMask to (reply.createLongArray() ?: error("Cell resource limits were not returned.")))
                } finally {
                    if (!transactionSucceeded) runCatching { client.close() }
                    writer.join(5_000)
                    check(!writer.isAlive) { "Visitor stream writer did not complete." }
                    if (transactionSucceeded) writerFailure.get()?.let { throw it }
                    data.recycle()
                    reply.recycle()
                }
                val results = probe.first
                val deniedNetworkIoMask = probe.second.first
                val resourceLimits = probe.second.second
                assertEquals("socket() must be denied with EPERM", 1, results[0])
                assertEquals("connect() must be denied with EPERM", 1, results[1])
                assertEquals("bind() must be denied with EPERM", 1, results[2])
                assertEquals("The inherited connected-socket capability must be closed before policy install", 1, results[3])
                assertEquals("An inherited socket on standard input must also be closed", 1, results[4])
                assertEquals("The brokered Unix listener must accept visitor traffic", 1, results[5])
                assertEquals("SO_PEERCRED must identify the host app UID", 1, results[6])
                assertEquals(
                    "sendto/sendmsg/sendmmsg/recvfrom/recvmsg/recvmmsg/shutdown must all be denied with EPERM",
                    0x7f,
                    deniedNetworkIoMask
                )
                assertTrue("NOFILE limit must be no greater than 128", resourceLimits[0] in 1L..128L)
                assertTrue("NPROC limit must be no greater than 32", resourceLimits[1] in 1L..32L)
                assertTrue("FSIZE limit must be no greater than 64 MiB", resourceLimits[2] in 1L..(64L * 1024L * 1024L))
                assertEquals("Core dumps must be disabled", 0L, resourceLimits[3])
                assertEquals("Cell response must return on the accepted visitor stream",
                    "cell-accepted", client.getInputStream().bufferedReader().readText())
            } finally {
                client.close()
            }
        } finally {
            runCatching { context.unbindService(serviceConnection) }
            listenerPfd.close()
            outboundPfd.close()
            listener.close()
            runCatching { outboundPeer.close() }
            runCatching { outboundServer.close() }
            Os.close(outbound)
        }
    }

    @Test(timeout = 30_000)
    fun isolatedCellUsesBinderCallingUidForProjectFileCapabilities() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projectId = "binder-probe-" + System.nanoTime().toString(16)
        val projectRoot = File(context.filesDir, projectId)
        check(projectRoot.mkdirs()) { "Could not create the host-side probe project root." }
        File(projectRoot, "index.html").writeText("isolated-project-only")
        val leases = RuntimeCellLeaseRegistry(context.applicationInfo.uid, capacity = 1)
        val lease = leases.reserve(projectId)
        val broker = RuntimeCellCapabilityBroker(leases) { id -> if (id == projectId) projectRoot else null }
        val hostBroker = RuntimeCellCapabilityBrokerBinder(broker)
        val connected = CountDownLatch(1)
        val binderRef = AtomicReference<IBinder?>()
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binderRef.set(service)
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        val service = Intent().setComponent(ComponentName(
            "com.assayflux.pockethost.worker",
            "com.assayflux.pockethost.worker.RuntimeCellProbeService"
        ))
        try {
            assertTrue("Runtime cell probe service must bind",
                context.bindService(service, serviceConnection, Context.BIND_AUTO_CREATE))
            assertTrue("Runtime cell probe service bind timed out", connected.await(10, TimeUnit.SECONDS))
            val cellBinder = checkNotNull(binderRef.get())
            val uidData = Parcel.obtain()
            val uidReply = Parcel.obtain()
            val isolatedUid = try {
                uidData.writeInterfaceToken(DESCRIPTOR)
                check(cellBinder.transact(TRANSACTION_UID, uidData, uidReply, 0))
                uidReply.readException()
                uidReply.readInt()
            } finally {
                uidData.recycle()
                uidReply.recycle()
            }
            assertNotEquals("Cell must run under a distinct isolated UID", context.applicationInfo.uid, isolatedUid)
            leases.activate(lease.id, isolatedUid)

            // A direct host-process call is rejected: the host UID is not the cell UID.
            val directData = Parcel.obtain()
            val directReply = Parcel.obtain()
            try {
                directData.writeInterfaceToken(RuntimeCellCapabilityBrokerBinder.DESCRIPTOR)
                directData.writeString(lease.id)
                check(hostBroker.transact(RuntimeCellCapabilityBrokerBinder.TRANSACTION_ISSUE, directData, directReply, 0))
                try {
                    directReply.readException()
                    fail("The host process must not impersonate the isolated cell.")
                } catch (_: SecurityException) {
                    // Expected: Binder.getCallingUid() reports the host UID.
                }
            } finally {
                directData.recycle()
                directReply.recycle()
            }

            val request = Parcel.obtain()
            val response = Parcel.obtain()
            try {
                request.writeInterfaceToken(DESCRIPTOR)
                request.writeStrongBinder(hostBroker)
                request.writeString(lease.id)
                request.writeString("index.html")
                check(cellBinder.transact(TRANSACTION_READ_PROJECT_FILE, request, response, 0))
                response.readException()
                assertEquals(
                    "isolated-project-only",
                    String(requireNotNull(response.createByteArray()), Charsets.UTF_8)
                )
            } finally {
                request.recycle()
                response.recycle()
            }
            assertEquals("Only the cell-issued read capability should be active.", 1, broker.activeGrantCount())

            val writeRequest = Parcel.obtain()
            val writeResponse = Parcel.obtain()
            val replacement = "written-through-binder".toByteArray(Charsets.UTF_8)
            try {
                writeRequest.writeInterfaceToken(DESCRIPTOR)
                writeRequest.writeStrongBinder(hostBroker)
                writeRequest.writeString(lease.id)
                writeRequest.writeString("index.html")
                writeRequest.writeByteArray(replacement)
                check(cellBinder.transact(TRANSACTION_WRITE_PROJECT_FILE, writeRequest, writeResponse, 0))
                writeResponse.readException()
                assertEquals(replacement.size.toLong(), writeResponse.readLong())
            } finally {
                writeRequest.recycle()
                writeResponse.recycle()
            }
            assertEquals("Cell writes must be mediated by the host rather than an escaped writable FD.",
                String(replacement, Charsets.UTF_8), File(projectRoot, "index.html").readText())
            assertEquals("Read and write capabilities are separately issued and bounded.", 2, broker.activeGrantCount())
        } finally {
            runCatching { context.unbindService(serviceConnection) }
            broker.revokeLease(lease.id)
            projectRoot.deleteRecursively()
        }
    }


    @Test(timeout = 10_000)
    fun projectFileBrokerRejectsSymlinksAndFifosWithoutBlocking() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "broker-path-probe-" + System.nanoTime())
        val outside = File(context.cacheDir, "broker-outside-" + System.nanoTime())
        check(root.mkdirs()) { "Could not create broker test root." }
        outside.writeText("outside-secret")
        val fifo = File(root, "pipe")
        val fileLink = File(root, "linked-file")
        val directoryLink = File(root, "linked-directory")
        try {
            Os.mkfifo(fifo.absolutePath, OsConstants.S_IRUSR or OsConstants.S_IWUSR)
            Os.symlink(outside.absolutePath, fileLink.absolutePath)
            Os.symlink(context.cacheDir.absolutePath, directoryLink.absolutePath)

            fun assertRejected(description: String, operation: () -> Unit) {
                try {
                    operation()
                    fail("$description must be rejected.")
                } catch (_: Exception) {
                    // Expected: no symlink or non-regular file may cross the broker.
                }
            }

            assertRejected("Opening a FIFO for reading") {
                com.assayflux.pockethost.runtime.RuntimeProjectFileBroker.openReadOnly(root, "pipe").close()
            }
            assertRejected("Traversing a FIFO as a directory") {
                com.assayflux.pockethost.runtime.RuntimeProjectFileBroker.openReadOnly(root, "pipe/child").close()
            }
            assertRejected("Using a FIFO as the project root") {
                com.assayflux.pockethost.runtime.RuntimeProjectFileBroker.openReadOnly(fifo, "child").close()
            }
            assertRejected("Opening a symlinked file") {
                com.assayflux.pockethost.runtime.RuntimeProjectFileBroker.openReadOnly(root, "linked-file").close()
            }
            assertRejected("Traversing a symlinked directory") {
                com.assayflux.pockethost.runtime.RuntimeProjectFileBroker.openReadOnly(root, "linked-directory/secret").close()
            }
            assertRejected("Capturing a FIFO as a project root") {
                com.assayflux.pockethost.runtime.RuntimeProjectFileBroker.captureRootIdentity(fifo)
            }
            assertEquals("The outside target must remain unchanged.", "outside-secret", outside.readText())
        } finally {
            root.deleteRecursively()
            outside.delete()
        }
    }

    companion object {
        private const val DESCRIPTOR = "com.assayflux.pockethost.worker.RUNTIME_CELL_PROBE"
        private const val TRANSACTION_INSTALL_POLICY_AND_ACCEPT = IBinder.FIRST_CALL_TRANSACTION
        private const val TRANSACTION_UID = IBinder.FIRST_CALL_TRANSACTION + 1
        private const val TRANSACTION_READ_PROJECT_FILE = IBinder.FIRST_CALL_TRANSACTION + 2
        private const val TRANSACTION_WRITE_PROJECT_FILE = IBinder.FIRST_CALL_TRANSACTION + 3
    }
}
