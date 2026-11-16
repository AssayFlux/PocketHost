package com.assayflux.pockethost

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
class RuntimeCompanionCapabilityTest {
    private val descriptor = "com.assayflux.pockethost.worker.RUNTIME_PROBE"
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
            val workerUid = transactInt(binder, transactionUid)
            assertNotEquals("Companion must have a distinct Android UID", context.applicationInfo.uid, workerUid)
            assertFalse(
                "Companion unexpectedly read host-private data",
                transactInt(binder, transactionCanRead, stringValue = marker.absolutePath) == 1
            )
            assertTrue(
                "Companion UID cannot bind a loopback listener",
                transactInt(binder, transactionLoopbackBind) == 1
            )
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
