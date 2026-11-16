package com.assayflux.pockethost.sites

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Persistent hostname-to-project mapping used by the Rust static server. */
object SiteRegistry {
    data class Site(val id: String, val name: String, val runtime: String, val root: String, val hostnames: List<String>, val enabled: Boolean, val accessMode: String = "public", val visitorCredentials: Map<String, String> = emptyMap())

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
                }, item.optBoolean("enabled", true), item.optString("accessMode", "public"),
                item.optJSONObject("visitorCredentials")?.let { credentials ->
                    buildMap { val names = credentials.keys(); while (names.hasNext()) { val name = names.next(); put(name, credentials.optString(name)) } }
                } ?: emptyMap())
        }
    }

    private fun validateAccessPolicy(site: Site) {
        require(site.accessMode in setOf("public", "private", "restricted")) { "Unsupported visitor access mode." }
        require(site.visitorCredentials.size <= 100 && site.visitorCredentials.all { (name, hash) ->
            name.matches(Regex("[A-Za-z0-9_.@-]{1,64}")) && hash.matches(Regex("[0-9a-f]{64}"))
        }) { "Invalid visitor credential registry." }
        require(site.accessMode != "private" || site.visitorCredentials.size <= 1) {
            "Private mode supports one shared visitor credential."
        }
        require(site.accessMode != "public" || site.visitorCredentials.isEmpty()) {
            "Public sites must not retain visitor credentials."
        }
    }

    @Synchronized fun put(context: Context, site: Site) {
        require(site.id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid site ID." }
        require(site.name.isNotBlank() && site.name.length <= 80) { "Site name must be 1–80 characters." }
        require(site.runtime in setOf("static", "python-wsgi", "node")) { "Unsupported runtime." }
        validateAccessPolicy(site)
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
        val previous = existingAll.firstOrNull { it.id == site.id }
        val preserved = if (site.accessMode == "public" && site.visitorCredentials.isEmpty() && previous != null) {
            site.copy(accessMode = previous.accessMode, visitorCredentials = previous.visitorCredentials)
        } else site
        val updated = existing + preserved.copy(root = root.absolutePath, hostnames = normalizedHosts)
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
            validateAccessPolicy(site)
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

    @Synchronized fun setAccessMode(context: Context, siteId: String, mode: String) {
        require(mode in setOf("public", "private", "restricted")) { "Unsupported visitor access mode." }
        val sites = list(context).toMutableList()
        val index = sites.indexOfFirst { it.id == siteId }
        require(index >= 0) { "Site not found." }
        val current = sites[index]
        val credentials = if (mode == "public") emptyMap() else current.visitorCredentials
        require(mode != "private" || credentials.size <= 1) { "Private mode supports one shared visitor credential." }
        sites[index] = current.copy(accessMode = mode, visitorCredentials = credentials)
        save(context, sites)
    }

    @Synchronized fun addVisitor(context: Context, siteId: String, username: String, token: String) {
        require(username.matches(Regex("[A-Za-z0-9_.@-]{1,64}"))) { "Visitor name must be 1–64 safe characters." }
        require(token.length in 32..256) { "Visitor token length is invalid." }
        val sites = list(context).toMutableList()
        val index = sites.indexOfFirst { it.id == siteId }
        require(index >= 0) { "Site not found." }
        val current = sites[index]
        require(current.accessMode != "public") { "Set the site to Private or Restricted before issuing visitor credentials." }
        if (current.accessMode == "private") require(username == "visitor" || current.visitorCredentials.isEmpty()) {
            "Private mode allows only one shared visitor credential named visitor."
        }
        require(current.visitorCredentials.size < 100 || username in current.visitorCredentials) { "Visitor limit reached." }
        val updated = current.visitorCredentials.toMutableMap()
        updated[if (current.accessMode == "private") "visitor" else username] = sha256(token)
        sites[index] = current.copy(visitorCredentials = updated)
        save(context, sites)
    }

    @Synchronized fun revokeVisitor(context: Context, siteId: String, username: String) {
        val sites = list(context).toMutableList()
        val index = sites.indexOfFirst { it.id == siteId }
        require(index >= 0) { "Site not found." }
        val current = sites[index]
        val updated = current.visitorCredentials.toMutableMap()
        require(updated.remove(username) != null) { "Visitor not found." }
        sites[index] = current.copy(visitorCredentials = updated)
        save(context, sites)
    }

    fun visitorNames(context: Context, siteId: String): List<String> =
        list(context).firstOrNull { it.id == siteId }?.visitorCredentials?.keys?.sorted() ?: emptyList()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

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
            site.hostnames.forEach { hostname ->
                val credentials = JSONObject()
                site.visitorCredentials.forEach { (name, hash) -> credentials.put(name, hash) }
                target.put("access", JSONObject().put("mode", site.accessMode).put("credentials", credentials))
                routes.put(hostname, target)
            }
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
                .put("hostnames", hosts).put("enabled", site.enabled)
                .put("accessMode", site.accessMode).put("visitorCredentials", JSONObject().apply { site.visitorCredentials.forEach { (name, hash) -> put(name, hash) } }))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
