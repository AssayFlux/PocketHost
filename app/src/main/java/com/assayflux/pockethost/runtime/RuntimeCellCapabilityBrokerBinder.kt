package com.assayflux.pockethost.runtime

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable

/**
 * Host-side Binder adapter for per-cell filesystem capabilities.
 *
 * The caller UID is obtained from Binder at transaction receipt. It is never accepted
 * from a parcel. Pass this Binder reference only to the isolated cell assigned to the
 * lease; the broker independently checks that UID against the active lease.
 */
class RuntimeCellCapabilityBrokerBinder(
    private val broker: RuntimeCellCapabilityBroker
) : Binder() {
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == INTERFACE_TRANSACTION) {
            reply?.writeString(DESCRIPTOR)
            return true
        }
        if (reply == null || flags and IBinder.FLAG_ONEWAY != 0) return false
        data.enforceInterface(DESCRIPTOR)
        val callerUid = Binder.getCallingUid()
        return try {
            when (code) {
                TRANSACTION_ISSUE -> {
                    val leaseId = requireNotNull(data.readString()) { "lease_id_required" }
                    val capability = broker.issue(leaseId, callerUid)
                    reply.writeNoException()
                    reply.writeString(capability)
                    true
                }
                TRANSACTION_OPEN_READ_ONLY -> {
                    val leaseId = requireNotNull(data.readString()) { "lease_id_required" }
                    val capability = requireNotNull(data.readString()) { "capability_required" }
                    val relativePath = requireNotNull(data.readString()) { "relative_path_required" }
                    require(relativePath.length <= MAX_RELATIVE_PATH_CHARS) { "relative_path_too_long" }
                    val descriptor = broker.openReadOnly(leaseId, capability, callerUid, relativePath)
                    try {
                        reply.writeNoException()
                        descriptor.writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
                    } finally {
                        descriptor.close()
                    }
                    true
                }
                TRANSACTION_WRITE_BYTES -> {
                    val leaseId = requireNotNull(data.readString()) { "lease_id_required" }
                    val capability = requireNotNull(data.readString()) { "capability_required" }
                    val relativePath = requireNotNull(data.readString()) { "relative_path_required" }
                    require(relativePath.length <= MAX_RELATIVE_PATH_CHARS) { "relative_path_too_long" }
                    val append = data.readInt() != 0
                    val content = requireNotNull(data.createByteArray()) { "content_required" }
                    val written = broker.writeBytes(leaseId, capability, callerUid, relativePath, content, append)
                    reply.writeNoException()
                    reply.writeLong(written)
                    true
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        } catch (error: Exception) {
            reply.writeException(error)
            true
        }
    }

    companion object {
        const val DESCRIPTOR = RuntimeCellCapabilityBrokerProtocol.DESCRIPTOR
        const val TRANSACTION_ISSUE = TRANSACTION_ISSUE
        const val TRANSACTION_OPEN_READ_ONLY = TRANSACTION_OPEN_READ_ONLY
        const val TRANSACTION_WRITE_BYTES = TRANSACTION_WRITE_BYTES
    }
}
