package com.assayflux.pockethost.runtime

import android.os.ParcelFileDescriptor
import java.io.File
import java.nio.file.Files

/**
 * Host-side file capability broker for isolated runtime cells.
 *
 * A cell receives only descriptors for individual files selected by the host. It is
 * never given a project directory descriptor or a host filesystem path.
 */
object RuntimeProjectFileBroker {
    private const val MAX_BROKERED_FILE_BYTES = 64L * 1024L * 1024L

    fun openReadOnly(projectRoot: File, relativePath: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(resolveRegularFile(projectRoot, relativePath), ParcelFileDescriptor.MODE_READ_ONLY)

    fun openWriteOnly(projectRoot: File, relativePath: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(
            resolveRegularFile(projectRoot, relativePath),
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE
        )

    private fun resolveRegularFile(projectRoot: File, relativePath: String): File {
        require(relativePath.isNotBlank()) { "Project file path is empty." }
        val normalized = relativePath.replace('\\', '/')
        require(!normalized.startsWith('/') && !File(relativePath).isAbsolute) {
            "Absolute project file paths are not permitted."
        }
        val segments = normalized.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) {
            "Project file path contains traversal or empty segments."
        }
        val root = projectRoot.canonicalFile
        require(root.isDirectory) { "Project root is not a directory." }
        var candidate = root
        for (segment in segments) {
            candidate = File(candidate, segment)
            require(!Files.isSymbolicLink(candidate.toPath())) {
                "Symbolic links are not permitted in brokered project paths."
            }
        }
        val canonical = candidate.canonicalFile
        require(canonical.toPath().startsWith(root.toPath()) && canonical.isFile) {
            "Project file is missing or escapes its project root."
        }
        require(canonical.length() <= MAX_BROKERED_FILE_BYTES) {
            "Project file exceeds the broker transfer limit."
        }
        return canonical
    }
}
