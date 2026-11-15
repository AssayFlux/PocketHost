package com.assayflux.pockethost.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Safe app-private file operations for the currently deployed static website. */
object SiteFileManager {
    private const val MAX_EDIT_BYTES = 256L * 1024L
    private val textExtensions = setOf("html", "htm", "css", "js", "mjs", "json", "txt", "md", "xml", "svg", "yaml", "yml", "toml", "py", "ts", "tsx", "jsx", "csv")

    data class Entry(
        val relativePath: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,
        val isEditable: Boolean
    )

    fun list(context: Context): List<Entry> {
        val root = root(context)
        return root.walkTopDown().maxDepth(8).filter { it != root }.take(150).map { file ->
            val directory = file.isDirectory
            val ext = file.extension.lowercase()
            Entry(
                relativePath = file.relativeTo(root).invariantSeparatorsPath,
                isDirectory = directory,
                size = if (directory) 0L else file.length(),
                lastModified = file.lastModified(),
                isEditable = !directory && file.length() <= MAX_EDIT_BYTES && ext in textExtensions
            )
        }.toList()
    }

    /** Copies one SAF-selected file into the deployed site with a strict byte limit. */
    fun importFile(context: Context, uri: Uri, relativePath: String? = null): Entry {
        val displayName = relativePath ?: run {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("The selected file has no usable filename.")
        }
        val root = root(context)
        val target = resolve(root, displayName)
        require(!target.exists()) { "A file or folder already exists at this path." }
        val parts = displayName.split('/')
        require(parts.none { part ->
            val lower = part.lowercase()
            lower in setOf(".git", ".ssh", ".pockethost", ".env", "id_rsa", "id_ed25519", "authorized_keys", "known_hosts") ||
                lower.startsWith(".env.") || lower.endsWith(".pem") || lower.endsWith(".key") ||
                lower.endsWith(".p12") || lower.endsWith(".pfx")
        }) { "Sensitive files cannot be added to the public website." }
        val parent = target.parentFile ?: error("Invalid destination path.")
        require(parent.mkdirs() || parent.isDirectory) { "Could not create the destination folder." }
        val temporary = File(parent, "." + target.name + "." + UUID.randomUUID() + ".upload")
        var total = 0L
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("The selected file cannot be opened.")
            input.use { source ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 50L * 1024L * 1024L) { "Individual website files are limited to 50 MiB." }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            java.nio.file.Files.move(temporary.toPath(), target.toPath())
        } finally {
            temporary.delete()
        }
        return Entry(target.relativeTo(root).invariantSeparatorsPath, false, total, target.lastModified(),
            total <= MAX_EDIT_BYTES && target.extension.lowercase() in textExtensions)
    }

    fun hasIndex(context: Context): Boolean = File(root(context), "index.html").isFile

    fun readText(context: Context, relativePath: String): String {
        val file = resolve(root(context), relativePath)
        require(file.isFile) { "Select a file, not a folder." }
        require(file.length() <= MAX_EDIT_BYTES) { "Files larger than 256 KiB are read-only in the editor." }
        require(file.extension.lowercase() in textExtensions) { "This file type is not supported by the text editor." }
        val bytes = file.readBytes()
        require(bytes.none { it == 0.toByte() }) { "Binary files cannot be edited as text." }
        return bytes.toString(Charsets.UTF_8)
    }

    fun createText(context: Context, relativePath: String, content: String) {
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_EDIT_BYTES) { "Text files are limited to 256 KiB." }
        require(extension(relativePath) in textExtensions) { "Choose a supported text-file extension." }
        val root = root(context)
        val target = resolve(root, relativePath)
        require(!target.exists()) { "A file or folder already exists at this path." }
        val parent = target.parentFile ?: error("Invalid file path.")
        require(parent.mkdirs() || parent.isDirectory) { "Could not create the parent folder." }
        val safeTarget = resolve(root, relativePath)
        val temporary = File(parent, "." + target.name + ".pockethost-tmp")
        temporary.delete()
        temporary.writeText(content, Charsets.UTF_8)
        check(temporary.renameTo(safeTarget)) { temporary.delete(); "Could not commit the new file." }
    }

    fun writeText(context: Context, relativePath: String, content: String) {
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_EDIT_BYTES) { "Text files are limited to 256 KiB." }
        val root = root(context)
        val target = resolve(root, relativePath)
        require(target.isFile) { "The selected file no longer exists." }
        require(extension(relativePath) in textExtensions) { "This file type is not supported by the text editor." }
        val parent = target.parentFile ?: error("Invalid file path.")
        val temporary = File(parent, "." + target.name + ".pockethost-tmp")
        val backup = File(parent, "." + target.name + ".pockethost-bak")
        temporary.delete()
        backup.delete()
        temporary.writeText(content, Charsets.UTF_8)
        check(target.renameTo(backup)) { temporary.delete(); "Could not stage the previous file safely." }
        if (!temporary.renameTo(target)) {
            backup.renameTo(target)
            temporary.delete()
            error("Could not save the file; the previous version was restored where possible.")
        }
        backup.delete()
    }

    /** Rename a file or folder without replacing an existing destination. */
    fun renameEntry(context: Context, sourcePath: String, destinationPath: String) {
        val root = root(context)
        val source = resolve(root, sourcePath)
        val destination = resolve(root, destinationPath)
        require(source.exists()) { "The selected file or folder no longer exists." }
        require(source.canonicalFile != root) { "The website root cannot be renamed." }
        require(!destination.exists()) { "A file or folder already exists at the new path." }
        val parent = destination.parentFile ?: error("Invalid destination path.")
        require(parent.isDirectory) { "The destination folder must already exist." }
        java.nio.file.Files.move(source.toPath(), destination.toPath())
    }

    fun deleteFile(context: Context, relativePath: String) {
        val file = resolve(root(context), relativePath)
        require(file.isFile) { "Only individual files can be deleted here." }
        check(file.delete()) { "Could not delete the selected file." }
    }

    fun setIndex(context: Context, relativePath: String) {
        val root = root(context)
        val source = resolve(root, relativePath)
        require(source.isFile) { "Select an HTML file." }
        require(extension(relativePath) in setOf("html", "htm")) { "Only HTML files can become the entry page." }
        val target = File(root, "index.html")
        val temporary = File(root, ".index.html.pockethost-tmp")
        val backup = File(root, ".index.html.pockethost-bak")
        temporary.delete()
        backup.delete()
        source.copyTo(temporary, overwrite = true)
        if (target.exists()) check(target.renameTo(backup)) { temporary.delete(); "Could not stage the current index.html safely." }
        if (!temporary.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target)
            temporary.delete()
            error("Could not replace index.html; the previous entry page was restored where possible.")
        }
        backup.delete()
    }

    private fun root(context: Context): File {
        val directory = File(context.applicationContext.filesDir, "site")
        require(directory.mkdirs() || directory.isDirectory) { "Website directory is unavailable." }
        return directory.canonicalFile
    }

    private fun resolve(root: File, relativePath: String): File {
        val value = relativePath.trim()
        require(value.isNotEmpty() && !value.startsWith("/") && !value.contains('\\')) { "Use a relative path inside the website." }
        val parts = value.split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." }) { "Path traversal and empty path segments are not allowed." }
        require(parts.none { it.any(Char::isISOControl) }) { "The file path contains unsupported characters." }
        val target = File(root, value)
        val rootPath = root.canonicalPath
        val canonical = target.canonicalFile
        require(canonical.path.startsWith(rootPath + File.separator)) { "The selected path escapes the website directory." }
        return target
    }

    private fun extension(path: String): String = path.substringAfterLast('.', "").lowercase()
}
