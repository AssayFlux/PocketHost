package com.assayflux.pockethost.runtime

import android.os.ParcelFileDescriptor
import java.io.File
import java.security.SecureRandom

/**
 * Lease-bound capability broker for project files.
 *
 * This is host-process code. The Binder entry point must authenticate the calling cell
 * and pass its observed UID; never accept a UID supplied by project code. A capability
 * is valid only for the exact active lease, project, and isolated UID that received it.
 *
 * This broker deliberately exposes individual regular-file reads and bounded write
 * operations, not directory descriptors or host filesystem paths.
 */
class RuntimeCellCapabilityBroker(
    private val leases: RuntimeCellLeaseRegistry
) {
    private data class Grant(
        val leaseId: String,
        val projectId: String,
        val isolatedUid: Int,
        val root: File
    )

    private val lock = Any()
    private val random = SecureRandom()
    private val grants = HashMap<String, Grant>()

    /** Issue a capability only after the registry has observed an ACTIVE isolated cell. */
    fun issue(leaseId: String, projectRoot: File): String = synchronized(lock) {
        val lease = leases.snapshot().singleOrNull { it.id == leaseId }
            ?: throw SecurityException("Unknown cell lease.")
        check(lease.state == RuntimeCellLeaseRegistry.State.ACTIVE) {
            "Filesystem access requires an active isolated cell."
        }
        val uid = requireNotNull(lease.isolatedUid) { "Active lease has no isolated UID." }
        val root = projectRoot.absoluteFile.toPath().normalize().toFile()
        require(root.isDirectory) { "Project root must exist." }
        val token = ByteArray(32).also(random::nextBytes)
            .joinToString("") { "%02x".format(it) }
        grants[token] = Grant(lease.id, lease.projectId, uid, root)
        token
    }

    /**
     * Open one read-only regular file. callerUid must come from Binder.getCallingUid()
     * in the trusted host-side IPC boundary, not from an IPC parcel field.
     */
    fun openReadOnly(
        leaseId: String,
        capability: String,
        callerUid: Int,
        relativePath: String
    ): ParcelFileDescriptor = synchronized(lock) {
        val grant = authorize(leaseId, capability, callerUid)
        RuntimeProjectFileBroker.openReadOnly(grant.root, relativePath)
    }

    /** Bounded write routed through the trusted host process; no writable FD escapes. */
    fun writeBytes(
        leaseId: String,
        capability: String,
        callerUid: Int,
        relativePath: String,
        content: ByteArray,
        append: Boolean = false
    ): Long = synchronized(lock) {
        val grant = authorize(leaseId, capability, callerUid)
        RuntimeProjectFileBroker.writeBytes(grant.root, relativePath, content, append)
    }

    /** Revoke every outstanding capability when the cell dies or its lease is released. */
    fun revokeLease(leaseId: String): Int = synchronized(lock) {
        val tokens = grants.filterValues { it.leaseId == leaseId }.keys.toList()
        tokens.forEach(grants::remove)
        tokens.size
    }

    fun revoke(capability: String): Boolean = synchronized(lock) {
        grants.remove(capability) != null
    }

    fun activeGrantCount(): Int = synchronized(lock) { grants.size }

    private fun authorize(leaseId: String, capability: String, callerUid: Int): Grant {
        require(capability.length == 64) { "Invalid filesystem capability." }
        val grant = grants[capability]
            ?: throw SecurityException("Filesystem capability is expired or revoked.")
        if (grant.leaseId != leaseId || grant.isolatedUid != callerUid) {
            throw SecurityException("Filesystem capability belongs to another execution cell.")
        }
        val lease = leases.snapshot().singleOrNull { it.id == leaseId }
        if (lease == null ||
            lease.state != RuntimeCellLeaseRegistry.State.ACTIVE ||
            lease.projectId != grant.projectId ||
            lease.isolatedUid != callerUid
        ) {
            grants.remove(capability)
            throw SecurityException("Execution cell is no longer authorized.")
        }
        return grant
    }
}
