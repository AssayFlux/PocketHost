package com.assayflux.pockethost.runtime

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Non-production architecture probe only. It executes no project code and exposes no
 * app data. Instrumentation measures isolated-UID filesystem and loopback behavior
 * before a runtime worker architecture is selected.
 */
open class RuntimeIsolationProbeService : Service() {
    private val probeBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                reply?.writeString(DESCRIPTOR)
                return true
            }
            data.enforceInterface(DESCRIPTOR)
            reply?.writeNoException()
            when (code) {
                TRANSACTION_UID -> reply?.writeInt(Process.myUid())
                TRANSACTION_CAN_READ -> {
                    val path = data.readString().orEmpty()
                    reply?.writeInt(if (runCatching { File(path).canRead() }.getOrDefault(false)) 1 else 0)
                }
                TRANSACTION_LOOPBACK -> {
                    val port = data.readInt()
                    val connected = runCatching {
                        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 1_000) }
                        true
                    }.getOrDefault(false)
                    reply?.writeInt(if (connected) 1 else 0)
                }
                TRANSACTION_BROKERED_SOCKET -> {
                    val descriptor = ParcelFileDescriptor.CREATOR.createFromParcel(data)
                    val acceptedRequest = try {
                        val expected = "pockethost-broker-probe"
                        val request = ByteArray(256)
                        var count = 0
                        while (count < expected.length) {
                            val read = Os.read(descriptor.fileDescriptor, request, count, request.size - count)
                            if (read <= 0) break
                            count += read
                        }
                        val received = String(request, 0, count, Charsets.UTF_8)
                        val payload = (if (received == expected) "isolated-cell-response" else "rejected")
                            .toByteArray(Charsets.UTF_8)
                        Os.write(descriptor.fileDescriptor, payload, 0, payload.size)
                        received == expected
                    } finally {
                        descriptor.close()
                    }
                    reply?.writeInt(if (acceptedRequest) 1 else 0)
                }
                TRANSACTION_PROJECT_DIRECTORY -> {
                    val directory = ParcelFileDescriptor.CREATOR.createFromParcel(data)
                    // Use openat relative to the passed directory descriptor. Do not
                    // resolve /proc/self/fd/<n>/...: isolated_app SELinux policy may
                    // deny procfs traversal even when the broker intentionally passed
                    // an already-open directory capability.
                    fun openBeneath(relative: String, flags: Int, mode: Int): java.io.FileDescriptor? {
                        val segments = relative.split('/')
                        if (segments.isEmpty() || segments.any { it.isBlank() || it == "." || it == ".." }) return null
                        var parent = ParcelFileDescriptor.dup(directory.fileDescriptor)
                        try {
                            for (segment in segments.dropLast(1)) {
                                val next = Os.openat(
                                    parent.fileDescriptor,
                                    segment,
                                    OsConstants.O_RDONLY or OsConstants.O_DIRECTORY or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC,
                                    0
                                )
                                val nextPfd = ParcelFileDescriptor.dup(next)
                                Os.close(next)
                                parent.close()
                                parent = nextPfd
                            }
                            return Os.openat(
                                parent.fileDescriptor,
                                segments.last(),
                                flags or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC,
                                mode
                            )
                        } finally {
                            parent.close()
                        }
                    }
                    fun canRead(relative: String): Boolean = runCatching {
                        val fd = openBeneath(relative, OsConstants.O_RDONLY, 0) ?: return@runCatching false
                        try {
                            val buffer = ByteArray(64)
                            Os.read(fd, buffer, 0, buffer.size) > 0
                        } finally {
                            Os.close(fd)
                        }
                    }.getOrDefault(false)
                    val ownFileReadable = canRead("marker.txt")
                    val siblingFileReadable = canRead("../sibling/secret.txt")
                    val created = runCatching {
                        val fd = openBeneath(
                            "worker-created.txt",
                            OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL,
                            0x1A4
                        ) ?: return@runCatching false
                        try {
                            Os.fchmod(fd, 0x1A4)
                            val bytes = "created-by-isolated-cell".toByteArray(Charsets.UTF_8)
                            Os.write(fd, bytes, 0, bytes.size)
                        } finally {
                            Os.close(fd)
                        }
                        true
                    }.getOrDefault(false)
                    directory.close()
                    reply?.writeInt((if (ownFileReadable) 1 else 0) or
                        (if (siblingFileReadable) 2 else 0) or (if (created) 4 else 0))
                }
                TRANSACTION_BROKERED_LISTENER -> {
                    val listener = ParcelFileDescriptor.CREATOR.createFromParcel(data)
                    var acceptedCount = 0
                    try {
                        repeat(2) {
                            val accepted = Os.accept(listener.fileDescriptor, null)
                            try {
                                val request = ByteArray(128)
                                var count = 0
                                while (count < request.size) {
                                    val read = Os.read(accepted, request, count, request.size - count)
                                    if (read <= 0 || request.copyOfRange(0, count + read).contains(10.toByte())) {
                                        count += read.coerceAtLeast(0)
                                        break
                                    }
                                    count += read
                                }
                                val line = String(request, 0, count, Charsets.UTF_8).trim()
                                val response = "isolated-listener:$line\n".toByteArray(Charsets.UTF_8)
                                var sent = 0
                                while (sent < response.size) {
                                    val written = Os.write(accepted, response, sent, response.size - sent)
                                    if (written <= 0) break
                                    sent += written
                                }
                                if (line == "request-one" || line == "request-two") acceptedCount++
                            } finally {
                                Os.close(accepted)
                            }
                        }
                    } finally {
                        listener.close()
                    }
                    reply?.writeInt(acceptedCount)
                }
                else -> return super.onTransact(code, data, reply, flags)
            }
            return true
        }
    }

    override fun onBind(intent: Intent?): IBinder = probeBinder

    companion object {
        const val DESCRIPTOR = "com.assayflux.pockethost.RUNTIME_ISOLATION_PROBE"
        const val TRANSACTION_UID = IBinder.FIRST_CALL_TRANSACTION
        const val TRANSACTION_CAN_READ = IBinder.FIRST_CALL_TRANSACTION + 1
        const val TRANSACTION_LOOPBACK = IBinder.FIRST_CALL_TRANSACTION + 2
        const val TRANSACTION_BROKERED_SOCKET = IBinder.FIRST_CALL_TRANSACTION + 3
        const val TRANSACTION_PROJECT_DIRECTORY = IBinder.FIRST_CALL_TRANSACTION + 4
        const val TRANSACTION_BROKERED_LISTENER = IBinder.FIRST_CALL_TRANSACTION + 5
    }
}

/** Second isolated process used to verify distinct per-process UIDs. */
class RuntimeIsolationProbeServiceTwo : RuntimeIsolationProbeService()
