package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.engine.NativeEngine
import com.assayflux.pockethost.management.RemoteControlAgent
import com.assayflux.pockethost.management.ManagementApiServer
import com.assayflux.pockethost.storage.SiteImporter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteDeploymentSmokeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun remoteAgentCredentialIsEncryptedAndRevocable() {
        RemoteControlAgent.clear(context)
        val token = "remote-test-" + "a".repeat(48)
        RemoteControlAgent.configure(context, "https://control.example.test", token)
        assertTrue(RemoteControlAgent.isConfigured(context))
        val stored = context.getSharedPreferences("pockethost_remote_control", 0)
            .getString("token", "").orEmpty()
        assertFalse("Raw remote token must never be persisted", stored.contains(token))
        RemoteControlAgent.clear(context)
        assertFalse(RemoteControlAgent.isConfigured(context))
    }

    @Test
    fun remoteResourceConfigurationKeepsStaticHostingOnline() {
        val site = File(context.filesDir, "remote-config-smoke").apply { mkdirs() }
        File(site, "index.html").writeText("static-origin-stays-online")
        val port = ServerSocket(0).use { it.localPort }
        val prefs = context.getSharedPreferences("pockethost_state", 0)
        val oldWorkers = prefs.getInt("python_max_workers", 8)
        val oldHeap = prefs.getInt("node_heap_mb", 192)
        try {
            assertEquals("OK", NativeEngine.start(site.absolutePath, port, false))
            val result = ManagementApiServer.executeRemoteCommand(
                context,
                "apply_config",
                JSONObject().put("config", JSONObject()
                    .put("pythonMaxWorkers", 6)
                    .put("nodeHeapMb", 256))
            )
            assertEquals(200, result.optInt("status"))
            assertEquals(6, prefs.getInt("python_max_workers", -1))
            assertEquals(256, prefs.getInt("node_heap_mb", -1))
            assertTrue("Remote configuration must not stop the Rust static server",
                result.optJSONObject("body")?.optJSONArray("applied") != null &&
                    NativeEngine.status() == "RUNNING")
            val response = Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 5_000
                socket.getOutputStream().write(
                    "GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray()
                )
                socket.getOutputStream().flush()
                socket.getInputStream().bufferedReader().use { it.readText() }
            }
            assertTrue(response.contains("static-origin-stays-online"))
        } finally {
            NativeEngine.stop()
            prefs.edit().putInt("python_max_workers", oldWorkers).putInt("node_heap_mb", oldHeap).commit()
            site.deleteRecursively()
        }
    }

    @Test
    fun websiteArchiveCanBeActivatedWithoutStoppingStaticServer() {
        val site = File(context.filesDir, "site")
        if (!site.exists()) assertTrue(site.mkdirs())
        val original = File(site, "index.html")
        val oldText = original.takeIf { it.isFile }?.readText()
        original.writeText("old-release")
        val port = ServerSocket(0).use { it.localPort }
        try {
            assertEquals("OK", NativeEngine.start(site.absolutePath, port, false))
            val archive = ByteArrayOutputStream().also { bytes ->
                ZipOutputStream(bytes).use { zip ->
                    zip.putNextEntry(ZipEntry("index.html"))
                    zip.write("new-release".toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }.toByteArray()
            val report = SiteImporter.importZipStream(context, ByteArrayInputStream(archive))
            assertEquals(1, report.fileCount)
            assertEquals("RUNNING", NativeEngine.status())
            val response = Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 5_000
                socket.getOutputStream().write("GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray())
                socket.getOutputStream().flush()
                socket.getInputStream().bufferedReader().use { it.readText() }
            }
            assertTrue(response.contains("new-release"))
            assertEquals("RUNNING", NativeEngine.status())
        } finally {
            NativeEngine.stop()
            runCatching { SiteImporter.rollbackLatest(context) }
            if (oldText != null) original.writeText(oldText) else original.delete()
        }
    }
}
