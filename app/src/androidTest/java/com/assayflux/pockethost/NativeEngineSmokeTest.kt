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
import org.json.JSONObject

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

    @Test
    fun unifiedGatewayRoutesMultipleStaticSitesByHostname() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "pockethost-gateway-root-${System.nanoTime()}")
        val siteA = File(root, "site-a")
        val siteB = File(root, "site-b")
        assertTrue(siteA.mkdirs())
        assertTrue(siteB.mkdirs())
        File(siteA, "index.html").writeText("gateway-static-a")
        File(siteB, "index.html").writeText("gateway-static-b")
        val routes = JSONObject()
            .put("a.pockethost.test", JSONObject().put("runtime", "static").put("root", siteA.absolutePath))
            .put("b.pockethost.test", JSONObject().put("runtime", "static").put("root", siteB.absolutePath))
        val port = ServerSocket(0).use { it.localPort }
        try {
            assertEquals("OK", NativeEngine.startMultiSiteWithBindMode(
                root.absolutePath, routes.toString(), port, "loopback", false
            ))
            assertTrue(exchange(port, "", "a.pockethost.test").contains("gateway-static-a"))
            assertTrue(exchange(port, "", "b.pockethost.test").contains("gateway-static-b"))
        } finally {
            NativeEngine.stop()
            root.deleteRecursively()
        }
    }

    @Test
    fun publicOriginBindingRequiresExplicitConsent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "pockethost-bind-mode-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        val port = ServerSocket(0).use { it.localPort }
        try {
            val result = NativeEngine.startMultiSiteWithBindMode(
                root.absolutePath, "{}", port, "public-origin", false
            )
            assertTrue(result.contains("explicit user consent", ignoreCase = true))
            assertEquals("STOPPED", NativeEngine.status())
        } finally {
            NativeEngine.stop()
            root.deleteRecursively()
        }
    }

    @Test
    fun unsupportedGatewayBindModeIsRejectedBeforeBinding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "pockethost-invalid-bind-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        val port = ServerSocket(0).use { it.localPort }
        try {
            val result = NativeEngine.startMultiSiteWithBindMode(
                root.absolutePath, "{}", port, "internet-everywhere", false
            )
            assertTrue(result.contains("Unsupported gateway bind mode"))
            assertEquals("STOPPED", NativeEngine.status())
        } finally {
            NativeEngine.stop()
            root.deleteRecursively()
        }
    }

    private fun exchange(port: Int, extraHeaders: String, host: String = "localhost"): String = Socket().use { socket ->
        socket.soTimeout = 5_000
        socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 5_000)
        val request = "GET / HTTP/1.1\r\nHost: $host\r\n" + extraHeaders + "Connection: close\r\n\r\n"
        socket.getOutputStream().write(request.toByteArray())
        socket.getOutputStream().flush()
        socket.getInputStream().bufferedReader().use { it.readText() }
    }
}
