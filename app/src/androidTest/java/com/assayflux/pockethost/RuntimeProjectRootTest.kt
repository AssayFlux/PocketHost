package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.runtime.RuntimeProjectRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class RuntimeProjectRootTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun acceptsProjectInsideEveryAllowedPrivateDirectory() {
        val filesProject = File(context.filesDir, "runtime-root-test-files").apply { mkdirs() }
        val cacheProject = File(context.cacheDir, "runtime-root-test-cache").apply { mkdirs() }
        val noBackupProject = File(context.noBackupFilesDir, "runtime-root-test-no-backup").apply { mkdirs() }
        val codeCacheProject = File(context.codeCacheDir, "runtime-root-test-code-cache").apply { mkdirs() }
        try {
            assertEquals(filesProject.canonicalFile,
                RuntimeProjectRoot.requireOwnedProjectRoot(context, filesProject.absolutePath))
            assertEquals(cacheProject.canonicalFile,
                RuntimeProjectRoot.requireOwnedProjectRoot(context, cacheProject.absolutePath))
            assertEquals(noBackupProject.canonicalFile,
                RuntimeProjectRoot.requireOwnedProjectRoot(context, noBackupProject.absolutePath))
            assertEquals(codeCacheProject.canonicalFile,
                RuntimeProjectRoot.requireOwnedProjectRoot(context, codeCacheProject.absolutePath))
        } finally {
            filesProject.deleteRecursively()
            cacheProject.deleteRecursively()
            noBackupProject.deleteRecursively()
            codeCacheProject.deleteRecursively()
        }
    }

    @Test
    fun rejectsExternalDirectoryAndSymlinkEscape() {
        val outside = File(context.filesDir.parentFile, "pockethost-outside-" + System.nanoTime())
            .apply { mkdirs() }
        val link = File(context.cacheDir, "pockethost-root-link-" + System.nanoTime())
        try {
            assertThrows(IllegalArgumentException::class.java) {
                RuntimeProjectRoot.requireOwnedProjectRoot(context, outside.absolutePath)
            }
            Files.createSymbolicLink(link.toPath(), outside.toPath())
            assertThrows(IllegalArgumentException::class.java) {
                RuntimeProjectRoot.requireOwnedProjectRoot(context, link.absolutePath)
            }
        } finally {
            Files.deleteIfExists(link.toPath())
            outside.deleteRecursively()
        }
    }
}
