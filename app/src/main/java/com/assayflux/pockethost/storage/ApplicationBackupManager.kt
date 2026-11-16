package com.assayflux.pockethost.storage

import android.content.Context
import android.net.Uri
import com.assayflux.pockethost.engine.NativeEngine
import com.assayflux.pockethost.diagnostics.OperationalLog
import com.assayflux.pockethost.runtime.NodeHostRuntime
import com.assayflux.pockethost.runtime.PythonHostRuntime
import com.assayflux.pockethost.sites.SiteRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.io.FilterOutputStream
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable encrypted backup of the deployed website, optional trusted runtime projects,
 * hostname routes and a deliberately small non-secret settings allowlist.
 *
 * Android Keystore credentials, Owner/Operator accounts, Cloudflare tokens, remote-control
 * tokens, sessions and device-bound keys are never exported. Restored runtimes remain stopped
 * and require a fresh trusted-code acknowledgement on the target device.
 */
object ApplicationBackupManager {
    private val MAGIC = "AFPHB02\n".toByteArray(Charsets.US_ASCII)
    private val LEGACY_MAGIC = "AFPHB01\n".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val ITERATIONS = 210_000
    private const val MAX_FILES = 20_000
    private const val MAX_ENTRIES = 30_000
    private const val MAX_TOTAL_BYTES = 200L * 1024L * 1024L
    private const val MAX_ENCRYPTED_BYTES = 220L * 1024L * 1024L
    private const val MAX_DEPTH = 32
    private const val STATIC_LIMIT = 50L * 1024L * 1024L
    private const val PYTHON_LIMIT = 50L * 1024L * 1024L
    private const val NODE_LIMIT = 100L * 1024L * 1024L

    data class RestoreReport(
        val fileCount: Int,
        val totalBytes: Long,
        val pythonRestored: Boolean,
        val nodeRestored: Boolean,
        val hostnameRoutes: Int,
        val legacyStaticOnly: Boolean = false
    )

    private data class SafeSettings(
        val port: Int,
        val pythonPort: Int,
        val nodePort: Int,
        val keepAwake: Boolean,
        val autoStartOnBoot: Boolean,
        val darkTheme: Boolean
    )

    private data class Counters(
        var files: Int = 0,
        var entries: Int = 0,
        var bytes: Long = 0L,
        var customStaticFiles: Int = 0,
        var customStaticEntries: Int = 0,
        var customStaticBytes: Long = 0L
    )

    private data class ExtraStaticRoot(val archivePath: String, val root: File)
    private data class ExportedRoutes(val manifest: JSONObject, val extraStaticRoots: List<ExtraStaticRoot>)
    private data class ParsedRoutes(
        val sites: List<SiteRegistry.Site>,
        val customStaticRoots: Map<String, File>
    )

    @Synchronized
    fun export(context: Context, destination: Uri, passphrase: CharArray): Boolean {
        try {
            require(passphrase.size in 12..128) { "Use a backup passphrase between 12 and 128 characters." }
            requireAllRuntimesStopped(context)

            val files = context.filesDir
            val preferences = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
            val site = File(files, "site").canonicalFile
            val pythonInput = File(
                preferences.getString("python_project_root", File(files, "python-project").absolutePath)
                    ?: File(files, "python-project").absolutePath
            )
            val nodeInput = File(
                preferences.getString("node_project_root", File(files, "node-project-default").absolutePath)
                    ?: File(files, "node-project-default").absolutePath
            )
            if (java.nio.file.Files.isSymbolicLink(pythonInput.toPath()) ||
                java.nio.file.Files.isSymbolicLink(nodeInput.toPath())) {
                throw IOException("Python and Node project roots cannot be symbolic links.")
            }
            val python = pythonInput.canonicalFile
            val node = nodeInput.canonicalFile
            val privateRoot = files.canonicalFile.toPath()
            val sitePath = site.toPath()
            val pythonPath = python.toPath()
            val nodePath = node.toPath()
            if (!pythonPath.startsWith(privateRoot) || !nodePath.startsWith(privateRoot) ||
                python == files.canonicalFile || node == files.canonicalFile) {
                throw IOException("Python and Node project folders must be real directories inside Pocket Host private storage.")
            }
            if (pythonPath.startsWith(sitePath) || sitePath.startsWith(pythonPath) ||
                nodePath.startsWith(sitePath) || sitePath.startsWith(nodePath) ||
                pythonPath.startsWith(nodePath) || nodePath.startsWith(pythonPath)) {
                throw IOException("Website, Python and Node project folders must not overlap.")
            }
            if (!File(site, "index.html").isFile) throw IOException("There is no deployed website to back up.")
            val includePython = python.exists()
            val includeNode = node.exists()
            if (includePython && !File(python, "wsgi.py").isFile) {
                throw IOException("The imported Python project is incomplete; expected wsgi.py.")
            }
            if (includeNode) {
                try {
                    NodeProjectImporter.resolveEntry(node)
                } catch (error: Exception) {
                    throw IOException("The imported Node.js project is incomplete: ${error.message}", error)
                }
            }

            val settings = readSafeSettings(context)
            val routeExport = exportRoutes(context, site, python, node, includePython, includeNode)
            val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
            val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, nonce))
            val output = context.contentResolver.openOutputStream(destination)
                ?: throw IOException("Cannot open the selected backup destination.")
            val header = DataOutputStream(output)
            header.write(MAGIC)
            header.write(salt)
            header.write(nonce)
            header.flush()

            ZipOutputStream(BoundedOutputStream(CipherOutputStream(header, cipher), MAX_ENCRYPTED_BYTES)).use { zip ->
                val counters = Counters()
                addTree(zip, site, "site", STATIC_LIMIT, counters)
                if (includePython) addTree(zip, python, "python", PYTHON_LIMIT, counters)
                if (includeNode) addTree(zip, node, "node", NODE_LIMIT, counters)
                routeExport.extraStaticRoots.forEach { extra ->
                    addTree(zip, extra.root, extra.archivePath, STATIC_LIMIT, counters)
                }
                addSmallEntry(zip, "settings.json", settings.toString().toByteArray(Charsets.UTF_8), counters)
                addSmallEntry(zip, "routes.json", routeExport.manifest.toString().toByteArray(Charsets.UTF_8), counters)
                if (counters.files > MAX_FILES || counters.entries > MAX_ENTRIES || counters.bytes > MAX_TOTAL_BYTES) {
                    throw IOException("The full app backup exceeds its file, entry or total-size safety limit.")
                }
            }
            return true
        } finally {
            passphrase.fill('\u0000')
        }
    }

    @Synchronized
    fun restore(context: Context, source: Uri, passphrase: CharArray): RestoreReport {
        val archive = File(context.cacheDir, "pockethost-full-backup-${UUID.randomUUID()}.zip")
        val stage = File(context.filesDir, "pockethost-full-restore-${UUID.randomUUID()}")
        val backupDir = File(context.filesDir, "pockethost-restore-previous-${UUID.randomUUID()}")
        var preserveBackup = true
        try {
            require(passphrase.size in 12..128) { "Use the backup passphrase (12–128 characters)." }
            requireAllRuntimesStopped(context)
            recoverInterruptedRestore(context)

            val prefix = ByteArray(MAGIC.size)
            context.contentResolver.openInputStream(source)?.use { input ->
                DataInputStream(input).readFully(prefix)
            } ?: throw IOException("The selected backup cannot be opened.")
            if (prefix.contentEquals(LEGACY_MAGIC)) {
                val report = SiteBackupManager.restore(context, source, passphrase)
                return RestoreReport(report.fileCount, report.totalBytes, false, false,
                    SiteRegistry.list(context).size, legacyStaticOnly = true)
            }
            if (!prefix.contentEquals(MAGIC)) throw IOException("This is not a supported Pocket Host encrypted backup.")

            decryptToArchive(context, source, archive, passphrase)
            if (!stage.mkdirs()) throw IOException("Could not create a private restore staging directory.")
            val counters = extractArchive(archive, stage)
            val siteRoot = File(stage, "site")
            if (!File(siteRoot, "index.html").isFile) {
                throw IOException("The backup does not contain a valid website entry page.")
            }
            val pythonStage = File(stage, "python")
            val nodeStage = File(stage, "node")
            val hasPython = pythonStage.isDirectory
            val hasNode = nodeStage.isDirectory
            if (hasPython && !File(pythonStage, "wsgi.py").isFile) {
                throw IOException("The backup contains an invalid Python project: wsgi.py is missing.")
            }
            if (hasNode) {
                try {
                    NodeProjectImporter.resolveEntry(nodeStage)
                } catch (error: Exception) {
                    throw IOException("The backup contains an invalid Node.js project: ${error.message}", error)
                }
            }

            val settings = parseSafeSettings(File(stage, "settings.json"))
            val parsedRoutes = parseRoutes(File(stage, "routes.json"), context, hasPython, hasNode)
            val routes = parsedRoutes.sites

            val activeSite = File(context.filesDir, "site")
            val activePython = File(context.filesDir, "python-project")
            val activeNode = File(context.filesDir, "node-project-default")
            val hadActiveSite = activeSite.isDirectory
            val hadPython = activePython.exists()
            val hadNode = activeNode.exists()
            val previousSettings = captureCurrentSafeSettings(context)
            val previousRegistry = context.getSharedPreferences("pockethost_site_registry", Context.MODE_PRIVATE)
                .getString("sites", "[]") ?: "[]"
            if (previousRegistry.toByteArray(Charsets.UTF_8).size > 256_000) {
                throw IOException("The existing site registry is too large to snapshot safely for backup restore.")
            }
            val previousLatestRelease = SiteImporter.listReleases(context).firstOrNull()
            if (!backupDir.mkdirs()) throw IOException("Could not create a rollback directory.")
            val customRootJournal = JSONArray().also { entries ->
                parsedRoutes.customStaticRoots.values.forEach { activeRoot ->
                    val previous = File(backupDir, "custom-static/" + activeRoot.name)
                    entries.put(JSONObject()
                        .put("activePath", activeRoot.absolutePath)
                        .put("backupPath", previous.absolutePath)
                        .put("hadPrevious", activeRoot.exists()))
                }
            }
            writeRestoreJournal(backupDir, JSONObject()
                .put("version", 1)
                .put("hadActiveSite", hadActiveSite)
                .put("hadPython", hadPython)
                .put("hadNode", hadNode)
                .put("previousLatestRelease", previousLatestRelease ?: JSONObject.NULL)
                .put("previousSiteRegistry", previousRegistry)
                .put("previousSettings", previousSettings)
                .put("customRoots", customRootJournal))

            try {
                val previousPython = File(backupDir, "python-project")
                if (activePython.exists()) {
                    if (!activePython.renameTo(previousPython)) throw IOException("Could not preserve the previous Python project.")
                }
                if (hasPython) {
                    if (!pythonStage.renameTo(activePython)) throw IOException("Could not activate the restored Python project.")
                }

                val previousNode = File(backupDir, "node-project-default")
                if (activeNode.exists()) {
                    if (!activeNode.renameTo(previousNode)) throw IOException("Could not preserve the previous Node.js project.")
                }
                if (hasNode) {
                    if (!nodeStage.renameTo(activeNode)) throw IOException("Could not activate the restored Node.js project.")
                }

                parsedRoutes.customStaticRoots.forEach { (archivePath, activeRoot) ->
                    val stagedRoot = File(stage, archivePath).canonicalFile
                    val stagePath = stage.canonicalFile.toPath()
                    if (!stagedRoot.toPath().startsWith(stagePath) || !stagedRoot.isDirectory ||
                        !File(stagedRoot, "index.html").isFile) {
                        throw IOException("The backup contains an invalid custom static site root.")
                    }
                    val rootParent = File(context.filesDir, "site-roots").canonicalFile
                    if (!rootParent.toPath().startsWith(context.filesDir.canonicalFile.toPath())) {
                        throw IOException("The custom site restore directory is unsafe.")
                    }
                    if (!rootParent.exists() && !rootParent.mkdirs()) {
                        throw IOException("Could not create the custom site restore directory.")
                    }
                    if (java.nio.file.Files.isSymbolicLink(activeRoot.toPath())) {
                        throw IOException("The existing custom site root is a symbolic link.")
                    }
                    val previous = File(backupDir, "custom-static/${activeRoot.name}")
                    if (activeRoot.exists()) {
                        if (!previous.parentFile!!.exists() && !previous.parentFile!!.mkdirs()) {
                            throw IOException("Could not create custom site rollback storage.")
                        }
                        if (!activeRoot.renameTo(previous)) {
                            throw IOException("Could not preserve the previous custom site root.")
                        }
                    }
                    if (!stagedRoot.renameTo(activeRoot)) {
                        throw IOException("Could not activate the restored custom site root.")
                    }
                }

                SiteImporter.activatePreparedDirectory(context, siteRoot)
                SiteRegistry.replaceAll(context, routes)
                applySafeSettings(context, settings)

                markRestoreCommitted(backupDir)
                preserveBackup = !cleanupCommittedRestore(backupDir)
                return RestoreReport(
                    fileCount = counters.files,
                    totalBytes = counters.bytes,
                    pythonRestored = hasPython,
                    nodeRestored = hasNode,
                    hostnameRoutes = routes.size
                )
            } catch (error: Throwable) {
                val rollback = runCatching { recoverInterruptedRestore(context) }
                if (rollback.isFailure) {
                    preserveBackup = true
                    throw IOException(
                        "Restore failed and automatic rollback was incomplete. Recovery files were preserved in " + backupDir.name + ".",
                        rollback.exceptionOrNull() ?: error
                    )
                }
                preserveBackup = false
                throw error
            }
        } finally {
            archive.delete()
            stage.deleteRecursively()
            if (!preserveBackup) backupDir.deleteRecursively()
            passphrase.fill('\u0000')
        }
    }

    private const val RESTORE_JOURNAL = "restore-journal.json"
    private const val RESTORE_COMMITTED = "restore-committed"

    private fun writeRestoreJournal(directory: File, journal: JSONObject) {
        val temporary = File(directory, RESTORE_JOURNAL + ".tmp")
        val file = File(directory, RESTORE_JOURNAL)
        val bytes = journal.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > 1_000_000) throw IOException("Restore recovery journal exceeds its safety limit.")
        if (java.nio.file.Files.isSymbolicLink(temporary.toPath()) ||
            java.nio.file.Files.isSymbolicLink(file.toPath())) {
            throw IOException("Restore journal path cannot be a symbolic link.")
        }
        java.io.FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        if (!temporary.renameTo(file)) {
            temporary.delete()
            throw IOException("Could not persist the full restore recovery journal.")
        }
    }

    private fun markRestoreCommitted(directory: File) {
        val marker = File(directory, RESTORE_COMMITTED)
        val noFollow = java.nio.file.LinkOption.NOFOLLOW_LINKS
        if (java.nio.file.Files.isSymbolicLink(marker.toPath())) {
            throw IOException("Restore commit marker cannot be a symbolic link.")
        }
        if (!java.nio.file.Files.isRegularFile(marker.toPath(), noFollow)) {
            java.io.FileOutputStream(marker).use { output ->
                output.write("committed".toByteArray(Charsets.US_ASCII))
                output.fd.sync()
            }
        }
    }

    private fun cleanupCommittedRestore(directory: File): Boolean {
        if (!java.nio.file.Files.isRegularFile(File(directory, RESTORE_COMMITTED).toPath(),
                java.nio.file.LinkOption.NOFOLLOW_LINKS)) return false
        val children = directory.listFiles() ?: return !directory.exists()
        for (child in children.filter { it.name != RESTORE_COMMITTED }) {
            val removed = if (java.nio.file.Files.isSymbolicLink(child.toPath())) {
                runCatching { java.nio.file.Files.deleteIfExists(child.toPath()) }.getOrDefault(false)
            } else child.deleteRecursively()
            if (!removed && child.exists()) return false
        }
        val marker = File(directory, RESTORE_COMMITTED)
        if (marker.exists() && !marker.delete()) return false
        return !directory.exists() || directory.delete()
    }

    private fun deleteRestoreEntry(entry: File) {
        if (java.nio.file.Files.isSymbolicLink(entry.toPath())) {
            if (!java.nio.file.Files.deleteIfExists(entry.toPath())) {
                throw IOException("Could not remove interrupted restore entry ${entry.name}.")
            }
        } else if (entry.exists() && !entry.deleteRecursively()) {
            throw IOException("Could not remove interrupted restore entry ${entry.name}.")
        }
    }

    private fun restoreJournalDirectory(
        context: Context,
        backupDir: File,
        activeName: String,
        backupName: String,
        hadPrevious: Boolean
    ) {
        val active = File(context.filesDir, activeName)
        val backup = File(backupDir, backupName)
        val noFollow = java.nio.file.LinkOption.NOFOLLOW_LINKS
        if (java.nio.file.Files.isSymbolicLink(backup.toPath())) {
            throw IOException("Interrupted restore backup contains an unexpected symbolic link.")
        }
        if (java.nio.file.Files.exists(backup.toPath(), noFollow)) {
            if (!java.nio.file.Files.isDirectory(backup.toPath(), noFollow)) {
                throw IOException("Interrupted restore backup directory is invalid.")
            }
            deleteRestoreEntry(active)
            if (!backup.renameTo(active)) throw IOException("Could not restore previous ${active.name} after interrupted backup restore.")
        } else if (!hadPrevious) {
            deleteRestoreEntry(active)
        }
        // If a previous directory existed but its backup is absent, the transaction
        // had not moved it yet or it was already restored; leave the active copy intact.
    }

    /**
     * Restores the pre-transaction state when Android kills the process during a
     * full backup restore. A durable commit marker distinguishes completed restores
     * from transactions that must be rolled back on the next app launch.
     */
    @Synchronized
    fun recoverInterruptedRestore(context: Context): Boolean {
        val noFollow = java.nio.file.LinkOption.NOFOLLOW_LINKS
        val directories = context.filesDir.listFiles()
            ?.filter { directory ->
                directory.name.startsWith("pockethost-restore-previous-") &&
                    java.nio.file.Files.exists(directory.toPath(), noFollow)
            }
            ?.sortedBy { it.name } ?: emptyList()
        if (directories.isNotEmpty()) requireAllRuntimesStopped(context)
        var recovered = false
        for (directory in directories) {
            if (java.nio.file.Files.isSymbolicLink(directory.toPath()) ||
                !java.nio.file.Files.isDirectory(directory.toPath(), noFollow)) {
                throw IOException("Interrupted backup restore directory is not a safe app-private directory.")
            }
            val committedFile = File(directory, RESTORE_COMMITTED)
            val committedExists = java.nio.file.Files.exists(committedFile.toPath(), noFollow)
            if (committedExists) {
                if (!java.nio.file.Files.isRegularFile(committedFile.toPath(), noFollow)) {
                    throw IOException("Restore commit marker is not a regular file.")
                }
                if (!cleanupCommittedRestore(directory)) {
                    throw IOException("Could not clean up a committed backup restore transaction.")
                }
                continue
            }
            val journalFile = File(directory, RESTORE_JOURNAL)
            val journalExists = java.nio.file.Files.exists(journalFile.toPath(), noFollow)
            if (!journalExists) {
                val leftovers = directory.listFiles().orEmpty()
                if (leftovers.all { it.name == RESTORE_JOURNAL + ".tmp" }) {
                    directory.deleteRecursively()
                    continue
                }
                throw IOException("A restore transaction has rollback files but no durable journal.")
            }
            if (!java.nio.file.Files.isRegularFile(journalFile.toPath(), noFollow)) {
                throw IOException("Interrupted backup restore journal is not a regular file.")
            }
            if (journalFile.length() > 1_000_000L) {
                throw IOException("Interrupted backup restore journal exceeds its safety limit.")
            }
            val journal = runCatching { JSONObject(journalFile.readText(Charsets.UTF_8)) }
                .getOrElse { throw IOException("Interrupted backup restore journal is unreadable.", it) }
            if (journal.optInt("version") != 1) throw IOException("Unsupported interrupted backup restore journal.")
            restoreJournalDirectory(context, directory, "python-project", "python-project",
                journal.optBoolean("hadPython"))
            restoreJournalDirectory(context, directory, "node-project-default", "node-project-default",
                journal.optBoolean("hadNode"))

            val siteRoots = File(context.filesDir, "site-roots").absoluteFile.toPath().normalize()
            val customRoots = journal.optJSONArray("customRoots") ?: JSONArray()
            if (customRoots.length() > 100) throw IOException("Interrupted restore journal contains too many custom roots.")
            for (index in customRoots.length() - 1 downTo 0) {
                val item = customRoots.getJSONObject(index)
                val active = File(item.getString("activePath")).absoluteFile
                val backup = File(item.getString("backupPath")).absoluteFile
                val activePath = active.toPath().normalize()
                val backupPath = backup.toPath().normalize()
                val backupBase = File(directory, "custom-static").absoluteFile.toPath().normalize()
                if (activePath == siteRoots || !activePath.startsWith(siteRoots) ||
                    backupPath == backupBase || !backupPath.startsWith(backupBase) ||
                    java.nio.file.Files.isSymbolicLink(File(directory, "custom-static").toPath())) {
                    throw IOException("Interrupted backup restore contains an unsafe custom site path.")
                }
                if (java.nio.file.Files.isSymbolicLink(backup.toPath())) {
                    throw IOException("Interrupted custom site backup contains a symbolic link.")
                }
                val hadPrevious = item.optBoolean("hadPrevious")
                if (java.nio.file.Files.exists(backup.toPath(), noFollow)) {
                    if (!SiteImporter.isSafeRecoveryRelease(backup)) {
                        throw IOException("Interrupted custom site backup is invalid.")
                    }
                    deleteRestoreEntry(active)
                    if (!backup.renameTo(active)) throw IOException("Could not restore custom site root ${active.name}.")
                } else if (!hadPrevious) {
                    deleteRestoreEntry(active)
                }
            }

            if (journal.optBoolean("hadActiveSite")) {
                val previousLatest = if (journal.isNull("previousLatestRelease")) null
                    else journal.getString("previousLatestRelease")
                SiteImporter.restoreLatestReleaseIfChanged(context, previousLatest)
            } else {
                deleteRestoreEntry(File(context.filesDir, "site"))
            }

            val registry = context.getSharedPreferences("pockethost_site_registry", Context.MODE_PRIVATE)
            val registryRestored = registry.edit()
                .putString("sites", journal.getString("previousSiteRegistry")).commit()
            if (!registryRestored) throw IOException("Could not restore the previous site registry after interrupted backup restore.")
            restoreCurrentSafeSettings(context, journal.getJSONObject("previousSettings"))

            markRestoreCommitted(directory)
            if (!cleanupCommittedRestore(directory)) {
                throw IOException("Previous data was restored, but transaction cleanup is still pending.")
            }
            recovered = true
            OperationalLog.record(context, "WARN", "BACKUP_RESTORE_RECOVERED",
                "The previous app state was restored after an interrupted full backup restore.")
        }
        return recovered
    }

    private fun requireAllRuntimesStopped(context: Context) {
        val nativeStatus = runCatching { NativeEngine.status() }
            .getOrElse { throw IOException("Could not verify static hosting status.", it) }
        if (nativeStatus != "STOPPED") {
            throw IOException("Stop static hosting before exporting or restoring a full app backup.")
        }
        val pythonStatus = runCatching { PythonHostRuntime.status(context) }
            .getOrElse { throw IOException("Could not verify Python runtime status.", it) }
        if (pythonStatus != "STOPPED") {
            throw IOException("Stop the Python runtime before exporting or restoring a full app backup.")
        }
        val nodeStatus = runCatching { NodeHostRuntime.status(context) }
            .getOrElse { throw IOException("Could not verify Node.js runtime status.", it) }
        if (nodeStatus != "STOPPED") {
            throw IOException("Stop the Node.js runtime before exporting or restoring a full app backup.")
        }
    }

    private fun readSafeSettings(context: Context): JSONObject {
        val prefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        val port = prefs.getInt("port", 8080)
        val pythonPort = prefs.getInt("python_port", 8081)
        val nodePort = prefs.getInt("node_port", 3000)
        validatePorts(port, pythonPort, nodePort)
        return JSONObject()
            .put("version", 2)
            .put("port", port)
            .put("pythonPort", pythonPort)
            .put("nodePort", nodePort)
            .put("keepAwake", prefs.getBoolean("keep_awake", false))
            .put("autoStartOnBoot", prefs.getBoolean("auto_start_on_boot", false))
            .put("darkTheme", prefs.getBoolean("dark_theme", false))
    }

    private fun parseSafeSettings(file: File): SafeSettings {
        if (!file.isFile || file.length() > 16_384L) throw IOException("The backup settings manifest is missing or too large.")
        val json = JSONObject(file.readText(Charsets.UTF_8))
        if (json.optInt("version") != 2) throw IOException("Unsupported full backup settings version.")
        val settings = SafeSettings(
            json.optInt("port", 8080),
            json.optInt("pythonPort", 8081),
            json.optInt("nodePort", 3000),
            json.optBoolean("keepAwake", false),
            json.optBoolean("autoStartOnBoot", false),
            json.optBoolean("darkTheme", false)
        )
        validatePorts(settings.port, settings.pythonPort, settings.nodePort)
        return settings
    }

    private fun validatePorts(port: Int, pythonPort: Int, nodePort: Int) {
        require(port in 1024..65535 && pythonPort in 1024..65535 && nodePort in 1024..65535) {
            "The backup contains an invalid server port."
        }
        require(port != pythonPort && port != nodePort && pythonPort != nodePort) {
            "The backup contains conflicting server and runtime ports."
        }
    }

    private fun exportRoutes(
        context: Context,
        site: File,
        python: File,
        node: File,
        includePython: Boolean,
        includeNode: Boolean
    ): ExportedRoutes {
        val routes = JSONArray()
        val extraRoots = linkedMapOf<String, ExtraStaticRoot>()
        val expected = mapOf(
            "static" to site.canonicalPath,
            "python-wsgi" to python.canonicalPath,
            "node" to node.canonicalPath
        )
        val defaultArchiveRoots = mapOf("static" to "site", "python-wsgi" to "python", "node" to "node")
        val privateRoot = context.filesDir.canonicalFile.toPath()
        SiteRegistry.list(context).forEach { entry ->
            val root = File(entry.root).canonicalFile
            val canonicalRoot = root.canonicalPath
            var archiveRoot = defaultArchiveRoots[entry.runtime]
                ?: throw IOException("The backup encountered an unsupported site runtime.")
            if (canonicalRoot != expected[entry.runtime]) {
                if (entry.runtime != "static") {
                    throw IOException("Site '${entry.name}' uses a custom runtime project directory that is not supported by portable backup.")
                }
                if (java.nio.file.Files.isSymbolicLink(File(entry.root).toPath()) ||
                    !root.isDirectory || !File(root, "index.html").isFile ||
                    !root.toPath().startsWith(privateRoot) || root == context.filesDir.canonicalFile ||
                    root.toPath().startsWith(site.canonicalFile.toPath()) ||
                    site.canonicalFile.toPath().startsWith(root.toPath()) ||
                    root.toPath().startsWith(python.canonicalFile.toPath()) ||
                    python.canonicalFile.toPath().startsWith(root.toPath()) ||
                    root.toPath().startsWith(node.canonicalFile.toPath()) ||
                    node.canonicalFile.toPath().startsWith(root.toPath())) {
                    throw IOException("Custom static site '${entry.name}' must be a separate, real directory inside Pocket Host private storage and contain index.html.")
                }
                val overlapping = extraRoots.values.firstOrNull { existing ->
                    val existingPath = existing.root.canonicalFile.toPath()
                    val candidatePath = root.toPath()
                    existingPath != candidatePath &&
                        (existingPath.startsWith(candidatePath) || candidatePath.startsWith(existingPath))
                }
                if (overlapping != null) throw IOException("Custom static site roots must not overlap.")
                archiveRoot = extraRoots.values.firstOrNull { it.root.canonicalPath == canonicalRoot }?.archivePath
                    ?: "custom-static/${entry.id}"
                extraRoots.putIfAbsent(archiveRoot, ExtraStaticRoot(archiveRoot, root))
            }
            if (entry.runtime == "python-wsgi" && !includePython) {
                throw IOException("A Python hostname route exists but its project directory is missing.")
            }
            if (entry.runtime == "node" && !includeNode) {
                throw IOException("A Node.js hostname route exists but its project directory is missing.")
            }
            val hostnames = JSONArray()
            entry.hostnames.forEach(hostnames::put)
            routes.put(JSONObject()
                .put("id", entry.id)
                .put("name", entry.name)
                .put("runtime", entry.runtime)
                .put("archiveRoot", archiveRoot)
                .put("hostnames", hostnames)
                .put("enabled", entry.enabled))
        }
        return ExportedRoutes(
            JSONObject().put("version", 3).put("sites", routes),
            extraRoots.values.toList()
        )
    }

    private fun parseRoutes(
        file: File,
        context: Context,
        hasPython: Boolean,
        hasNode: Boolean
    ): ParsedRoutes {
        if (!file.isFile || file.length() > 256_000L) throw IOException("The backup route manifest is missing or too large.")
        val json = JSONObject(file.readText(Charsets.UTF_8))
        val version = json.optInt("version")
        if (version !in setOf(2, 3)) throw IOException("Unsupported full backup route manifest.")
        val array = json.optJSONArray("sites") ?: JSONArray()
        if (array.length() > 100) throw IOException("The backup contains too many site routes.")
        val roots = mapOf(
            "static" to File(context.filesDir, "site").absolutePath,
            "python-wsgi" to File(context.filesDir, "python-project").absolutePath,
            "node" to File(context.filesDir, "node-project-default").absolutePath
        )
        val customRoots = linkedMapOf<String, File>()
        val sites = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val id = item.getString("id")
            if (!id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) throw IOException("The backup contains an invalid site ID.")
            val runtime = item.getString("runtime")
            if (runtime !in roots.keys) throw IOException("The backup contains an unsupported site runtime.")
            if (runtime == "python-wsgi" && !hasPython) throw IOException("A Python route has no Python project in this backup.")
            if (runtime == "node" && !hasNode) throw IOException("A Node.js route has no Node.js project in this backup.")
            val defaultArchive = roots.getValue(runtime).substringAfterLast('/')
            val archiveRoot = if (version >= 3) item.optString("archiveRoot", defaultArchive) else defaultArchive
            val root = if (runtime == "static" && archiveRoot != "site") {
                val archiveId = archiveRoot.removePrefix("custom-static/")
                if (!archiveRoot.startsWith("custom-static/") ||
                    !archiveId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) {
                    throw IOException("The backup contains an invalid custom static root mapping.")
                }
                val parent = File(context.filesDir, "site-roots").canonicalFile
                if (!parent.toPath().startsWith(context.filesDir.canonicalFile.toPath())) {
                    throw IOException("The custom static restore path is unsafe.")
                }
                val target = File(parent, archiveId).canonicalFile
                if (!target.toPath().startsWith(parent.toPath())) throw IOException("The custom static restore path escapes app storage.")
                customRoots.putIfAbsent(archiveRoot, target)
                target.absolutePath
            } else {
                if (archiveRoot != defaultArchive) throw IOException("The backup contains an invalid project root mapping.")
                roots.getValue(runtime)
            }
            val hostArray = item.optJSONArray("hostnames") ?: JSONArray()
            if (hostArray.length() > 100) throw IOException("A site contains too many hostnames.")
            val hosts = (0 until hostArray.length()).map { hostArray.getString(it) }
            SiteRegistry.Site(
                id = id,
                name = item.getString("name"),
                runtime = runtime,
                root = root,
                hostnames = hosts,
                enabled = item.optBoolean("enabled", true)
            )
        }
        return ParsedRoutes(sites, customRoots)
    }

    private fun addTree(
        zip: ZipOutputStream,
        root: File,
        prefix: String,
        rootLimit: Long,
        counters: Counters
    ) {
        val canonicalRoot = root.canonicalFile
        var rootBytes = 0L
        var rootFiles = 0
        var rootEntries = 0
        val rootFileLimit = when {
            prefix == "site" || prefix == "python" || prefix.startsWith("custom-static/") -> 5_000
            prefix == "node" -> 10_000
            else -> MAX_FILES
        }
        fun visit(directory: File, relative: String) {
            val children = directory.listFiles()?.sortedBy { it.name }
                ?: throw IOException("Could not read project directory: ${directory.name}")
            for (child in children) {
                if (isExcludedName(child.name)) continue
                validateName(child.name)
                if (java.nio.file.Files.isSymbolicLink(child.toPath())) {
                    throw IOException("Backup does not follow symbolic links: ${child.name}")
                }
                val canonical = child.canonicalFile
                if (!canonical.toPath().startsWith(canonicalRoot.toPath())) {
                    throw IOException("A project file escapes its app-private directory.")
                }
                val childRelative = if (relative.isEmpty()) child.name else "$relative/${child.name}"
                val maximumRelativeDepth = if (prefix.startsWith("custom-static/")) MAX_DEPTH - 1 else MAX_DEPTH
                if (childRelative.split('/').size > maximumRelativeDepth) {
                    throw IOException("Project folder nesting exceeds the backup limit.")
                }
                counters.entries++
                rootEntries++
                if (prefix.startsWith("custom-static/")) {
                    counters.customStaticEntries++
                    if (counters.customStaticEntries > 10_000) {
                        throw IOException("Custom static sites contain too many combined archive entries.")
                    }
                }
                if (counters.entries > MAX_ENTRIES || rootEntries > 10_000) {
                    throw IOException("The app contains too many files and folders for backup.")
                }
                if (child.isDirectory) {
                    zip.putNextEntry(ZipEntry("$prefix/$childRelative/"))
                    zip.closeEntry()
                    visit(child, childRelative)
                } else if (child.isFile) {
                    counters.files++
                    rootFiles++
                    if (prefix.startsWith("custom-static/")) {
                        counters.customStaticFiles++
                        if (counters.customStaticFiles > 5_000) {
                            throw IOException("Custom static sites contain too many combined files.")
                        }
                    }
                    if (counters.files > MAX_FILES || rootFiles > rootFileLimit) {
                        throw IOException("$prefix project contains too many files for backup.")
                    }
                    zip.putNextEntry(ZipEntry("$prefix/$childRelative"))
                    child.inputStream().use { input ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            rootBytes += count
                            counters.bytes += count
                            if (prefix.startsWith("custom-static/")) {
                                counters.customStaticBytes += count
                                if (counters.customStaticBytes > STATIC_LIMIT) {
                                    throw IOException("Custom static sites exceed their combined backup size limit.")
                                }
                            }
                            if (rootBytes > rootLimit) throw IOException("$prefix project exceeds its backup size limit.")
                            if (counters.bytes > MAX_TOTAL_BYTES) throw IOException("The full app backup exceeds 200 MiB.")
                            zip.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        visit(canonicalRoot, "")
    }

    private fun addSmallEntry(zip: ZipOutputStream, name: String, bytes: ByteArray, counters: Counters) {
        counters.entries++
        counters.files++
        counters.bytes += bytes.size
        if (counters.entries > MAX_ENTRIES || counters.files > MAX_FILES || counters.bytes > MAX_TOTAL_BYTES) {
            throw IOException("The full app backup exceeds its safety limits.")
        }
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun decryptToArchive(context: Context, source: Uri, destination: File, passphrase: CharArray) {
        val raw = context.contentResolver.openInputStream(source)
            ?: throw IOException("The selected backup cannot be opened.")
        raw.use { input ->
            val header = DataInputStream(input)
            val magic = ByteArray(MAGIC.size)
            header.readFully(magic)
            if (!magic.contentEquals(MAGIC)) throw IOException("Unsupported Pocket Host backup format.")
            val salt = ByteArray(SALT_BYTES)
            val nonce = ByteArray(NONCE_BYTES)
            header.readFully(salt)
            header.readFully(nonce)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, nonce))
            CipherInputStream(header, cipher).use { decrypted ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val count = decrypted.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_ENCRYPTED_BYTES) throw IOException("Decrypted backup exceeds the archive safety limit.")
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
    }

    private fun extractArchive(archive: File, stage: File): Counters {
        val counters = Counters()
        val rootLimits = mapOf("site" to STATIC_LIMIT, "python" to PYTHON_LIMIT, "node" to NODE_LIMIT, "custom-static" to STATIC_LIMIT)
        val rootBytes = mutableMapOf("site" to 0L, "python" to 0L, "node" to 0L, "custom-static" to 0L)
        val rootFiles = mutableMapOf("site" to 0, "python" to 0, "node" to 0, "custom-static" to 0)
        val rootEntries = mutableMapOf("site" to 0, "python" to 0, "node" to 0, "custom-static" to 0)
        val allowedRoots = setOf("site", "python", "node", "custom-static", "settings.json", "routes.json")
        ZipInputStream(FileInputStream(archive).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                counters.entries++
                val normalized = entry.name.replace('\\', '/')
                val rootName = normalized.substringBefore('/')
                if (rootName in rootEntries) {
                    rootEntries[rootName] = (rootEntries[rootName] ?: 0) + 1
                    if ((rootEntries[rootName] ?: 0) > 10_000) {
                        throw IOException("$rootName project contains too many archive entries.")
                    }
                }
                if (counters.entries > MAX_ENTRIES) throw IOException("The backup contains too many archive entries.")
                if (normalized.startsWith('/') || normalized.contains('\u0000') || normalized.contains(':')) {
                    throw IOException("The backup contains an unsafe archive path.")
                }
                val parts = normalized.split('/').filter { it.isNotEmpty() }
                if (parts.isEmpty() || parts.any { it == "." || it == ".." } || parts[0] !in allowedRoots) {
                    throw IOException("The backup contains an unsupported or unsafe archive path.")
                }
                if (parts.size == 1 && parts[0] !in setOf("settings.json", "routes.json")) {
                    if (!entry.isDirectory && !normalized.endsWith("/")) {
                        throw IOException("A project archive entry is missing its project directory.")
                    }
                }
                parts.drop(1).forEach(::validateName)
                if (parts.size > MAX_DEPTH + 1) throw IOException("The backup contains excessive folder nesting.")
                val target = File(stage, parts.joinToString(File.separator)).canonicalFile
                if (!target.toPath().startsWith(stage.canonicalFile.toPath())) {
                    throw IOException("The backup path escapes its restore staging directory.")
                }
                val isDirectory = entry.isDirectory || normalized.endsWith("/")
                if (isDirectory) {
                    if (!target.exists() && !target.mkdirs()) throw IOException("Could not create a restore folder.")
                } else {
                    target.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw IOException("Could not create a restore folder.") }
                    if (target.exists()) throw IOException("The backup contains a duplicate archive path.")
                    counters.files++
                    val root = parts[0]
                    if (root in rootFiles) {
                        rootFiles[root] = (rootFiles[root] ?: 0) + 1
                        val rootFileLimit = if (root == "node") 10_000 else 5_000
                        if ((rootFiles[root] ?: 0) > rootFileLimit) {
                            throw IOException("$root project contains too many files.")
                        }
                    }
                    if (counters.files > MAX_FILES) throw IOException("The backup contains too many files.")
                    var entryBytes = 0L
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            counters.bytes += count
                            if (root in rootLimits) {
                                rootBytes[root] = (rootBytes[root] ?: 0L) + count
                                if ((rootBytes[root] ?: 0L) > rootLimits.getValue(root)) {
                                    throw IOException("$root project exceeds its restore size limit.")
                                }
                            } else if (entryBytes > 256_000L) {
                                throw IOException("Backup metadata exceeds its size limit.")
                            }
                            if (counters.bytes > MAX_TOTAL_BYTES) throw IOException("The backup exceeds 200 MiB uncompressed.")
                            output.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        if (!File(stage, "settings.json").isFile || !File(stage, "routes.json").isFile) {
            throw IOException("The backup is missing required settings or route metadata.")
        }
        return counters
    }

    private fun rollbackRuntimeDirectory(active: File, backup: File): Boolean = runCatching {
        if (active.exists()) active.deleteRecursively()
        if (backup.exists() && !backup.renameTo(active)) throw IOException("Could not restore ${active.name}.")
    }.isSuccess

    private fun applySafeSettings(context: Context, settings: SafeSettings) {
        val files = context.filesDir
        val saved = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
            .putInt("port", settings.port)
            .putInt("python_port", settings.pythonPort)
            .putInt("node_port", settings.nodePort)
            .putString("python_project_root", File(files, "python-project").absolutePath)
            .putString("node_project_root", File(files, "node-project-default").absolutePath)
            .putBoolean("keep_awake", settings.keepAwake)
            .putBoolean("auto_start_on_boot", settings.autoStartOnBoot)
            .putBoolean("dark_theme", settings.darkTheme)
            .putBoolean("allow_lan", false)
            .putBoolean("python_enabled", false)
            .putBoolean("node_enabled", false)
            .putBoolean("trusted_runtime_code_ack", false)
            .commit()
        if (!saved) throw IOException("Could not apply restored non-secret settings.")
    }

    private fun captureCurrentSafeSettings(context: Context): JSONObject {
        val prefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        return JSONObject()
            .put("port", prefs.getInt("port", 8080))
            .put("python_port", prefs.getInt("python_port", 8081))
            .put("node_port", prefs.getInt("node_port", 3000))
            .put("python_project_root", prefs.getString("python_project_root", File(context.filesDir, "python-project").absolutePath))
            .put("node_project_root", prefs.getString("node_project_root", File(context.filesDir, "node-project-default").absolutePath))
            .put("keep_awake", prefs.getBoolean("keep_awake", false))
            .put("auto_start_on_boot", prefs.getBoolean("auto_start_on_boot", false))
            .put("dark_theme", prefs.getBoolean("dark_theme", false))
            .put("allow_lan", prefs.getBoolean("allow_lan", false))
            .put("python_enabled", prefs.getBoolean("python_enabled", false))
            .put("node_enabled", prefs.getBoolean("node_enabled", false))
            .put("trusted_runtime_code_ack", prefs.getBoolean("trusted_runtime_code_ack", false))
    }

    private fun restoreCurrentSafeSettings(context: Context, old: JSONObject) {
        val saved = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
            .putInt("port", old.optInt("port", 8080))
            .putInt("python_port", old.optInt("python_port", 8081))
            .putInt("node_port", old.optInt("node_port", 3000))
            .putString("python_project_root", old.optString("python_project_root", File(context.filesDir, "python-project").absolutePath))
            .putString("node_project_root", old.optString("node_project_root", File(context.filesDir, "node-project-default").absolutePath))
            .putBoolean("keep_awake", old.optBoolean("keep_awake", false))
            .putBoolean("auto_start_on_boot", old.optBoolean("auto_start_on_boot", false))
            .putBoolean("dark_theme", old.optBoolean("dark_theme", false))
            .putBoolean("allow_lan", old.optBoolean("allow_lan", false))
            .putBoolean("python_enabled", old.optBoolean("python_enabled", false))
            .putBoolean("node_enabled", old.optBoolean("node_enabled", false))
            .putBoolean("trusted_runtime_code_ack", old.optBoolean("trusted_runtime_code_ack", false))
            .commit()
        if (!saved) throw IOException("Could not restore the previous settings after a failed backup restore.")
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            try {
                SecretKeySpec(encoded, "AES")
            } finally {
                encoded.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    private fun validateName(name: String) {
        if (name.isBlank() || name == "." || name == ".." || name.contains('/') ||
            name.contains('\\') || name.contains('\u0000') || name.contains(':') ||
            isExcludedName(name)) {
            throw IOException("The backup contains an unsafe or sensitive filename: $name")
        }
    }

    private fun isExcludedName(name: String): Boolean {
        val lower = name.lowercase()
        return lower in setOf(
            ".git", ".ssh", ".pockethost", ".npmrc", "id_rsa", "id_ed25519",
            "authorized_keys", "known_hosts"
        ) || lower == ".env" || lower.startsWith(".env.") ||
            lower.endsWith(".pem") || lower.endsWith(".key") ||
            lower.endsWith(".p12") || lower.endsWith(".pfx")
    }

    private class BoundedOutputStream(output: OutputStream, private val limit: Long) : FilterOutputStream(output) {
        private var written = 0L

        override fun write(value: Int) {
            ensureCapacity(1)
            out.write(value)
            written++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            ensureCapacity(length)
            out.write(buffer, offset, length)
            written += length
        }

        private fun ensureCapacity(length: Int) {
            if (length < 0 || written + length > limit) {
                throw IOException("Encrypted full app backup exceeds its 220 MiB archive limit.")
            }
        }
    }
}
