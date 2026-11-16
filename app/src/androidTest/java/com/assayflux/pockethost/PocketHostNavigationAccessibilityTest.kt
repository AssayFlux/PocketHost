package com.assayflux.pockethost

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PocketHostNavigationAccessibilityTest {
    @Test
    fun allFiveDestinationsExposeLabelsAndRenderTheirOwnScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("pockethost_state", 0).edit()
            .putBoolean("biometric_unlock_enabled", false)
            .commit()
        val device = UiDevice.getInstance(instrumentation)
        val screenshotDir = File(checkNotNull(context.getExternalFilesDir(null)), "screenshots")
        assertTrue(screenshotDir.mkdirs() || screenshotDir.isDirectory)

        val expectedContent = linkedMapOf(
            "Sites" to "WEBSITE",
            "Activity" to "RECENT OPERATIONS",
            "Learn" to "Learn Pocket Host",
            "Settings" to "Dark theme",
            "Dashboard" to "HOSTING STATUS"
        )
        ActivityScenario.launch(MainActivity::class.java).use {
            expectedContent.forEach { (destinationName, expectedText) ->
                val accessibleDestination = device.wait(
                    Until.findObject(By.desc(destinationName)), 20_000
                )
                assertTrue("Navigation icon for $destinationName has no accessible label",
                    accessibleDestination != null)
                accessibleDestination!!.click()
                assertTrue("Top bar did not reflect $destinationName",
                    device.wait(Until.hasObject(By.text("ASSAYFLUX  ·  $destinationName")), 15_000))
                assertTrue("$destinationName screen did not render $expectedText",
                    device.wait(Until.hasObject(By.text(expectedText)), 15_000))
                assertTrue("Could not capture $destinationName screenshot",
                    device.takeScreenshot(File(screenshotDir, "navigation-${destinationName.lowercase()}.png")))
                device.dumpWindowHierarchy(File(screenshotDir, "navigation-${destinationName.lowercase()}-hierarchy.xml"))
            }
        }
    }
}
