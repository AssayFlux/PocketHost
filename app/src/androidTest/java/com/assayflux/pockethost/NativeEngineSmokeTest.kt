package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.engine.NativeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL

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

            val connection = URL("http://127.0.0.1:$port/").openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            try {
                assertEquals(200, connection.responseCode)
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                assertEquals("PocketHost Android compatibility smoke test", body)
            } finally {
                connection.disconnect()
            }
            assertTrue(NativeEngine.requestCount() >= 1L)
        } finally {
            NativeEngine.stop()
            root.deleteRecursively()
        }
        assertEquals("STOPPED", NativeEngine.status())
    }
}
