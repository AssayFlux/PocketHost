package com.assayflux.pockethost.sites

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64
import com.assayflux.pockethost.storage.WebsiteLimits

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
        val root = canonicalSiteRoot(context, site)
        val normalizedHosts = site.hostnames.map { it.trim().trimEnd('.').lowercase() }
        require(normalizedHosts.all { hostnamePattern.matches(it) }) { "One or more hostnames are invalid." }
        val existingAll = list(context)
        val existing = existingAll.filterNot { it.id == site.id }
        require(existing.size < WebsiteLimits.MAX_REGISTERED_SITES) { "PocketHost supports up to 100 registered sites." }
        require(normalizedHosts.size <= WebsiteLimits.MAX_HOSTNAMES_PER_SITE) { "A site can have at most 100 hostnames." }
        require(existing.sumOf { it.hostnames.size } + normalizedHosts.size <= WebsiteLimits.MAX_HOSTNAME_ROUTES) {
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
        existing.forEach { other ->
            val otherRoot = canonicalSiteRoot(context, other).toPath()
            val candidate = root.toPath()
            val sameRoot = candidate == otherRoot
            require((sameRoot && site.runtime == other.runtime) ||
                (!candidate.startsWith(otherRoot) && !otherRoot.startsWith(candidate))) {
                "Different sites and runtime projects must not have overlapping root directories."
            }
        }
        val previous = existingAll.firstOrNull { it.id == site.id }
        val preserved = if (site.accessMode == "public" && site.visitorCredentials.isEmpty() && previous != null) {
            site.copy(accessMode = previous.accessMode, visitorCredentials = previous.visitorCredentials)
        } else site
        val updated = existing + preserved.copy(root = root.absolutePath, hostnames = normalizedHosts)
        save(context, updated)
    }

    @Synchronized fun isEnabledSite(context: Context, siteId: String): Boolean =
        list(context).any { it.id == siteId && it.enabled }

    @Synchronized fun localHostnameForRelay(context: Context, siteId: String): String? =
        list(context).firstOrNull { it.id == siteId && it.enabled }?.hostnames?.firstOrNull()

    @Synchronized fun remove(context: Context, siteId: String) {
        save(context, list(context).filterNot { it.id == siteId })
    }

    /**
     * Atomically replaces the complete route table after validating every restored route.
     * Paths must resolve inside app-private storage and hostnames must remain globally unique.
     */
    @Synchronized fun replaceAll(context: Context, sites: List<Site>) {
        require(sites.size <= WebsiteLimits.MAX_REGISTERED_SITES) { "PocketHost supports up to 100 registered sites." }
        val normalized = sites.map { site ->
            require(site.id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid site ID." }
            require(site.name.isNotBlank() && site.name.length <= 80) { "Site name must be 1–80 characters." }
            require(site.runtime in setOf("static", "python-wsgi", "node")) { "Unsupported runtime." }
            validateAccessPolicy(site)
            val root = canonicalSiteRoot(context, site)
            val hosts = site.hostnames.map { it.trim().trimEnd('.').lowercase() }
            require(hosts.size <= WebsiteLimits.MAX_HOSTNAMES_PER_SITE && hosts.all { hostnamePattern.matches(it) }) {
                "Restored site contains an invalid hostname."
            }
            require(hosts.distinct().size == hosts.size) { "Restored site contains duplicate hostnames." }
            site.copy(root = root.absolutePath, hostnames = hosts)
        }
        require(normalized.map { it.id }.distinct().size == normalized.size) {
            "Restored routes contain duplicate site IDs."
        }
        for (i in normalized.indices) {
            for (j in i + 1 until normalized.size) {
                val left = File(normalized[i].root).canonicalFile.toPath()
                val right = File(normalized[j].root).canonicalFile.toPath()
                val sameRoot = left == right
                require((sameRoot && normalized[i].runtime == normalized[j].runtime) ||
                    (!left.startsWith(right) && !right.startsWith(left))) {
                    "Restored sites and runtime projects must not have overlapping root directories."
                }
            }
        }
        val allHosts = normalized.flatMap { it.hostnames }
        require(allHosts.size <= WebsiteLimits.MAX_HOSTNAME_ROUTES && allHosts.distinct().size == allHosts.size) {
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

    @Synchronized fun issueVisitor(context: Context, siteId: String, username: String): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val token = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        addVisitor(context, siteId, username, token)
        bytes.fill(0)
        return token
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
        list(context).filter { it.enabled && it.runtime == "static" && runCatching { canonicalSiteRoot(context, it) }.isSuccess }.forEach { site ->
            site.hostnames.forEach { host -> map.put(host, canonicalSiteRoot(context, site).path) }
        }
        return map
    }

    fun pythonHostnameMap(context: Context): JSONObject {
        val roots = JSONObject()
        val prefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        val runtimeAllowed = prefs.getBoolean("trusted_runtime_code_ack", false) &&
            prefs.getBoolean("python_enabled", false)
        if (!runtimeAllowed) return roots
        list(context).filter { it.enabled && it.runtime == "python-wsgi" && runCatching { canonicalSiteRoot(context, it) }.isSuccess }.forEach { site ->
            site.hostnames.forEach { hostname -> roots.put(hostname, canonicalSiteRoot(context, site).path) }
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
            site.enabled && runCatching { canonicalSiteRoot(context, site) }.isSuccess && when (site.runtime) {
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

    private fun canonicalSiteRoot(context: Context, site: Site): File {
        val privateRoot = context.filesDir.canonicalFile.toPath()
        val requested = File(site.root).absoluteFile.toPath().normalize()
        require(requested.startsWith(privateRoot)) { "Site roots must be selected from PocketHost's managed site directories." }
        var cursor = privateRoot
        for (part in privateRoot.relativize(requested)) {
            cursor = cursor.resolve(part)
            require(!java.nio.file.Files.isSymbolicLink(cursor)) { "Site roots cannot contain symbolic-link path components." }
        }
        val root = requested.toFile().canonicalFile
        val relative = privateRoot.relativize(root.toPath())
        val parts = relative.map { it.toString() }
        val standardRoot = parts.size == 1 && (
            parts[0] == "site" ||
            parts[0] == "python-project" ||
            parts[0].matches(Regex("node-project-[A-Za-z0-9_-]{1,64}"))
        )
        val customRoot = parts.size == 2 && parts[0] == "site-roots" &&
            parts[1].matches(Regex("[A-Za-z0-9_-]{1,64}"))
        require(root.isDirectory && (standardRoot || customRoot)) {
            "Site root must be a dedicated Pocket Host website, Python, Node, or registered custom-site directory."
        }
        return root
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
