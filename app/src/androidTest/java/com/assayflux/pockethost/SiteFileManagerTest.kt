package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.storage.SiteFileManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SiteFileManagerTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun importsSingleFileWithinBoundedWebsiteRoot() {
        val suffix = System.nanoTime().toString()
        val source = File(context.cacheDir, "single-upload-$suffix.html")
        val destination = "uploads/single-upload-$suffix.html"
        try {
            source.writeText("<h1>uploaded</h1>")
            val imported = SiteFileManager.importFile(context, Uri.fromFile(source), destination)
            assertEquals(destination, imported.relativePath)
            assertEquals("<h1>uploaded</h1>", SiteFileManager.readText(context, destination))
            val traversal = runCatching {
                SiteFileManager.importFile(context, Uri.fromFile(source), "../outside-$suffix.html")
            }.exceptionOrNull()
            assertNotNull("Upload must reject paths outside the website root.", traversal)
        } finally {
            source.delete()
            File(context.filesDir, "site/uploads/single-upload-$suffix.html").delete()
        }
    }

    @Test
    fun renamesFilesAndFoldersWithoutOverwriting() {
        val suffix = System.nanoTime().toString()
        val folder = "rename-test-$suffix"
        val renamedFolder = "renamed-test-$suffix"
        val sourceFile = "$folder/page.html"
        val destinationFile = "$renamedFolder/page.html"
        val collisionFile = "collision-test-$suffix.txt"
        try {
            SiteFileManager.createText(context, sourceFile, "<h1>rename works</h1>")
            SiteFileManager.createText(context, collisionFile, "keep")
            SiteFileManager.renameEntry(context, folder, renamedFolder)
            assertEquals("<h1>rename works</h1>", SiteFileManager.readText(context, destinationFile))

            val collision = runCatching {
                SiteFileManager.renameEntry(context, destinationFile, collisionFile)
            }.exceptionOrNull()
            assertNotNull("Rename must not overwrite an existing entry.", collision)
            assertEquals("keep", SiteFileManager.readText(context, collisionFile))
        } finally {
            File(context.filesDir, "site/$renamedFolder").deleteRecursively()
            File(context.filesDir, "site/$folder").deleteRecursively()
            File(context.filesDir, "site/$collisionFile").delete()
        }
    }
}
