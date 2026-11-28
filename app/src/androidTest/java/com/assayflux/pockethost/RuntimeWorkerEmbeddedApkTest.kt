package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeWorkerEmbeddedApkTest {
    @Test
    fun runtimeWorkerIsInstalledAsASeparatePackageAndNotEmbedded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assetNames = context.assets.list("")?.toSet().orEmpty()
        assertFalse(
            "Runtime Worker must not be embedded in the PocketHost app APK",
            "pockethost-runtime-worker.apk" in assetNames
        )
        val worker = context.packageManager.getPackageInfo("com.assayflux.pockethost.worker", 0)
        assertNotNull("The independently installed Runtime Worker APK is required", worker)
        assertTrue("Runtime Worker package version must be set", worker.longVersionCode > 0L)
    }
}
