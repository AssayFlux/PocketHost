package com.assayflux.pockethost

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assayflux.pockethost.tunnel.CloudflareTokenStore
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudflareConnectorSmokeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun packagedConnectorExecutesForCurrentAbi() {
        val binary = File(context.applicationInfo.nativeLibraryDir, "libcloudflared.so")
        assertTrue("cloudflared executable is missing from nativeLibraryDir", binary.isFile)
        assertTrue("cloudflared executable permission could not be set", binary.setExecutable(true, true) || binary.canExecute())

        val child = ProcessBuilder(binary.absolutePath, "version")
            .redirectErrorStream(true)
            .start()
        val output = child.inputStream.bufferedReader().use { it.readText() }
        assertTrue("cloudflared version command timed out", child.waitFor(20, TimeUnit.SECONDS))
        assertEquals("cloudflared exited unsuccessfully: $output", 0, child.exitValue())
        assertTrue("Unexpected cloudflared output: $output", output.contains("cloudflared", ignoreCase = true))
    }

    @Test
    fun tunnelTokenIsEncryptedAndCanBeRemoved() {
        CloudflareTokenStore.clear(context)
        val token = "test-token-" + "a".repeat(48)
        CloudflareTokenStore.save(context, token)
        assertEquals(token, CloudflareTokenStore.read(context))
        val raw = context.getSharedPreferences("pockethost_secure", Context.MODE_PRIVATE)
            .getString("cloudflare_tunnel_token", "")
        assertFalse("Raw token must not be stored in preferences", raw.orEmpty().contains(token))
        CloudflareTokenStore.clear(context)
        assertFalse(CloudflareTokenStore.hasToken(context))
        assertEquals(null, CloudflareTokenStore.read(context))
    }
}
