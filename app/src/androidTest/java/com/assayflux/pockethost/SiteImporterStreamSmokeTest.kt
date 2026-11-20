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
    @Test
    fun rejectsCaseInsensitiveZipPathCollisions() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "site-case-collision-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        val context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = root
            override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        }
        try {
            val archive = zipOf("index.html" to "ok", "Index.html" to "collision")
            assertTrue(runCatching { SiteImporter.importZipStream(context, ByteArrayInputStream(archive)) }.isFailure)
            assertTrue(!File(root, "site").exists())
        } finally { root.deleteRecursively() }
    }

    @Test
    fun rejectsZipFileUsedAsParentDirectory() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "site-parent-collision-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        val context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = root
            override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        }
        try {
            val archive = zipOf("index.html" to "ok", "assets" to "file", "assets/logo.png" to "child")
            assertTrue(runCatching { SiteImporter.importZipStream(context, ByteArrayInputStream(archive)) }.isFailure)
            assertTrue(!File(root, "site").exists())
        } finally { root.deleteRecursively() }
    }

    @Test
    fun rejectsCredentialAndPrivateKeyPathsFromZip() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "site-secret-path-" + System.nanoTime())
        assertTrue(root.mkdirs())
        val context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = root
            override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        }
        try {
            val forbidden = listOf(
                ".aws/credentials",
                ".npmrc",
                ".git-credentials",
                ".docker/config.json",
                ".kube/config",
                "secrets/service-account-prod.json",
                "tls/server.key",
                ".env.production"
            )
            forbidden.forEach { path ->
                val archive = zipOf("index.html" to "ok", path to "secret")
                assertTrue("ZIP must reject $path",
                    runCatching { SiteImporter.importZipStream(context, ByteArrayInputStream(archive)) }.isFailure)
                assertTrue(!File(root, "site").exists())
            }
        } finally { root.deleteRecursively() }
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val archive = ByteArrayOutputStream()
        ZipOutputStream(archive).use { zip ->
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return archive.toByteArray()
    }

    @Test
    fun websiteImportLimitIs500Megabytes() {
        assertEquals(500_000_000L, SiteImporter.MAX_IMPORT_BYTES)
    }
}
