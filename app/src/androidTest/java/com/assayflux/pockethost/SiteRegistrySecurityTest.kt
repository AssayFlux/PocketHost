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
    fun rejectsPersistedRoutesThatPointOutsideManagedSiteRoots() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("pockethost_site_registry", 0)
        val previous = preferences.getString("sites", "[]")
        try {
            val invalid = """[{"id":"forged-route","name":"Private storage","runtime":"static","root":"${context.filesDir.absolutePath}","hostnames":["private.example.test"],"enabled":true}]"""
            preferences.edit().putString("sites", invalid).commit()
            assertTrue("Invalid persisted roots must fail closed and never be routed.",
                SiteRegistry.list(context).isEmpty())
        } finally {
            preferences.edit().putString("sites", previous ?: "[]").commit()
        }
    }

    @Test
    fun rejectsPrivateStorageRootAndOverlappingSiteRoots() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = System.nanoTime().toString()
        val siteRoots = File(context.filesDir, "site-roots")
        val parent = File(siteRoots, "isolation-$suffix")
        val nested = File(parent, "nested")
        val alias = File(siteRoots, "alias-$suffix")
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
            val siteRootsParent = SiteRegistry.Site("site_roots_parent_$suffix", "Site roots parent", "static",
                siteRoots.absolutePath, emptyList(), true)
            assertTrue("The custom-sites parent must never be served as a website.",
                runCatching { SiteRegistry.put(context, siteRootsParent) }.isFailure)

            java.nio.file.Files.createSymbolicLink(alias.toPath(), parent.toPath())
            val aliasedRoot = SiteRegistry.Site("aliased_root_$suffix", "Aliased root", "static",
                alias.absolutePath, emptyList(), true)
            assertTrue("Symlink path components must be rejected.",
                runCatching { SiteRegistry.put(context, aliasedRoot) }.isFailure)
        } finally {
            SiteRegistry.remove(context, parentId)
            SiteRegistry.remove(context, nestedId)
            java.nio.file.Files.deleteIfExists(alias.toPath())
            parent.deleteRecursively()
        }
    }
}
