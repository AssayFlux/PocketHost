package com.assayflux.pockethost.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Imports a self-contained Node project; dependency installation and lifecycle subprocesses are not supported. */
object NodeProjectImporter {
    data class Report(val fileCount: Int, val totalBytes: Long, val projectDirectory: File, val entryFile: File)

    private const val MAX_FILES = 10_000
    private const val MAX_BYTES = 100L * 1024L * 1024L
    private const val MAX_DEPTH = 32

    fun importTree(context: Context, uri: Uri, siteId: String = "default"): Report {
        require(siteId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid Node project ID." }
        val source = DocumentFile.fromTreeUri(context, uri)
            ?: error("Cannot access the selected Node project folder.")
        require(source.isDirectory) { "Select a Node project folder." }
        val base = File(context.filesDir, "node-project-$siteId")
        val staging = File(context.filesDir, "node-project-$siteId-staging")
        val backup = File(context.filesDir, "node-project-$siteId-backup")
        staging.deleteRecursively()
        require(staging.mkdirs()) { "Cannot create Node project staging directory." }
        var count = 0
        var totalBytes = 0L
        try {
            fun copyDirectory(directory: DocumentFile, target: File, depth: Int) {
                require(depth <= MAX_DEPTH) { "Node project nesting exceeds $MAX_DEPTH levels." }
                for (child in directory.listFiles()) {
                    val name = child.name ?: error("A project entry has no filename.")
                    require(name != "." && name != ".." && !name.contains('/') && !name.contains('\\')) {
                        "Invalid project filename: $name"
                    }
                    val lower = name.lowercase()
                    require(lower !in setOf(".git", ".ssh", ".env", ".npmrc", ".pockethost") &&
                        !lower.startsWith(".env.") && !lower.endsWith(".pem") &&
                        !lower.endsWith(".key") && !lower.endsWith(".p12") && !lower.endsWith(".pfx")) {
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
                        require(count <= MAX_FILES) { "Node project exceeds $MAX_FILES files." }
                        FileOutputStream(destination).use { output ->
                            context.contentResolver.openInputStream(child.uri)?.use { input ->
                                val buffer = ByteArray(32 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    totalBytes += read
                                    require(totalBytes <= MAX_BYTES) { "Node project exceeds 100 MiB." }
                                    output.write(buffer, 0, read)
                                }
                            } ?: error("Cannot read project file: $name")
                        }
                    }
                }
            }
            copyDirectory(source, staging, 0)
            val entry = resolveEntry(staging)
            backup.deleteRecursively()
            if (base.exists()) require(base.renameTo(backup)) { "Cannot stage the previous Node project." }
            if (!staging.renameTo(base)) {
                if (backup.exists()) backup.renameTo(base)
                error("Cannot activate the imported Node project.")
            }
            backup.deleteRecursively()
            val activatedEntry = resolveEntry(base)
            return Report(count, totalBytes, base, activatedEntry)
        } catch (error: Throwable) {
            staging.deleteRecursively()
            if (!base.exists() && backup.exists()) backup.renameTo(base)
            throw error
        }
    }

    fun resolveEntry(root: File): File {
        val canonicalRoot = root.canonicalFile
        val packageFile = File(canonicalRoot, "package.json")
        require(packageFile.isFile) { "Node project must contain package.json." }
        val packageJson = JSONObject(packageFile.readText(Charsets.UTF_8))
        val start = packageJson.optJSONObject("scripts")?.optString("start").orEmpty()
        val scriptEntry = Regex("^(?:node\\s+)?([A-Za-z0-9_./-]+\\.m?js)$").matchEntire(start)?.groupValues?.get(1)
        val candidates = listOfNotNull(
            scriptEntry,
            packageJson.optString("main").takeIf { it.isNotBlank() },
            "server.js", "index.js", "app.js"
        ).distinct()
        val selected = candidates.firstNotNullOfOrNull { relative ->
            val candidate = File(canonicalRoot, relative).canonicalFile
            if (candidate.toPath().startsWith(canonicalRoot.toPath()) && candidate.isFile) candidate else null
        } ?: error("Could not find a safe Node entry file. Add a main file such as server.js and set package.json main.")
        return selected
    }
}
