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
    fun ownerHomeLaunchesAndProducesReviewableScreenshot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("pockethost_state", 0).edit()
            .putBoolean("biometric_unlock_enabled", false)
            .commit()

        val owner = com.assayflux.pockethost.management.HostAccountStore.list(context).firstOrNull { it.role == "OWNER" }
            ?: com.assayflux.pockethost.management.HostAccountStore.create(
                context, "uiowner", "PocketHost-UI-Owner-Password-42".toCharArray(), "OWNER"
            )
        PocketHostOwnerSession.username = owner.username
        val device = UiDevice.getInstance(instrumentation)
        val externalFiles = checkNotNull(context.getExternalFilesDir(null)) { "App external files directory is unavailable" }
        val screenshotDir = File(externalFiles, "screenshots")
        assertTrue("Screenshot directory could not be created", screenshotDir.mkdirs() || screenshotDir.isDirectory)
        val screenshot = File(screenshotDir, "home.png")
        val hierarchy = File(screenshotDir, "home-hierarchy.xml")
        screenshot.delete()
        hierarchy.delete()

        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.waitForIdleSync()
            // Capture first so the artifact remains useful even when an accessibility
            // selector regresses on an older Compose/Android combination.
            assertTrue("Dashboard screenshot could not be captured", device.takeScreenshot(screenshot))
            device.dumpWindowHierarchy(hierarchy)
            // Compose startup can be unusually slow on the serialized API 26/33
            // emulators. Wait for the app window and its destination semantics rather
            // than treating a delayed first frame as a missing screen.
            device.waitForWindowUpdate(context.packageName, 30_000)
            val homeVisible = device.wait(Until.hasObject(By.text("Your services")), 60_000) &&
                device.wait(Until.hasObject(By.desc("Open app navigation")), 5_000) &&
                device.wait(Until.hasObject(By.desc("Open account and app menu")), 5_000)
            assertTrue(
                "Owner home did not render its service carousel and menu controls. Accessibility hierarchy: " +
                    hierarchy.takeIf { it.isFile }?.readText()?.take(6_000),
                homeVisible
            )
            val accountMenu = device.wait(Until.findObject(By.desc("Open account and app menu")), 10_000)
            assertTrue("Account dropdown control is missing", accountMenu != null)
            accountMenu!!.click()
            assertTrue("Account dropdown settings route is missing",
                device.wait(Until.hasObject(By.text("App settings")), 5_000))
            assertTrue("Account dropdown network and access route is missing",
                device.wait(Until.hasObject(By.text("Network & access")), 5_000))
            assertTrue("Account dropdown About route is missing",
                device.wait(Until.hasObject(By.text("About Pocket Host")), 5_000))
            device.findObject(By.text("App settings")).click()
            assertTrue("Appearance settings did not open",
                device.wait(Until.hasObject(By.text("Make Pocket Host feel like yours")), 10_000))
            val darkMode = device.wait(Until.findObject(By.text("Dark")), 5_000)
            assertTrue("Dark appearance option is missing", darkMode != null)
            darkMode!!.click()
            val forestTheme = device.wait(Until.findObject(By.text("Forest")), 5_000)
            assertTrue("Forest classic palette option is missing", forestTheme != null)
            forestTheme!!.click()
            instrumentation.waitForIdleSync()
            assertTrue("Dark appearance preference was not persisted",
                context.getSharedPreferences("pockethost_state", 0).getString("appearance_mode", "") == "Dark")
            assertTrue("Classic palette preference was not persisted",
                context.getSharedPreferences("pockethost_state", 0).getString("appearance_theme", "") == "Forest")
            device.findObject(By.text("System")).click()
            device.findObject(By.text("Classic")).click()
            instrumentation.waitForIdleSync()
            device.pressBack()

        }

        assertTrue("Home screenshot is empty", screenshot.isFile && screenshot.length() > 10_000)
        assertTrue("Dashboard accessibility hierarchy was not captured", hierarchy.isFile && hierarchy.length() > 100)
    }
}
