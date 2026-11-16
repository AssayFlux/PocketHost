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
                if (destinationName == "Sites") {
                    assertTrue("Folder publishing action is missing",
                        device.wait(Until.hasObject(By.text("Choose website folder")), 10_000))
                    assertTrue("ZIP publishing action is missing",
                        device.wait(Until.hasObject(By.text("Import website ZIP")), 10_000))
                    assertTrue("Encrypted backup export action is missing",
                        device.wait(Until.hasObject(By.text("Export encrypted full app backup")), 10_000))
                }
                assertTrue("Could not capture $destinationName screenshot",
                    device.takeScreenshot(File(screenshotDir, "navigation-${destinationName.lowercase()}.png")))
                device.dumpWindowHierarchy(File(screenshotDir, "navigation-${destinationName.lowercase()}-hierarchy.xml"))
            }
            device.wait(Until.findObject(By.desc("Settings")), 10_000)!!.click()
            listOf(
                "Network" to "CLOUDFLARE TUNNEL",
                "Runtimes" to "TRUSTED APPLICATION CODE ONLY",
                "About" to "AssayFlux Pocket Host",
                "General" to "Dark theme"
            ).forEach { (section, expectedText) ->
                val sectionTab = device.wait(Until.findObject(By.text(section)), 10_000)
                assertTrue("Settings section $section is not reachable", sectionTab != null)
                sectionTab!!.click()
                assertTrue("Settings section $section did not render $expectedText",
                    device.wait(Until.hasObject(By.text(expectedText)), 10_000))
                assertTrue("Settings header changed while selecting $section",
                    device.wait(Until.hasObject(By.text("ASSAYFLUX  ·  Settings")), 5_000))
                if (section == "Network") {
                    assertTrue("Visitor access policy controls are missing",
                        device.wait(Until.hasObject(By.text("Visitor access")), 10_000))
                    assertTrue("Hostname route publishing control is missing",
                        device.wait(Until.hasObject(By.text("Save hostname route")), 10_000))
                }
                assertTrue("Could not capture Settings/$section screenshot",
                    device.takeScreenshot(File(screenshotDir, "settings-${section.lowercase()}.png")))
                device.dumpWindowHierarchy(File(screenshotDir, "settings-${section.lowercase()}-hierarchy.xml"))
            }
        }
    }
}
