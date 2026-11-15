package com.assayflux.pockethost.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Debug
import android.os.IBinder
import android.os.Process
import android.os.Handler
import android.os.Looper
import com.assayflux.pockethost.diagnostics.OperationalLog
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/** Node.js runs once in a dedicated Android process because V8 cannot be safely restarted in-process. */
class NodeHostingService : Service() {
    @Volatile private var activeProjectRoot: String? = null
    @Volatile private var stopping = false
    @Volatile private var nodeThread: Thread? = null
    @Volatile private var monitorThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "PocketHost Node.js", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification("Starting Node.js runtime…"))
        val root = intent?.getStringExtra(EXTRA_ROOT)
        val entry = intent?.getStringExtra(EXTRA_ENTRY)
        val port = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT) ?: DEFAULT_PORT
        val heapLimitMb = intent?.getIntExtra(EXTRA_HEAP_MB, DEFAULT_HEAP_MB) ?: DEFAULT_HEAP_MB
        if (root.isNullOrBlank() || entry.isNullOrBlank() || port !in 1024..65535 || heapLimitMb !in 96..512) {
            setState("ERROR", "Invalid Node runtime configuration.")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (nodeThread?.isAlive == true) return START_NOT_STICKY

        stopping = false
        setState("STARTING", "Launching Node.js.")
        val rootFile = File(root).canonicalFile
        val entryFile = File(entry).canonicalFile
        activeProjectRoot = rootFile.absolutePath
        NodeRuntimeStateStore.update(this, state = "STARTING", detail = "Launching Node.js.", projectRoot = rootFile.absolutePath)
        if (!entryFile.toPath().startsWith(rootFile.toPath()) || !entryFile.isFile) {
            setState("ERROR", "Node entry file is outside the project or missing.")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val worker = Thread({
            try {
                val result = NodeBridge.start(rootFile.absolutePath, entryFile.absolutePath, File(noBackupFilesDir, "node-runtime").absolutePath, port, heapLimitMb)
                if (!stopping) setState(if (result == 0) "STOPPED" else "ERROR", "Node.js exited with code $result.")
            } catch (error: Throwable) {
                if (!stopping) {
                    OperationalLog.record(this, "ERROR", "NODE_RUNTIME_CRASH", error.message ?: "Node.js failed to start.")
                    setState("ERROR", error.message ?: "Node.js failed to start.")
                }
            } finally {
                if (!stopping) Handler(Looper.getMainLooper()).post { stopSelf(startId) }
            }
        }, "PocketHost-Node-Main")
        nodeThread = worker
        worker.start()
        startReadinessMonitor(port)
        return START_NOT_STICKY
    }

    private fun startReadinessMonitor(port: Int) {
        monitorThread = Thread({
            val deadline = System.currentTimeMillis() + 45_000
            var ready = false
            while (!stopping && nodeThread?.isAlive == true && System.currentTimeMillis() < deadline) {
                if (isListening(port)) {
                    ready = true
                    setState("RUNNING", "Node.js is listening on loopback port $port.")
                    break
                }
                sampleMetrics()
                try { Thread.sleep(500) } catch (_: InterruptedException) { break }
            }
            if (!ready && !stopping && nodeThread?.isAlive == true) {
                setState("ERROR", "Node.js did not listen on port $port within 45 seconds.")
                Handler(Looper.getMainLooper()).post { stopSelf() }
                return@Thread
            }
            while (!stopping && nodeThread?.isAlive == true) {
                sampleMetrics()
                try { Thread.sleep(2_000) } catch (_: InterruptedException) { break }
            }
        }, "PocketHost-Node-Monitor").apply { start() }
    }

    private fun isListening(port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 400)
            true
        }
    } catch (_: Exception) { false }

    private fun sampleMetrics() {
        val root = activeProjectRoot ?: return
        val file = File(noBackupFilesDir, "node-runtime/metrics.json")
        val metrics = runCatching { if (file.isFile) JSONObject(file.readText()) else JSONObject() }.getOrDefault(JSONObject())
        metrics.put("source", "node")
            .put("processPssKb", Debug.getPss())
            .put("nativeHeapBytes", Debug.getNativeHeapAllocatedSize())
            .put("sampledAt", System.currentTimeMillis())
        NodeRuntimeStateStore.update(this, metrics = metrics)
    }

    private fun setState(state: String, detail: String) {
        NodeRuntimeStateStore.update(this, state = state, detail = detail, projectRoot = activeProjectRoot)
        OperationalLog.record(this, if (state == "ERROR") "ERROR" else "INFO", "NODE_RUNTIME_$state", detail)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(detail))
    }

    override fun onDestroy() {
        stopping = true
        monitorThread?.interrupt()
        setState("STOPPED", "Node.js service stopped.")
        // Node 24's mobile embedding supports one Start() call per process. Killing only
        // this isolated :node process is the safe, deterministic stop/recovery boundary.
        Process.killProcess(Process.myPid())
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(message: String): Notification =
        Notification.Builder(this, CHANNEL)
            .setContentTitle("AssayFlux PocketHost · Node.js")
            .setContentText(message.take(180))
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .build()

    companion object {
        const val EXTRA_ROOT = "project_root"
        const val EXTRA_ENTRY = "entry_file"
        const val EXTRA_PORT = "port"
        const val EXTRA_HEAP_MB = "heap_limit_mb"
        const val DEFAULT_HEAP_MB = 192
        const val DEFAULT_PORT = 3000
        private const val CHANNEL = "pockethost-node"
        private const val NOTIFICATION_ID = 2
    }
}
