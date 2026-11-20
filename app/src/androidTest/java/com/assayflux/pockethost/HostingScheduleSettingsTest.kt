package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.service.HostingSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostingScheduleSettingsTest {
    @Test
    fun validatesAndPersistsDailyLocalTimeSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("pockethost_state", 0)
        val hadEnabled = preferences.contains("hosting_schedule_enabled")
        val oldEnabled = preferences.getBoolean("hosting_schedule_enabled", false)
        val hadStart = preferences.contains("hosting_schedule_start")
        val oldStart = preferences.getString("hosting_schedule_start", null)
        val hadStop = preferences.contains("hosting_schedule_stop")
        val oldStop = preferences.getString("hosting_schedule_stop", null)

        try {
            assertThrows(IllegalArgumentException::class.java) {
                HostingSchedule.save(context, false, "25:00", "22:00")
            }
            assertThrows(IllegalArgumentException::class.java) {
                HostingSchedule.save(context, false, "08:00", "08:00")
            }

            HostingSchedule.save(context, false, "07:30", "21:15")
            val saved = HostingSchedule.settings(context)
            assertFalse(saved.enabled)
            assertEquals("07:30", saved.start)
            assertEquals("21:15", saved.stop)
        } finally {
            val editor = preferences.edit()
            if (hadEnabled) editor.putBoolean("hosting_schedule_enabled", oldEnabled)
            else editor.remove("hosting_schedule_enabled")
            if (hadStart) editor.putString("hosting_schedule_start", oldStart)
            else editor.remove("hosting_schedule_start")
            if (hadStop) editor.putString("hosting_schedule_stop", oldStop)
            else editor.remove("hosting_schedule_stop")
            editor.commit()
            HostingSchedule.scheduleNext(context)
        }
    }
}
