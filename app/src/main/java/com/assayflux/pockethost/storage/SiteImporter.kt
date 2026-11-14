package com.assayflux.pockethost.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipInputStream

data class ImportReport(val fileCount: Int, val totalBytes: Long, val source: String)

object SiteImporter {
    private const val MAX_FILES = 5_000
    private const val MAX_ENTRIES = 10_000
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
        return deploy(context) { staging, counter ->
            copyDirectory(context, source, staging, staging.canonicalFile, counter, 0)
        }.copy(source = "Folder")
    }

    fun importZip(context: Context, zipUri: Uri): ImportReport {
        val stagingBase = File(context.filesDir, "site-staging-${UUID.randomUUID()}")
        if (!stagingBase.mkdirs()) throw IOException("Could not create a staging directory.")
        val counter = Counter()
        try {
            val input = context.contentResolver.openInputStream(zipUri)
                ?: throw IOException("The selected ZIP file cannot be opened.")
            input.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        counter.entries++
                        if (counter.entries > MAX_ENTRIES) throw IOException("ZIP contains too many entries (limit: $MAX_ENTRIES).")
                        val normalized = entry.name.replace('\\', '/')
                        if (normalized.startsWith('/') || normalized.startsWith("../") ||
                            normalized.split('/').any { it == ".." || it.isBlank() && it != normalized.substringAfterLast('/') }) {
                            throw IOException("ZIP contains an unsafe path.")
                        }
                        val parts = normalized.split('/').filter { it.isNotEmpty() }
                        if (parts.isEmpty() || parts.size > MAX_DEPTH + 1) throw IOException("ZIP folder nesting is too deep.")
                        parts.forEach(::validateName)
                        val target = File(stagingBase, parts.joinToString(File.separator))
                        if (!target.canonicalFile.toPath().startsWith(stagingBase.canonicalFile.toPath())) {
                            throw IOException("ZIP path escapes the import directory.")
                        }
                        if (entry.isDirectory || normalized.endsWith("/")) {
                            if (!target.exists() && !target.mkdirs()) throw IOException("Could not create ZIP folder.")
                        } else {
                            target.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw IOException("Could not create ZIP folder.") }
                            counter.files++
                            if (counter.files > MAX_FILES) throw IOException("ZIP contains too many files (limit: $MAX_FILES).")
                            target.outputStream().buffered().use { output ->
                                val buffer = ByteArray(32 * 1024)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    counter.bytes += count
                                    if (counter.bytes > MAX_BYTES) throw IOException("Uncompressed website exceeds the 50 MB import limit.")
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
            val siteRoot = resolveZipRoot(stagingBase)
            if (!File(siteRoot, "index.html").isFile) throw IOException("ZIP must contain index.html at its root (or inside one top-level folder).")
            activate(context, stagingBase, siteRoot)
            return ImportReport(counter.files, counter.bytes, "ZIP")
        } catch (error: Throwable) {
            stagingBase.deleteRecursively()
            throw error
        }
    }

    private fun resolveZipRoot(staging: File): File {
        if (File(staging, "index.html").isFile) return staging
        val children = staging.listFiles()?.filter { it.name != "__MACOSX" } ?: emptyList()
        if (children.size == 1 && children[0].isDirectory && File(children[0], "index.html").isFile) {
            return children[0]
        }
        return staging
    }

    private fun deploy(context: Context, copy: (File, Counter) -> Unit): ImportReport {
        val staging = File(context.filesDir, "site-staging-${UUID.randomUUID()}")
        if (!staging.mkdirs()) throw IOException("Could not create a staging directory.")
        val counter = Counter()
        try {
            copy(staging, counter)
            if (counter.files == 0) throw IOException("The selected folder is empty.")
            if (!File(staging, "index.html").isFile) throw IOException("No index.html was found at the selected folder's root.")
            activate(context, staging, staging)
            return ImportReport(counter.files, counter.bytes, "Folder")
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun activate(context: Context, stagingBase: File, siteRoot: File) {
        val active = File(context.filesDir, "site")
        val releases = File(context.filesDir, "site-releases")
        if (!releases.exists() && !releases.mkdirs()) throw IOException("Could not create deployment history.")
        val replacement = if (siteRoot == stagingBase) stagingBase else {
            val normalized = File(context.filesDir, "site-normalized-${UUID.randomUUID()}")
            if (!siteRoot.renameTo(normalized)) throw IOException("Could not prepare ZIP website root.")
            stagingBase.deleteRecursively()
            normalized
        }
        val previous = File(releases, "release-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}")
        val hadActive = active.exists()
        if (hadActive && !active.renameTo(previous)) throw IOException("Could not preserve the currently deployed website.")
        if (!replacement.renameTo(active)) {
            if (hadActive) previous.renameTo(active)
            throw IOException("Could not activate the imported website. The previous site was preserved.")
        }
        pruneReleases(releases)
    }

    fun listReleases(context: Context): List<String> =
        File(context.filesDir, "site-releases").listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("release-") }
            ?.sortedByDescending { it.name }
            ?.map { it.name } ?: emptyList()

    fun rollbackLatest(context: Context): String {
        val releases = File(context.filesDir, "site-releases")
        val latest = releases.listFiles()?.filter { it.isDirectory && it.name.startsWith("release-") }
            ?.maxByOrNull { it.name } ?: throw IOException("There is no previous deployment to restore.")
        val active = File(context.filesDir, "site")
        val displaced = File(releases, "release-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}")
        if (!active.exists() || !active.renameTo(displaced)) throw IOException("Could not preserve the current deployment.")
        if (!latest.renameTo(active)) {
            displaced.renameTo(active)
            throw IOException("Rollback failed. The current deployment was restored.")
        }
        pruneReleases(releases)
        return latest.name
    }

    private fun pruneReleases(directory: File) {
        directory.listFiles()?.filter { it.isDirectory && it.name.startsWith("release-") }
            ?.sortedByDescending { it.name }
            ?.drop(5)?.forEach { it.deleteRecursively() }
    }

    private class Counter(var files: Int = 0, var entries: Int = 0, var bytes: Long = 0)

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
            counter.entries++
            if (counter.entries > MAX_ENTRIES) throw IOException("Website contains too many files and folders (limit: $MAX_ENTRIES).")
            val name = entry.name ?: throw IOException("A selected item has no usable filename.")
            validateName(name)
            val target = File(destination, name)
            if (!target.canonicalFile.toPath().startsWith(stagingRoot.toPath())) throw IOException("An unsafe file path was detected.")
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
        if (name.isBlank() || name == "." || name == ".." || name.contains('/') ||
            name.contains('\\') || name.contains('\u0000')) throw IOException("An unsafe filename was found.")
        val lower = name.lowercase()
        if (lower in blockedNames || lower == ".env" || lower.startsWith(".env.") ||
            lower.endsWith(".pem") || lower.endsWith(".key") || lower.endsWith(".p12") || lower.endsWith(".pfx")) {
            throw IOException("The import contains a sensitive file or directory: $name")
        }
    }

    private fun copyFile(context: Context, source: DocumentFile, target: File, counter: Counter) {
        counter.files++
        if (counter.files > MAX_FILES) throw IOException("The website contains too many files (limit: $MAX_FILES).")
        val input = context.contentResolver.openInputStream(source.uri) ?: throw IOException("Could not read ${source.name}.")
        input.use { stream ->
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    counter.bytes += read
                    if (counter.bytes > MAX_BYTES) throw IOException("Website exceeds the 50 MB import limit.")
                    output.write(buffer, 0, read)
                }
            }
        }
    }
}
