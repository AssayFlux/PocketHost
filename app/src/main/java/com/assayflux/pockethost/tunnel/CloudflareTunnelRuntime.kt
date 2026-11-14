package com.assayflux.pockethost.tunnel

import android.content.Context
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Runs the Android-targeted cloudflared executable as an app-owned child process.
 * The tunnel token is passed in the TUNNEL_TOKEN environment variable, never argv.
 */
object CloudflareTunnelRuntime {
    @Volatile private var process: Process? = null
    @Volatile private var currentState: String = "STOPPED"

    @Synchronized
    fun start(context: Context, token: String, onEvent: (String) -> Unit = {}): String {
        if (process?.isAlive == true) return currentState
        val executable = File(context.applicationInfo.nativeLibraryDir, "libcloudflared.so")
        check(executable.isFile) {
            "Cloudflare connector is not packaged for this device ABI. Rebuild the Android runtime first."
        }
        check(executable.setExecutable(true, true) || executable.canExecute()) {
            "Cannot mark the Cloudflare connector executable."
        }

        val builder = ProcessBuilder(
            executable.absolutePath,
            "tunnel",
            "--no-autoupdate",
            "--loglevel", "info",
            "--metrics", "127.0.0.1:20241",
            "run"
        ).redirectErrorStream(true)
        builder.environment()["TUNNEL_TOKEN"] = token
        builder.environment()["NO_AUTOUPDATE"] = "true"
        val child = builder.start()
        process = child
        currentState = "CONNECTING"

        Thread({
            try {
                BufferedReader(InputStreamReader(child.inputStream, Charsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        val safeLine = line.replace(token, "[REDACTED]").take(500)
                        if (safeLine.contains("Registered tunnel connection", ignoreCase = true)) {
                            currentState = "CONNECTED"
                            onEvent("Cloudflare Tunnel connected.")
                        } else if (safeLine.contains("ERR", ignoreCase = true) ||
                            safeLine.contains("failed", ignoreCase = true)) {
                            onEvent("Cloudflare: $safeLine")
                        }
                    }
                }
                val exitCode = child.waitFor()
                synchronized(this) {
                    if (process === child) {
                        process = null
                        currentState = if (exitCode == 0) "STOPPED" else "ERROR"
                    }
                }
                if (exitCode != 0) onEvent("Cloudflare connector exited with code $exitCode.")
            } catch (error: Exception) {
                synchronized(this) {
                    if (process === child) {
                        process = null
                        currentState = "ERROR"
                    }
                }
                onEvent("Cloudflare connector error: ${error.message ?: "unknown error"}")
            }
        }, "PocketHost-Cloudflare-Logs").start()

        Thread({
            val deadline = System.currentTimeMillis() + 30_000
            while (child.isAlive && System.currentTimeMillis() < deadline) {
                if (isReady()) {
                    if (child.isAlive) {
                        currentState = "CONNECTED"
                        onEvent("Cloudflare Tunnel reports an active edge connection.")
                    }
                    return@Thread
                }
                try {
                    Thread.sleep(1_000)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
            if (child.isAlive && currentState == "CONNECTING") {
                onEvent("Cloudflare Tunnel did not become ready within 30 seconds. Restarting the connector.")
                child.destroy()
                if (!runCatching { child.waitFor(3, TimeUnit.SECONDS) }.getOrDefault(false)) {
                    child.destroyForcibly()
                    runCatching { child.waitFor(2, TimeUnit.SECONDS) }
                }
                synchronized(this) {
                    if (process === child) {
                        process = null
                        currentState = "ERROR"
                    }
                }
            }
        }, "PocketHost-Cloudflare-Readiness").start()
        return currentState
    }

    fun status(): String {
        if (process?.isAlive != true && currentState == "CONNECTED") currentState = "STOPPED"
        return currentState
    }

    @Synchronized
    fun stop(): String {
        val child = process
        if (child == null || !child.isAlive) {
            process = null
            currentState = "STOPPED"
            return currentState
        }
        child.destroy()
        if (!child.waitFor(3, TimeUnit.SECONDS)) {
            child.destroyForcibly()
            child.waitFor(2, TimeUnit.SECONDS)
        }
        process = null
        currentState = "STOPPED"
        return currentState
    }

    private fun isReady(): Boolean = try {
        val connection = (URL("http://127.0.0.1:20241/ready").openConnection() as HttpURLConnection)
        connection.connectTimeout = 800
        connection.readTimeout = 800
        connection.requestMethod = "GET"
        try {
            connection.responseCode == 200
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        false
    }
}
