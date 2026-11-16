package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.storage.SiteImporter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SiteImporterRecoveryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

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
