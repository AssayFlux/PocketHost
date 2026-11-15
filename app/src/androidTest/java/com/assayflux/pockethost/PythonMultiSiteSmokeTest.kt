package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.runtime.PythonHostRuntime
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.net.Socket

@RunWith(AndroidJUnit4::class)
class PythonMultiSiteSmokeTest {
    @Test
    fun routesMultipleWsgiProjectsByHostAndAppliesWorkerLimit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = File(context.cacheDir, "pockethost-python-a-${System.nanoTime()}")
        val second = File(context.cacheDir, "pockethost-python-b-${System.nanoTime()}")
        assertTrue(first.mkdirs())
        assertTrue(second.mkdirs())
        File(first, "wsgi.py").writeText(wsgi("python-site-a"))
        File(second, "wsgi.py").writeText(wsgi("python-site-b"))
        val preferences = context.getSharedPreferences("pockethost_state", android.content.Context.MODE_PRIVATE)
        val hadOld = preferences.contains("python_max_workers")
        val oldWorkers = preferences.getInt("python_max_workers", 8)
        val port = ServerSocket(0).use { it.localPort }

        try {
            preferences.edit().putInt("python_max_workers", 2).commit()
            val routes = JSONObject()
                .put("a.pockethost.test", first.absolutePath)
                .put("b.pockethost.test", second.absolutePath)
            assertEquals("RUNNING", PythonHostRuntime.startSites(context, routes.toString(), port))
            assertEquals("python-site-a", get(port, "a.pockethost.test"))
            assertEquals("python-site-b", get(port, "b.pockethost.test"))
            val metrics = PythonHostRuntime.metrics(context) ?: error("Python metrics unavailable")
            assertTrue(metrics.getInt("requests") >= 2)
            assertEquals(2, metrics.getInt("worker_limit"))
        } finally {
            PythonHostRuntime.stop(context)
            if (hadOld) preferences.edit().putInt("python_max_workers", oldWorkers).commit()
            else preferences.edit().remove("python_max_workers").commit()
            first.deleteRecursively()
            second.deleteRecursively()
        }
    }

    private fun wsgi(body: String) =
        "def application(environ, start_response):\n" +
            "    body = b'$body'\n" +
            "    start_response('200 OK', [('Content-Type', 'text/plain'), ('Content-Length', str(len(body)))])\n" +
            "    return [body]\n"

    private fun get(port: Int, host: String): String = Socket().use { socket ->
        socket.soTimeout = 5_000
        socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 5_000)
        socket.getOutputStream().write(
            "GET / HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray()
        )
        socket.getOutputStream().flush()
        val response = socket.getInputStream().bufferedReader().readText()
        check(response.startsWith("HTTP/1.0 200")) { response }
        response.substringAfter("\r\n\r\n").trim()
    }
}
