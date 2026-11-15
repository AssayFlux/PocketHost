package com.assayflux.pockethost.runtime

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.assayflux.pockethost.storage.NodeProjectImporter
import org.json.JSONObject
import java.io.File

object NodeHostRuntime {
    @Synchronized
    fun start(context: Context, projectRoot: String, port: Int = NodeHostingService.DEFAULT_PORT): String {
        require(port in 1024..65535) { "Node port must be between 1024 and 65535." }
        val root = File(projectRoot).canonicalFile
        require(root.isDirectory) { "Node project directory does not exist." }
        val entry = NodeProjectImporter.resolveEntry(root)
        val state = status(context)
        require(state !in setOf("RUNNING", "STARTING")) { "Only one Node.js application can run at a time." }

        val metadata = File(root, ".pockethost")
        require(metadata.exists() || metadata.mkdirs()) { "Cannot create Node runtime directory." }
        val preload = File(metadata, "metrics-preload.cjs")
        if (!preload.isFile) {
            context.assets.open("pockethost-node-metrics.cjs").use { input ->
                preload.outputStream().use { output -> input.copyTo(output) }
            }
            preload.setReadable(true, true)
            preload.setWritable(true, true)
        }
        val metrics = File(metadata, "metrics.json")
        metrics.delete()
        val statePrefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        val memory = android.app.ActivityManager.MemoryInfo()
        context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(memory)
        val automaticHeapMb = (memory.totalMem / (1024L * 1024L * 20L)).toInt().coerceIn(128, 384)
        val heapLimitMb = statePrefs.getInt("node_heap_mb", automaticHeapMb).coerceIn(96, 512)
        NodeRuntimeStateStore.update(context, state = "STARTING", detail = "Node.js startup requested.",
            projectRoot = root.absolutePath, metrics = JSONObject().put("source", "node"))
        val intent = Intent(context, NodeHostingService::class.java)
            .putExtra(NodeHostingService.EXTRA_ROOT, root.absolutePath)
            .putExtra(NodeHostingService.EXTRA_ENTRY, entry.absolutePath)
            .putExtra(NodeHostingService.EXTRA_PORT, port)
            .putExtra(NodeHostingService.EXTRA_HEAP_MB, heapLimitMb)
        ContextCompat.startForegroundService(context, intent)
        return "STARTING"
    }

    @Synchronized
    fun stop(context: Context): String {
        val stopped = context.stopService(Intent(context, NodeHostingService::class.java))
        if (!stopped) {
            NodeRuntimeStateStore.update(context, state = "STOPPED", detail = "Node.js service was not running.")
        }
        return "STOPPING"
    }

    fun status(context: Context): String {
        val state = NodeRuntimeStateStore.state(context)
        val manager = context.getSystemService(ActivityManager::class.java)
        val serviceAlive = runCatching {
            manager.getRunningServices(Int.MAX_VALUE).any {
                it.service.className == NodeHostingService::class.java.name
            }
        }.getOrDefault(false)
        // Never re-enter node::Start while the old :node process is still tearing down.
        if (serviceAlive && state in setOf("ERROR", "STOPPED")) return "STARTING"
        if (!serviceAlive && state in setOf("RUNNING", "STARTING")) {
            NodeRuntimeStateStore.update(context, state = "STOPPED", detail = "Node process is no longer running.")
            return "STOPPED"
        }
        return state
    }

    fun metrics(context: Context): JSONObject? {
        if (status(context) != "RUNNING") return null
        return NodeRuntimeStateStore.metrics(context)?.put("source", "node")
    }
}
