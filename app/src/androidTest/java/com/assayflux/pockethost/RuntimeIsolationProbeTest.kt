package com.assayflux.pockethost

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
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
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class RuntimeIsolationProbeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
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
                val response = client.getInputStream().bufferedReader().readText()
                assertTrue("Isolated process could not answer on the brokered visitor stream",
                    response == "isolated-cell-response")
            } finally {
                client.close()
                accepted.close()
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
