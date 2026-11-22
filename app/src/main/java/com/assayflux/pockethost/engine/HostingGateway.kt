package com.assayflux.pockethost.engine

import android.content.Context
import com.assayflux.pockethost.sites.SiteRegistry
import java.security.MessageDigest

/**
 * Single lifecycle boundary for the direct-origin HTTP gateway.
 * TLS termination and certificate provisioning are added by the publishing phase.
 */
object HostingGateway {
    enum class BindMode(val wireValue: String) {
        LOOPBACK("loopback"),
        LAN("lan"),
        PUBLIC_ORIGIN("public-origin");

        companion object {
            fun fromPreferences(value: String?, legacyAllowLan: Boolean): BindMode =
                entries.firstOrNull { it.wireValue == value }
                    ?: if (legacyAllowLan) LAN else LOOPBACK
        }
    }

    data class Snapshot(val state: String, val bindMode: BindMode, val port: Int)

    @Synchronized
    fun start(context: Context, port: Int, bindMode: BindMode): String {
        require(port in 1024..65535) { "Gateway port must be between 1024 and 65535." }
        val preferences = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        if (bindMode == BindMode.PUBLIC_ORIGIN) {
            require(preferences.getBoolean("public_origin_consent", false)) {
                "Public-origin binding requires explicit consent in Publish settings."
            }
            require(preferences.getBoolean("public_origin_diagnostics_passed", false)) {
                "Public-origin binding is blocked until network diagnostics pass."
            }
            require(preferences.getBoolean("public_origin_tls_ready", false)) {
                "Public-origin binding is blocked until the HTTPS gateway and certificate are ready."
            }
        }
        val routes = SiteRegistry.hostRoutingJson(
            context,
            preferences.getInt("python_port", 8081),
            preferences.getInt("node_port", 3000)
        )
        val root = java.io.File(context.filesDir, "site")
        if (!root.exists() && !root.mkdirs()) return "Cannot create the managed website directory."
        val routeDigest = MessageDigest.getInstance("SHA-256")
            .digest(routes.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val alreadyRunning = NativeEngine.status() == "RUNNING"
        val unchanged = alreadyRunning &&
            preferences.getString("gateway_route_digest", null) == routeDigest &&
            preferences.getString("gateway_bind_mode", null) == bindMode.wireValue &&
            preferences.getInt("gateway_port", -1) == port
        if (unchanged) return "OK"
        if (alreadyRunning) {
            val stopped = NativeEngine.stop()
            if (stopped != "OK") return "Cannot safely replace the active gateway: $stopped"
        }
        val result = NativeEngine.startMultiSiteWithBindMode(
            root.absolutePath, routes.toString(), port, bindMode.wireValue,
            bindMode == BindMode.PUBLIC_ORIGIN
        )
        if (result == "OK") {
            preferences.edit()
                .putString("gateway_bind_mode", bindMode.wireValue)
                .putInt("gateway_port", port)
                .putString("gateway_route_digest", routeDigest)
                .apply()
        }
        return result
    }

    @Synchronized
    fun stop(): String = NativeEngine.stop()

    fun status(): String = NativeEngine.status()

    fun snapshot(context: Context): Snapshot {
        val preferences = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        val mode = BindMode.fromPreferences(
            preferences.getString("gateway_bind_mode", null),
            preferences.getBoolean("allow_lan", false)
        )
        return Snapshot(status(), mode, preferences.getInt("gateway_port", preferences.getInt("port", 8080)))
    }
}
