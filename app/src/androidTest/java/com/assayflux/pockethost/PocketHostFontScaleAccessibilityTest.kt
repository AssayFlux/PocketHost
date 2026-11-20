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

@RunWith(AndroidJUnit4::class)
class PocketHostFontScaleAccessibilityTest {
    private fun shell(instrumentation: android.app.Instrumentation, command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    @Test
    fun hostingDashboardRemainsReachableAtLargeSystemFontScale() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val originalScale = shell(instrumentation, "settings get system font_scale").trim()
            .takeIf { it.isNotBlank() && it != "null" } ?: "1.0"
        val device = UiDevice.getInstance(instrumentation)
        try {
            shell(instrumentation, "settings put system font_scale 1.5")
            context.getSharedPreferences("pockethost_state", 0).edit()
                .putBoolean("biometric_unlock_enabled", false)
                .commit()
            PocketHostUiTestLogin.ensureOwnerAccount(context)

            ActivityScenario.launch(MainActivity::class.java).use {
                PocketHostUiTestLogin.signInIfRequired(device)
                val menu = device.wait(Until.findObject(By.desc("Open app navigation")), 15_000)
                assertTrue("Navigation control disappeared at 150% font scale", menu != null)
                menu!!.click()
                val hosting = device.wait(Until.findObject(By.text("Hosting")), 10_000)
                assertTrue("Hosting destination is unreachable at 150% font scale", hosting != null)
                hosting!!.click()
                assertTrue("Hosting status heading is not readable at 150% font scale",
                    device.wait(Until.hasObject(By.text("HOSTING STATUS")), 10_000))
                assertTrue("Hosting action is not reachable at 150% font scale",
                    device.wait(Until.hasObject(By.text("Start hosting")), 10_000) ||
                        device.wait(Until.hasObject(By.text("Stop hosting")), 5_000))
                assertTrue("Website management action is not reachable at 150% font scale",
                    device.wait(Until.hasObject(By.text("Manage site")), 10_000))
            }
        } finally {
            shell(instrumentation, "settings put system font_scale $originalScale")
        }
    }
}
