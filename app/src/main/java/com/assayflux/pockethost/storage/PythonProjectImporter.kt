package com.assayflux.pockethost.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

object PythonProjectImporter {
    data class Report(val fileCount: Int, val totalBytes: Long, val projectDirectory: File)

    private const val MAX_FILES = 5_000
    private const val MAX_BYTES = 50L * 1024L * 1024L
    private const val MAX_DEPTH = 32

    fun importTree(context: Context, uri: Uri): Report {
        val source = DocumentFile.fromTreeUri(context, uri)
            ?: error("Cannot access the selected Python project folder.")
        require(source.isDirectory) { "Select a Python project folder." }

        val base = File(context.filesDir, "python-project")
        val staging = File(context.filesDir, "python-project-staging")
        val backup = File(context.filesDir, "python-project-backup")
        staging.deleteRecursively()
        require(staging.mkdirs()) { "Cannot create Python project staging directory." }

        var count = 0
        var totalBytes = 0L
        try {
            fun copyDirectory(directory: DocumentFile, target: File, depth: Int) {
                require(depth <= MAX_DEPTH) { "Python project folder nesting exceeds $MAX_DEPTH levels." }
                for (child in directory.listFiles()) {
                    val name = child.name ?: error("A project entry has no filename.")
                    require(name != "." && name != ".." && !name.contains('/') && !name.contains('\\')) {
                        "Invalid project filename: $name"
                    }
                    require(name != ".git" && name != ".ssh" && !name.startsWith(".env") &&
                        !name.endsWith(".pem", true) && !name.endsWith(".key", true) &&
                        !name.endsWith(".p12", true) && !name.endsWith(".pfx", true)) {
                        "Sensitive file or directory is not allowed: $name"
                    }
                    val destination = File(target, name).canonicalFile
                    require(destination.toPath().startsWith(target.canonicalFile.toPath())) {
                        "Project entry escapes the selected directory."
                    }
                    if (child.isDirectory) {
                        require(destination.mkdirs() || destination.isDirectory) { "Cannot create $name" }
                        copyDirectory(child, destination, depth + 1)
                    } else if (child.isFile) {
                        count++
                        require(count <= MAX_FILES) { "Python project exceeds $MAX_FILES files." }
                        val output = FileOutputStream(destination)
                        try {
                            context.contentResolver.openInputStream(child.uri)?.use { input ->
                                val buffer = ByteArray(32 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    totalBytes += read
                                    require(totalBytes <= MAX_BYTES) { "Python project exceeds 50 MiB." }
                                    output.write(buffer, 0, read)
                                }
                            } ?: error("Cannot read project file: $name")
                        } finally {
                            output.close()
                        }
                    }
                }
            }

            copyDirectory(source, staging, 0)
            require(File(staging, "wsgi.py").isFile) {
                "Python project must contain wsgi.py at its root."
            }
            require(count > 0) { "Python project is empty." }

            backup.deleteRecursively()
            if (base.exists()) require(base.renameTo(backup)) { "Cannot stage the previous Python project." }
            if (!staging.renameTo(base)) {
                if (backup.exists()) backup.renameTo(base)
                error("Cannot activate the imported Python project.")
            }
            backup.deleteRecursively()
            return Report(count, totalBytes, base)
        } catch (error: Throwable) {
            staging.deleteRecursively()
            if (!base.exists() && backup.exists()) backup.renameTo(base)
            throw error
        }
    }
}
