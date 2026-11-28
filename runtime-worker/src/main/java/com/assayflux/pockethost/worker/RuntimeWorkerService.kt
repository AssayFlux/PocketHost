package com.assayflux.pockethost.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

/**
 * Separate-UID runtime companion. Only the signed Pocket Host host app may issue commands.
 * Project archives are unpacked into this package's private files directory; customer code
 * executes only under this companion application's Android UID.
 */
// CI checkpoint: keep host/companion protocol and runtime wiring under the Android matrix.
class RuntimeWorkerService : Service() {
    @Volatile private var probeServer: java.net.ServerSocket? = null
    private val projectsRoot by lazy { File(filesDir, "projects").apply { mkdirs() } }

    override fun onCreate() {
        super.onCreate()
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Pocket Host Runtime Worker", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, Notification.Builder(this, CHANNEL)
            .setContentTitle("AssayFlux Pocket Host")
            .setContentText("Runtime companion is available")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true).build())
        return START_STICKY
    }

    override fun onDestroy() {
        // Node runs in its own process; stop it explicitly on orderly broker teardown.
        runCatching { stopService(Intent(this, NodeHostingService::class.java)) }
        runCatching { pythonModule()?.callAttr("stop") }
        runCatching { probeServer?.close() }
        probeServer = null
        super.onDestroy()
    }

    private fun ensurePython(): com.chaquo.python.PyObject {
        if (!Python.isStarted()) Python.start(AndroidPlatform(applicationContext))
        return Python.getInstance().getModule("pockethost_runtime")
    }

    private fun pythonModule(): com.chaquo.python.PyObject? =
        if (Python.isStarted()) Python.getInstance().getModule("pockethost_runtime") else null

    private fun verifyHostCaller() {
        val callerUid = Binder.getCallingUid()
        val allowedHostPackages = setOf("com.assayflux.pockethost", "com.assayflux.webhost")
        val callerPackages = packageManager.getPackagesForUid(callerUid).orEmpty()
        val matchedHost = callerPackages.firstOrNull { host ->
            host in allowedHostPackages &&
                packageManager.checkSignatures(packageName, host) == PackageManager.SIGNATURE_MATCH
        }
        check(matchedHost != null) {
            "Only an allowlisted, matching-signed PocketHost or AssayFlux WebHost application may control the runtime companion."
        }
    }

    private fun isSensitiveProjectPath(relativePath: String): Boolean {
        val normalized = relativePath.replace('\\', '/').lowercase(java.util.Locale.ROOT)
        val segments = normalized.split('/').filter { it.isNotEmpty() }
        val name = segments.lastOrNull().orEmpty()
        if (segments.any { it in setOf(
                ".ssh", ".aws", ".azure", ".docker", ".kube", ".gnupg", ".pki",
                ".terraform", ".pulumi", "secrets"
            ) }) return true
        if (segments.windowed(2).any { it[0] == ".config" && it[1] == "gcloud" }) return true
        if (name.startsWith(".env") || name in setOf(
                ".netrc", ".npmrc", ".pypirc", ".git-credentials", ".gitconfig",
                "credentials", "credentials.json", "application_default_credentials.json",
                "service-account.json", "serviceaccount.json", "service_account.json",
                "secrets.json", "id_rsa", "id_dsa", "id_ed25519", "id_ecdsa",
                "known_hosts", "kubeconfig", "identity", "authorized_keys"
            )) return true
        if ((name.startsWith("service-account-") || name.startsWith("serviceaccount-") ||
                name.startsWith("firebase-adminsdk")) && name.endsWith(".json")) return true
        return listOf(".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".mobileprovision")
            .any { name.endsWith(it) }
    }


    /**
     * Keep CMS runtime-owned state when the host refreshes its transferred source bundle.
     * SQLite, session secrets, setup state and uploads live under instance/ and must not be
     * replaced by a stale host-side asset copy during the next project install.
     */
    private fun preserveRuntimeState(existingRoot: File, stagingRoot: File) {
        val source = File(existingRoot, "instance")
        if (!source.exists()) return
        check(source.isDirectory && !java.nio.file.Files.isSymbolicLink(source.toPath())) {
            "Existing CMS instance state is not a regular directory."
        }
        val target = File(stagingRoot, "instance")
        if (target.exists()) check(target.deleteRecursively()) { "Could not clear staged instance data." }
        val canonicalTarget = target.canonicalFile
        var entries = 0
        var bytes = 0L
        source.walkTopDown().forEach { item ->
            check(!java.nio.file.Files.isSymbolicLink(item.toPath())) {
                "Existing CMS instance contains a symbolic link; refusing to replace the project."
            }
            val relative = source.toPath().relativize(item.toPath()).toString()
            val output = if (relative.isEmpty()) canonicalTarget else File(canonicalTarget, relative).canonicalFile
            check(output.toPath().startsWith(canonicalTarget.toPath())) {
                "Existing CMS instance contains an unsafe path."
            }
            entries++
            check(entries <= MAX_ARCHIVE_ENTRIES) { "Existing CMS instance contains too many entries." }
            if (item.isDirectory) {
                check(output.mkdirs() || output.isDirectory) { "Could not preserve CMS instance directory." }
            } else {
                check(item.isFile) { "Existing CMS instance contains an unsupported filesystem entry." }
                val size = item.length()
                check(size <= MAX_PERSISTENT_STATE_BYTES - bytes) {
                    "CMS persistent instance data exceeds the 512 MiB preservation limit."
                }
                bytes += size
                output.parentFile?.mkdirs()
                item.copyTo(output, overwrite = true)
            }
        }
    }

    @Synchronized
    private fun installProject(siteId: String, descriptor: ParcelFileDescriptor): String {
        require(siteId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid project identifier." }
        val destination = File(projectsRoot, siteId).canonicalFile
        check(destination.toPath().startsWith(projectsRoot.canonicalFile.toPath())) { "Invalid project destination." }
        check(pythonModule()?.callAttr("is_project_active", destination.absolutePath)?.toString() != "True") {
            "Stop the Python site before replacing its transferred project."
        }
        val nodeState = NodeRuntimeStateStore.state(this)
        val nodeProject = NodeRuntimeStateStore.projectRoot(this)
        check(nodeState !in setOf("RUNNING", "STARTING", "STOPPING") || nodeProject != destination.absolutePath) {
            "Stop the Node.js site before replacing its transferred project."
        }
        val backup = File(projectsRoot, ".$siteId-previous")
        // Recover an interrupted replacement before accepting a new transfer.
        if (!destination.exists() && backup.exists()) {
            check(backup.renameTo(destination)) {
                "Could not recover the previous project; its backup remains at ${backup.name}."
            }
        } else if (destination.exists() && backup.exists()) {
            check(backup.deleteRecursively()) {
                "Could not clean the previous project backup before replacement."
            }
        }
        val staging = File(projectsRoot, ".$siteId-staging")
        if (staging.exists()) {
            check(staging.deleteRecursively()) { "Could not clear stale project staging data." }
        }
        check(staging.mkdirs() || staging.isDirectory) { "Could not create project staging directory." }
        var entries = 0
        var treeEntries = 0
        var totalBytes = 0L
        // Track explicit and implicit directories as well as files. ZIP archives can
        // contain duplicate names or file/directory collisions that otherwise overwrite
        // earlier entries during extraction.
        val archivePaths = HashMap<String, Boolean>()
        val portableNames = HashMap<String, String>()
        val explicitEntries = HashSet<String>()
        try {
            ZipInputStream(FileInputStream(descriptor.fileDescriptor).buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    require(entries <= MAX_ARCHIVE_ENTRIES) { "Project archive contains too many entries." }
                    val rawName = entry.name.replace('\\', '/')
                    val isDirectory = entry.isDirectory || rawName.endsWith("/")
                    val name = rawName.trimEnd('/')
                    require(name.isNotBlank() && !name.startsWith("/") &&
                        !name.contains('\u0000') &&
                        name.split('/').none { it.isBlank() || it == ".." || it == "." } &&
                        !isSensitiveProjectPath(name)) {
                        "Project archive contains an unsafe path."
                    }
                    require(name.length <= MAX_PROJECT_PATH_CHARS &&
                        name.split('/').size <= MAX_PROJECT_PATH_DEPTH) {
                        "Project archive path exceeds the 1,024-character or 32-level limit."
                    }
                    val segments = name.split('/')
                    var parent = ""
                    for (segment in segments.dropLast(1)) {
                        parent = if (parent.isEmpty()) segment else "$parent/$segment"
                        val knownParent = archivePaths[parent]
                        require(knownParent != false) { "Project archive has a file used as a directory." }
                        val portableParent = parent.lowercase(java.util.Locale.ROOT)
                        val previousPortableParent = portableNames.putIfAbsent(portableParent, parent)
                        require(previousPortableParent == null || previousPortableParent == parent) {
                            "Project archive contains directory paths that differ only by letter case."
                        }
                        if (archivePaths.putIfAbsent(parent, true) == null) {
                            treeEntries++
                            require(treeEntries <= MAX_ARCHIVE_ENTRIES) {
                                "Project archive expands to too many files and directories."
                            }
                        }
                    }
                    require(explicitEntries.add(name)) { "Project archive contains duplicate paths." }
                    val previousType = archivePaths.putIfAbsent(name, isDirectory)
                    require(previousType == null || (previousType == true && isDirectory)) {
                        "Project archive contains conflicting file and directory paths."
                    }
                    if (previousType == null) {
                        treeEntries++
                        require(treeEntries <= MAX_ARCHIVE_ENTRIES) {
                            "Project archive expands to too many files and directories."
                        }
                    }
                    val portableName = name.lowercase(java.util.Locale.ROOT)
                    val previousPortableName = portableNames.putIfAbsent(portableName, name)
                    require(previousPortableName == null || previousPortableName == name) {
                        "Project archive contains paths that differ only by letter case."
                    }
                    val target = File(staging, name).canonicalFile
                    require(target.toPath().startsWith(staging.canonicalFile.toPath())) {
                        "Project archive path escapes its destination."
                    }
                    if (isDirectory) {
                        require(!target.exists() || target.isDirectory) {
                            "Project archive has a directory/file path collision."
                        }
                        require(target.mkdirs() || target.isDirectory) { "Could not create project directory." }
                    } else {
                        require(!target.exists()) { "Project archive would overwrite an existing path." }
                        target.parentFile?.let { parentDir ->
                            require(parentDir.mkdirs() || parentDir.isDirectory) { "Could not create project directory." }
                        }
                        var fileBytes = 0L
                        require(target.createNewFile()) { "Could not create project file." }
                        target.outputStream().buffered().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                fileBytes += count
                                totalBytes += count
                                require(fileBytes <= MAX_FILE_BYTES && totalBytes <= MAX_PROJECT_BYTES) {
                                    "Project archive exceeds the permitted extraction size."
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            if (destination.exists()) preserveRuntimeState(destination, staging)
            val movedPrevious = if (destination.exists()) {
                check(destination.renameTo(backup)) { "Could not stage existing project for replacement." }
                true
            } else false
            if (!staging.renameTo(destination)) {
                if (movedPrevious && backup.exists() && !backup.renameTo(destination)) {
                    error("Could not activate the new project or restore the previous project; backup remains at ${backup.name}.")
                }
                error("Could not activate transferred project; the previous project was restored.")
            }
            // A leftover backup is recoverable and must not invalidate the activated tree.
            if (movedPrevious) runCatching { backup.deleteRecursively() }
            return destination.absolutePath
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        } finally {
            runCatching { descriptor.close() }
        }
    }


    private fun pythonProjectStatus(siteId: String): String {
        require(siteId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid project identifier." }
        val root = File(projectsRoot, siteId).canonicalFile
        check(root.toPath().startsWith(projectsRoot.canonicalFile.toPath())) { "Invalid project root." }
        val active = pythonModule()?.callAttr("is_project_active", root.absolutePath)?.toString() == "True"
        return if (active) "RUNNING" else "STOPPED"
    }

    private fun startPython(siteId: String, port: Int): String {
        require(siteId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid Python project identifier." }
        require(port in 1024..65535) { "Invalid Python listener port." }
        val root = File(projectsRoot, siteId).canonicalFile
        require(root.isDirectory && root.toPath().startsWith(projectsRoot.canonicalFile.toPath())) {
            "Transferred Python project was not found."
        }
        synchronized(this) {
            return ensurePython().callAttr("start", root.absolutePath, port).toString()
        }
    }

    private fun startPythonSites(hostsJson: String, port: Int): String {
        require(port in 1024..65535) { "Invalid Python listener port." }
        val incoming = JSONObject(hostsJson)
        require(incoming.length() in 1..100) { "Python route registry must contain 1 to 100 sites." }
        val owned = JSONObject()
        val keys = incoming.keys()
        while (keys.hasNext()) {
            val hostname = keys.next()
            val siteId = incoming.getString(hostname)
            require(siteId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid project identifier." }
            val root = File(projectsRoot, siteId).canonicalFile
            require(root.isDirectory && root.toPath().startsWith(projectsRoot.canonicalFile.toPath())) {
                "Transferred Python project was not found for $hostname."
            }
            owned.put(hostname, root.absolutePath)
        }
        synchronized(this) {
            return ensurePython().callAttr("start_many", owned.toString(), port).toString()
        }
    }

    @Synchronized
    private fun startNode(siteId: String, entryRelative: String, port: Int, heapLimitMb: Int): String {
        require(siteId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid Node project identifier." }
        require(port in 1024..65535 && heapLimitMb in 96..512) { "Invalid Node runtime limits." }
        val root = File(projectsRoot, siteId).canonicalFile
        require(root.isDirectory && root.toPath().startsWith(projectsRoot.canonicalFile.toPath())) {
            "Transferred Node project was not found."
        }
        val entry = File(root, entryRelative).canonicalFile
        require(entry.toPath().startsWith(root.toPath()) && entry.isFile) {
            "Node entry file is outside the transferred project or missing."
        }
        val current = NodeRuntimeStateStore.state(this)
        require(current !in setOf("RUNNING", "STARTING", "STOPPING")) { "Only one Node.js application can run at a time." }
        NodeRuntimeStateStore.update(this, state = "STARTING", detail = "Node.js startup requested.")
        val intent = Intent(this, NodeHostingService::class.java)
            .putExtra(NodeHostingService.EXTRA_ROOT, siteId)
            .putExtra(NodeHostingService.EXTRA_ENTRY, entryRelative)
            .putExtra(NodeHostingService.EXTRA_PORT, port)
            .putExtra(NodeHostingService.EXTRA_HEAP_MB, heapLimitMb)
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        return "STARTING"
    }

    @Synchronized
    private fun stopNode(): String {
        NodeRuntimeStateStore.update(this, state = "STOPPING", detail = "Node.js shutdown requested.")
        stopService(Intent(this, NodeHostingService::class.java))
        return "STOPPING"
    }

    private fun nodeStatus(): String {
        val state = NodeRuntimeStateStore.state(this)
        val alive = runCatching {
            getSystemService(android.app.ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
                .any { it.service.className == NodeHostingService::class.java.name }
        }.getOrDefault(false)
        if (alive && state in setOf("ERROR", "STOPPED")) return "STARTING"
        if (!alive && state in setOf("RUNNING", "STARTING", "STOPPING")) {
            NodeRuntimeStateStore.update(this, state = "STOPPED", detail = "Node.js worker is no longer running.")
            return "STOPPED"
        }
        return state
    }

    private val workerBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(DESCRIPTOR)
                return true
            }
            verifyHostCaller()
            data.enforceInterface(DESCRIPTOR)
            try {
                val result: Any? = when (code) {
                    TRANSACTION_PROTOCOL_VERSION -> PROTOCOL_VERSION
                    TRANSACTION_UID -> Process.myUid()
                    TRANSACTION_CAN_READ -> {
                        val path = data.readString().orEmpty()
                        if (runCatching { File(path).canRead() }.getOrDefault(false)) 1 else 0
                    }
                    TRANSACTION_LOOPBACK_BIND -> startLoopbackProbeServer()
                    TRANSACTION_LOOPBACK_CONNECT -> {
                        val port = data.readInt()
                        if (runCatching {
                            java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", port), 1_000) }
                            true
                        }.getOrDefault(false)) 1 else 0
                    }
                    TRANSACTION_INSTALL_PROJECT -> {
                        val siteId = data.readString().orEmpty()
                        val fd = ParcelFileDescriptor.CREATOR.createFromParcel(data)
                        installProject(siteId, fd)
                    }
                    TRANSACTION_PYTHON_CONFIGURE -> {
                        val workers = data.readInt()
                        require(workers in 2..16) { "Python worker limit must be between 2 and 16." }
                        synchronized(this@RuntimeWorkerService) { ensurePython().callAttr("configure", workers) }
                        "CONFIGURED"
                    }
                    TRANSACTION_PYTHON_START -> startPython(data.readString().orEmpty(), data.readInt())
                    TRANSACTION_PYTHON_START_SITES -> startPythonSites(data.readString().orEmpty(), data.readInt())
                    TRANSACTION_PYTHON_STOP -> synchronized(this@RuntimeWorkerService) {
                        pythonModule()?.callAttr("stop")?.toString() ?: "STOPPED"
                    }
                    TRANSACTION_PYTHON_STATUS -> pythonModule()?.callAttr("status")?.toString() ?: "STOPPED"
                    TRANSACTION_PYTHON_PROJECT_STATUS -> pythonProjectStatus(data.readString().orEmpty())
                    TRANSACTION_PYTHON_METRICS -> pythonModule()?.callAttr("metrics_json")?.toString() ?: "{}"
                    TRANSACTION_NODE_START -> startNode(
                        data.readString().orEmpty(), data.readString().orEmpty(), data.readInt(), data.readInt()
                    )
                    TRANSACTION_NODE_STOP -> stopNode()
                    TRANSACTION_NODE_STATUS -> nodeStatus()
                    TRANSACTION_NODE_METRICS -> NodeRuntimeStateStore.metrics(this@RuntimeWorkerService)?.toString() ?: "{}"
                    TRANSACTION_SHUTDOWN -> synchronized(this@RuntimeWorkerService) {
                        runCatching { pythonModule()?.callAttr("stop") }
                        runCatching { stopService(Intent(this@RuntimeWorkerService, NodeHostingService::class.java)) }
                        stopSelf()
                        "STOPPING"
                    }
                    else -> return super.onTransact(code, data, reply, flags)
                }
                reply?.writeNoException()
                when (result) {
                    is Int -> reply?.writeInt(result)
                    is String -> reply?.writeString(result)
                }
                return true
            } catch (error: Throwable) {
                reply?.writeException(IllegalStateException(error.message ?: "Runtime companion request failed."))
                return true
            }
        }
    }

    private fun startLoopbackProbeServer(): Int = runCatching {
        runCatching { probeServer?.close() }
        val server = java.net.ServerSocket().apply {
            reuseAddress = true
            bind(java.net.InetSocketAddress(java.net.InetAddress.getByName("127.0.0.1"), 0))
        }
        probeServer = server
        Thread({
            runCatching {
                server.accept().use { client ->
                    client.getOutputStream().apply {
                        write("PocketHost companion loopback OK\n".toByteArray())
                        flush()
                    }
                }
            }
            if (probeServer === server) probeServer = null
            runCatching { server.close() }
        }, "PocketHost-Worker-Probe").apply { isDaemon = true; start() }
        server.localPort
    }.getOrDefault(-1)

    override fun onBind(intent: Intent?): IBinder = workerBinder

    companion object {
        const val DESCRIPTOR = "com.assayflux.pockethost.worker.RUNTIME_WORKER"
        const val PROTOCOL_VERSION = 1
        const val TRANSACTION_UID = IBinder.FIRST_CALL_TRANSACTION
        const val TRANSACTION_CAN_READ = IBinder.FIRST_CALL_TRANSACTION + 1
        const val TRANSACTION_LOOPBACK_BIND = IBinder.FIRST_CALL_TRANSACTION + 2
        const val TRANSACTION_LOOPBACK_CONNECT = IBinder.FIRST_CALL_TRANSACTION + 3
        const val TRANSACTION_INSTALL_PROJECT = IBinder.FIRST_CALL_TRANSACTION + 4
        const val TRANSACTION_PYTHON_START = IBinder.FIRST_CALL_TRANSACTION + 5
        const val TRANSACTION_PYTHON_START_SITES = IBinder.FIRST_CALL_TRANSACTION + 6
        const val TRANSACTION_PYTHON_STOP = IBinder.FIRST_CALL_TRANSACTION + 7
        const val TRANSACTION_PYTHON_STATUS = IBinder.FIRST_CALL_TRANSACTION + 8
        const val TRANSACTION_PYTHON_METRICS = IBinder.FIRST_CALL_TRANSACTION + 9
        const val TRANSACTION_NODE_START = IBinder.FIRST_CALL_TRANSACTION + 10
        const val TRANSACTION_NODE_STOP = IBinder.FIRST_CALL_TRANSACTION + 11
        const val TRANSACTION_NODE_STATUS = IBinder.FIRST_CALL_TRANSACTION + 12
        const val TRANSACTION_NODE_METRICS = IBinder.FIRST_CALL_TRANSACTION + 13
        const val TRANSACTION_SHUTDOWN = IBinder.FIRST_CALL_TRANSACTION + 14
        const val TRANSACTION_PYTHON_CONFIGURE = IBinder.FIRST_CALL_TRANSACTION + 15
        const val TRANSACTION_PROTOCOL_VERSION = IBinder.FIRST_CALL_TRANSACTION + 16
        const val TRANSACTION_PYTHON_PROJECT_STATUS = IBinder.FIRST_CALL_TRANSACTION + 17
        private const val CHANNEL = "pockethost-runtime-worker"
        private const val NOTIFICATION_ID = 41
        const val MAX_PERSISTENT_STATE_BYTES = 512L * 1024L * 1024L
        private const val MAX_ARCHIVE_ENTRIES = 10_000
        private const val MAX_FILE_BYTES = 64L * 1024L * 1024L
        private const val MAX_PROJECT_BYTES = 256L * 1024L * 1024L
        private const val MAX_PROJECT_PATH_DEPTH = 32
        private const val MAX_PROJECT_PATH_CHARS = 1_024
    }
}
