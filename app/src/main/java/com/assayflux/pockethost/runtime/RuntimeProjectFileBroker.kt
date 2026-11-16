package com.assayflux.pockethost.runtime

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Host-side, descriptor-only project filesystem broker.
 *
 * Each path component is resolved relative to an open directory with O_NOFOLLOW.
 * When a capability is used, the root directory's device/inode identity is checked
 * against the identity captured at grant issuance, preventing path replacement from
 * silently retargeting an existing capability to a different directory.
 */
object RuntimeProjectFileBroker {
    data class RootIdentity internal constructor(val device: Long, val inode: Long)

    const val MAX_BROKERED_FILE_BYTES = 64L * 1024L * 1024L
    const val MAX_BROKER_WRITE_BYTES = 1024 * 1024

    fun captureRootIdentity(projectRoot: File): RootIdentity {
        val flags = OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC or OsConstants.O_NONBLOCK or OsConstants.O_NONBLOCK
        val fd = Os.open(projectRoot.absoluteFile.toPath().normalize().toString(), flags, 0)
        try {
            val stat = Os.fstat(fd)
            require((stat.st_mode and OsConstants.S_IFMT) == OsConstants.S_IFDIR) {
                "Project root is not a directory."
            }
            return RootIdentity(stat.st_dev, stat.st_ino)
        } finally {
            Os.close(fd)
        }
    }

    fun openReadOnly(
        projectRoot: File,
        relativePath: String,
        expectedRootIdentity: RootIdentity? = null
    ): ParcelFileDescriptor =
        openRegularFile(projectRoot, relativePath, OsConstants.O_RDONLY, expectedRootIdentity = expectedRootIdentity)

    /**
     * Legacy probe-only writable descriptor. Never pass this to project code: descriptor
     * writes cannot be byte-quota enforced after transfer. Production writes use writeBytes.
     */
    @Deprecated("Probe-only. Use RuntimeProjectCapabilityBroker.writeBytes for bounded writes.")
    fun openWriteOnly(projectRoot: File, relativePath: String): ParcelFileDescriptor =
        openRegularFile(projectRoot, relativePath, OsConstants.O_WRONLY, truncate = true)

    /** Bounded broker-mediated write. Parent directories must already exist. */
    @Synchronized
    fun writeBytes(
        projectRoot: File,
        relativePath: String,
        content: ByteArray,
        append: Boolean = false,
        expectedRootIdentity: RootIdentity? = null
    ): Long {
        require(content.size <= MAX_BROKER_WRITE_BYTES) { "Write chunk exceeds the broker limit." }
        val segments = safeSegments(relativePath)
        val rootPath = projectRoot.absoluteFile.toPath().normalize().toString()
        val directoryFlags = OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC
        var directoryFd = Os.open(rootPath, directoryFlags, 0)
        try {
            verifyRootIdentity(Os.fstat(directoryFd), expectedRootIdentity)
            for (segment in segments.dropLast(1)) {
                val nextFd = openChildDirectory(directoryFd, segment, directoryFlags)
                Os.close(directoryFd)
                directoryFd = nextFd
            }
            val fileFd = openChildFile(directoryFd, segments.last(), OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_NOFOLLOW or
                    OsConstants.O_CLOEXEC or OsConstants.O_NONBLOCK or
                    (if (append) OsConstants.O_APPEND else 0),
                OsConstants.S_IRUSR or OsConstants.S_IWUSR
            )
            try {
                val metadata = Os.fstat(fileFd)
                require((metadata.st_mode and OsConstants.S_IFMT) == OsConstants.S_IFREG) {
                    "Only regular project files may be written."
                }
                val resultingSize = if (append) metadata.st_size + content.size else content.size.toLong()
                require(resultingSize <= MAX_BROKERED_FILE_BYTES) {
                    "Project file would exceed the 64 MiB broker limit."
                }
                if (!append) Os.ftruncate(fileFd, 0L)
                var offset = 0
                while (offset < content.size) {
                    val written = Os.write(fileFd, content, offset, content.size - offset)
                    check(written > 0) { "Project file write made no progress." }
                    offset += written
                }
                Os.fsync(fileFd)
                resultingSize
            } finally {
                Os.close(fileFd)
            }
        } finally {
            Os.close(directoryFd)
        }
    }

    private fun openRegularFile(
        projectRoot: File,
        relativePath: String,
        accessMode: Int,
        truncate: Boolean = false,
        expectedRootIdentity: RootIdentity? = null
    ): ParcelFileDescriptor {
        val segments = safeSegments(relativePath)
        val rootPath = projectRoot.absoluteFile.toPath().normalize().toString()
        val directoryFlags = OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC
        var directoryFd = Os.open(rootPath, directoryFlags, 0)
        try {
            verifyRootIdentity(Os.fstat(directoryFd), expectedRootIdentity)
            for (segment in segments.dropLast(1)) {
                val nextFd = openChildDirectory(directoryFd, segment, directoryFlags)
                Os.close(directoryFd)
                directoryFd = nextFd
            }

            val flags = accessMode or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC or OsConstants.O_NONBLOCK
            val fileFd = openChildFile(directoryFd, segments.last(), flags,
                OsConstants.S_IRUSR or OsConstants.S_IWUSR
            )
            try {
                val metadata = Os.fstat(fileFd)
                require((metadata.st_mode and OsConstants.S_IFMT) == OsConstants.S_IFREG) {
                    "Only regular project files may be brokered."
                }
                require(metadata.st_size <= MAX_BROKERED_FILE_BYTES) {
                    "Project file exceeds the broker transfer limit."
                }
                if (truncate) Os.ftruncate(fileFd, 0L)
                return ParcelFileDescriptor.adoptFd(fileFd)
            } catch (error: Throwable) {
                runCatching { Os.close(fileFd) }
                throw error
            }
        } finally {
            Os.close(directoryFd)
        }
    }

    private fun openChildDirectory(
        parentFd: java.io.FileDescriptor,
        name: String,
        flags: Int
    ): java.io.FileDescriptor = ParcelFileDescriptor.dup(parentFd).use { parent ->
        val child = Os.open("/proc/self/fd/${parent.fd}/${name}", flags, 0)
        try {
            val stat = Os.fstat(child)
            require((stat.st_mode and OsConstants.S_IFMT) == OsConstants.S_IFDIR) {
                "A project path component is not a directory."
            }
            child
        } catch (error: Throwable) {
            Os.close(child)
            throw error
        }
    }

    private fun openChildFile(
        parentFd: java.io.FileDescriptor,
        name: String,
        flags: Int,
        mode: Int
    ): java.io.FileDescriptor = ParcelFileDescriptor.dup(parentFd).use { parent ->
        Os.open("/proc/self/fd/${parent.fd}/${name}", flags, mode)
    }

    private fun verifyRootIdentity(stat: android.system.StructStat, expected: RootIdentity?) {
        require((stat.st_mode and OsConstants.S_IFMT) == OsConstants.S_IFDIR) {
            "Project root is not a directory."
        }
        if (expected != null && (stat.st_dev != expected.device || stat.st_ino != expected.inode)) {
            throw SecurityException("Project root changed after its filesystem capability was issued.")
        }
    }

    private fun safeSegments(relativePath: String): List<String> {
        val normalized = relativePath.replace(92.toChar(), '/')
        require(!normalized.contains(0.toChar())) { "Project file path contains a NUL byte." }
        require(normalized.isNotBlank() && !normalized.startsWith('/') && !File(normalized).isAbsolute) {
            "Absolute or empty project paths are not permitted."
        }
        val segments = normalized.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) {
            "Project file path contains traversal or empty segments."
        }
        return segments
    }
}
