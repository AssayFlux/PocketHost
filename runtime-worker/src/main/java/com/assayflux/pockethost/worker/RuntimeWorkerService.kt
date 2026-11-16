package com.assayflux.pockethost.worker

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Companion-package capability probe. It executes no customer code and accepts only
 * bounded diagnostic Binder transactions. The package UID is distinct from PocketHost.
 */
class RuntimeWorkerService : Service() {
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
                TRANSACTION_LOOPBACK_BIND -> {
                    val canBind = runCatching {
                        ServerSocket().use { it.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0)) }
                        true
                    }.getOrDefault(false)
                    reply?.writeInt(if (canBind) 1 else 0)
                }
                TRANSACTION_LOOPBACK_CONNECT -> {
                    val port = data.readInt()
                    val canConnect = runCatching {
                        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 1_000) }
                        true
                    }.getOrDefault(false)
                    reply?.writeInt(if (canConnect) 1 else 0)
                }
                else -> return super.onTransact(code, data, reply, flags)
            }
            return true
        }
    }

    override fun onBind(intent: Intent?): IBinder = probeBinder

    companion object {
        const val DESCRIPTOR = "com.assayflux.pockethost.worker.RUNTIME_PROBE"
        const val TRANSACTION_UID = IBinder.FIRST_CALL_TRANSACTION
        const val TRANSACTION_CAN_READ = IBinder.FIRST_CALL_TRANSACTION + 1
        const val TRANSACTION_LOOPBACK_BIND = IBinder.FIRST_CALL_TRANSACTION + 2
        const val TRANSACTION_LOOPBACK_CONNECT = IBinder.FIRST_CALL_TRANSACTION + 3
    }
}
