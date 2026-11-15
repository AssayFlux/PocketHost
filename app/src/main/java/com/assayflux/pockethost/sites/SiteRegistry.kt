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
        require(File(site.root).isDirectory) { "Site root must be an existing directory." }
        val normalizedHosts = site.hostnames.map { it.trim().trimEnd('.').lowercase() }
        require(normalizedHosts.all { hostnamePattern.matches(it) }) { "One or more hostnames are invalid." }
        val existing = list(context).filterNot { it.id == site.id }
        val used = existing.flatMap { it.hostnames }.toSet()
        require(normalizedHosts.distinct().size == normalizedHosts.size && normalizedHosts.none { it in used }) {
            "A hostname can belong to only one site."
        }
        val updated = existing + site.copy(hostnames = normalizedHosts)
        save(context, updated)
    }

    @Synchronized fun remove(context: Context, siteId: String) {
        save(context, list(context).filterNot { it.id == siteId })
    }

    fun staticHostnameMap(context: Context): JSONObject {
        val map = JSONObject()
        list(context).filter { it.enabled && it.runtime == "static" }.forEach { site ->
            site.hostnames.forEach { host -> map.put(host, File(site.root).canonicalPath) }
        }
        return map
    }

    fun hostRoutingJson(context: Context, pythonPort: Int, nodePort: Int): JSONObject {
        val routes = JSONObject()
        list(context).filter { it.enabled }.forEach { site ->
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
