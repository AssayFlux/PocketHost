package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.engine.NativeEngine
import com.assayflux.pockethost.management.RemoteControlAgent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerOnlyModeEnforcementTest {
    @Test
    fun switchingToServerOnlyStopsFleetPollingWithoutStoppingWebsiteHosting() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modePreferences = context.getSharedPreferences("pockethost_state", 0)
        val previousMode = modePreferences.getString("operating_mode", "SERVER_ONLY")
        val remotePreferences = context.getSharedPreferences("pockethost_remote_control", 0)
        val previousRemoteValues = remotePreferences.all.toMap()
        val siteRoot = File(context.cacheDir, "server-only-mode-${System.nanoTime()}")
        assertTrue(siteRoot.mkdirs())
        File(siteRoot, "index.html").writeText("<h1>Server only remains hosted</h1>")
        val wasHosting = NativeEngine.status() == "RUNNING"
        val startedHere = !wasHosting
        val port = 19_000 + (System.nanoTime() % 10_000L).toInt()

        try {
            RemoteControlAgent.stop()
            if (startedHere) {
                assertEquals("Website engine failed to start for the mode-transition test.",
                    "OK", NativeEngine.start(siteRoot.absolutePath, port, false))
            }
            assertEquals("RUNNING", NativeEngine.status())

            modePreferences.edit().putString("operating_mode", "COMMAND_CENTER_SERVER").commit()
            RemoteControlAgent.configure(context, "https://127.0.0.1:1", "server-only-mode-test-token-0123456789")
            RemoteControlAgent.start(context)
            assertTrue("Command Center mode did not start the configured fleet agent.",
                RemoteControlAgent.isRunningForTest())

            modePreferences.edit().putString("operating_mode", "SERVER_ONLY").commit()
            // HostingService calls start on service startup; the start guard must
            // stop any prior worker when the owner has switched back to Server Only.
            RemoteControlAgent.start(context)

            assertFalse("Server Only mode left the fleet control agent polling.",
                RemoteControlAgent.isRunningForTest())
            assertEquals("Disabling fleet polling must not stop local website hosting.",
                "RUNNING", NativeEngine.status())
        } finally {
            RemoteControlAgent.stop()
            val editor = remotePreferences.edit().clear()
            previousRemoteValues.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            editor.commit()
            if (previousMode == null) modePreferences.edit().remove("operating_mode").commit()
            else modePreferences.edit().putString("operating_mode", previousMode).commit()
            if (startedHere) NativeEngine.stop()
            siteRoot.deleteRecursively()
        }
    }
}
