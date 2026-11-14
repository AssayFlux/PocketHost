package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.diagnostics.OperationalLog
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OperationalLogSmokeTest {
    @Test
    fun redactsSecretsAndRotatesWithinBoundedStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "operations")
        directory.deleteRecursively()

        OperationalLog.record(
            context,
            "INFO",
            "REDACTION_TEST",
            "token=super-secret Bearer eyJhbGciOiJIUzI1NiJ9"
        )
        val redacted = OperationalLog.read(context, 1).firstOrNull()
        assertNotNull(redacted)
        assertTrue(redacted!!.message.contains("[REDACTED]"))
        assertFalse(redacted.message.contains("super-secret"))
        assertFalse(redacted.message.contains("eyJhbGciOiJIUzI1NiJ9"))

        val payload = "x".repeat(1_000)
        repeat(1_100) { index ->
            OperationalLog.record(context, "INFO", "ROTATION_TEST", "$index $payload")
        }

        assertTrue("first rotated log should exist", File(directory, "events.jsonl.1").isFile)
        val totalBytes = directory.listFiles()?.sumOf { it.length() } ?: 0L
        assertTrue("rotated log storage must remain bounded", totalBytes <= 4L * 1024L * 1024L)
        assertTrue("latest event should be readable", OperationalLog.read(context, 1).first().event == "ROTATION_TEST")
        directory.deleteRecursively()
    }
}
