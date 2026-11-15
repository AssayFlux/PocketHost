package com.assayflux.pockethost

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.storage.SiteImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class SiteImporterStreamSmokeTest {
    @Test
    fun validatesAndActivatesZipFromDecodedInputStream() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedRoot = File(base.cacheDir, "site-stream-test-${System.nanoTime()}")
        assertTrue(isolatedRoot.mkdirs())
        val isolatedContext = object : ContextWrapper(base) {
            override fun getFilesDir(): File = isolatedRoot
            override fun getCacheDir(): File = File(isolatedRoot, "cache").apply { mkdirs() }
        }

        try {
            val archive = ByteArrayOutputStream()
            ZipOutputStream(archive).use { zip ->
                zip.putNextEntry(ZipEntry("site/index.html"))
                zip.write("<!doctype html><title>stream import</title>".toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            val report = SiteImporter.importZipStream(isolatedContext, ByteArrayInputStream(archive.toByteArray()))
            assertEquals(1, report.fileCount)
            assertTrue(File(isolatedRoot, "site/index.html").isFile)
        } finally {
            isolatedRoot.deleteRecursively()
        }
    }
}
