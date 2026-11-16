package com.assayflux.pockethost.runtime

import java.security.SecureRandom

/**
 * Trusted host-side allocator for a bounded pool of isolated execution cells.
 *
 * This class owns lease identity and UID uniqueness; it does not pretend to bind Android
 * services itself. A caller must activate a reservation only after binding the declared
 * isolated service and reading Process.myUid() from that service. A slot is reusable only
 * after its Binder death callback has been observed (or before the service was started).
 */
class RuntimeCellLeaseRegistry(
    private val hostUid: Int,
    private val capacity: Int = DEFAULT_CAPACITY
) {
    enum class State { RESERVED, ACTIVE, DEAD }

    data class Lease internal constructor(
        val id: String,
        val projectId: String,
        val slot: Int,
        val isolatedUid: Int?,
        val state: State
    )

    private data class Entry(
        val id: String,
        val projectId: String,
        val slot: Int,
        var isolatedUid: Int? = null,
        var state: State = State.RESERVED
    )

    private val lock = Any()
    private val random = SecureRandom()
    private val slots = arrayOfNulls<Entry>(capacity)

    init {
        require(hostUid > 0) { "Host UID must be positive." }
        require(capacity in 1..MAX_CAPACITY) { "Cell capacity must be between 1 and $MAX_CAPACITY." }
    }

    fun reserve(projectId: String): Lease = synchronized(lock) {
        require(projectId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid project identifier." }
        check(slots.filterNotNull().none { it.projectId == projectId }) {
            "Project already owns an execution-cell lease."
        }
        val slot = slots.indexOfFirst { it == null }
        check(slot >= 0) { "No isolated execution cell is available; refusing to share a UID." }
        val id = ByteArray(24).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        val entry = Entry(id, projectId, slot)
        slots[slot] = entry
        entry.snapshot()
    }

    fun activate(leaseId: String, isolatedUid: Int): Lease = synchronized(lock) {
        require(isolatedUid > 0 && isolatedUid != hostUid) { "Cell did not report a distinct isolated UID." }
        val entry = find(leaseId)
        check(entry.state == State.RESERVED) { "Only a reserved cell can be activated." }
        check(slots.filterNotNull().none { it !== entry && it.state == State.ACTIVE && it.isolatedUid == isolatedUid }) {
            "An active project already owns this isolated UID."
        }
        entry.isolatedUid = isolatedUid
        entry.state = State.ACTIVE
        entry.snapshot()
    }

    /** Called only from the bound service's Binder.DeathRecipient/disconnect recovery path. */
    fun markProcessDied(leaseId: String): Lease = synchronized(lock) {
        val entry = find(leaseId)
        check(entry.state == State.ACTIVE) { "Only an active cell can be marked dead." }
        entry.state = State.DEAD
        entry.snapshot()
    }

    /** Release a never-started reservation or a lease whose process death was observed. */
    fun release(leaseId: String): Boolean = synchronized(lock) {
        val entry = findOrNull(leaseId) ?: return false
        check(entry.state != State.ACTIVE) {
            "Cannot recycle an active cell before its process death is observed."
        }
        slots[entry.slot] = null
        true
    }

    fun snapshot(): List<Lease> = synchronized(lock) { slots.filterNotNull().map { it.snapshot() } }

    private fun find(id: String): Entry = findOrNull(id) ?: throw SecurityException("Unknown cell lease.")
    private fun findOrNull(id: String): Entry? = slots.filterNotNull().firstOrNull { it.id == id }
    private fun Entry.snapshot() = Lease(id, projectId, slot, isolatedUid, state)

    companion object {
        const val DEFAULT_CAPACITY = 4
        const val MAX_CAPACITY = 8
    }
}
