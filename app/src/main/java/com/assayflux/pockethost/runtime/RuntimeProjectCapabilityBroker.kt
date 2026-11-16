package com.assayflux.pockethost.runtime

import android.os.ParcelFileDescriptor
import java.io.File
import java.security.SecureRandom

/**
 * In-memory trusted broker for per-project filesystem capabilities.
 *
 * Tokens are random, process-local handles. Project paths remain in the host process;
 * callers receive only a descriptor for the single requested regular file.
 */
class RuntimeProjectCapabilityBroker {
    private data class Grant(val projectId: String, val root: File)
    private val grants = LinkedHashMap<String, Grant>()
    private val random = SecureRandom()

    @Synchronized
    fun registerProject(projectId: String, projectRoot: File): String {
        require(projectId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid project identifier." }
        val root = projectRoot.absoluteFile.toPath().normalize().toFile()
        require(root.isDirectory) { "Project root must already exist and be a directory." }
        check(grants.values.none { it.projectId == projectId }) { "Project already has an active filesystem grant." }
        val token = ByteArray(32).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        grants[token] = Grant(projectId, root)
        return token
    }

    @Synchronized
    fun openReadOnly(capability: String, relativePath: String): ParcelFileDescriptor =
        grant(capability).let { RuntimeProjectFileBroker.openReadOnly(it.root, relativePath) }

    @Synchronized
    fun writeBytes(capability: String, relativePath: String, content: ByteArray, append: Boolean = false): Long =
        grant(capability).let { RuntimeProjectFileBroker.writeBytes(it.root, relativePath, content, append) }

    @Synchronized
    fun revoke(capability: String): Boolean = grants.remove(capability) != null

    @Synchronized
    fun revokeProject(projectId: String): Int {
        val tokens = grants.filterValues { it.projectId == projectId }.keys.toList()
        tokens.forEach(grants::remove)
        return tokens.size
    }

    @Synchronized
    fun activeGrantCount(): Int = grants.size

    private fun grant(capability: String): Grant {
        require(capability.length == 64) { "Invalid filesystem capability." }
        return grants[capability] ?: throw SecurityException("Filesystem capability is expired or revoked.")
    }
}
