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
class RuntimeWorkerService : Service() {
    @Volatile private var probeServer: java.net.ServerSocket? = null
    private val runtimeLock = Any()
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
        runCatching { pythonModule()?.callAttr("stop") }
        runCatching { probeServer?.close() }
        probeServer = null
        super.onDestroy()
    }

    private fun ensurePython(): com.chaquo.python.PyObject {
        if (!Python.isStarted()) Python.start(AndroidPlatform(applicationContext))
        val module = Python.getInstance().getModule("pockethost_runtime")
        module.callAttr("configure", 8)
        return module
    }

    private fun pythonModule(): com.chaquo.python.PyObject? =
        if (Python.isStarted()) Python.getInstance().getModule("pockethost_runtime") else null

    private fun verifyHostCaller() {
        val callerUid = Binder.getCallingUid()
        val hostPackage = "com.assayflux.pockethost"
        val callerPackages = packageManager.getPackagesForUid(callerUid).orEmpty()
        check(hostPackage in callerPackages) { "Only AssayFlux Pocket Host may control the runtime companion." }
        check(packageManager.checkSignatures(packageName, hostPackage) == PackageManager.SIGNATURE_MATCH) {
            "Runtime companion and host application signatures do not match."
        }
    }

    private fun installProject(siteId: String, descriptor: ParcelFileDescriptor): String {
        require(siteId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid project identifier." }
        val destination = File(projectsRoot, siteId).canonicalFile
        check(destination.toPath().startsWith(projectsRoot.canonicalFile.toPath())) { "Invalid project destination." }
        val staging = File(projectsRoot, ".$siteId-staging").apply {
            deleteRecursively()
            mkdirs()
        }
        var entries = 0
        var totalBytes = 0L
        try {
            ZipInputStream(FileInputStream(descriptor.fileDescriptor).buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    require(entries <= MAX_ARCHIVE_ENTRIES) { "Project archive contains too many entries." }
                    val name = entry.name.replace('\\\\', '/')
                    require(name.isNotBlank() && !name.startsWith("/") &&
                        name.split('/').none { it == ".." || it == "." || it.isEmpty() }) {
                        "Project archive contains an unsafe path."
                    }
                    val target = File(staging, name).canonicalFile
                    require(target.toPath().startsWith(staging.canonicalFile.toPath())) {
                        "Project archive path escapes its destination."
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        var fileBytes = 0L
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
            val backup = File(projectsRoot, ".$siteId-previous")
            backup.deleteRecursively()
            if (destination.exists()) check(destination.renameTo(backup)) { "Could not stage existing project for replacement." }
            if (!staging.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                error("Could not activate transferred project.")
            }
            backup.deleteRecursively()
            return destination.absolutePath
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        } finally {
            runCatching { descriptor.close() }
        }
    }

    private fun startPython(siteId: String, port: Int): String {
        require(port in 1024..65535) { "Invalid Python listener port." }
        val root = File(projectsRoot, siteId).canonicalFile
        require(root.isDirectory && root.toPath().startsWith(projectsRoot.canonicalFile.toPath())) {
            "Transferred Python project was not found."
        }
        synchronized(runtimeLock) {
            return ensurePython().callAttr("start", root.absolutePath, port, false).toString()
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
        synchronized(runtimeLock) {
            return ensurePython().callAttr("start_many", owned.toString(), port, false).toString()
        }
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
                when (code) {
                    TRANSACTION_UID -> reply?.writeInt(Process.myUid())
                    TRANSACTION_CAN_READ -> {
                        val path = data.readString().orEmpty()
                        reply?.writeInt(if (runCatching { File(path).canRead() }.getOrDefault(false)) 1 else 0)
                    }
                    TRANSACTION_INSTALL_PROJECT -> {
                        val siteId = data.readString().orEmpty()
                        val fd = ParcelFileDescriptor.CREATOR.createFromParcel(data)
                        reply?.writeString(installProject(siteId, fd))
                    }
                    TRANSACTION_PYTHON_START -> reply?.writeString(startPython(data.readString().orEmpty(), data.readInt()))
                    TRANSACTION_PYTHON_START_SITES -> reply?.writeString(startPythonSites(data.readString().orEmpty(), data.readInt()))
                    TRANSACTION_PYTHON_STOP -> reply?.writeString(synchronized(runtimeLock) {
                        pythonModule()?.callAttr("stop")?.toString() ?: "STOPPED"
                    })
                    TRANSACTION_PYTHON_STATUS -> reply?.writeString(
                        pythonModule()?.callAttr("status")?.toString() ?: "STOPPED"
                    )
                    TRANSACTION_PYTHON_METRICS -> reply?.writeString(
                        pythonModule()?.callAttr("metrics_json")?.toString() ?: "{}"
                    )
                    else -> return super.onTransact(code, data, reply, flags)
                }
                return true
            } catch (error: Throwable) {
                reply?.writeException(IllegalStateException(error.message ?: "Runtime companion request failed."))
                return true
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = workerBinder

    companion object {
        const val DESCRIPTOR = "com.assayflux.pockethost.worker.RUNTIME_WORKER"
        const val TRANSACTION_UID = IBinder.FIRST_CALL_TRANSACTION
        const val TRANSACTION_CAN_READ = IBinder.FIRST_CALL_TRANSACTION + 1
        const val TRANSACTION_INSTALL_PROJECT = IBinder.FIRST_CALL_TRANSACTION + 2
        const val TRANSACTION_PYTHON_START = IBinder.FIRST_CALL_TRANSACTION + 3
        const val TRANSACTION_PYTHON_START_SITES = IBinder.FIRST_CALL_TRANSACTION + 4
        const val TRANSACTION_PYTHON_STOP = IBinder.FIRST_CALL_TRANSACTION + 5
        const val TRANSACTION_PYTHON_STATUS = IBinder.FIRST_CALL_TRANSACTION + 6
        const val TRANSACTION_PYTHON_METRICS = IBinder.FIRST_CALL_TRANSACTION + 7
        private const val CHANNEL = "pockethost-runtime-worker"
        private const val NOTIFICATION_ID = 41
        private const val MAX_ARCHIVE_ENTRIES = 10_000
        private const val MAX_FILE_BYTES = 64L * 1024L * 1024L
        private const val MAX_PROJECT_BYTES = 256L * 1024L * 1024L
    }
}
