package com.assayflux.pockethost.runtime

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Non-production architecture probe only. It executes no project code and exposes no
 * app data. Instrumentation measures isolated-UID filesystem and loopback behavior
 * before a runtime worker architecture is selected.
 */
class RuntimeIsolationProbeService : Service() {
    private val probeBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
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
    }
}
