package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.sites.SiteRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SiteRegistrySecurityTest {
    @Test
    fun rejectsPrivateStorageRootAndOverlappingSiteRoots() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = System.nanoTime().toString()
        val parent = File(context.filesDir, "site-roots/isolation-$suffix")
        val nested = File(parent, "nested")
        val parentId = "isolation_parent_$suffix"
        val nestedId = "isolation_nested_$suffix"
        try {
            assertTrue(nested.mkdirs())
            File(parent, "index.html").writeText("parent-site")
            File(nested, "index.html").writeText("nested-site")

            val parentSite = SiteRegistry.Site(parentId, "Parent site", "static",
                parent.absolutePath, listOf("parent-$suffix.example.test"), true)
            SiteRegistry.put(context, parentSite)

            val nestedSite = SiteRegistry.Site(nestedId, "Nested site", "static",
                nested.absolutePath, listOf("nested-$suffix.example.test"), true)
            assertTrue("A nested site root would break cross-site isolation.",
                runCatching { SiteRegistry.put(context, nestedSite) }.isFailure)

            val privateRootSite = SiteRegistry.Site("private_root_$suffix", "Private root", "static",
                context.filesDir.absolutePath, emptyList(), true)
            assertTrue("The app files directory must never be served as a website.",
                runCatching { SiteRegistry.put(context, privateRootSite) }.isFailure)
        } finally {
            SiteRegistry.remove(context, parentId)
            SiteRegistry.remove(context, nestedId)
            parent.deleteRecursively()
        }
    }
}
