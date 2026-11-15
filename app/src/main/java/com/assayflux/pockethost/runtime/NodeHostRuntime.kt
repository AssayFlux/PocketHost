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
        val prefs = context.getSharedPreferences("pockethost_node_runtime", Context.MODE_PRIVATE)
        val statePrefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        val memory = android.app.ActivityManager.MemoryInfo()
        context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(memory)
        val automaticHeapMb = (memory.totalMem / (1024L * 1024L * 20L)).toInt().coerceIn(128, 384)
        val heapLimitMb = statePrefs.getInt("node_heap_mb", automaticHeapMb).coerceIn(96, 512)
        prefs.edit().putString("state", "STARTING").putString("project_root", root.absolutePath)
            .putString("metrics_json", JSONObject().put("source", "node").toString())
            .putLong("updated_at", System.currentTimeMillis()).apply()
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
            context.getSharedPreferences("pockethost_node_runtime", Context.MODE_PRIVATE)
                .edit().putString("state", "STOPPED").apply()
        }
        return "STOPPING"
    }

    fun status(context: Context): String {
        val prefs = context.getSharedPreferences("pockethost_node_runtime", Context.MODE_PRIVATE)
        val state = prefs.getString("state", "STOPPED") ?: "STOPPED"
        if (state in setOf("RUNNING", "STARTING")) {
            val manager = context.getSystemService(ActivityManager::class.java)
            val serviceAlive = runCatching {
                manager.getRunningServices(Int.MAX_VALUE).any {
                    it.service.className == NodeHostingService::class.java.name
                }
            }.getOrDefault(false)
            if (!serviceAlive) {
                prefs.edit().putString("state", "STOPPED").putString("detail", "Node process is no longer running.").apply()
                return "STOPPED"
            }
        }
        return state
    }

    fun metrics(context: Context): JSONObject? {
        if (status(context) != "RUNNING") return null
        val raw = context.getSharedPreferences("pockethost_node_runtime", Context.MODE_PRIVATE)
            .getString("metrics_json", null) ?: return null
        return runCatching { JSONObject(raw).put("source", "node") }.getOrNull()
    }
}
