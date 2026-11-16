package com.assayflux.pockethost.runtime

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Host-side file capability broker for isolated runtime cells.
 *
 * A cell receives only descriptors for individual files selected by the host. It is
 * never given a project directory descriptor or a host filesystem path. Every path
 * component is opened relative to an already-open directory with O_NOFOLLOW, avoiding
 * the check-then-open symlink race of canonical-path validation alone.
 */
object RuntimeProjectFileBroker {
    private const val MAX_BROKERED_FILE_BYTES = 64L * 1024L * 1024L

    fun openReadOnly(projectRoot: File, relativePath: String): ParcelFileDescriptor =
        openRegularFile(projectRoot, relativePath, OsConstants.O_RDONLY)

    fun openWriteOnly(projectRoot: File, relativePath: String): ParcelFileDescriptor =
        openRegularFile(projectRoot, relativePath, OsConstants.O_WRONLY, truncate = true)

    private fun openRegularFile(
        projectRoot: File,
        relativePath: String,
        accessMode: Int,
        truncate: Boolean = false
    ): ParcelFileDescriptor {
        require(relativePath.isNotBlank()) { "Project file path is empty." }
        val normalized = relativePath.replace(92.toChar(), '/')
        require(!normalized.startsWith('/') && !File(normalized).isAbsolute) {
            "Absolute project file paths are not permitted."
        }
        val segments = normalized.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) {
            "Project file path contains traversal or empty segments."
        }

        val root = projectRoot.canonicalFile
        require(root.isDirectory) { "Project root is not a directory." }

        var directoryFd = Os.open(
            root.path,
            OsConstants.O_RDONLY or OsConstants.O_DIRECTORY or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC,
            0
        )
        try {
            for (segment in segments.dropLast(1)) {
                val nextFd = Os.openat(
                    directoryFd,
                    segment,
                    OsConstants.O_RDONLY or OsConstants.O_DIRECTORY or
                        OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC,
                    0
                )
                Os.close(directoryFd)
                directoryFd = nextFd
            }

            val fileFd = Os.openat(
                directoryFd,
                segments.last(),
                accessMode or OsConstants.O_NOFOLLOW or
                    OsConstants.O_CLOEXEC or OsConstants.O_NONBLOCK,
                0
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
