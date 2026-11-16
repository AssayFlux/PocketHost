package com.assayflux.pockethost.runtime

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Host-side, descriptor-only project filesystem broker.
 *
 * The cell receives a descriptor for one regular file at a time, never a directory
 * descriptor or a host path. Every component is resolved relative to an open directory
 * using O_NOFOLLOW, so a project cannot swap a symlink between validation and open.
 */
object RuntimeProjectFileBroker {
    const val MAX_BROKERED_FILE_BYTES = 64L * 1024L * 1024L
    const val MAX_BROKER_WRITE_BYTES = 1024 * 1024

    fun openReadOnly(projectRoot: File, relativePath: String): ParcelFileDescriptor =
        openRegularFile(projectRoot, relativePath, OsConstants.O_RDONLY)

    /** Truncates an existing regular file; it never creates missing files. */
    fun openWriteOnly(projectRoot: File, relativePath: String): ParcelFileDescriptor =
        openRegularFile(projectRoot, relativePath, OsConstants.O_WRONLY, truncate = true)

    /** Creates a new file if absent, or truncates an existing regular file. */
    fun openCreateOrTruncate(projectRoot: File, relativePath: String): ParcelFileDescriptor =
        openRegularFile(projectRoot, relativePath, OsConstants.O_WRONLY, create = true, truncate = true)

    /** Creates a file if absent and positions writes at its end without truncating it. */
    fun openAppendOnly(projectRoot: File, relativePath: String): ParcelFileDescriptor =
        openRegularFile(projectRoot, relativePath, OsConstants.O_WRONLY, create = true, append = true)


    /**
     * Bounded broker-mediated write. Unlike handing an untrusted cell a writable FD,
     * this enforces both a per-call byte cap and the resulting project-file size cap.
     * Parent directories must already exist.
     */
    @Synchronized
    fun writeBytes(
        projectRoot: File,
        relativePath: String,
        content: ByteArray,
        append: Boolean = false
    ): Long {
        require(content.size <= MAX_BROKER_WRITE_BYTES) { "Write chunk exceeds the broker limit." }
        val normalized = relativePath.replace(92.toChar(), '/')
        require(normalized.isNotBlank() && !normalized.startsWith('/') && !File(normalized).isAbsolute) {
            "Absolute or empty project paths are not permitted."
        }
        val segments = normalized.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) {
            "Project file path contains traversal or empty segments."
        }
        val rootPath = projectRoot.absoluteFile.toPath().normalize().toString()
        val directoryFlags = OsConstants.O_RDONLY or OsConstants.O_DIRECTORY or
            OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC
        var directoryFd = Os.open(rootPath, directoryFlags, 0)
        try {
            for (segment in segments.dropLast(1)) {
                val nextFd = Os.openat(directoryFd, segment, directoryFlags, 0)
                Os.close(directoryFd)
                directoryFd = nextFd
            }
            val fileFd = Os.openat(
                directoryFd,
                segments.last(),
                OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_NOFOLLOW or
                    OsConstants.O_CLOEXEC or OsConstants.O_NONBLOCK or
                    if (append) OsConstants.O_APPEND else 0,
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
        create: Boolean = false,
        truncate: Boolean = false,
        append: Boolean = false
    ): ParcelFileDescriptor {
        val normalized = relativePath.replace(92.toChar(), '/')
        require(normalized.isNotBlank() && !normalized.startsWith('/') && !File(normalized).isAbsolute) {
            "Absolute or empty project paths are not permitted."
        }
        val segments = normalized.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) {
            "Project file path contains traversal or empty segments."
        }

        // Do not canonicalize the root: that would silently follow a root symlink.
        val rootPath = projectRoot.absoluteFile.toPath().normalize().toString()
        val directoryFlags = OsConstants.O_RDONLY or OsConstants.O_DIRECTORY or
            OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC
        var directoryFd = Os.open(rootPath, directoryFlags, 0)
        try {
            val rootMetadata = Os.fstat(directoryFd)
            require((rootMetadata.st_mode and OsConstants.S_IFMT) == OsConstants.S_IFDIR) {
                "Project root is not a directory."
            }
            for (segment in segments.dropLast(1)) {
                val nextFd = Os.openat(directoryFd, segment, directoryFlags, 0)
                Os.close(directoryFd)
                directoryFd = nextFd
            }

            var flags = accessMode or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC or OsConstants.O_NONBLOCK
            if (create) flags = flags or OsConstants.O_CREAT
            if (append) flags = flags or OsConstants.O_APPEND
            val fileFd = Os.openat(
                directoryFd,
                segments.last(),
                flags,
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
}
