package com.assayflux.pockethost

import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
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

            // A no-network isolated process can still serve a host-accepted visitor
            // stream when the host explicitly transfers that socket FD over Binder.
            val client = Socket("127.0.0.1", host.localPort)
            val accepted = host.accept()
            try {
                client.getOutputStream().write("pockethost-broker-probe".toByteArray(Charsets.UTF_8))
                client.getOutputStream().flush()
                val socketFd = ParcelFileDescriptor.fromSocket(accepted)
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                val brokered = try {
                    data.writeInterfaceToken(RuntimeIsolationProbeService.DESCRIPTOR)
                    socketFd.writeToParcel(data, 0)
                    check(binder.transact(
                        RuntimeIsolationProbeService.TRANSACTION_BROKERED_SOCKET, data, reply, 0
                    )) { "Brokered socket transaction failed." }
                    reply.readException()
                    reply.readInt() == 1
                } finally {
                    socketFd.close()
                    data.recycle()
                    reply.recycle()
                }
                assertTrue("Isolated process did not consume the brokered visitor stream", brokered)
                val expectedResponse = "isolated-cell-response"
                val responseBytes = ByteArray(expectedResponse.length)
                var responseCount = 0
                client.soTimeout = 3_000
                while (responseCount < responseBytes.size) {
                    val count = client.getInputStream().read(
                        responseBytes, responseCount, responseBytes.size - responseCount
                    )
                    if (count < 0) break
                    responseCount += count
                }
                val response = String(responseBytes, 0, responseCount, Charsets.UTF_8)
                assertTrue("Isolated process could not answer on the brokered visitor stream",
                    responseCount == expectedResponse.length && response == expectedResponse)
            } finally {
                client.close()
                accepted.close()
            }

            // Probe whether an isolated UID can use only a directory capability passed
            // by the host, while the app-private parent and sibling project stay closed.
            val capabilityParent = File(context.filesDir, "isolation-fs-" + System.nanoTime()).apply { mkdirs() }
            val projectRoot = File(capabilityParent, "project-a").apply { mkdirs() }
            val siblingRoot = File(capabilityParent, "sibling").apply { mkdirs() }
            val markerFile = File(projectRoot, "marker.txt").apply { writeText("project-a-only") }
            File(siblingRoot, "secret.txt").writeText("project-b-secret")
            Os.chmod(capabilityParent.absolutePath, 0x1C0) // 0700: inaccessible without a passed capability.
            Os.chmod(projectRoot.absolutePath, 0x1FF) // 0777: access only through the passed directory FD.
            Os.chmod(siblingRoot.absolutePath, 0x1FF)
            Os.chmod(markerFile.absolutePath, 0x1A4) // 0644.
            Os.chmod(File(siblingRoot, "secret.txt").absolutePath, 0x1A4)
            val rawDirectoryFd = Os.open(projectRoot.absolutePath, OsConstants.O_RDONLY, 0)
            val directoryFd = ParcelFileDescriptor.dup(rawDirectoryFd)
            Os.close(rawDirectoryFd)
            val directoryData = Parcel.obtain()
            val directoryReply = Parcel.obtain()
            val directoryCapabilities = try {
                directoryData.writeInterfaceToken(RuntimeIsolationProbeService.DESCRIPTOR)
                directoryFd.writeToParcel(directoryData, 0)
                check(binder.transact(
                    RuntimeIsolationProbeService.TRANSACTION_PROJECT_DIRECTORY,
                    directoryData, directoryReply, 0
                )) { "Project directory capability transaction failed." }
                directoryReply.readException()
                directoryReply.readInt()
            } finally {
                directoryFd.close()
                directoryData.recycle()
                directoryReply.recycle()
            }
            assertTrue("Isolated cell could not read its explicitly shared project directory",
                directoryCapabilities and 1 != 0)
            assertFalse("Isolated cell traversed into a sibling project directory",
                directoryCapabilities and 2 != 0)
            assertTrue("Isolated cell could not create data inside its own project directory",
                directoryCapabilities and 4 != 0)
            assertTrue("Host could not read the isolated cell's project-local output",
                File(projectRoot, "worker-created.txt").readText() == "created-by-isolated-cell")
            capabilityParent.deleteRecursively()

            // A trusted process creates and binds the listener. The isolated process
            // receives only that listener FD and accepts two visitor connections from it.
            val listenerFd = Os.socket(
                OsConstants.AF_INET, OsConstants.SOCK_STREAM, OsConstants.IPPROTO_TCP
            )
            Os.bind(listenerFd, InetAddress.getByName("127.0.0.1"), 0)
            Os.listen(listenerFd, 8)
            val listenerFlags = Os.fcntlInt(listenerFd, OsConstants.F_GETFL, 0)
            Os.fcntlInt(listenerFd, OsConstants.F_SETFL, listenerFlags or OsConstants.O_NONBLOCK)
            val listenerPort = (Os.getsockname(listenerFd) as InetSocketAddress).port
            val listenerPfd = ParcelFileDescriptor.dup(listenerFd)
            val visitorOne = Socket("127.0.0.1", listenerPort)
            val visitorTwo = Socket("127.0.0.1", listenerPort)
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
                Os.close(listenerFd)
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
