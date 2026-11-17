package com.assayflux.pockethost.workbench

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/**
 * App-private, per-site editable workspace. It deliberately never writes to the live site root.
 * Commits are immutable snapshots; publishing is a separate, not-yet-implemented operation.
 */
object WorkbenchWorkspaceManager {
    private const val MAX_FILES = 5_000
    private const val MAX_ENTRIES = 10_000
    private const val MAX_BYTES = 50L * 1024L * 1024L
    private const val MAX_DEPTH = 32
    private const val MAX_TEXT_BYTES = 256L * 1024L
    private const val MAX_COMMITS = 10
    private const val MAX_COMMIT_STORAGE_BYTES = 200L * 1024L * 1024L
    private val textExtensions = setOf("html", "htm", "css", "js", "mjs", "json", "txt", "md", "xml", "svg", "yaml", "yml", "toml", "ts", "tsx", "jsx", "csv")
    private val blockedNames = setOf(".git", ".ssh", ".pockethost", "id_rsa", "id_ed25519", "authorized_keys", "known_hosts")

    data class Entry(val path: String, val directory: Boolean, val size: Long, val editable: Boolean)
    data class Change(val path: String, val status: String, val workspaceDigest: String?, val liveDigest: String?)
    data class Commit(val id: String, val message: String, val createdAt: Long, val fileCount: Int, val totalBytes: Long)

    /** Creates a workspace from the live tree once; later calls never overwrite user edits. */
    @Synchronized
    @Throws(IOException::class)
    fun open(context: Context, projectId: String, liveRoot: File): File {
        validateProjectId(projectId)
        if (java.nio.file.Files.isSymbolicLink(liveRoot.toPath())) {
            throw IOException("The selected live website directory cannot be a symbolic link.")
        }
        val live = liveRoot.canonicalFile
        if (!live.isDirectory || java.nio.file.Files.isSymbolicLink(live.toPath())) {
            throw IOException("The selected live website directory is unavailable or unsafe.")
        }
        val workspace = workspaceRoot(context, projectId)
        val baseline = baselineRoot(context, projectId)
        val parent = workspace.parentFile ?: throw IOException("Workbench storage is unavailable.")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create Workbench storage.")
        if (workspace.exists()) {
            validateTree(workspace)
            if (!baseline.exists() && commits(context, projectId).isEmpty()) {
                val baselineStaging = File(parent, ".baseline-${UUID.randomUUID()}")
                try {
                    copyTree(live, baselineStaging)
                    validateTree(baselineStaging)
                    if (!baselineStaging.renameTo(baseline)) throw IOException("Could not initialize the Workbench baseline.")
                } finally {
                    baselineStaging.deleteRecursively()
                }
            }
            return workspace
        }
        val staging = File(parent, ".workspace-${UUID.randomUUID()}")
        val baselineStaging = File(parent, ".baseline-${UUID.randomUUID()}")
        try {
            copyTree(live, staging)
            validateTree(staging)
            copyTree(live, baselineStaging)
            validateTree(baselineStaging)
            if (!staging.renameTo(workspace)) throw IOException("Could not initialize the Workbench workspace.")
            if (!baselineStaging.renameTo(baseline)) throw IOException("Could not initialize the Workbench baseline.")
        } finally {
            staging.deleteRecursively()
            baselineStaging.deleteRecursively()
        }
        return workspace
    }

    fun list(context: Context, projectId: String): List<Entry> {
        val root = workspaceRoot(context, projectId)
        if (!root.isDirectory) return emptyList()
        validateTree(root)
        val result = mutableListOf<Entry>()
        root.walkTopDown().maxDepth(MAX_DEPTH + 1).forEach { file ->
            if (file == root) return@forEach
            val relative = file.relativeTo(root).invariantSeparatorsPath
            val directory = file.isDirectory
            result += Entry(relative, directory, if (directory) 0L else file.length(),
                !directory && file.length() <= MAX_TEXT_BYTES && file.extension.lowercase() in textExtensions)
            if (result.size > MAX_ENTRIES) throw IOException("Workbench workspace contains too many entries.")
        }
        return result.sortedWith(compareBy<Entry> { !it.directory }.thenBy { it.path.lowercase() })
    }

    fun readText(context: Context, projectId: String, relativePath: String): String {
        val file = resolve(workspaceRoot(context, projectId), relativePath)
        if (!file.isFile || file.length() > MAX_TEXT_BYTES || file.extension.lowercase() !in textExtensions) {
            throw IOException("This file is not an editable text file or exceeds 256 KiB.")
        }
        val bytes = file.readBytes()
        if (bytes.any { it == 0.toByte() }) throw IOException("Binary files cannot be opened in the text editor.")
        return bytes.toString(Charsets.UTF_8)
    }

    fun writeText(context: Context, projectId: String, relativePath: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_TEXT_BYTES) throw IOException("Workbench text files are limited to 256 KiB.")
        val root = workspaceRoot(context, projectId)
        val stats = validateTree(root)
        val target = resolve(root, relativePath)
        if (!target.isFile || target.extension.lowercase() !in textExtensions) throw IOException("Select an existing supported text file.")
        val existingBytes = target.length()
        if (bytes.size.toLong() > MAX_BYTES - (stats.second - existingBytes)) {
            throw IOException("Workbench workspace would exceed its 50 MiB size limit.")
        }
        atomicWrite(target, bytes)
    }

    fun createText(context: Context, projectId: String, relativePath: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_TEXT_BYTES) throw IOException("Workbench text files are limited to 256 KiB.")
        if (relativePath.substringAfterLast('.', "").lowercase() !in textExtensions) throw IOException("Choose a supported text-file extension.")
        val root = workspaceRoot(context, projectId)
        val target = resolve(root, relativePath)
        if (target.exists()) throw IOException("A file or folder already exists at this path.")
        val parent = target.parentFile ?: throw IOException("Invalid destination path.")
        if (!parent.mkdirs() && !parent.isDirectory) throw IOException("Could not create the parent folder.")
        atomicWrite(target, bytes, mustNotExist = true)
        try {
            validateTree(root)
        } catch (error: IOException) {
            target.delete()
            throw error
        }
    }

    fun createFolder(context: Context, projectId: String, relativePath: String) {
        val root = workspaceRoot(context, projectId)
        val target = resolve(root, relativePath)
        if (target.exists()) throw IOException("A file or folder already exists at this path.")
        val parent = target.parentFile ?: throw IOException("Invalid folder path.")
        if (!parent.isDirectory) throw IOException("Create the parent folder first.")
        if (!target.mkdir()) throw IOException("Could not create the folder.")
        try {
            validateTree(root)
        } catch (error: IOException) {
            target.delete()
            throw error
        }
    }

    fun rename(context: Context, projectId: String, sourcePath: String, destinationPath: String) {
        val root = workspaceRoot(context, projectId)
        val source = resolve(root, sourcePath)
        val destination = resolve(root, destinationPath)
        if (!source.exists() || source.canonicalFile == root.canonicalFile) throw IOException("The selected entry does not exist or cannot be renamed.")
        if (destination.exists()) throw IOException("The destination already exists.")
        val parent = destination.parentFile ?: throw IOException("Invalid destination folder.")
        if (!parent.isDirectory) throw IOException("The destination folder must already exist.")
        if (java.nio.file.Files.isSymbolicLink(source.toPath())) throw IOException("Symbolic links are not supported.")
        java.nio.file.Files.move(source.toPath(), destination.toPath())
        validateTree(root)
    }

    fun deleteFile(context: Context, projectId: String, relativePath: String) {
        val root = workspaceRoot(context, projectId)
        val target = resolve(root, relativePath)
        if (!target.isFile || java.nio.file.Files.isSymbolicLink(target.toPath())) throw IOException("Only regular files can be deleted.")
        if (!target.delete()) throw IOException("Could not delete the workspace file.")
    }

    /** Compares workspace content with the currently live tree without modifying either. */
    fun changes(context: Context, projectId: String, liveRoot: File): List<Change> {
        val workspace = workspaceRoot(context, projectId)
        if (!workspace.isDirectory) return emptyList()
        validateTree(workspace)
        val latestCommit = commits(context, projectId).firstOrNull()
        val baseline = latestCommit?.let { File(commitsRoot(context, projectId), "${it.id}/snapshot") }
            ?: baselineRoot(context, projectId)
        val workspaceFiles = fileDigests(workspace)
        val baseFiles = if (baseline.isDirectory) fileDigests(baseline) else emptyMap()
        return (workspaceFiles.keys + baseFiles.keys).toSortedSet().mapNotNull { path ->
            val w = workspaceFiles[path]
            val b = baseFiles[path]
            when {
                w == null -> Change(path, "deleted", null, b)
                b == null -> Change(path, "added", w, null)
                w != b -> Change(path, "modified", w, b)
                else -> null
            }
        }
    }

    /** Creates a durable immutable workspace snapshot. It does not deploy or alter live files. */
    @Synchronized
    fun commit(context: Context, projectId: String, message: String, liveRoot: File): Commit {
        val trimmed = message.trim()
        if (trimmed.isEmpty() || trimmed.length > 160) throw IOException("Commit message must contain 1–160 characters.")
        val workspace = workspaceRoot(context, projectId)
        if (!workspace.isDirectory) throw IOException("Open the website in Workbench before committing.")
        val changes = changes(context, projectId, liveRoot)
        if (changes.isEmpty()) throw IOException("There are no changes to commit.")
        val stats = validateTree(workspace)
        val parent = commitsRoot(context, projectId)
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create commit history.")
        val previousCommits = commits(context, projectId)
        val createdAt = maxOf(System.currentTimeMillis(), (previousCommits.maxOfOrNull { it.createdAt } ?: 0L) + 1L)
        val id = "commit-$createdAt-${UUID.randomUUID().toString().take(8)}"
        val staging = File(parent, ".$id.tmp")
        val destination = File(parent, id)
        try {
            copyTree(workspace, File(staging, "snapshot"))
            val manifest = JSONObject()
                .put("id", id)
                .put("project_id", projectId)
                .put("message", trimmed)
                .put("created_at", createdAt)
                .put("file_count", stats.first)
                .put("total_bytes", stats.second)
                .put("parent_commit", commits(context, projectId).firstOrNull()?.id ?: "initial-live-baseline")
                .put("live_tree_sha256_at_commit", treeDigest(liveRoot))
            val files = JSONArray()
            fileDigests(File(staging, "snapshot")).toSortedMap().forEach { (path, digest) ->
                files.put(JSONObject().put("path", path).put("sha256", digest))
            }
            manifest.put("files", files)
            File(staging, "commit.json").writeText(manifest.toString(), Charsets.UTF_8)
            if (!staging.renameTo(destination)) throw IOException("Could not finalize the immutable commit snapshot.")
            return Commit(id, trimmed, createdAt, stats.first, stats.second)
        } finally {
            staging.deleteRecursively()
        }
    }

    fun commits(context: Context, projectId: String): List<Commit> {
        val root = commitsRoot(context, projectId)
        return root.listFiles()?.filter {
            it.isDirectory && !java.nio.file.Files.isSymbolicLink(it.toPath()) && isValidCommitId(it.name)
        }?.mapNotNull { directory ->
            runCatching {
                val json = JSONObject(File(directory, "commit.json").readText(Charsets.UTF_8))
                val id = json.getString("id")
                if (!isValidCommitId(id) || id != directory.name || json.getString("project_id") != projectId) {
                    throw IOException("Commit metadata does not match its storage directory.")
                }
                val snapshot = File(directory, "snapshot")
                val stats = validateTree(snapshot)
                if (json.getInt("file_count") != stats.first || json.getLong("total_bytes") != stats.second) {
                    throw IOException("Commit snapshot metadata is inconsistent.")
                }
                Commit(id, json.getString("message"), json.getLong("created_at"), stats.first, stats.second)
            }.getOrNull()
        }?.sortedWith(compareByDescending<Commit> { it.createdAt }.thenByDescending { it.id }) ?: emptyList()
    }

    /** Retains a bounded recent history; the newest commit is never selected for deletion. */
    private fun pruneCommitHistory(context: Context, projectId: String, newestCommitId: String) {
        val root = commitsRoot(context, projectId)
        val history = commits(context, projectId)
        var retainedBytes = history.sumOf { snapshotStorageBytes(File(root, it.id)) }
        var retainedCount = history.size
        history.sortedWith(compareBy<Commit> { it.createdAt }.thenBy { it.id }).forEach { commit ->
            if (retainedCount <= MAX_COMMITS && retainedBytes <= MAX_COMMIT_STORAGE_BYTES) return
            if (newestCommitId == commit.id) return@forEach
            val directory = File(root, commit.id)
            val size = snapshotStorageBytes(directory)
            if (directory.deleteRecursively()) {
                retainedCount--
                retainedBytes = (retainedBytes - size).coerceAtLeast(0L)
            }
        }
    }

    private fun snapshotStorageBytes(directory: File): Long {
        if (!directory.isDirectory || java.nio.file.Files.isSymbolicLink(directory.toPath())) return 0L
        var total = 0L
        directory.walkTopDown().forEach { file ->
            if (java.nio.file.Files.isSymbolicLink(file.toPath())) throw IOException("Commit history contains a symbolic link.")
            if (file.isFile) {
                val length = file.length()
                if (length > MAX_COMMIT_STORAGE_BYTES - total) return MAX_COMMIT_STORAGE_BYTES + 1L
                total += length
            }
        }
        return total
    }

    private fun isValidCommitId(id: String): Boolean =
        id.matches(Regex("commit-[0-9]{10,}-[0-9a-fA-F]{8}"))

    private fun validateTree(root: File): Pair<Int, Long> {
        if (!root.isDirectory || java.nio.file.Files.isSymbolicLink(root.toPath())) {
            throw IOException("Workbench tree root is unavailable or unsafe.")
        }
        val canonicalRoot = root.canonicalFile
        var files = 0
        var entries = 0
        var bytes = 0L
        val noFollow = java.nio.file.LinkOption.NOFOLLOW_LINKS
        java.nio.file.Files.walk(canonicalRoot.toPath()).use { paths ->
            val iterator = paths.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                entries++
                if (entries > MAX_ENTRIES || canonicalRoot.toPath().relativize(path).nameCount > MAX_DEPTH ||
                    java.nio.file.Files.isSymbolicLink(path)) throw IOException("Workbench contains an unsafe or excessively nested entry.")
                if (path != canonicalRoot.toPath()) {
                    val name = path.fileName.toString().lowercase()
                    if (isBlockedName(name)) throw IOException("Sensitive files and private metadata are excluded from Workbench: $name")
                }
                if (java.nio.file.Files.isRegularFile(path, noFollow)) {
                    files++
                    val size = java.nio.file.Files.size(path)
                    if (files > MAX_FILES || size > MAX_BYTES - bytes) throw IOException("Workbench workspace exceeds its file or 50 MiB size limit.")
                    bytes += size
                } else if (!java.nio.file.Files.isDirectory(path, noFollow)) {
                    throw IOException("Workbench contains an unsupported filesystem entry.")
                }
            }
        }
        return files to bytes
    }

    private fun fileDigests(root: File): Map<String, String> {
        if (!root.exists()) return emptyMap()
        if (!root.isDirectory || java.nio.file.Files.isSymbolicLink(root.toPath())) {
            throw IOException("Website tree root is unavailable or unsafe.")
        }
        val result = linkedMapOf<String, String>()
        var entries = 0
        var files = 0
        var bytes = 0L
        fun visit(directory: File, depth: Int) {
            if (depth > MAX_DEPTH) throw IOException("Website folder nesting is too deep.")
            val children = directory.listFiles() ?: throw IOException("Could not inspect website files.")
            children.forEach { file ->
                entries++
                if (entries > MAX_ENTRIES) throw IOException("Website contains too many entries.")
                if (java.nio.file.Files.isSymbolicLink(file.toPath())) throw IOException("Symbolic links are not supported in Workbench.")
                if (isBlockedName(file.name.lowercase())) return@forEach
                if (file.isDirectory) visit(file, depth + 1)
                else if (file.isFile) {
                    files++
                    bytes += file.length()
                    if (files > MAX_FILES || bytes > MAX_BYTES) throw IOException("Website exceeds Workbench file or size limits.")
                    result[file.relativeTo(root).invariantSeparatorsPath] = sha256(file)
                } else throw IOException("Unsupported entry in website tree.")
            }
        }
        visit(root, 0)
        return result
    }

    private fun copyTree(source: File, destination: File) {
        if (!source.isDirectory || java.nio.file.Files.isSymbolicLink(source.toPath())) {
            throw IOException("Source website tree is unavailable or unsafe.")
        }
        if (!destination.mkdirs()) throw IOException("Could not create Workbench snapshot.")
        var copiedBytes = 0L
        var copiedFiles = 0
        var copiedEntries = 0
        fun copyDirectory(from: File, to: File, depth: Int) {
            if (depth > MAX_DEPTH) throw IOException("Website folder nesting is too deep.")
            val children = from.listFiles() ?: throw IOException("Could not inspect website files.")
            children.forEach { file ->
                copiedEntries++
                if (copiedEntries > MAX_ENTRIES) throw IOException("Website contains too many entries.")
                if (java.nio.file.Files.isSymbolicLink(file.toPath())) throw IOException("Symbolic links are not supported in Workbench.")
                if (isBlockedName(file.name.lowercase())) return@forEach
                val target = File(to, file.name)
                if (file.isDirectory) {
                    if (!target.mkdirs()) throw IOException("Could not copy a Workbench folder.")
                    copyDirectory(file, target, depth + 1)
                } else if (file.isFile) {
                    copiedFiles++
                    copiedBytes += file.length()
                    if (copiedFiles > MAX_FILES || copiedBytes > MAX_BYTES) throw IOException("Website exceeds Workbench file or size limits.")
                    file.copyTo(target, overwrite = false)
                } else throw IOException("Unsupported entry in website tree.")
            }
        }
        copyDirectory(source, destination, 0)
    }

    private fun atomicWrite(target: File, bytes: ByteArray, mustNotExist: Boolean = false) {
        val parent = target.parentFile ?: throw IOException("Invalid file path.")
        val temp = File(parent, ".${target.name}.${UUID.randomUUID()}.tmp")
        try {
            temp.outputStream().use { output -> output.write(bytes); output.fd.sync() }
            if (mustNotExist && target.exists()) throw IOException("A file or folder already exists at this path.")
            if (mustNotExist) {
                java.nio.file.Files.move(temp.toPath(), target.toPath())
            } else {
                java.nio.file.Files.move(temp.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            }
        } finally {
            temp.delete()
        }
    }

    private fun resolve(root: File, relativePath: String): File {
        validateProjectRoot(root)
        val value = relativePath.trim()
        if (value.isEmpty() || value.startsWith("/") || value.contains('\\')) throw IOException("Use a relative path inside the Workbench workspace.")
        val parts = value.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." || it.any(Char::isISOControl) || isBlockedName(it.lowercase()) }) {
            throw IOException("The path is invalid or refers to protected project data.")
        }
        val target = File(root, value)
        val canonicalRoot = root.canonicalFile.toPath()
        if (!target.canonicalFile.toPath().startsWith(canonicalRoot) || target.canonicalFile == root.canonicalFile) {
            throw IOException("The path escapes the Workbench workspace.")
        }
        var cursor = root
        for (part in parts) {
            cursor = File(cursor, part)
            if (java.nio.file.Files.isSymbolicLink(cursor.toPath())) throw IOException("Symbolic links are not supported in Workbench.")
        }
        return target
    }

    private fun validateProjectRoot(root: File) {
        if (!root.isDirectory || java.nio.file.Files.isSymbolicLink(root.toPath())) throw IOException("Workbench workspace is unavailable.")
    }

    private fun workspaceRoot(context: Context, projectId: String): File {
        validateProjectId(projectId)
        return File(File(context.filesDir, "workbench"), "$projectId/workspace")
    }

    private fun commitsRoot(context: Context, projectId: String): File {
        validateProjectId(projectId)
        return File(File(context.filesDir, "workbench"), "$projectId/commits")
    }

    private fun baselineRoot(context: Context, projectId: String): File {
        validateProjectId(projectId)
        return File(File(context.filesDir, "workbench"), "$projectId/baseline")
    }

    private fun treeDigest(root: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fileDigests(root).toSortedMap().forEach { (path, hash) ->
            digest.update(path.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(hash.toByteArray(Charsets.US_ASCII))
            digest.update(0.toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun validateProjectId(projectId: String) {
        if (!projectId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) throw IOException("Invalid Workbench project identifier.")
    }

    private fun isBlockedName(name: String): Boolean =
        name in blockedNames || name == ".env" || name.startsWith(".env.") ||
            name.endsWith(".pem") || name.endsWith(".key") || name.endsWith(".p12") || name.endsWith(".pfx")

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
