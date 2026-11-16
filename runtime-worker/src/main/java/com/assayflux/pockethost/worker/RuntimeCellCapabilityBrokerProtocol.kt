package com.assayflux.pockethost.worker

/** Stable Binder contract shared by the host-side capability broker and isolated cells. */
object RuntimeCellCapabilityBrokerProtocol {
    const val DESCRIPTOR = "com.assayflux.pockethost.RUNTIME_CELL_CAPABILITY_BROKER"
    const val TRANSACTION_ISSUE = android.os.IBinder.FIRST_CALL_TRANSACTION
    const val TRANSACTION_OPEN_READ_ONLY = android.os.IBinder.FIRST_CALL_TRANSACTION + 1
    const val TRANSACTION_WRITE_BYTES = android.os.IBinder.FIRST_CALL_TRANSACTION + 2
    const val MAX_RELATIVE_PATH_CHARS = 4096
}
