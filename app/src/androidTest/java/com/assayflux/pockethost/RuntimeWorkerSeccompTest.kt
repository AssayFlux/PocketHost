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
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class RuntimeWorkerSeccompTest {
    @Test(timeout = 30_000)
    fun isolatedCellClosesInheritedSocketsDeniesNewSocketsAndAcceptsHostUnixListener() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val socketName = "pockethost-seccomp-" + System.nanoTime()
        val address = LocalSocketAddress(socketName)
        val listener = LocalServerSocket(socketName)
        val outboundServer = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val outbound = Os.socket(OsConstants.AF_INET, OsConstants.SOCK_STREAM, OsConstants.IPPROTO_TCP)
        Os.connect(outbound, InetSocketAddress("127.0.0.1", outboundServer.localPort))
        val outboundPeer = outboundServer.accept()
        val listenerPfd = ParcelFileDescriptor.dup(listener.getFileDescriptor())
        val outboundPfd = ParcelFileDescriptor.dup(outbound)
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
                val results = try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    listenerPfd.writeToParcel(data, 0)
                    outboundPfd.writeToParcel(data, 0)
                    data.writeInt(context.applicationInfo.uid)
                    check(binder.transact(TRANSACTION_INSTALL_POLICY_AND_ACCEPT, data, reply, 0)) {
                        "Isolated cell Binder transaction failed before returning a policy result."
                    }
                    reply.readException()
                    transactionSucceeded = true
                    listOf(reply.readInt(), reply.readInt(), reply.readInt(), reply.readInt())
                } finally {
                    if (!transactionSucceeded) runCatching { client.close() }
                    writer.join(5_000)
                    check(!writer.isAlive) { "Visitor stream writer did not complete." }
                    if (transactionSucceeded) writerFailure.get()?.let { throw it }
                    data.recycle()
                    reply.recycle()
                }
                assertEquals("socket() must be denied with EPERM", 1, results[0])
                assertEquals("The inherited connected-socket capability must be closed before policy install", 1, results[1])
                assertEquals("The brokered Unix listener must accept visitor traffic", 1, results[2])
                assertEquals("SO_PEERCRED must identify the host app UID", 1, results[3])
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

    companion object {
        private const val DESCRIPTOR = "com.assayflux.pockethost.worker.RUNTIME_CELL_PROBE"
        private const val TRANSACTION_INSTALL_POLICY_AND_ACCEPT = IBinder.FIRST_CALL_TRANSACTION
        private const val TRANSACTION_UID = IBinder.FIRST_CALL_TRANSACTION + 1
    }
}
