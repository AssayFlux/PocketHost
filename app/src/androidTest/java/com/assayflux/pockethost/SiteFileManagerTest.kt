package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
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
