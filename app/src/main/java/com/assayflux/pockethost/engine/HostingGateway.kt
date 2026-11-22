package com.assayflux.pockethost.engine

import android.content.Context
import com.assayflux.pockethost.sites.SiteRegistry

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
        }
        val routes = SiteRegistry.hostRoutingJson(
            context,
            preferences.getInt("python_port", 8081),
            preferences.getInt("node_port", 3000)
        )
        val root = java.io.File(context.filesDir, "site")
        if (!root.exists() && !root.mkdirs()) return "Cannot create the managed website directory."
        val result = NativeEngine.startMultiSiteWithBindMode(
            root.absolutePath,
            routes.toString(),
            port,
            bindMode.wireValue,
            bindMode == BindMode.PUBLIC_ORIGIN
        )
        if (result == "OK") {
            preferences.edit()
                .putString("gateway_bind_mode", bindMode.wireValue)
                .putInt("gateway_port", port)
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
