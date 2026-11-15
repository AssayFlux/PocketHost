package com.assayflux.pockethost.runtime

import android.content.Context
import java.io.File

/**
 * Runtime entry points may execute project code with the app UID. Keep project roots
 * inside app-private storage so a malformed local/remote request cannot point the
 * interpreter at arbitrary filesystem locations. Canonical paths resolve symlinks.
 */
object RuntimeProjectRoot {
    fun requireOwnedProjectRoot(context: Context, path: String): File {
        require(path.isNotBlank()) { "Runtime project directory is required." }
        val candidate = File(path).canonicalFile
        require(candidate.isDirectory) { "Runtime project directory does not exist." }

        val allowedRoots = listOf(
            context.filesDir,
            context.cacheDir,
            context.noBackupFilesDir,
            context.codeCacheDir
        ).map { it.canonicalFile.toPath() }

        val candidatePath = candidate.toPath()
        val isPrivateDescendant = allowedRoots.any { root ->
            candidatePath != root && candidatePath.startsWith(root)
        }
        require(isPrivateDescendant) {
            "Runtime projects must be stored inside PocketHost's private app directories. Re-import the project through PocketHost."
        }
        return candidate
    }
}
