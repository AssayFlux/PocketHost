package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.diagnostics.OperationalLog
import com.assayflux.pockethost.diagnostics.TelemetryStore
import com.assayflux.pockethost.storage.SiteFileManager
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OperationalStorageBoundsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun websiteFileManagerDoesNotListOrReadSensitiveSymlinkAliases() {
        val root = File(context.filesDir, "site")
        root.mkdirs()
        val secret = File(root, ".env")
        val alias = File(root, "public-config.txt")
        try {
            secret.writeText("PRIVATE=never-serve")
            java.nio.file.Files.deleteIfExists(alias.toPath())
            java.nio.file.Files.createSymbolicLink(alias.toPath(), secret.toPath().fileName)
            assertTrue(SiteFileManager.list(context).none { it.relativePath == "public-config.txt" })
            assertTrue(runCatching { SiteFileManager.readText(context, "public-config.txt") }.isFailure)
        } finally {
            java.nio.file.Files.deleteIfExists(alias.toPath())
            secret.delete()
        }
    }

    @Test
    fun operationalLogsAndTelemetryRotateWithinTheirConfiguredBudgets() {
        val directory = File(context.filesDir, "operations")
        directory.mkdirs()
        val eventFile = File(directory, "events.jsonl")
        val eventArchives = (1..3).map { File(directory, "events.jsonl.$it") }
        val telemetryFile = File(directory, "telemetry.jsonl")
        val telemetryArchive = File(directory, "telemetry.jsonl.1")
        try {
            eventArchives.forEach { it.delete() }
            eventFile.writeBytes(ByteArray(1024 * 1024) { 'x'.code.toByte() })
            OperationalLog.record(context, "INFO", "ROTATION_TEST", "password=never-store-this")
            assertTrue(eventFile.length() < 10_000L)
            assertTrue(File(directory, "events.jsonl.1").isFile)
            assertTrue(File(directory, "events.jsonl.1").length() <= 1024L * 1024L)
            val latest = OperationalLog.read(context, 5).firstOrNull { it.event == "ROTATION_TEST" }
            assertTrue(latest != null)
            assertTrue(latest!!.message.contains("[REDACTED]"))
            assertTrue(!latest.message.contains("never-store-this"))

            telemetryArchive.delete()
            telemetryFile.writeBytes(ByteArray(512 * 1024) { 't'.code.toByte() })
            TelemetryStore.record(context)
            assertTrue(telemetryFile.length() < 20_000L)
            assertTrue(telemetryArchive.isFile)
            assertTrue(telemetryArchive.length() <= 512L * 1024L)
        } finally {
            eventFile.delete()
            eventArchives.forEach { it.delete() }
            telemetryFile.delete()
            telemetryArchive.delete()
        }
    }
}
