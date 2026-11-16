package com.assayflux.pockethost.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FilterInputStream
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
        val input = context.contentResolver.openInputStream(zipUri)
            ?: throw IOException("The selected ZIP file cannot be opened.")
        return input.use { importZipStream(context, it) }
    }

    /** Imports a ZIP stream after any caller-managed decryption or transport decoding. */
    fun importZipStream(context: Context, input: java.io.InputStream): ImportReport {
        val stagingBase = File(context.filesDir, "site-staging-${UUID.randomUUID()}")
        if (!stagingBase.mkdirs()) throw IOException("Could not create a staging directory.")
        val counter = Counter()
        try {
            ZipInputStream(CappedInputStream(input, MAX_BYTES).buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        counter.entries++
                        if (counter.entries > MAX_ENTRIES) throw IOException("ZIP contains too many entries (limit: $MAX_ENTRIES).")
                        val normalized = entry.name.replace('\\', '/')
                        val pathParts = normalized.split('/')
                        if (normalized.startsWith('/') || normalized.contains('\u0000') ||
                            pathParts.any { it == ".." || it == "." || (it.isBlank() && it != pathParts.last()) }) {
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

    /**
     * Activates a directory already extracted and validated by another trusted importer.
     * The source must be an app-private staging directory on the same filesystem.
     */
    fun activatePreparedDirectory(context: Context, preparedRoot: File): ImportReport {
        val root = preparedRoot.canonicalFile
        val privateRoot = context.filesDir.canonicalFile.toPath()
        if (!root.isDirectory || !root.toPath().startsWith(privateRoot) ||
            root == File(context.filesDir, "site").canonicalFile) {
            throw IOException("Prepared website directory is not a safe app-private staging folder.")
        }
        val counter = Counter()
        fun validateDirectory(directory: File, depth: Int) {
            if (depth > MAX_DEPTH) throw IOException("Website folder nesting is too deep.")
            val children = directory.listFiles() ?: throw IOException("Could not inspect prepared website files.")
            for (child in children) {
                validateName(child.name)
                if (java.nio.file.Files.isSymbolicLink(child.toPath())) {
                    throw IOException("Prepared website contains an unsupported symbolic link.")
                }
                if (!child.canonicalFile.toPath().startsWith(root.toPath())) {
                    throw IOException("Prepared website contains a path outside its staging directory.")
                }
                counter.entries++
                if (counter.entries > MAX_ENTRIES) throw IOException("Website contains too many entries.")
                if (child.isDirectory) {
                    validateDirectory(child, depth + 1)
                } else if (child.isFile) {
                    counter.files++
                    counter.bytes += child.length()
                    if (counter.files > MAX_FILES || counter.bytes > MAX_BYTES) {
                        throw IOException("Prepared website exceeds the import safety limits.")
                    }
                } else {
                    throw IOException("Prepared website contains an unsupported filesystem entry.")
                }
            }
        }
        validateDirectory(root, 0)
        if (counter.files == 0 || !File(root, "index.html").isFile) {
            throw IOException("Prepared website must contain index.html at its root.")
        }
        activate(context, root, root)
        return ImportReport(counter.files, counter.bytes, "Full backup")
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

    /**
     * Recovers the last-known-good deployment if the process or device stopped
     * between moving the active site aside and activating its replacement.
     * Invalid active data is preserved in the release directory for diagnosis.
     */
    private fun isSafeRecoveryRelease(root: File): Boolean {
        val noFollow = java.nio.file.LinkOption.NOFOLLOW_LINKS
        if (!java.nio.file.Files.isDirectory(root.toPath(), noFollow) ||
            !java.nio.file.Files.isRegularFile(File(root, "index.html").toPath(), noFollow)) return false
        return runCatching {
            java.nio.file.Files.walk(root.toPath()).use { paths ->
                paths.allMatch { path ->
                    !java.nio.file.Files.isSymbolicLink(path) &&
                        (java.nio.file.Files.isDirectory(path, noFollow) ||
                            java.nio.file.Files.isRegularFile(path, noFollow))
                }
            }
        }.getOrDefault(false)
    }

    fun recoverInterruptedActivation(context: Context): Boolean {
        val active = File(context.filesDir, "site")
        if (isSafeRecoveryRelease(active)) return false
        val noFollow = java.nio.file.LinkOption.NOFOLLOW_LINKS
        val activeExists = java.nio.file.Files.exists(active.toPath(), noFollow)
        val releases = File(context.filesDir, "site-releases")
        val latest = releases.listFiles()
            ?.filter { it.name.startsWith("release-") && isSafeRecoveryRelease(it) }
            ?.maxByOrNull { it.name }
            ?: if (activeExists) {
                throw IOException("The active website is invalid and no safe last-known-good release is available.")
            } else return false
        val quarantined = if (activeExists) {
            File(releases, "recovery-invalid-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}")
                .also { if (!active.renameTo(it)) throw IOException("Could not preserve the interrupted active deployment.") }
        } else null
        if (!latest.renameTo(active)) {
            if (quarantined != null) quarantined.renameTo(active)
            throw IOException("Could not recover the last-known-good website deployment.")
        }
        return true
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

    private class CappedInputStream(input: java.io.InputStream, private val limit: Long) : FilterInputStream(input) {
        private var consumed = 0L
        private fun account(count: Int) {
            if (count > 0) {
                consumed += count
                if (consumed > limit) throw IOException("Compressed ZIP exceeds the 50 MB import limit.")
            }
        }
        override fun read(): Int {
            val value = super.read()
            if (value >= 0) account(1)
            return value
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            account(count)
            return count
        }
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
