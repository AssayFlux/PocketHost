package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.workbench.WorkbenchWorkspaceManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkbenchWorkspaceManagerTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun projectId() = "wb-${System.nanoTime()}"

    @Test
    fun workspaceEditsAndCommitsNeverModifyLiveWebsite() {
        val id = projectId()
        val live = File(context.cacheDir, "$id-live")
        val projectStorage = File(context.filesDir, "workbench/$id")
        try {
            assertTrue(live.mkdirs())
            File(live, "index.html").writeText("<h1>live</h1>")
            File(live, "assets").mkdirs()
            File(live, "assets/site.css").writeText("body { color: black; }")

            val workspace = WorkbenchWorkspaceManager.open(context, id, live)
            assertEquals("<h1>live</h1>", File(workspace, "index.html").readText())
            WorkbenchWorkspaceManager.writeText(context, id, "index.html", "<h1>draft</h1>")
            WorkbenchWorkspaceManager.createText(context, id, "new-page.html", "<p>draft</p>")

            assertEquals("<h1>live</h1>", File(live, "index.html").readText())
            assertFalse(File(live, "new-page.html").exists())
            val changes = WorkbenchWorkspaceManager.changes(context, id, live)
            assertTrue(changes.any { it.path == "index.html" && it.status == "modified" })
            assertTrue(changes.any { it.path == "new-page.html" && it.status == "added" })

            val commit = WorkbenchWorkspaceManager.commit(context, id, "Draft website update", live)
            assertNotNull(commit.id)
            val snapshot = File(projectStorage, "commits/${commit.id}/snapshot/index.html")
            assertEquals("<h1>draft</h1>", snapshot.readText())
            assertTrue("A successful commit must reset the change baseline.", WorkbenchWorkspaceManager.changes(context, id, live).isEmpty())
            WorkbenchWorkspaceManager.writeText(context, id, "index.html", "<h1>later edit</h1>")
            assertEquals("Commit snapshots are immutable.", "<h1>draft</h1>", snapshot.readText())
            assertEquals(1, WorkbenchWorkspaceManager.commits(context, id).size)
            assertEquals("<h1>live</h1>", File(live, "index.html").readText())
        } finally {
            live.deleteRecursively()
            projectStorage.deleteRecursively()
        }
    }

    @Test
    fun reopeningWorkspaceDoesNotDiscardExistingEdits() {
        val id = projectId()
        val live = File(context.cacheDir, "$id-live")
        val storage = File(context.filesDir, "workbench/$id")
        try {
            assertTrue(live.mkdirs())
            File(live, "index.html").writeText("original")
            WorkbenchWorkspaceManager.open(context, id, live)
            WorkbenchWorkspaceManager.writeText(context, id, "index.html", "unsaved workspace draft")
            File(live, "index.html").writeText("new production version")
            val reopened = WorkbenchWorkspaceManager.open(context, id, live)
            assertEquals("unsaved workspace draft", File(reopened, "index.html").readText())
            assertEquals("new production version", File(live, "index.html").readText())
        } finally {
            live.deleteRecursively()
            storage.deleteRecursively()
        }
    }

    @Test
    fun rejectsTraversalAndExcludesSensitiveFiles() {
        val id = projectId()
        val live = File(context.cacheDir, "$id-live")
        val storage = File(context.filesDir, "workbench/$id")
        try {
            assertTrue(live.mkdirs())
            File(live, "index.html").writeText("safe")
            File(live, ".env").writeText("SECRET=never-publish")
            val workspace = WorkbenchWorkspaceManager.open(context, id, live)
            assertFalse(File(workspace, ".env").exists())
            val traversal = runCatching {
                WorkbenchWorkspaceManager.createText(context, id, "../escape.html", "bad")
            }.exceptionOrNull()
            assertNotNull("Traversal must be rejected.", traversal)
            assertFalse(File(context.filesDir, "workbench/escape.html").exists())
        } finally {
            live.deleteRecursively()
            storage.deleteRecursively()
        }
    }

    @Test
    fun nestedFoldersAndFileOperationsStayInsideDraftWorkspace() {
        val id = projectId()
        val live = File(context.cacheDir, "$id-live")
        val storage = File(context.filesDir, "workbench/$id")
        try {
            assertTrue(live.mkdirs())
            File(live, "index.html").writeText("<h1>live</h1>")
            val workspace = WorkbenchWorkspaceManager.open(context, id, live)
            WorkbenchWorkspaceManager.createFolder(context, id, "assets")
            WorkbenchWorkspaceManager.createText(context, id, "assets/theme.css", "body { color: navy; }")
            assertEquals("body { color: navy; }", WorkbenchWorkspaceManager.readText(context, id, "assets/theme.css"))
            WorkbenchWorkspaceManager.rename(context, id, "assets/theme.css", "assets/site.css")
            assertTrue(File(workspace, "assets/site.css").isFile)
            WorkbenchWorkspaceManager.deleteFile(context, id, "assets/site.css")
            assertFalse(File(workspace, "assets/site.css").exists())
            assertFalse(File(live, "assets").exists())
        } finally {
            live.deleteRecursively()
            storage.deleteRecursively()
        }
    }


    @Test
    fun commitHistoryRetainsOnlyBoundedRecentSnapshots() {
        val id = projectId()
        val live = File(context.cacheDir, "$id-live")
        val storage = File(context.filesDir, "workbench/$id")
        try {
            assertTrue(live.mkdirs())
            File(live, "index.html").writeText("version-0")
            WorkbenchWorkspaceManager.open(context, id, live)
            val created = mutableListOf<String>()
            repeat(12) { version ->
                WorkbenchWorkspaceManager.writeText(context, id, "index.html", "version-${version + 1}")
                created += WorkbenchWorkspaceManager.commit(context, id, "Version ${version + 1}", live).id
            }
            val history = WorkbenchWorkspaceManager.commits(context, id)
            assertEquals("History must retain at most ten commits.", 10, history.size)
            assertTrue("The newest commit must remain available.", File(storage, "commits/${created.last()}/snapshot/index.html").isFile)
            assertFalse("The oldest commit should be pruned.", File(storage, "commits/${created.first()}").exists())
            assertEquals("The current commit is the new change baseline.", emptyList<WorkbenchWorkspaceManager.Change>(),
                WorkbenchWorkspaceManager.changes(context, id, live))
        } finally {
            live.deleteRecursively()
            storage.deleteRecursively()
        }
    }

    @Test
    fun projectIdsCannotEscapeWorkbenchStorage() {
        val error = runCatching {
            WorkbenchWorkspaceManager.open(context, "../outside", context.filesDir)
        }.exceptionOrNull()
        assertNotNull(error)
    }
}
