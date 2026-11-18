package com.assayflux.pockethost

import android.content.Context
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.assayflux.pockethost.management.HostAccountStore
import org.junit.Assert.assertTrue

/**
 * UI tests must authenticate through the real login screen. Setting the app's
 * process-local Owner session from the instrumentation process does not cross
 * Android process boundaries and can make tests accidentally depend on process reuse.
 */
internal object PocketHostUiTestLogin {
    const val USERNAME = "uiowner"
    const val PASSWORD = "PocketHost-UI-Owner-Password-42"

    fun ensureOwnerAccount(context: Context) {
        if (HostAccountStore.list(context).none { it.role == "OWNER" }) {
            HostAccountStore.create(context, USERNAME, PASSWORD.toCharArray(), "OWNER")
        }
    }

    fun signInIfRequired(device: UiDevice) {
        if (device.wait(Until.hasObject(By.desc("Open app navigation")), 2_000)) return

        val loginHeading = device.wait(
            Until.findObject(By.text("Sign in to Pocket Host")), 10_000
        ) ?: device.wait(Until.findObject(By.text("Create your Owner account")), 5_000)
        assertTrue("Owner login screen did not appear", loginHeading != null)

        val fields = device.wait(
            Until.findObject(By.clazz("android.widget.EditText")), 10_000
        )
        assertTrue("Owner login fields did not appear", fields != null)
        val editFields = device.findObjects(By.clazz("android.widget.EditText"))
        assertTrue("Username and password fields are missing", editFields.size >= 2)
        editFields[0].click()
        editFields[0].setText(USERNAME)
        editFields[1].click()
        editFields[1].setText(PASSWORD)

        val submit = device.wait(
            Until.findObject(By.text("Sign in")), 5_000
        ) ?: device.wait(Until.findObject(By.text("Create Owner account")), 5_000)
        assertTrue("Owner sign-in action is missing", submit != null)
        submit!!.click()
        assertTrue(
            "Owner sign-in did not open the dashboard",
            device.wait(Until.hasObject(By.desc("Open app navigation")), 30_000)
        )
    }
}
