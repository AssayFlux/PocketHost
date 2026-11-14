package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.engine.NativeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.Socket
import java.net.ServerSocket

@RunWith(AndroidJUnit4::class)
class NativeEngineSmokeTest {
    @Test
    fun servesStaticContentOnLoopbackAndStopsCleanly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "pockethost-smoke-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        File(root, "index.html").writeText("PocketHost Android compatibility smoke test")
        val port = ServerSocket(0).use { it.localPort }

        try {
            assertEquals("OK", NativeEngine.start(root.absolutePath, port, false))
            assertEquals("RUNNING", NativeEngine.status())

            val response = exchange(port, "")
            assertTrue(response.startsWith("HTTP/1.1 200"))
            assertTrue(response.contains("PocketHost Android compatibility smoke test"))

            val rangeResponse = exchange(port, "Range: bytes=0-9\r\n")
            assertTrue(rangeResponse.startsWith("HTTP/1.1 206"))
            assertTrue(rangeResponse.contains("PocketHost"))
            assertTrue(NativeEngine.requestCount() >= 2L)
        } finally {
            NativeEngine.stop()
            root.deleteRecursively()
        }
        assertEquals("STOPPED", NativeEngine.status())
    }

    private fun exchange(port: Int, extraHeaders: String): String = Socket().use { socket ->
        socket.soTimeout = 5_000
        socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 5_000)
        val request = "GET / HTTP/1.1\r\nHost: localhost\r\n" + extraHeaders + "Connection: close\r\n\r\n"
        socket.getOutputStream().write(request.toByteArray())
        socket.getOutputStream().flush()
        socket.getInputStream().bufferedReader().use { it.readText() }
    }
}
