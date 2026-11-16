package com.assayflux.pockethost

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.runtime.RuntimeIsolationProbeService
import com.assayflux.pockethost.runtime.RuntimeProjectFileBroker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class RuntimeIsolationProbeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test(timeout = 60_000)
    fun isolatedUidCannotReadHostPrivateFilesAndLoopbackCapabilityIsMeasured() {
        val marker = File(context.filesDir, "isolation-probe-" + System.nanoTime() + ".txt")
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
            assertTrue(
                "Isolated probe service did not bind",
                context.bindService(
                    Intent(context, RuntimeIsolationProbeService::class.java),
                    connection,
                    Context.BIND_AUTO_CREATE
                )
            )
            assertTrue("Isolated probe service connection timed out", connected.await(10, TimeUnit.SECONDS))
            val binder = checkNotNull(binderRef.get())
            val workerUid = transactInt(binder, RuntimeIsolationProbeService.TRANSACTION_UID)
            assertNotEquals("Probe must run under a distinct Android UID", context.applicationInfo.uid, workerUid)

            val secondConnected = CountDownLatch(1)
            val secondBinder = AtomicReference<IBinder?>()
            val secondConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    secondBinder.set(service)
                    secondConnected.countDown()
                }
                override fun onServiceDisconnected(name: ComponentName) = Unit
            }
            assertTrue("Second isolated execution slot did not bind", context.bindService(
                Intent().setComponent(ComponentName(
                    context.packageName,
                    "com.assayflux.pockethost.runtime.RuntimeIsolationProbeServiceTwo"
                )), secondConnection, Context.BIND_AUTO_CREATE
            ))
            try {
                assertTrue("Second isolated execution slot timed out", secondConnected.await(10, TimeUnit.SECONDS))
                val secondUid = transactInt(checkNotNull(secondBinder.get()), RuntimeIsolationProbeService.TRANSACTION_UID)
                assertNotEquals("Two active isolated slots must not share a UID", workerUid, secondUid)
            } finally {
                runCatching { context.unbindService(secondConnection) }
            }

            // Use a brokered per-file descriptor, not a directory FD. The host validates
            // each relative path and passes only the requested file capability to the cell.
            val capabilityParent = File(context.filesDir, "isolation-fs-" + System.nanoTime()).apply { mkdirs() }
            val projectRoot = File(capabilityParent, "project-a").apply { mkdirs() }
            val siblingRoot = File(capabilityParent, "sibling").apply { mkdirs() }
            val markerFile = File(projectRoot, "marker.txt").apply { writeText("project-a-only") }
            val outputFile = File(projectRoot, "worker-created.txt").apply { createNewFile() }
            File(siblingRoot, "secret.txt").writeText("project-b-secret")
            Os.chmod(capabilityParent.absolutePath, 0x1C0)
            val inputFd = RuntimeProjectFileBroker.openReadOnly(projectRoot, "marker.txt")
            val outputFd = RuntimeProjectFileBroker.openWriteOnly(projectRoot, "worker-created.txt")
            val traversalRejected = runCatching {
                RuntimeProjectFileBroker.openReadOnly(projectRoot, "../sibling/secret.txt")
            }.isFailure
            val fileData = Parcel.obtain()
            val fileReply = Parcel.obtain()
            val fileResults = try {
                fileData.writeInterfaceToken(RuntimeIsolationProbeService.DESCRIPTOR)
                fileFdWrite(inputFd, fileData)
                fileFdWrite(outputFd, fileData)
                fileData.writeInt(context.applicationInfo.uid)
                check(binder.transact(
                    RuntimeIsolationProbeService.TRANSACTION_PROJECT_FILES,
                    fileData, fileReply, 0
                )) { "Brokered project-file capability transaction failed." }
                fileReply.readException()
                listOf(fileReply.readInt(), fileReply.readInt(), fileReply.readInt())
            } finally {
                inputFd.close()
                outputFd.close()
                fileData.recycle()
                fileReply.recycle()
            }
            assertTrue("Isolated cell could not read its explicitly brokered project file", fileResults[0] == 1)
            assertTrue("Isolated cell could not write through its explicitly brokered output file", fileResults[1] == 1)
            assertTrue("Host file broker accepted a sibling traversal path", traversalRejected)
            assertTrue("Host did not receive the isolated cell's project-local output",
                outputFile.readText() == "created-by-isolated-cell")
            assertTrue("Sibling project data changed during the capability test",
                File(siblingRoot, "secret.txt").readText() == "project-b-secret")
            capabilityParent.deleteRecursively()

            // API 26 SELinux denies isolated_app access to a Binder-passed TCP socket.
            // Use the filesystem Unix-domain listener selected for production transport.
            val socketName = "pockethost-isolation-" + System.nanoTime()
            val address = LocalSocketAddress(socketName)
            val localServer = LocalServerSocket(socketName)
            val listenerPfd = ParcelFileDescriptor.dup(localServer.getFileDescriptor())
            val visitorOne = LocalSocket()
            val visitorTwo = LocalSocket()
            visitorOne.connect(address)
            visitorTwo.connect(address)
            try {
                visitorOne.getOutputStream().write("request-one\n".toByteArray(Charsets.UTF_8))
                visitorOne.getOutputStream().flush()
                visitorOne.soTimeout = 3_000
                visitorTwo.soTimeout = 3_000
                visitorTwo.getOutputStream().write("request-two\n".toByteArray(Charsets.UTF_8))
                visitorTwo.getOutputStream().flush()
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                val acceptedCount = try {
                    data.writeInterfaceToken(RuntimeIsolationProbeService.DESCRIPTOR)
                    listenerPfd.writeToParcel(data, 0)
                    check(binder.transact(
                        RuntimeIsolationProbeService.TRANSACTION_BROKERED_LISTENER,
                        data, reply, 0
                    )) { "Brokered listener transaction failed." }
                    reply.readException()
                    reply.readInt()
                } finally {
                    listenerPfd.close()
                    data.recycle()
                    reply.recycle()
                }
                assertTrue("Isolated process did not accept both host-listener connections", acceptedCount == 2)
                val responses = listOf(
                    visitorOne.getInputStream().bufferedReader().readLine(),
                    visitorTwo.getInputStream().bufferedReader().readLine()
                )
                assertTrue("Listener FD did not return visitor responses", responses.toSet() ==
                    setOf("isolated-listener:request-one", "isolated-listener:request-two"))
            } finally {
                visitorOne.close()
                visitorTwo.close()
                localServer.close()
            }

            val canRead = transactInt(
                binder,
                RuntimeIsolationProbeService.TRANSACTION_CAN_READ,
                stringValue = marker.absolutePath
            ) == 1
            assertFalse("Isolated worker unexpectedly read host-private data", canRead)

            val loopbackAllowed = transactInt(
                binder,
                RuntimeIsolationProbeService.TRANSACTION_LOOPBACK,
                intValue = host.localPort
            ) == 1
            assertFalse("Isolated process unexpectedly opened a loopback connection", loopbackAllowed)
            Log.i(
                "PocketHostIsolationProbe",
                "hostUid=" + context.applicationInfo.uid + " workerUid=" + workerUid +
                    " hostPrivateReadable=" + canRead + " loopbackAllowed=" + loopbackAllowed
            )
            instrumentation.sendStatus(0, android.os.Bundle().apply {
                putString("runtime_isolation_worker_uid", workerUid.toString())
                putString("runtime_isolation_host_uid", context.applicationInfo.uid.toString())
                putString("runtime_isolation_host_private_readable", canRead.toString())
                putString("runtime_isolation_loopback_allowed", loopbackAllowed.toString())
            })
        } finally {
            runCatching { context.unbindService(connection) }
            host.close()
            marker.delete()
        }
    }

    private fun fileFdWrite(descriptor: ParcelFileDescriptor, parcel: Parcel) {
        descriptor.writeToParcel(parcel, 0)
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
            data.writeInterfaceToken(RuntimeIsolationProbeService.DESCRIPTOR)
            if (stringValue != null) data.writeString(stringValue)
            if (intValue != null) data.writeInt(intValue)
            check(binder.transact(code, data, reply, 0)) { "Probe transaction " + code + " failed." }
            reply.readException()
            return reply.readInt()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
