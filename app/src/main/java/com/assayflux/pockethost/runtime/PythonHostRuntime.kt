package com.assayflux.pockethost.runtime

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject

/**
 * Starts a user-provided WSGI application inside the app's embedded CPython 3.11 runtime.
 *
 * The runtime binds to loopback by default. LAN exposure must be an explicit user choice.
 * User project code executes with the Android app's process privileges: only run trusted code.
 */
object PythonHostRuntime {
    @Synchronized
    private fun python(context: Context): Python {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
        return Python.getInstance()
    }

    private fun configure(context: Context) {
        val memory = android.app.ActivityManager.MemoryInfo()
        context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(memory)
        val automaticWorkers = (memory.totalMem / (512L * 1024L * 1024L)).toInt().coerceIn(4, 16)
        val workers = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
            .getInt("python_max_workers", automaticWorkers).coerceIn(2, 16)
        python(context).getModule("pockethost_runtime").callAttr("configure", workers)
    }

    @Synchronized
    fun start(context: Context, projectRoot: String, port: Int, allowLan: Boolean = false): String {
        RuntimeTrustGate.requireAcknowledged(context)
        require(port in 1024..65535) { "Python port must be between 1024 and 65535." }
        val ownedRoot = RuntimeProjectRoot.requireOwnedProjectRoot(context, projectRoot)
        configure(context)
        return python(context).getModule("pockethost_runtime")
            .callAttr("start", ownedRoot.absolutePath, port, allowLan).toString()
    }

    @Synchronized
    fun startSites(context: Context, hostnameRootsJson: String, port: Int, allowLan: Boolean = false): String {
        RuntimeTrustGate.requireAcknowledged(context)
        require(port in 1024..65535) { "Python port must be between 1024 and 65535." }
        val requestedRoutes = JSONObject(hostnameRootsJson)
        val ownedRoutes = JSONObject()
        val keys = requestedRoutes.keys()
        while (keys.hasNext()) {
            val hostname = keys.next()
            val root = requestedRoutes.optString(hostname, "")
            val ownedRoot = RuntimeProjectRoot.requireOwnedProjectRoot(context, root)
            ownedRoutes.put(hostname, ownedRoot.absolutePath)
        }
        require(ownedRoutes.length() in 1..100) { "Python hostname registry must contain between 1 and 100 routes." }
        configure(context)
        return python(context).getModule("pockethost_runtime")
            .callAttr("start_many", ownedRoutes.toString(), port, allowLan).toString()
    }

    @Synchronized
    fun stop(context: Context): String {
        if (!Python.isStarted()) return "STOPPED"
        return Python.getInstance().getModule("pockethost_runtime").callAttr("stop").toString()
    }

    @Synchronized
    fun status(context: Context): String {
        if (!Python.isStarted()) return "STOPPED"
        return Python.getInstance().getModule("pockethost_runtime").callAttr("status").toString()
    }

    @Synchronized
    fun metrics(context: Context): JSONObject? {
        if (!Python.isStarted()) return null
        val module = Python.getInstance().getModule("pockethost_runtime")
        if (module.callAttr("status").toString() != "RUNNING") return null
        return JSONObject(module.callAttr("metrics_json").toString()).put("source", "python-wsgi")
    }
}
