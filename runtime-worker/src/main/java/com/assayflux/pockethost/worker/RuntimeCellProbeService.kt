package com.assayflux.pockethost.worker

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.net.InetSocketAddress

/**
 * CI-only isolated-UID proof service. It installs the exact native syscall
 * policy before project code and proves a Unix-domain visitor capability works.
 */
class RuntimeCellProbeService : Service() {
    private val cellBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(DESCRIPTOR)
                return true
            }
            data.enforceInterface(DESCRIPTOR)
            if (code == TRANSACTION_UID) {
                reply?.writeNoException()
                reply?.writeInt(android.os.Process.myUid())
                return true
            }
            if (code != TRANSACTION_INSTALL_POLICY_AND_ACCEPT) return super.onTransact(code, data, reply, flags)

            val listener = ParcelFileDescriptor.CREATOR.createFromParcel(data)
            val outbound = ParcelFileDescriptor.CREATOR.createFromParcel(data)
            val expectedHostUid = data.readInt()
            try {
                val policyResult = NetworkSandbox.installNetworkPolicy(listener.fd)
                check(policyResult == 0) { "Network syscall policy failed to install (errno=$policyResult)." }

                val socketCreationDenied = runCatching {
                    Os.socket(OsConstants.AF_INET, OsConstants.SOCK_STREAM, OsConstants.IPPROTO_TCP)
                }.exceptionOrNull()?.let { it is android.system.ErrnoException && it.errno == OsConstants.EPERM } ?: false

                val connectDenied = runCatching {
                    Os.connect(outbound.fileDescriptor, InetSocketAddress("127.0.0.1", 9))
                }.exceptionOrNull()?.let { it is android.system.ErrnoException && it.errno == OsConstants.EPERM } ?: false

                val accepted = Os.accept(listener.fileDescriptor, null)
                val acceptedPfd = ParcelFileDescriptor.dup(accepted)
                val peerUid = try { NetworkSandbox.getPeerUid(acceptedPfd.fd) } finally { acceptedPfd.close() }
                val request = ByteArray(5)
                var count = 0
                while (count < request.size) {
                    val read = Os.read(accepted, request, count, request.size - count)
                    if (read <= 0) break
                    count += read
                }
                val response = "cell-accepted".toByteArray(Charsets.UTF_8)
                var sent = 0
                while (sent < response.size) {
                    val written = Os.write(accepted, response, sent, response.size - sent)
                    if (written <= 0) break
                    sent += written
                }
                Os.close(accepted)

                reply?.writeNoException()
                reply?.writeInt(if (socketCreationDenied) 1 else 0)
                reply?.writeInt(if (connectDenied) 1 else 0)
                reply?.writeInt(if (String(request, 0, count, Charsets.UTF_8) == "probe") 1 else 0)
                reply?.writeInt(if (peerUid == expectedHostUid) 1 else 0)
                return true
            } finally {
                listener.close()
                outbound.close()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = cellBinder

    companion object {
        const val DESCRIPTOR = "com.assayflux.pockethost.worker.RUNTIME_CELL_PROBE"
        const val TRANSACTION_INSTALL_POLICY_AND_ACCEPT = IBinder.FIRST_CALL_TRANSACTION
        const val TRANSACTION_UID = IBinder.FIRST_CALL_TRANSACTION + 1
    }
}
