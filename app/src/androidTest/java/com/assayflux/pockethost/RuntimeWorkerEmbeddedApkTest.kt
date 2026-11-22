package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeWorkerEmbeddedApkTest {
    @Test
    fun universalHostApkContainsTheSignedRuntimeCompanionPackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val embedded = context.cacheDir.resolve("runtime-worker-embedded-test.apk")
        try {
            context.assets.open("pockethost-runtime-worker.apk").use { input ->
                embedded.outputStream().use { output -> input.copyTo(output) }
            }
            assertTrue("Embedded runtime APK is empty", embedded.isFile && embedded.length() > 1_000_000L)
            val packageInfo = context.packageManager.getPackageArchiveInfo(embedded.absolutePath, 0)
            assertNotNull("Embedded runtime APK could not be parsed", packageInfo)
            assertEquals("com.assayflux.pockethost.worker", packageInfo?.packageName)
            assertEquals("0.1.0", packageInfo?.versionName)
        } finally {
            embedded.delete()
        }
    }
}
