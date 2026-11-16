package com.assayflux.pockethost.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Authenticated host-to-companion bridge. The companion owns the extracted project and
 * executes code under its own Android UID; this process only transfers bounded archives
 * and sends lifecycle commands.
 */
object RuntimeWorkerClient {
    private const val WORKER_PACKAGE = "com.assayflux.pockethost.worker"
    private const val WORKER_SERVICE = "$WORKER_PACKAGE.RuntimeWorkerService"
    private const val DESCRIPTOR = "com.assayflux.pockethost.worker.RUNTIME_WORKER"
    private const val TRANSACTION_INSTALL_PROJECT = IBinder.FIRST_CALL_TRANSACTION + 4
    private const val TRANSACTION_PYTHON_START = IBinder.FIRST_CALL_TRANSACTION + 5
    private const val TRANSACTION_PYTHON_START_SITES = IBinder.FIRST_CALL_TRANSACTION + 6
    private const val TRANSACTION_PYTHON_STOP = IBinder.FIRST_CALL_TRANSACTION + 7
    private const val TRANSACTION_PYTHON_STATUS = IBinder.FIRST_CALL_TRANSACTION + 8
    private const val TRANSACTION_PYTHON_METRICS = IBinder.FIRST_CALL_TRANSACTION + 9
    private const val TRANSACTION_NODE_START = IBinder.FIRST_CALL_TRANSACTION + 10
    private const val TRANSACTION_NODE_STOP = IBinder.FIRST_CALL_TRANSACTION + 11
    private const val TRANSACTION_NODE_STATUS = IBinder.FIRST_CALL_TRANSACTION + 12
    private const val TRANSACTION_NODE_METRICS = IBinder.FIRST_CALL_TRANSACTION + 13
    private const val TRANSACTION_SHUTDOWN = IBinder.FIRST_CALL_TRANSACTION + 14
    private const val TRANSACTION_PYTHON_CONFIGURE = IBinder.FIRST_CALL_TRANSACTION + 15
    private const val TRANSACTION_PROTOCOL_VERSION = IBinder.FIRST_CALL_TRANSACTION + 16
    private const val TRANSACTION_PID = IBinder.FIRST_CALL_TRANSACTION + 17
    private const val EXPECTED_PROTOCOL_VERSION = 1
    private const val MAX_PROJECT_BYTES = 256L * 1024L * 1024L
    private const val MAX_ARCHIVE_ENTRIES = 10_000

    private fun workerIntent() = Intent().setComponent(ComponentName(WORKER_PACKAGE, WORKER_SERVICE))

    @Volatile private var cachedBinder: IBinder? = null
    @Volatile private var cachedConnection: ServiceConnection? = null
    private val connectionLock = Any()

    private fun <T> withWorker(context: Context, operation: (IBinder) -> T): T {
        val app = context.applicationContext
        var binder = cachedBinder
        if (binder == null || !binder.isBinderAlive) {
            check(Looper.myLooper() != Looper.getMainLooper()) {
                "Starting the runtime companion must happen on a background thread."
            }
            synchronized(connectionLock) {
                binder = cachedBinder
                if (binder == null || !binder!!.isBinderAlive) {
                    cachedBinder = null
                    cachedConnection?.let { previous -> runCatching { app.unbindService(previous) } }
                    cachedConnection = null
                    val intent = workerIntent()
                    try {
                        ContextCompat.startForegroundService(app, intent)
                    } catch (error: Throwable) {
                        throw IllegalStateException("Runtime companion is not installed. Install the matching companion APK first.", error)
                    }
                    val latch = CountDownLatch(1)
                    var failure: Throwable? = null
                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, service: IBinder) {
                            cachedBinder = service
                            latch.countDown()
                        }
                        override fun onServiceDisconnected(name: ComponentName) {
                            cachedBinder = null
                            failure = IllegalStateException("Runtime companion disconnected.")
                            latch.countDown()
                        }
                        override fun onBindingDied(name: ComponentName) {
                            cachedBinder = null
                            failure = IllegalStateException("Runtime companion binding died.")
                            latch.countDown()
                        }
                        override fun onNullBinding(name: ComponentName) {
                            failure = IllegalStateException("Runtime companion returned an empty binding.")
                            latch.countDown()
                        }
                    }
                    if (!app.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                        runCatching { app.stopService(intent) }
                        throw IllegalStateException("Could not bind to the runtime companion. Check that both APKs are installed and signed with the same certificate.")
                    }
                    cachedConnection = connection
                    if (!latch.await(20, TimeUnit.SECONDS)) {
                        runCatching { app.unbindService(connection) }
                        runCatching { app.stopService(intent) }
                        cachedConnection = null
                        throw IllegalStateException("Timed out waiting for the runtime companion.")
                    }
                    if (failure != null) {
                        runCatching { app.unbindService(connection) }
                        runCatching { app.stopService(intent) }
                        cachedConnection = null
                        throw failure!!
                    }
                    binder = checkNotNull(cachedBinder) { "Runtime companion did not provide a Binder." }
                    try {
                        val version = transact(binder!!, TRANSACTION_PROTOCOL_VERSION) as Int
                        check(version == EXPECTED_PROTOCOL_VERSION) {
                            "Runtime companion protocol mismatch. Update the host and companion APKs together."
                        }
                    } catch (error: Throwable) {
                        cachedBinder = null
                        cachedConnection = null
                        runCatching { app.unbindService(connection) }
                        runCatching { app.stopService(intent) }
                        throw error
                    }
                }
            }
        }
        return operation(checkNotNull(binder))
    }

    /** Status queries must not start a foreground service or block the UI on first launch. */
    private fun cachedWorkerAvailable(): IBinder? = cachedBinder?.takeIf { it.isBinderAlive }

    private fun transact(binder: IBinder, code: Int, write: (Parcel) -> Unit = {}): Any? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            write(data)
            check(binder.transact(code, data, reply, 0)) { "Runtime companion rejected transaction $code." }
            reply.readException()
            return when (code) {
                in setOf(TRANSACTION_INSTALL_PROJECT, TRANSACTION_PYTHON_START,
                    TRANSACTION_PYTHON_START_SITES, TRANSACTION_PYTHON_STOP,
                    TRANSACTION_PYTHON_STATUS, TRANSACTION_PYTHON_METRICS, TRANSACTION_NODE_START,
                    TRANSACTION_NODE_STOP, TRANSACTION_NODE_STATUS, TRANSACTION_NODE_METRICS, TRANSACTION_SHUTDOWN, TRANSACTION_PYTHON_CONFIGURE) -> reply.readString()
                else -> reply.readInt()
            }
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun stableProjectId(root: File): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(root.canonicalPath.toByteArray(Charsets.UTF_8))
        return "site_" + digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun isSensitiveProjectPath(relativePath: String): Boolean {
        val normalized = relativePath.lowercase()
        val segments = normalized.split('/')
        val name = segments.lastOrNull().orEmpty()
        if (segments.any { it in setOf(".ssh", ".aws", ".azure", ".gnupg", ".pki") }) return true
        if (segments.size >= 2 && segments.takeLast(2) == listOf(".docker", "config.json")) return true
        if (name.startsWith(".env") || name in setOf(
                ".netrc", ".npmrc", ".pypirc", ".git-credentials", "credentials",
                "credentials.json", "secrets", "secrets.json", "service-account.json",
                "id_rsa", "id_ed25519", "known_hosts", "kubeconfig"
            )) return true
        return listOf(".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".mobileprovision")
            .any { name.endsWith(it) }
    }

    private fun createProjectArchive(context: Context, root: File): File {
        val canonicalRoot = root.canonicalFile
        require(canonicalRoot.isDirectory) { "Runtime project directory does not exist." }
        val archive = File.createTempFile("pockethost-project-", ".zip", context.cacheDir)
        var totalBytes = 0L
        var entries = 0
        try {
            ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                Files.walk(canonicalRoot.toPath()).use { paths ->
                    paths.sorted().forEach { path ->
                        if (path == canonicalRoot.toPath()) return@forEach
                        val relative = canonicalRoot.toPath().relativize(path).toString().replace(File.separatorChar, '/')
                        if (relative == ".git" || relative.startsWith(".git/") ||
                            relative == "__pycache__" || relative.startsWith("__pycache__/") ||
                            relative.startsWith(".pockethost/") || isSensitiveProjectPath(relative)) return@forEach
                        require(!Files.isSymbolicLink(path)) { "Runtime project contains a symbolic link; remove it before transfer." }
                        entries++
                        require(entries <= MAX_ARCHIVE_ENTRIES) { "Runtime project contains too many files." }
                        if (Files.isDirectory(path)) {
                            zip.putNextEntry(ZipEntry("$relative/"))
                            zip.closeEntry()
                        } else if (Files.isRegularFile(path)) {
                            val size = Files.size(path)
                            totalBytes += size
                            require(totalBytes <= MAX_PROJECT_BYTES) { "Runtime project exceeds the 256 MiB transfer limit." }
                            zip.putNextEntry(ZipEntry(relative))
                            Files.newInputStream(path).use { input ->
                                val buffer = ByteArray(32 * 1024)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    zip.write(buffer, 0, count)
                                }
                            }
                            zip.closeEntry()
                        } else {
                            throw IllegalArgumentException("Runtime project contains an unsupported filesystem entry.")
                        }
                    }
                }
            }
            return archive
        } catch (error: Throwable) {
            archive.delete()
            throw error
        }
    }

    private fun installProject(binder: IBinder, context: Context, root: File): String {
        val id = stableProjectId(root)
        val archive = createProjectArchive(context, root)
        try {
            val descriptor = ParcelFileDescriptor.open(archive, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                return withBinderTransaction(binder, TRANSACTION_INSTALL_PROJECT) { data ->
                    data.writeString(id)
                    descriptor.writeToParcel(data, 0)
                }
            } finally {
                runCatching { descriptor.close() }
            }
        } finally {
            archive.delete()
        }
    }

    private fun withBinderTransaction(binder: IBinder, code: Int, write: (Parcel) -> Unit): String {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            write(data)
            check(binder.transact(code, data, reply, 0)) { "Runtime companion rejected transaction $code." }
            reply.readException()
            return reply.readString().orEmpty()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun workerPid(context: Context): Int =
        withWorker(context) { transact(it, TRANSACTION_PID) as Int }

    fun configurePython(context: Context, workers: Int): String =
        withWorker(context) { transact(it, TRANSACTION_PYTHON_CONFIGURE) { parcel -> parcel.writeInt(workers) } as String }

    fun startPython(context: Context, projectRoot: File, port: Int): String =
        withWorker(context) { binder ->
            val root = projectRoot.canonicalFile
            val id = stableProjectId(root)
            installProject(binder, context, root)
            transact(binder, TRANSACTION_PYTHON_START) {
                it.writeString(id)
                it.writeInt(port)
            } as String
        }

    fun startPythonSites(context: Context, hostnameRoots: JSONObject, port: Int): String =
        withWorker(context) { binder ->
            val siteIds = JSONObject()
            val keys = hostnameRoots.keys()
            while (keys.hasNext()) {
                val host = keys.next()
                val root = File(hostnameRoots.getString(host)).canonicalFile
                val id = stableProjectId(root)
                installProject(binder, context, root)
                siteIds.put(host, id)
            }
            transact(binder, TRANSACTION_PYTHON_START_SITES) {
                it.writeString(siteIds.toString())
                it.writeInt(port)
            } as String
        }

    fun startNode(context: Context, projectRoot: File, entryFile: File, port: Int, heapLimitMb: Int): String =
        withWorker(context) { binder ->
            val root = projectRoot.canonicalFile
            val entry = entryFile.canonicalFile
            require(entry.toPath().startsWith(root.toPath()) && entry.isFile) {
                "Node entry file must be inside its project."
            }
            val relativeEntry = root.toPath().relativize(entry.toPath()).toString()
            val id = stableProjectId(root)
            installProject(binder, context, root)
            transact(binder, TRANSACTION_NODE_START) {
                it.writeString(id)
                it.writeString(relativeEntry)
                it.writeInt(port)
                it.writeInt(heapLimitMb)
            } as String
        }

    fun stopNode(context: Context): String =
        withWorker(context) { transact(it, TRANSACTION_NODE_STOP) as String }

    fun nodeStatus(context: Context): String {
        if (cachedWorkerAvailable() == null && Looper.myLooper() == Looper.getMainLooper()) return "STOPPED"
        return withWorker(context) { transact(it, TRANSACTION_NODE_STATUS) as String }
    }

    fun nodeMetrics(context: Context): JSONObject? {
        if (cachedWorkerAvailable() == null && Looper.myLooper() == Looper.getMainLooper()) return null
        return withWorker(context) {
            if ((transact(it, TRANSACTION_NODE_STATUS) as String) != "RUNNING") null
            else JSONObject(transact(it, TRANSACTION_NODE_METRICS) as String).put("source", "node-companion")
        }
    }

    fun shutdownWorker(context: Context): String? {
        if (cachedWorkerAvailable() == null) return null
        val app = context.applicationContext
        val result = withWorker(context) { transact(it, TRANSACTION_SHUTDOWN) as String }
        cachedBinder = null
        cachedConnection?.let { connection -> runCatching { app.unbindService(connection) } }
        cachedConnection = null
        return result
    }

    fun stopPython(context: Context): String =
        withWorker(context) { transact(it, TRANSACTION_PYTHON_STOP) as String }

    fun pythonStatus(context: Context): String {
        if (cachedWorkerAvailable() == null && Looper.myLooper() == Looper.getMainLooper()) return "STOPPED"
        return withWorker(context) { transact(it, TRANSACTION_PYTHON_STATUS) as String }
    }

    fun pythonMetrics(context: Context): JSONObject? {
        if (cachedWorkerAvailable() == null && Looper.myLooper() == Looper.getMainLooper()) return null
        return withWorker(context) {
            if ((transact(it, TRANSACTION_PYTHON_STATUS) as String) != "RUNNING") null
            else JSONObject(transact(it, TRANSACTION_PYTHON_METRICS) as String)
                .put("source", "python-companion")
        }
    }
}
