package com.assayflux.pockethost

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
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
    fun drawerDestinationsExposeLabelsAndRenderTheirOwnScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val statePreferences = context.getSharedPreferences("pockethost_state", 0)
        val previousOperatingMode = statePreferences.getString("operating_mode", "SERVER_ONLY") ?: "SERVER_ONLY"
        statePreferences.edit()
            .putBoolean("biometric_unlock_enabled", false)
            .putBoolean("python_enabled", false)
            .putBoolean("node_enabled", false)
            .putBoolean("tunnel_enabled", false)
            .putBoolean("auto_start_on_boot", false)
            .putString("operating_mode", "COMMAND_CENTER_SERVER")
            .commit()
        val registryPreferences = context.getSharedPreferences("pockethost_site_registry", 0)
        val previousRegistry = registryPreferences.getString("sites", "[]") ?: "[]"
        val visitorSiteId = "ui-visitor-" + System.nanoTime()
        val visitorHostname = "ui-" + System.nanoTime() + ".local"
        val visitorSiteRoot = File(context.filesDir, "site-roots/ui-visitor-site-${System.nanoTime()}")
        PocketHostUiTestLogin.ensureOwnerAccount(context)
        val device = UiDevice.getInstance(instrumentation)
        val screenshotDir = File(checkNotNull(context.getExternalFilesDir(null)), "screenshots")
        assertTrue(screenshotDir.mkdirs() || screenshotDir.isDirectory)

        val expectedContent = linkedMapOf(
            "Home" to Triple("Home", "Your services", "Home"),
            "Hosting" to Triple("Hosting", "HOSTING STATUS", "Dashboard"),
            "Websites & files" to Triple("Websites & files", "WEBSITE OVERVIEW", "Sites"),
            "Workbench" to Triple("Workbench", "POCKETHOST WORKBENCH", "Workbench"),
            "Deployments" to Triple("Deployments", "SERVICE COMING SOON", "Deployments"),
            "Devices" to Triple("Devices", "COMMAND CENTER IDENTITY", "Devices"),
            "Activity & logs" to Triple("Activity & logs", "RECENT OPERATIONS", "Activity"),
            "Learn" to Triple("Learn", "Learn Pocket Host", "Learn"),
            "Settings" to Triple("Settings", "Make Pocket Host feel like yours", "Settings")
        )
        fun openDrawerDestination(label: String) {
            val menu = device.wait(Until.findObject(By.desc("Open app navigation")), 10_000)
            assertTrue("Hamburger navigation control is missing", menu != null)
            menu!!.click()
            val scrollableDrawer = UiScrollable(UiSelector().scrollable(true)).setAsVerticalList()
            scrollableDrawer.scrollTextIntoView(label)
            val destination = device.wait(Until.findObject(By.text(label)), 10_000)
            assertTrue("Drawer destination $label is missing", destination != null)
            destination!!.click()
        }
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
            PocketHostUiTestLogin.signInIfRequired(device)
            val firstDrawer = device.wait(Until.findObject(By.desc("Open app navigation")), 10_000)
            assertTrue("Hamburger navigation control is missing", firstDrawer != null)
            firstDrawer!!.click()
            listOf("Capacity & performance", "Backups & recovery", "Security & access", "Relay & tunnels").forEach { legacy ->
                assertFalse("Legacy duplicate navigation entry $legacy is still visible",
                    device.hasObject(By.text(legacy)))
            }
            device.pressBack()
            openDrawerDestination("Hosting")
            device.wait(Until.findObject(By.text("Schedule")), 10_000)!!.click()
            assertTrue("Hosting schedule page is missing",
                device.wait(Until.hasObject(By.text("AUTOMATIC HOSTING HOURS")), 10_000))
            device.wait(Until.findObject(By.text("Traffic & capacity")), 10_000)!!.click()
            UiScrollable(UiSelector().scrollable(true)).setAsVerticalList().scrollTextIntoView("Capacity")
            assertTrue("Traffic and capacity page is missing",
                device.wait(Until.hasObject(By.text("Capacity")), 10_000))
            device.wait(Until.findObject(By.text("Overview")), 10_000)!!.click()

            openDrawerDestination("Workbench")
            device.wait(Until.findObject(By.text("Editor")), 10_000)!!.click()
            assertTrue("Workbench Editor page is missing",
                device.wait(Until.hasObject(By.text("No file selected")), 10_000))
            device.wait(Until.findObject(By.text("Changes")), 10_000)!!.click()
            UiScrollable(UiSelector().scrollable(true)).setAsVerticalList().scrollTextIntoView("CHANGES")
            assertTrue("Workbench Changes page is missing",
                device.wait(Until.hasObject(By.text("CHANGES")), 10_000))
            device.wait(Until.findObject(By.text("History")), 10_000)!!.click()
            UiScrollable(UiSelector().scrollable(true)).setAsVerticalList().scrollTextIntoView("COMMIT HISTORY")
            assertTrue("Workbench History page is missing",
                device.wait(Until.hasObject(By.text("COMMIT HISTORY")), 10_000))
            device.wait(Until.findObject(By.text("Explorer")), 10_000)!!.click()

            openDrawerDestination("Devices")
            device.wait(Until.findObject(By.text("Fleet")), 10_000)!!.click()
            assertTrue("Devices Fleet page is missing",
                device.wait(Until.hasObject(By.text("REMOTE FLEET CONNECTION")), 10_000))
            device.wait(Until.findObject(By.text("Trusted devices")), 10_000)!!.click()
            assertTrue("Trusted devices page is missing",
                device.wait(Until.hasObject(By.text("TRUSTED DEVICES")), 10_000))
            device.wait(Until.findObject(By.text("Enrollment")), 10_000)!!.click()

            expectedContent.forEach { (destinationName, expected) ->
                val (headerName, expectedText, screenshotName) = expected
                openDrawerDestination(destinationName)
                assertTrue("Header did not reflect $headerName",
                    device.wait(Until.hasObject(By.text(headerName)), 15_000))
                assertTrue("$destinationName screen did not render $expectedText",
                    device.wait(Until.hasObject(By.text(expectedText)), 15_000))
                if (destinationName == "Websites & files") {
                    assertTrue("Folder publishing action is missing",
                        device.wait(Until.hasObject(By.text("Choose website folder")), 10_000))
                    assertTrue("ZIP publishing action is missing",
                        device.wait(Until.hasObject(By.text("Import website ZIP")), 10_000))
                    val backupTab = device.wait(Until.findObject(By.text("Backup")), 10_000)
                    assertTrue("Dedicated backup page is missing", backupTab != null)
                    backupTab!!.click()
                    UiScrollable(UiSelector().scrollable(true)).setAsVerticalList().scrollTextIntoView("Export encrypted full app backup")
                    assertTrue("Encrypted backup export action is missing",
                        device.wait(Until.hasObject(By.text("Export encrypted full app backup")), 10_000))
                    val releasesTab = device.wait(Until.findObject(By.text("Releases")), 10_000)
                    assertTrue("Dedicated releases page is missing", releasesTab != null)
                    releasesTab!!.click()
                    assertTrue("Release history page did not render",
                        device.wait(Until.hasObject(By.text("RELEASE HISTORY")), 10_000))
                    val filesTab = device.wait(Until.findObject(By.text("Files")), 10_000)
                    assertTrue("Dedicated file manager page is missing", filesTab != null)
                    filesTab!!.click()
                    assertTrue("File manager page did not render",
                        device.wait(Until.hasObject(By.text("SITE FILES")), 10_000))
                    device.wait(Until.findObject(By.text("Overview")), 10_000)!!.click()
                }
                assertTrue("Could not capture $destinationName screenshot",
                    device.takeScreenshot(File(screenshotDir, "navigation-${screenshotName.lowercase()}.png")))
                device.dumpWindowHierarchy(File(screenshotDir, "navigation-${screenshotName.lowercase()}-hierarchy.xml"))
            }
            openDrawerDestination("Settings")
            listOf(
                Triple("Network", "NETWORK & PUBLIC ACCESS", "Network & tunnels"),
                Triple("Server", "SERVER CONFIGURATION", "Server settings"),
                Triple("Remote", "REMOTE CONTROL PLANE", "Remote control"),
                Triple("Local access", "Device & access", "Local access"),
                Triple("Relay node", "NATIVE ASSAYFLUX RELAY NODE", "Native Relay Node"),
                Triple("Relay host", "ASSAYFLUX RELAY", "Relay host connection"),
                Triple("Cloudflare", "CLOUDFLARE TUNNEL", "Cloudflare Tunnel"),
                Triple("Cloudflare Access", "SERVICE COMING SOON", "Cloudflare Access"),
                Triple("DNS management", "Automatic DNS record management", "DNS management"),
                Triple("Hostnames & access", "CUSTOM HOSTNAME ROUTING", "Hostnames & visitor access"),
                Triple("Runtimes", "OPTIONAL APPLICATION RUNTIMES", "Runtimes"),
                Triple("Python", "PYTHON BACKEND · CPYTHON 3.11", "Python runtime"),
                Triple("Node.js", "NODE.JS 24 LTS · EMBEDDED RUNTIME", "Node.js runtime"),
                Triple("About", "AssayFlux Pocket Host", "About"),
                Triple("General", "Make Pocket Host feel like yours", "Settings")
            ).forEach { (section, expectedText, expectedHeader) ->
                UiScrollable(UiSelector().scrollable(true)).setAsVerticalList().scrollTextIntoView(section)
                val sectionTab = device.wait(Until.findObject(By.text(section)), 10_000)
                assertTrue("Settings section $section is not reachable", sectionTab != null)
                sectionTab!!.click()
                UiScrollable(UiSelector().scrollable(true)).setAsVerticalList().scrollTextIntoView(expectedText)
                assertTrue("Settings section $section did not render $expectedText",
                    device.wait(Until.hasObject(By.text(expectedText)), 10_000))
                assertTrue("Settings header changed while selecting $section",
                    device.wait(Until.hasObject(By.text(expectedHeader)), 5_000))
                if (section == "Hostnames & access") {
                    UiScrollable(UiSelector().scrollable(true)).setAsVerticalList().scrollTextIntoView("Visitor access")
                    assertTrue("Visitor access policy controls are missing",
                        device.wait(Until.hasObject(By.textContains("Visitor access")), 10_000))
                    assertTrue("Hostname route publishing control is missing",
                        device.wait(Until.hasObject(By.text("Save hostname route")), 10_000))
                    UiScrollable(UiSelector().scrollable(true)).setAsVerticalList().scrollTextIntoView("Private")
                    device.wait(Until.findObject(By.text("Private")), 10_000)!!.click()
                    assertTrue("Private visitor credential action is missing",
                        device.wait(Until.hasObject(By.text("Create / rotate shared credential")), 10_000))
                    device.findObject(By.text("Create / rotate shared credential")).click()
                    assertTrue("Visitor credential was not stored",
                        SiteRegistry.visitorNames(context, visitorSiteId).contains("visitor"))
                    assertTrue("One-time credential copy action is missing",
                        device.wait(Until.hasObject(By.text("Copy credential")), 10_000))
                    UiScrollable(UiSelector().scrollable(true)).setAsVerticalList().scrollTextIntoView("Revoke")
                    device.wait(Until.findObject(By.text("Revoke")), 10_000)!!.click()
                    assertFalse("Revoked visitor credential remains active",
                        SiteRegistry.visitorNames(context, visitorSiteId).contains("visitor"))
                }
                assertTrue("Could not capture Settings/$section screenshot",
                    device.takeScreenshot(File(screenshotDir, "settings-${section.lowercase().replace(' ', '-')}.png")))
                device.dumpWindowHierarchy(File(screenshotDir, "settings-${section.lowercase().replace(' ', '-')}-hierarchy.xml"))
            }
            }
        } finally {
            registryPreferences.edit().putString("sites", previousRegistry).commit()
            statePreferences.edit().putString("operating_mode", previousOperatingMode).commit()
            visitorSiteRoot.deleteRecursively()
        }
    }

    @Test
    fun emptySitesScreenExplainsFirstPublishWithoutTerminal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val registry = context.getSharedPreferences("pockethost_site_registry", 0)
        val previousRegistry = registry.getString("sites", "[]") ?: "[]"
        PocketHostUiTestLogin.ensureOwnerAccount(context)
        val device = UiDevice.getInstance(instrumentation)
        try {
            registry.edit().putString("sites", "[]").commit()
            ActivityScenario.launch(MainActivity::class.java).use {
                PocketHostUiTestLogin.signInIfRequired(device)
                val menu = device.wait(Until.findObject(By.desc("Open app navigation")), 20_000)
                assertTrue("Hamburger navigation control is missing", menu != null)
                menu!!.click()
                val sites = device.wait(Until.findObject(By.text("Websites & files")), 20_000)
                assertTrue("Sites navigation destination is not accessible", sites != null)
                sites!!.click()
                assertTrue("First-publish guidance is missing",
                    device.wait(Until.hasObject(By.text("Your first publish — no terminal needed")), 15_000))
                assertTrue("Root index.html requirement is not explained",
                    device.wait(Until.hasObject(By.textContains("index.html at the top level")), 10_000))
                assertTrue("Hosting start and preview steps are missing",
                    device.wait(Until.hasObject(By.textContains("start hosting")), 10_000))
            }
        } finally {
            registry.edit().putString("sites", previousRegistry).commit()
        }
    }

}
