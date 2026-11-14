package com.assayflux.pockethost.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.util.UUID

data class ImportReport(val fileCount: Int, val totalBytes: Long)

object SiteImporter {
    private const val MAX_FILES = 5_000
    private const val MAX_BYTES = 50L * 1024L * 1024L
    private const val MAX_DEPTH = 32
    private val blockedNames = setOf(
        ".git", ".ssh", ".pockethost", "id_rsa", "id_ed25519",
        "authorized_keys", "known_hosts"
    )

    fun importTree(context: Context, treeUri: Uri): ImportReport {
        val source = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("The selected folder cannot be opened.")
        if (!source.isDirectory) throw IOException("Please select a website folder.")

        val base = context.filesDir
        val staging = File(base, "site-staging-${UUID.randomUUID()}")
        val active = File(base, "site")
        val backup = File(base, "site-backup-${UUID.randomUUID()}")
        if (!staging.mkdirs()) throw IOException("Could not create a staging directory.")

        val counter = Counter()
        try {
            copyDirectory(context, source, staging, staging.canonicalFile, counter, 0)
            if (counter.files == 0) throw IOException("The selected folder is empty.")
            if (!File(staging, "index.html").isFile) {
                throw IOException("No index.html was found at the selected folder's root.")
            }

            val hadActive = active.exists()
            if (hadActive && !active.renameTo(backup)) {
                throw IOException("Could not preserve the currently deployed website.")
            }
            if (!staging.renameTo(active)) {
                if (hadActive) backup.renameTo(active)
                throw IOException("Could not activate the imported website. The previous site was preserved.")
            }
            if (backup.exists()) backup.deleteRecursively()
            return ImportReport(counter.files, counter.bytes)
        } catch (error: Throwable) {
            if (staging.exists()) staging.deleteRecursively()
            if (!active.exists() && backup.exists()) backup.renameTo(active)
            throw error
        }
    }

    private class Counter(var files: Int = 0, var bytes: Long = 0)

    private fun copyDirectory(
        context: Context,
        source: DocumentFile,
        destination: File,
        stagingRoot: File,
        counter: Counter,
        depth: Int
    ) {
        if (depth > MAX_DEPTH) throw IOException("Website folder nesting is too deep.")
        for (entry in source.listFiles()) {
            val name = entry.name ?: throw IOException("A selected item has no usable filename.")
            validateName(name)
            val target = File(destination, name)
            if (!target.canonicalFile.toPath().startsWith(stagingRoot.toPath())) {
                throw IOException("An unsafe file path was detected.")
            }
            when {
                entry.isDirectory -> {
                    if (!target.exists() && !target.mkdir()) throw IOException("Could not create folder: $name")
                    copyDirectory(context, entry, target, stagingRoot, counter, depth + 1)
                }
                entry.isFile -> copyFile(context, entry, target, counter)
                else -> throw IOException("Unsupported item in website folder: $name")
            }
        }
    }

    private fun validateName(name: String) {
        if (name.isBlank() || name == "." || name == ".." ||
            name.contains('/') || name.contains('\\') || name.contains('\u0000')) {
            throw IOException("An unsafe filename was found.")
        }
        val lower = name.lowercase()
        if (lower in blockedNames || lower == ".env" || lower.startsWith(".env.") ||
            lower.endsWith(".pem") || lower.endsWith(".key") ||
            lower.endsWith(".p12") || lower.endsWith(".pfx")) {
            throw IOException("The import contains a sensitive file or directory: $name")
        }
    }

    private fun copyFile(context: Context, source: DocumentFile, target: File, counter: Counter) {
        counter.files += 1
        if (counter.files > MAX_FILES) throw IOException("The website contains too many files (limit: $MAX_FILES).")
        val input = context.contentResolver.openInputStream(source.uri)
            ?: throw IOException("Could not read ${source.name}.")
        input.use { stream ->
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    counter.bytes += read
                    if (counter.bytes > MAX_BYTES) {
                        throw IOException("Website exceeds the 50 MB import limit.")
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
    }
}
