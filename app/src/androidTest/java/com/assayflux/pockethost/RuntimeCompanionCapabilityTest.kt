package com.assayflux.pockethost

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class RuntimeCompanionCapabilityTest {
    private val descriptor = "com.assayflux.pockethost.worker.RUNTIME_WORKER"
    private val transactionUid = IBinder.FIRST_CALL_TRANSACTION
    private val transactionCanRead = IBinder.FIRST_CALL_TRANSACTION + 1
    private val transactionLoopbackBind = IBinder.FIRST_CALL_TRANSACTION + 2
    private val transactionLoopbackConnect = IBinder.FIRST_CALL_TRANSACTION + 3
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun companionUidProtectsHostFilesAndRetainsLoopbackNetworking() {
        val marker = File(context.filesDir, "companion-probe-" + System.nanoTime() + ".txt")
            .apply { writeText("host-private-marker") }
        val host = ServerSocket(0)
        val connected = CountDownLatch(1)
        val binderRef = AtomicReference<IBinder?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binderRef.set(service)
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        try {
            val intent = Intent().setComponent(
                ComponentName(
                    "com.assayflux.pockethost.worker",
                    "com.assayflux.pockethost.worker.RuntimeWorkerService"
                )
            )
            assertTrue("Companion package is not installed or signature permission was denied",
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
            assertTrue("Companion service bind timed out", connected.await(10, TimeUnit.SECONDS))
            val binder = checkNotNull(binderRef.get())
            assertTrue("Unexpected companion Binder protocol version",
                transactInt(binder, IBinder.FIRST_CALL_TRANSACTION + 16) == 1)
            val workerUid = transactInt(binder, transactionUid)
            assertNotEquals("Companion must have a distinct Android UID", context.applicationInfo.uid, workerUid)
            assertFalse(
                "Companion unexpectedly read host-private data",
                transactInt(binder, transactionCanRead, stringValue = marker.absolutePath) == 1
            )
            val workerPort = transactInt(binder, transactionLoopbackBind)
            assertTrue("Companion UID cannot bind a loopback listener", workerPort in 1..65535)
            val workerResponse = Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", workerPort), 1_000)
                socket.getInputStream().bufferedReader().readLine()
            }
            assertTrue("Host could not reach companion's loopback listener", workerResponse == "PocketHost companion loopback OK")
            assertTrue(
                "Companion UID cannot connect to the host loopback listener",
                transactInt(binder, transactionLoopbackConnect, intValue = host.localPort) == 1
            )
        } finally {
            runCatching { context.unbindService(connection) }
            host.close()
            marker.delete()
        }
    }

    @Test
    fun companionRejectsProjectArchivePathTraversal() {
        val connected = CountDownLatch(1)
        val binderRef = AtomicReference<IBinder?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binderRef.set(service)
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        val intent = Intent().setComponent(
            ComponentName(
                "com.assayflux.pockethost.worker",
                "com.assayflux.pockethost.worker.RuntimeWorkerService"
            )
        )
        assertTrue(context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
        val archive = File(context.cacheDir, "pockethost-malicious-project.zip")
        try {
            assertTrue("Companion bind timed out", connected.await(10, TimeUnit.SECONDS))
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("../host-escape-marker.txt"))
                zip.write("must-not-extract".toByteArray())
                zip.closeEntry()
            }
            val descriptor = ParcelFileDescriptor.open(archive, ParcelFileDescriptor.MODE_READ_ONLY)
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(this@RuntimeCompanionCapabilityTest.descriptor)
                data.writeString("malicious_project")
                descriptor.writeToParcel(data, 0)
                assertTrue(checkNotNull(binderRef.get()).transact(
                    IBinder.FIRST_CALL_TRANSACTION + 4, data, reply, 0
                ))
                try {
                    reply.readException()
                    fail("Companion accepted a path-traversal archive.")
                } catch (_: RuntimeException) {
                    // Expected: the companion rejects the archive before activation.
                }
            } finally {
                descriptor.close()
                data.recycle()
                reply.recycle()
            }
        } finally {
            archive.delete()
            runCatching { context.unbindService(connection) }
        }
    }

    private fun transactInt(
        binder: IBinder,
        code: Int,
        stringValue: String? = null,
        intValue: Int? = null
    ): Int {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(descriptor)
            if (stringValue != null) data.writeString(stringValue)
            if (intValue != null) data.writeInt(intValue)
            check(binder.transact(code, data, reply, 0)) { "Companion transaction " + code + " failed." }
            reply.readException()
            return reply.readInt()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
