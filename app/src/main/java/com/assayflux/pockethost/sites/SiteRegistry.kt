package com.assayflux.pockethost.sites

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persistent hostname-to-project mapping used by the Rust static server. */
object SiteRegistry {
    data class Site(val id: String, val name: String, val runtime: String, val root: String, val hostnames: List<String>, val enabled: Boolean)

    private const val PREFS = "pockethost_site_registry"
    private const val KEY = "sites"
    private val hostnamePattern = Regex("(?i)^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")

    @Synchronized fun list(context: Context): List<Site> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            Site(item.optString("id"), item.optString("name"), item.optString("runtime", "static"),
                item.optString("root"), (item.optJSONArray("hostnames") ?: JSONArray()).let { hosts ->
                    (0 until hosts.length()).map { hosts.optString(it) }
                }, item.optBoolean("enabled", true))
        }
    }

    @Synchronized fun put(context: Context, site: Site) {
        require(site.id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid site ID." }
        require(site.name.isNotBlank() && site.name.length <= 80) { "Site name must be 1–80 characters." }
        require(site.runtime in setOf("static", "python-wsgi", "node")) { "Unsupported runtime." }
        val root = File(site.root).canonicalFile
        val privateRoot = context.filesDir.canonicalFile.toPath()
        require(root.isDirectory && root.toPath().startsWith(privateRoot)) {
            "Site root must be an existing directory inside PocketHost private storage."
        }
        val normalizedHosts = site.hostnames.map { it.trim().trimEnd('.').lowercase() }
        require(normalizedHosts.all { hostnamePattern.matches(it) }) { "One or more hostnames are invalid." }
        val existingAll = list(context)
        val existing = existingAll.filterNot { it.id == site.id }
        require(existing.size < 100) { "PocketHost supports up to 100 registered sites." }
        require(normalizedHosts.size <= 100) { "A site can have at most 100 hostnames." }
        require(existing.sumOf { it.hostnames.size } + normalizedHosts.size <= 1_000) {
            "PocketHost supports up to 1,000 hostname routes."
        }
        val used = existing.flatMap { it.hostnames }.toSet()
        require(normalizedHosts.distinct().size == normalizedHosts.size && normalizedHosts.none { it in used }) {
            "A hostname can belong to only one site."
        }
        if (site.runtime == "node") {
            require(existing.filter { it.runtime == "node" }.all { File(it.root).canonicalPath == root.path }) {
                "Only one Node.js project can be active in the embedded Node process; map additional hostnames to the same project."
            }
        }
        val updated = existing + site.copy(root = root.absolutePath, hostnames = normalizedHosts)
        save(context, updated)
    }

    @Synchronized fun remove(context: Context, siteId: String) {
        save(context, list(context).filterNot { it.id == siteId })
    }

    /**
     * Atomically replaces the complete route table after validating every restored route.
     * Paths must resolve inside app-private storage and hostnames must remain globally unique.
     */
    @Synchronized fun replaceAll(context: Context, sites: List<Site>) {
        require(sites.size <= 100) { "PocketHost supports up to 100 registered sites." }
        val privateRoot = context.filesDir.canonicalFile.toPath()
        val normalized = sites.map { site ->
            require(site.id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid site ID." }
            require(site.name.isNotBlank() && site.name.length <= 80) { "Site name must be 1–80 characters." }
            require(site.runtime in setOf("static", "python-wsgi", "node")) { "Unsupported runtime." }
            val root = File(site.root).canonicalFile
            require(root.isDirectory && root.toPath().startsWith(privateRoot)) {
                "Restored site root must be an existing directory inside Pocket Host private storage."
            }
            val hosts = site.hostnames.map { it.trim().trimEnd('.').lowercase() }
            require(hosts.size <= 100 && hosts.all { hostnamePattern.matches(it) }) {
                "Restored site contains an invalid hostname."
            }
            require(hosts.distinct().size == hosts.size) { "Restored site contains duplicate hostnames." }
            site.copy(root = root.absolutePath, hostnames = hosts)
        }
        require(normalized.map { it.id }.distinct().size == normalized.size) {
            "Restored routes contain duplicate site IDs."
        }
        val allHosts = normalized.flatMap { it.hostnames }
        require(allHosts.size <= 1_000 && allHosts.distinct().size == allHosts.size) {
            "Restored routes contain duplicate hostnames or exceed the route limit."
        }
        val nodeRoots = normalized.filter { it.runtime == "node" }.map { it.root }.distinct()
        require(nodeRoots.size <= 1) { "Only one Node.js project can be active in the embedded Node process." }
        save(context, normalized)
    }

    fun staticHostnameMap(context: Context): JSONObject {
        val map = JSONObject()
        list(context).filter { it.enabled && it.runtime == "static" }.forEach { site ->
            site.hostnames.forEach { host -> map.put(host, File(site.root).canonicalPath) }
        }
        return map
    }

    fun pythonHostnameMap(context: Context): JSONObject {
        val roots = JSONObject()
        val prefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        val runtimeAllowed = prefs.getBoolean("trusted_runtime_code_ack", false) &&
            prefs.getBoolean("python_enabled", false)
        if (!runtimeAllowed) return roots
        list(context).filter { it.enabled && it.runtime == "python-wsgi" }.forEach { site ->
            site.hostnames.forEach { hostname -> roots.put(hostname, File(site.root).canonicalPath) }
        }
        return roots
    }

    fun hostRoutingJson(context: Context, pythonPort: Int, nodePort: Int): JSONObject {
        val routes = JSONObject()
        val prefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        val trustedCodeAcknowledged = prefs.getBoolean("trusted_runtime_code_ack", false)
        val pythonEnabled = trustedCodeAcknowledged && prefs.getBoolean("python_enabled", false)
        val nodeEnabled = trustedCodeAcknowledged && prefs.getBoolean("node_enabled", false)
        list(context).filter { site ->
            site.enabled && when (site.runtime) {
                "static" -> true
                "python-wsgi" -> pythonEnabled
                "node" -> nodeEnabled
                else -> false
            }
        }.forEach { site ->
            val target = when (site.runtime) {
                "static" -> JSONObject().put("runtime", "static").put("root", File(site.root).canonicalPath)
                "python-wsgi" -> JSONObject().put("runtime", "python-wsgi").put("port", pythonPort)
                "node" -> JSONObject().put("runtime", "node").put("port", nodePort)
                else -> return@forEach
            }
            site.hostnames.forEach { hostname -> routes.put(hostname, target) }
        }
        return routes
    }

    private fun save(context: Context, sites: List<Site>) {
        val array = JSONArray()
        sites.forEach { site ->
            val hosts = JSONArray()
            site.hostnames.forEach(hosts::put)
            array.put(JSONObject().put("id", site.id).put("name", site.name)
                .put("runtime", site.runtime).put("root", site.root)
                .put("hostnames", hosts).put("enabled", site.enabled))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
