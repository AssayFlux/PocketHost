package com.assayflux.pockethost.runtime

import android.os.ParcelFileDescriptor
import java.io.File
import java.security.SecureRandom

/**
 * Lease-bound capability broker for project files.
 *
 * The trusted host resolves a project ID to its registered root. Callers cannot supply
 * an arbitrary path when requesting a capability, preventing a valid lease for project A
 * from accidentally being bound to project B's directory.
 *
 * This is host-process code. The future Binder entry point must authenticate the calling
 * cell and pass Binder.getCallingUid(); never accept a UID from a parcel or project code.
 */
class RuntimeCellCapabilityBroker(
    private val leases: RuntimeCellLeaseRegistry,
    private val projectRootFor: (projectId: String) -> File?
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

    /** Resolve the root from the lease's immutable project ID; callers cannot choose it. */
    fun issue(leaseId: String): String = synchronized(lock) {
        val lease = leases.snapshot().singleOrNull { it.id == leaseId }
            ?: throw SecurityException("Unknown cell lease.")
        check(lease.state == RuntimeCellLeaseRegistry.State.ACTIVE) {
            "Filesystem access requires an active isolated cell."
        }
        val uid = requireNotNull(lease.isolatedUid) { "Active lease has no isolated UID." }
        val root = requireNotNull(projectRootFor(lease.projectId)) {
            "No registered filesystem root exists for this project."
        }.canonicalFile
        require(root.isDirectory) { "Registered project root must exist and be a directory." }
        val token = ByteArray(32).also(random::nextBytes)
            .joinToString("") { "%02x".format(it) }
        grants[token] = Grant(lease.id, lease.projectId, uid, root)
        token
    }

    /**
     * Open one read-only regular file. callerUid must come from Binder.getCallingUid()
     * inside the trusted host-side Binder transaction, not from an IPC parcel field.
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
