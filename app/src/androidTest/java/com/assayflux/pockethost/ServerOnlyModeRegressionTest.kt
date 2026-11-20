package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.engine.NativeEngine
import com.assayflux.pockethost.management.RemoteControlAgent
import java.io.File
import java.net.ServerSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerOnlyModeRegressionTest {
    @Test
    fun switchingToServerOnlyStopsFleetPollingWithoutStoppingWebsiteHosting() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = context.getSharedPreferences("pockethost_state", 0)
        val remote = context.getSharedPreferences("pockethost_remote_control", 0)
        val oldMode = state.getString("operating_mode", "SERVER_ONLY")
        val oldRemoteValues = remote.all
        val root = File(context.cacheDir, "server-only-mode-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        File(root, "index.html").writeText("website hosting remains active")
        val port = ServerSocket(0).use { it.localPort }

        try {
            RemoteControlAgent.stop()
            RemoteControlAgent.configure(context, "https://127.0.0.1:1", "mode-regression-token-0123456789abcdef")
            assertEquals("OK", NativeEngine.start(root.absolutePath, port, false))
            state.edit().putString("operating_mode", "COMMAND_CENTER_SERVER").commit()
            RemoteControlAgent.start(context)
            assertTrue("Command Center + Server should start the configured fleet agent.",
                RemoteControlAgent.isRunningForTests())

            state.edit().putString("operating_mode", "SERVER_ONLY").commit()
            RemoteControlAgent.start(context)
            assertFalse("Server Only must stop fleet polling even if credentials remain stored.",
                RemoteControlAgent.isRunningForTests())
            assertEquals("RUNNING", NativeEngine.status())
        } finally {
            RemoteControlAgent.stop()
            NativeEngine.stop()
            state.edit().putString("operating_mode", oldMode ?: "SERVER_ONLY").commit()
            remote.edit().clear().apply {
                oldRemoteValues.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
            }.commit()
            root.deleteRecursively()
        }
    }
}
