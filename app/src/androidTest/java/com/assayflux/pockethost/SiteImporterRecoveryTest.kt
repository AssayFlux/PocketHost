package com.assayflux.pockethost

import android.content.ContextWrapper
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.storage.SiteImporter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SiteImporterRecoveryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun invalidActiveEntryIsQuarantinedBeforeLastKnownGoodRecovery() {
        val active = File(context.filesDir, "site")
        val releases = File(context.filesDir, "site-releases")
        val original = File(context.cacheDir, "site-recovery-invalid-original-${System.nanoTime()}")
        val hadActive = active.isDirectory
        if (hadActive) active.copyRecursively(original, overwrite = true)
        val release = File(releases, "release-${System.currentTimeMillis() + 120_000_000L}-invalid-test")
        val marker = "preserve-invalid-active-${System.nanoTime()}"
        var quarantine: File? = null
        try {
            assertTrue(releases.exists() || releases.mkdirs())
            assertTrue(release.mkdirs())
            File(release, "index.html").writeText("<h1>recovered</h1>")
            active.deleteRecursively()
            assertTrue(active.mkdirs())
            File(active, "broken.txt").writeText(marker)

            assertTrue(SiteImporter.recoverInterruptedActivation(context))
            assertEquals("<h1>recovered</h1>", File(active, "index.html").readText())
            quarantine = releases.listFiles().orEmpty().firstOrNull {
                it.name.startsWith("recovery-invalid-") && File(it, "broken.txt").isFile
            }
            assertTrue("Invalid active deployment should be preserved for diagnosis.", quarantine != null)
            assertEquals(marker, File(checkNotNull(quarantine), "broken.txt").readText())
        } finally {
            active.deleteRecursively()
            if (hadActive && original.isDirectory) original.copyRecursively(active, overwrite = true)
            release.deleteRecursively()
            quarantine?.deleteRecursively()
            original.deleteRecursively()
        }
    }

    @Test
    fun symlinkedActiveRootIsQuarantinedWithoutFollowingItsTarget() {
        val base = context
        val root = File(base.cacheDir, "site-recovery-symlink-${System.nanoTime()}")
        val outside = File(base.cacheDir, "site-recovery-outside-${System.nanoTime()}")
        val releases = File(root, "site-releases")
        val active = File(root, "site")
        val release = File(releases, "release-${System.currentTimeMillis() + 180_000_000L}-safe")
        val isolated = object : ContextWrapper(base) {
            override fun getFilesDir(): File = root
        }
        try {
            assertTrue(root.mkdirs())
            assertTrue(outside.mkdirs())
            assertTrue(releases.mkdirs())
            assertTrue(release.mkdirs())
            File(outside, "index.html").writeText("must-not-become-active")
            File(release, "index.html").writeText("safe-release")
            Os.symlink(outside.absolutePath, active.absolutePath)

            assertTrue(SiteImporter.recoverInterruptedActivation(isolated))
            assertEquals("safe-release", File(active, "index.html").readText())
            assertEquals("must-not-become-active", File(outside, "index.html").readText())
            assertTrue(releases.listFiles().orEmpty().any { it.name.startsWith("recovery-invalid-") })
        } finally {
            active.delete()
            releases.deleteRecursively()
            outside.deleteRecursively()
            root.deleteRecursively()
        }
    }

    @Test
    fun missingActiveSiteRecoversNewestValidLastKnownGoodRelease() {
        val active = File(context.filesDir, "site")
        val releases = File(context.filesDir, "site-releases")
        val original = File(context.cacheDir, "site-recovery-original-${System.nanoTime()}")
        val hadActive = active.isDirectory
        if (hadActive) active.copyRecursively(original, overwrite = true)
        val release = File(releases, "release-${System.currentTimeMillis() + 60_000_000L}-recovery-test")
        try {
            assertTrue(releases.exists() || releases.mkdirs())
            assertTrue(release.mkdirs())
            File(release, "index.html").writeText("<h1>last-known-good</h1>")
            active.deleteRecursively()

            assertTrue(SiteImporter.recoverInterruptedActivation(context))
            assertEquals("<h1>last-known-good</h1>", File(active, "index.html").readText())
            assertTrue(!SiteImporter.recoverInterruptedActivation(context))
        } finally {
            active.deleteRecursively()
            if (hadActive && original.isDirectory) original.copyRecursively(active, overwrite = true)
            release.deleteRecursively()
            // The test release is renamed to the active site during recovery.
            if (active.isDirectory && File(active, "index.html").readText().contains("last-known-good")) {
                active.deleteRecursively()
                if (hadActive && original.isDirectory) original.copyRecursively(active, overwrite = true)
            }
            original.deleteRecursively()
        }
    }
}
