package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.runtime.PythonHostRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.net.Socket

@RunWith(AndroidJUnit4::class)
class PythonRuntimeSmokeTest {
    @Test
    fun runsWsgiApplicationOnLoopbackAndStops() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "pockethost-python-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        File(root, "wsgi.py").writeText(
            "def application(environ, start_response):\n" +
                "    body = b'PocketHost embedded Python is ready'\n" +
                "    start_response('200 OK', [('Content-Type', 'text/plain'), ('Content-Length', str(len(body)))])\n" +
                "    return [body]\n"
        )
        val port = ServerSocket(0).use { it.localPort }

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
            assertTrue(response.contains("PocketHost embedded Python is ready"))
        } finally {
            assertEquals("STOPPED", PythonHostRuntime.stop(context))
            root.deleteRecursively()
        }
        assertEquals("STOPPED", PythonHostRuntime.status(context))
    }
}
