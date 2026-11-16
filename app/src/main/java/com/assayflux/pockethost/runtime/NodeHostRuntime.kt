package com.assayflux.pockethost.runtime

import android.content.Context
import com.assayflux.pockethost.storage.NodeProjectImporter
import org.json.JSONObject
import java.io.File

/** Host-side lifecycle facade; Node.js executes only inside the separate-UID companion APK. */
object NodeHostRuntime {
    const val DEFAULT_PORT = 3000
    private const val DEFAULT_HEAP_MB = 192

    @Synchronized
    fun start(context: Context, projectRoot: String, port: Int = DEFAULT_PORT): String {
        RuntimeTrustGate.requireAcknowledged(context)
        require(port in 1024..65535) { "Node port must be between 1024 and 65535." }
        val root = RuntimeProjectRoot.requireOwnedProjectRoot(context, projectRoot)
        val entry = NodeProjectImporter.resolveEntry(root)
        val state = status(context)
        require(state !in setOf("RUNNING", "STARTING")) { "Only one Node.js application can run at a time." }

        val memory = android.app.ActivityManager.MemoryInfo()
        context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(memory)
        val automaticHeapMb = (memory.totalMem / (1024L * 1024L * 20L)).toInt().coerceIn(128, 384)
        val heapLimitMb = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
            .getInt("node_heap_mb", automaticHeapMb).coerceIn(96, 512)
        return RuntimeWorkerClient.startNode(context, root, entry, port, heapLimitMb)
    }

    @Synchronized
    fun stop(context: Context): String =
        runCatching { RuntimeWorkerClient.stopNode(context) }.getOrDefault("STOPPED")

    fun status(context: Context): String =
        runCatching { RuntimeWorkerClient.nodeStatus(context) }.getOrDefault("STOPPED")

    fun metrics(context: Context): JSONObject? =
        runCatching { RuntimeWorkerClient.nodeMetrics(context) }.getOrNull()
}
