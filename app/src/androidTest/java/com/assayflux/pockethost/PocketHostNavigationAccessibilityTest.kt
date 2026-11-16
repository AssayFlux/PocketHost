package com.assayflux.pockethost

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.assayflux.pockethost.sites.SiteRegistry
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PocketHostNavigationAccessibilityTest {
    @Test
    fun allFiveDestinationsExposeLabelsAndRenderTheirOwnScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val statePreferences = context.getSharedPreferences("pockethost_state", 0)
        statePreferences.edit()
            .putBoolean("biometric_unlock_enabled", false)
            .putBoolean("python_enabled", false)
            .putBoolean("node_enabled", false)
            .putBoolean("tunnel_enabled", false)
            .putBoolean("auto_start_on_boot", false)
            .commit()
        val registryPreferences = context.getSharedPreferences("pockethost_site_registry", 0)
        val previousRegistry = registryPreferences.getString("sites", "[]") ?: "[]"
        val visitorSiteId = "ui-visitor-" + System.nanoTime()
        val visitorHostname = "ui-" + System.nanoTime() + ".local"
        val visitorSiteRoot = File(context.filesDir, "ui-visitor-site-${System.nanoTime()}")
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
        try {
            assertTrue(visitorSiteRoot.mkdirs())
            File(visitorSiteRoot, "index.html").writeText("<h1>UI visitor policy test</h1>")
            SiteRegistry.put(context, SiteRegistry.Site(
                id = visitorSiteId,
                name = "UI visitor test",
                runtime = "static",
                root = visitorSiteRoot.absolutePath,
                hostnames = listOf(visitorHostname),
                enabled = true
            ))
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
                    device.wait(Until.findObject(By.text("Private")), 10_000)!!.click()
                    assertTrue("Private visitor credential action is missing",
                        device.wait(Until.hasObject(By.text("Create / rotate shared credential")), 10_000))
                    device.findObject(By.text("Create / rotate shared credential")).click()
                    assertTrue("Visitor credential was not stored",
                        SiteRegistry.visitorNames(context, visitorSiteId).contains("visitor"))
                    assertTrue("One-time credential copy action is missing",
                        device.wait(Until.hasObject(By.text("Copy credential")), 10_000))
                    device.wait(Until.findObject(By.text("Revoke")), 10_000)!!.click()
                    assertFalse("Revoked visitor credential remains active",
                        SiteRegistry.visitorNames(context, visitorSiteId).contains("visitor"))
                }
                assertTrue("Could not capture Settings/$section screenshot",
                    device.takeScreenshot(File(screenshotDir, "settings-${section.lowercase()}.png")))
                device.dumpWindowHierarchy(File(screenshotDir, "settings-${section.lowercase()}-hierarchy.xml"))
            }
            }
        } finally {
            registryPreferences.edit().putString("sites", previousRegistry).commit()
            visitorSiteRoot.deleteRecursively()
        }
    }
}
