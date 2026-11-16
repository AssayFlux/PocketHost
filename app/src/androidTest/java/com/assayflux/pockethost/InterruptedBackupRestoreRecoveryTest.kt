package com.assayflux.pockethost

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.storage.ApplicationBackupManager
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InterruptedBackupRestoreRecoveryTest {
    @Test
    fun startupRecoveryRestoresPreviousSiteRuntimeRegistryAndSettings() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "restore-recovery-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        val context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = root
        }
        val backup = File(root, "pockethost-restore-previous-test")
        val pythonBackup = File(backup, "python-project")
        val pythonActive = File(root, "python-project")
        val releases = File(root, "site-releases")
        val oldRelease = File(releases, "release-100-old")
        val activeSite = File(root, "site")
        val prefs = base.getSharedPreferences("pockethost_state", 0)
        val registry = base.getSharedPreferences("pockethost_site_registry", 0)
        val oldPort = prefs.getInt("port", 8080)
        val oldPythonEnabled = prefs.getBoolean("python_enabled", false)
        val oldRegistry = registry.getString("sites", "[]") ?: "[]"

        try {
            assertTrue(pythonBackup.mkdirs())
            assertTrue(pythonActive.mkdirs())
            assertTrue(releases.mkdirs())
            assertTrue(oldRelease.mkdirs())
            assertTrue(activeSite.mkdirs())
            File(pythonBackup, "old-project.txt").writeText("previous-python")
            File(pythonActive, "new-project.txt").writeText("partially-restored-python")
            File(oldRelease, "index.html").writeText("previous-static-site")
            File(activeSite, "index.html").writeText("partially-restored-static-site")

            val previousSettings = JSONObject()
                .put("port", 8080).put("python_port", 8081).put("node_port", 3000)
                .put("python_project_root", File(root, "python-project").absolutePath)
                .put("node_project_root", File(root, "node-project-default").absolutePath)
                .put("keep_awake", false).put("auto_start_on_boot", false).put("dark_theme", false)
                .put("allow_lan", false).put("python_enabled", false).put("node_enabled", false)
                .put("trusted_runtime_code_ack", false)
            val journal = JSONObject()
                .put("version", 1)
                .put("hadActiveSite", true)
                .put("hadPython", true)
                .put("hadNode", false)
                .put("previousLatestRelease", JSONObject.NULL)
                .put("previousSiteRegistry", "[]")
                .put("previousSettings", previousSettings)
                .put("customRoots", JSONArray())
            File(backup, "restore-journal.json").writeText(journal.toString())

            prefs.edit().putInt("port", 9090).putBoolean("python_enabled", true).commit()
            registry.edit().putString("sites", "[{\"id\":\"partial\"}]").commit()

            assertTrue(ApplicationBackupManager.recoverInterruptedRestore(context))
            assertEquals("previous-python", File(pythonActive, "old-project.txt").readText())
            assertFalse(File(pythonActive, "new-project.txt").exists())
            assertEquals("previous-static-site", File(activeSite, "index.html").readText())
            assertEquals(8080, prefs.getInt("port", -1))
            assertFalse(prefs.getBoolean("python_enabled", true))
            assertEquals("[]", registry.getString("sites", null))
            assertFalse("Recovered transaction directory should be cleaned", backup.exists())
        } finally {
            root.deleteRecursively()
            prefs.edit().putInt("port", oldPort).putBoolean("python_enabled", oldPythonEnabled).commit()
            registry.edit().putString("sites", oldRegistry).commit()
        }
    }
}
