package com.assayflux.pockethost.runtime

import android.content.Context
import org.json.JSONObject

/**
 * Host-side facade for CPython running inside the separately installed runtime companion.
 * Customer WSGI code is transferred into companion-owned storage and never executed by the
 * main Pocket Host application UID.
 */
object PythonHostRuntime {
    @Synchronized
    fun start(context: Context, projectRoot: String, port: Int, allowLan: Boolean = false): String {
        RuntimeTrustGate.requireAcknowledged(context)
        require(port in 1024..65535) { "Python port must be between 1024 and 65535." }
        val ownedRoot = RuntimeProjectRoot.requireOwnedProjectRoot(context, projectRoot)
        return RuntimeWorkerClient.startPython(context, ownedRoot, port)
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
        return RuntimeWorkerClient.startPythonSites(context, ownedRoutes, port)
    }

    @Synchronized
    fun stop(context: Context): String =
        runCatching { RuntimeWorkerClient.stopPython(context) }.getOrDefault("STOPPED")

    @Synchronized
    fun status(context: Context): String =
        runCatching { RuntimeWorkerClient.pythonStatus(context) }.getOrDefault("STOPPED")

    @Synchronized
    fun metrics(context: Context): JSONObject? =
        runCatching { RuntimeWorkerClient.pythonMetrics(context) }.getOrNull()
}
