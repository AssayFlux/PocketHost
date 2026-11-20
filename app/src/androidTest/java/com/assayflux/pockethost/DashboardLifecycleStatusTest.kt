package com.assayflux.pockethost

import androidx.lifecycle.Lifecycle
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
class DashboardLifecycleStatusTest {
    @Test
    fun dashboardKeepsPersistentServiceStatusSeparateAcrossActivityStopAndResume() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("pockethost_state", 0)
        val previousStatus = preferences.getString("service_status", null)
        preferences.edit()
            .putBoolean("biometric_unlock_enabled", false)
            .putString("service_status", "Persistent service status test")
            .commit()
        PocketHostUiTestLogin.ensureOwnerAccount(context)
        val device = UiDevice.getInstance(instrumentation)

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                PocketHostUiTestLogin.signInIfRequired(device)
                val menu = device.wait(Until.findObject(By.desc("Open app navigation")), 15_000)
                assertTrue("Navigation control did not render", menu != null)
                menu!!.click()
                val hosting = device.wait(Until.findObject(By.text("Hosting")), 10_000)
                assertTrue("Hosting destination is missing", hosting != null)
                hosting!!.click()

                assertTrue("Persistent service status is not rendered",
                    device.wait(Until.hasObject(By.text("Persistent service status test")), 10_000))
                assertTrue("Transient action feedback is not rendered separately",
                    device.wait(Until.hasObject(By.text("Ready to configure your first website.")), 10_000))

                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.STARTED)
                instrumentation.waitForIdleSync()

                assertTrue("Persistent service status disappeared after stop/resume",
                    device.wait(Until.hasObject(By.text("Persistent service status test")), 10_000))
                assertTrue("Dashboard stopped responding after resume",
                    device.wait(Until.hasObject(By.text("HOSTING STATUS")), 10_000))
            }
        } finally {
            val editor = preferences.edit()
            if (previousStatus == null) editor.remove("service_status")
            else editor.putString("service_status", previousStatus)
            editor.commit()
        }
    }
}
