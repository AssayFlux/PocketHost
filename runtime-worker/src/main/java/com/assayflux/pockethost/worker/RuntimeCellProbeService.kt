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
import java.io.ByteArrayOutputStream

/**
 * CI-only isolated-UID proof service. It installs the exact native syscall
 * policy before project code and proves a Unix-domain visitor capability works.
 */
open class RuntimeCellProbeService : Service() {
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
            if (code == TRANSACTION_READ_PROJECT_FILE) {
                check(data.dataSize() <= MAX_BINDER_REQUEST_BYTES) { "Capability request exceeds the Binder probe limit." }
                val content = readProjectFile(data)
                reply?.writeNoException()
                reply?.writeByteArray(content)
                return true
            }
            if (code == TRANSACTION_WRITE_PROJECT_FILE) {
                check(data.dataSize() <= MAX_BINDER_REQUEST_BYTES) { "Capability request exceeds the Binder probe limit." }
                val written = writeProjectFile(data)
                reply?.writeNoException()
                reply?.writeLong(written)
                return true
            }
            if (code != TRANSACTION_INSTALL_POLICY_AND_ACCEPT) return super.onTransact(code, data, reply, flags)

            val listener = ParcelFileDescriptor.CREATOR.createFromParcel(data)
            val outbound = ParcelFileDescriptor.CREATOR.createFromParcel(data)
            try {
                // The broker transfers a listener plus an intentionally untrusted socket.
                // Detach ownership before native cleanup so a later FD reuse cannot be
                // accidentally closed by ParcelFileDescriptor.close().
                val inheritedSocketFd = outbound.detachFd()
                // Deliberately duplicate an inherited network socket onto stdin.
                // Descriptor hygiene must inspect standard descriptors too.
                val duplicateResult = NetworkSandbox.duplicateToStandardInput(inheritedSocketFd)
                check(duplicateResult == 0) { "Could not place inherited socket on stdin (errno=$duplicateResult)." }
                val cleanupResult = NetworkSandbox.closeInheritedSocketsExcept(listener.fd)
                check(cleanupResult == 0) {
                    "Could not close inherited socket descriptors (errno=$cleanupResult); refusing to continue."
                }
                val inheritedSocketClosed = NetworkSandbox.isDescriptorClosed(inheritedSocketFd) == 1
                check(inheritedSocketClosed) {
                    "An inherited socket descriptor remained open; refusing to install the policy."
                }
                val standardSocketClosed = NetworkSandbox.isDescriptorClosed(0) == 1
                check(standardSocketClosed) {
                    "An inherited socket on standard input remained open; refusing to install the policy."
                }

                val policyResult = NetworkSandbox.installNetworkPolicy(listener.fd)
                check(policyResult == 0) { "Network syscall policy failed to install (errno=$policyResult)." }

                val socketCreationDenied = runCatching {
                    val fd = Os.socket(OsConstants.AF_INET, OsConstants.SOCK_STREAM, OsConstants.IPPROTO_TCP)
                    Os.close(fd)
                    false
                }.exceptionOrNull()?.let {
                    it is android.system.ErrnoException && it.errno == OsConstants.EPERM
                } ?: false
                // Use a native sockaddr_un with the existing AF_UNIX listener FD.
                // Creating another socket is itself denied, and Android's Os.connect
                // does not accept LocalSocketAddress. The native probe returns errno
                // directly so only seccomp's EPERM counts as a successful denial.
                val connectDenied = NetworkSandbox.probeConnectErrno(listener.fd) == OsConstants.EPERM
                val bindDenied = runCatching {
                    Os.bind(listener.fileDescriptor, InetSocketAddress("127.0.0.1", 0))
                }.exceptionOrNull()?.let {
                    it is android.system.ErrnoException && it.errno == OsConstants.EPERM
                } ?: false

                val resourceLimits = NetworkSandbox.getResourceLimits()
                check(resourceLimits.size == 4 &&
                    resourceLimits[0] <= 128L &&
                    resourceLimits[1] <= 32L &&
                    resourceLimits[2] <= 64L * 1024L * 1024L &&
                    resourceLimits[3] == 0L) {
                    "Cell resource limits were not applied: ${resourceLimits.contentToString()}"
                }

                val accepted = Os.accept(listener.fileDescriptor, null)
                try {
                    val acceptedPfd = ParcelFileDescriptor.dup(accepted)
                    val peerUid = try { NetworkSandbox.getPeerUid(acceptedPfd.fd) } finally { acceptedPfd.close() }
                    val hostUid = packageManager.getApplicationInfo(HOST_PACKAGE, 0).uid
                    check(peerUid == hostUid) {
                        "Visitor socket peer is not the installed host app UID; refusing the capability."
                    }
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

                    reply?.writeNoException()
                    reply?.writeInt(if (socketCreationDenied) 1 else 0)
                    reply?.writeInt(if (connectDenied) 1 else 0)
                    reply?.writeInt(if (bindDenied) 1 else 0)
                    reply?.writeInt(if (inheritedSocketClosed) 1 else 0)
                    reply?.writeInt(if (standardSocketClosed) 1 else 0)
                    reply?.writeInt(if (String(request, 0, count, Charsets.UTF_8) == "probe") 1 else 0)
                    reply?.writeInt(if (peerUid == hostUid) 1 else 0)
                    reply?.writeLongArray(resourceLimits)
                    return true
                } finally {
                    runCatching { Os.close(accepted) }
                }
            } finally {
                listener.close()
                runCatching { outbound.close() }
            }
        }
    }

    private fun readProjectFile(data: Parcel): ByteArray {
        val hostBroker = requireNotNull(data.readStrongBinder()) { "Host capability broker is missing." }
        val leaseId = requireNotNull(data.readString()) { "Cell lease ID is missing." }
        val relativePath = requireNotNull(data.readString()) { "Project path is missing." }

        val issueData = Parcel.obtain()
        val issueReply = Parcel.obtain()
        val capability = try {
            issueData.writeInterfaceToken(RuntimeCellCapabilityBrokerProtocol.DESCRIPTOR)
            issueData.writeString(leaseId)
            check(hostBroker.transact(RuntimeCellCapabilityBrokerProtocol.TRANSACTION_ISSUE, issueData, issueReply, 0)) {
                "Host broker rejected capability issuance transaction."
            }
            issueReply.readException()
            requireNotNull(issueReply.readString()) { "Host broker returned no capability." }
        } finally {
            issueData.recycle()
            issueReply.recycle()
        }

        val openData = Parcel.obtain()
        val openReply = Parcel.obtain()
        return try {
            openData.writeInterfaceToken(RuntimeCellCapabilityBrokerProtocol.DESCRIPTOR)
            openData.writeString(leaseId)
            openData.writeString(capability)
            openData.writeString(relativePath)
            check(hostBroker.transact(RuntimeCellCapabilityBrokerProtocol.TRANSACTION_OPEN_READ_ONLY, openData, openReply, 0)) {
                "Host broker rejected file-open transaction."
            }
            openReply.readException()
            val descriptor = ParcelFileDescriptor.CREATOR.createFromParcel(openReply)
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_BINDER_FILE_BYTES) { "Probe file exceeds its bounded Binder transfer limit." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally {
            openData.recycle()
            openReply.recycle()
        }
    }

    private fun writeProjectFile(data: Parcel): Long {
        val hostBroker = requireNotNull(data.readStrongBinder()) { "Host capability broker is missing." }
        val leaseId = requireNotNull(data.readString()) { "Cell lease ID is missing." }
        val relativePath = requireNotNull(data.readString()) { "Project path is missing." }
        val content = requireNotNull(data.createByteArray()) { "Project content is missing." }

        val issueData = Parcel.obtain()
        val issueReply = Parcel.obtain()
        val capability = try {
            issueData.writeInterfaceToken(RuntimeCellCapabilityBrokerProtocol.DESCRIPTOR)
            issueData.writeString(leaseId)
            check(hostBroker.transact(RuntimeCellCapabilityBrokerProtocol.TRANSACTION_ISSUE, issueData, issueReply, 0))
            issueReply.readException()
            requireNotNull(issueReply.readString())
        } finally {
            issueData.recycle()
            issueReply.recycle()
        }

        val writeData = Parcel.obtain()
        val writeReply = Parcel.obtain()
        return try {
            writeData.writeInterfaceToken(RuntimeCellCapabilityBrokerProtocol.DESCRIPTOR)
            writeData.writeString(leaseId)
            writeData.writeString(capability)
            writeData.writeString(relativePath)
            writeData.writeInt(0) // replace, do not append
            writeData.writeByteArray(content)
            check(hostBroker.transact(RuntimeCellCapabilityBrokerProtocol.TRANSACTION_WRITE_BYTES, writeData, writeReply, 0))
            writeReply.readException()
            writeReply.readLong()
        } finally {
            writeData.recycle()
            writeReply.recycle()
        }
    }

    override fun onBind(intent: Intent?): IBinder = cellBinder

    companion object {
        const val HOST_PACKAGE = "com.assayflux.pockethost"
        const val DESCRIPTOR = "com.assayflux.pockethost.worker.RUNTIME_CELL_PROBE"
        const val TRANSACTION_INSTALL_POLICY_AND_ACCEPT = IBinder.FIRST_CALL_TRANSACTION
        const val TRANSACTION_UID = IBinder.FIRST_CALL_TRANSACTION + 1
        const val TRANSACTION_READ_PROJECT_FILE = IBinder.FIRST_CALL_TRANSACTION + 2
        const val TRANSACTION_WRITE_PROJECT_FILE = IBinder.FIRST_CALL_TRANSACTION + 3
        const val MAX_BINDER_REQUEST_BYTES = 512 * 1024
        const val MAX_BINDER_FILE_BYTES = 256 * 1024
    }
}

/** Second fixed isolated slot for API 26–28 multi-project UID feasibility tests. */
class RuntimeCellProbeServiceTwo : RuntimeCellProbeService()
