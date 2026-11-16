package com.assayflux.pockethost

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PocketHostDashboardUiSmokeTest {
    @Test
    fun dashboardLaunchesAndProducesReviewableScreenshot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("pockethost_state", 0).edit()
            .putBoolean("biometric_unlock_enabled", false)
            .commit()

        val device = UiDevice.getInstance(instrumentation)
        val externalFiles = checkNotNull(context.getExternalFilesDir(null)) { "App external files directory is unavailable" }
        val screenshotDir = File(externalFiles, "screenshots")
        assertTrue("Screenshot directory could not be created", screenshotDir.mkdirs() || screenshotDir.isDirectory)
        val screenshot = File(screenshotDir, "dashboard.png")
        val hierarchy = File(screenshotDir, "dashboard-hierarchy.xml")
        screenshot.delete()
        hierarchy.delete()

        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.waitForIdleSync()
            // Capture first so the artifact remains useful even when an accessibility
            // selector regresses on an older Compose/Android combination.
            assertTrue("Dashboard screenshot could not be captured", device.takeScreenshot(screenshot))
            device.dumpWindowHierarchy(hierarchy)
            val dashboardVisible = device.wait(Until.hasObject(By.text("Dashboard")), 15_000) ||
                device.hasObject(By.desc("Dashboard"))
            assertTrue(
                "Dashboard destination did not render. Accessibility hierarchy: " +
                    hierarchy.takeIf { it.isFile }?.readText()?.take(6_000),
                dashboardVisible
            )
        }

        assertTrue("Dashboard screenshot is empty", screenshot.isFile && screenshot.length() > 10_000)
        assertTrue("Dashboard accessibility hierarchy was not captured", hierarchy.isFile && hierarchy.length() > 100)
    }
}
