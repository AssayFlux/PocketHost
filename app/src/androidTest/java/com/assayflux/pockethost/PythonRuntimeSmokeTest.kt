package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.runtime.PythonHostRuntime
import com.assayflux.pockethost.engine.NativeEngine
import com.assayflux.pockethost.runtime.RuntimeWorkerClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.net.Socket

@RunWith(AndroidJUnit4::class)
class PythonRuntimeSmokeTest {
    @Test
    fun runsWsgiApplicationOnLoopbackAndStops() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            context.startActivity(android.content.Intent(context, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        val root = File(context.cacheDir, "pockethost-python-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        File(root, ".env").writeText("HOST_SECRET=must-not-transfer")
        File(root, "wsgi.py").writeText(
            "def application(environ, start_response):\n" +
                "    import os\n" +
                "    body = (str(os.getuid()) + ':' + str(os.path.exists(os.path.join(os.path.dirname(__file__), '.env')))).encode('ascii')\n" +
                "    start_response('200 OK', [('Content-Type', 'text/plain'), ('Content-Length', str(len(body)))])\n" +
                "    return [body]\n"
        )
        val port = ServerSocket(0).use { it.localPort }
        val prefs = context.getSharedPreferences("pockethost_state", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("trusted_runtime_code_ack", true).commit()

        try {
            assertEquals("RUNNING", PythonHostRuntime.start(context, root.absolutePath, port))
            assertEquals("RUNNING", PythonHostRuntime.status(context))
            val response = Socket().use { socket ->
                socket.soTimeout = 5_000
                socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 5_000)
                socket.getOutputStream().write(
                    "GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray()
                )
                socket.getOutputStream().flush()
                socket.getInputStream().bufferedReader().readText()
            }
            assertTrue(response.startsWith("HTTP/1.0 200"))
            assertTrue(response.contains("200 OK"))
            val body = response.substringAfter("\r\n\r\n").trim().split(":")
            val runtimeUid = body.first().toInt()
            assertEquals("False", body.getOrElse(1) { "missing" })
            org.junit.Assert.assertNotEquals("Python must execute under companion UID, not host UID",
                context.applicationInfo.uid, runtimeUid)

            // Verify the complete visitor path: Rust routes by Host and proxies to
            // the companion's loopback-only WSGI listener.
            val staticRoot = File(context.cacheDir, "pockethost-python-proxy-${System.nanoTime()}")
                .apply { mkdirs() }
            val frontPort = ServerSocket(0).use { it.localPort }
            val routes = JSONObject().put("python-smoke.local",
                JSONObject().put("runtime", "python-wsgi").put("port", port))
            try {
                assertEquals("OK", NativeEngine.startMultiSite(
                    staticRoot.absolutePath, routes.toString(), frontPort, false
                ))
                val proxied = Socket().use { socket ->
                    socket.soTimeout = 5_000
                    socket.connect(java.net.InetSocketAddress("127.0.0.1", frontPort), 5_000)
                    socket.getOutputStream().write(
                        "GET / HTTP/1.1\r\nHost: python-smoke.local\r\nConnection: close\r\n\r\n".toByteArray()
                    )
                    socket.getOutputStream().flush()
                    socket.getInputStream().bufferedReader().readText()
                }
                assertTrue("Python request did not pass through Rust: $proxied",
                    proxied.startsWith("HTTP/1.1 200"))
                assertTrue("Rust proxy did not preserve the WSGI response: $proxied",
                    proxied.contains("$runtimeUid:False"))
            } finally {
                NativeEngine.stop()
                staticRoot.deleteRecursively()
            }
        } finally {
            assertEquals("STOPPED", PythonHostRuntime.stop(context))
            RuntimeWorkerClient.shutdownWorker(context)
            prefs.edit().putBoolean("trusted_runtime_code_ack", false).commit()
            root.deleteRecursively()
        }
        // A later explicit command must establish a fresh companion connection.
        assertEquals("CONFIGURED", RuntimeWorkerClient.configurePython(context, 2))
        assertEquals("STOPPED", PythonHostRuntime.status(context))
        RuntimeWorkerClient.shutdownWorker(context)
        assertEquals("STOPPED", PythonHostRuntime.status(context))
    }
}
