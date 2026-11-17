package com.assayflux.pockethost

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.sites.SiteRegistry
import com.assayflux.pockethost.storage.ApplicationBackupManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.DataOutputStream
import java.security.SecureRandom
import java.util.LinkedHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationBackupCustomRootTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun customStaticRootRoundTripsAndFailedRestoreRollsBackExistingRoot() {
        val suffix = System.nanoTime().toString()
        val siteId = "custom_backup_$suffix"
        val hostname = "custom-$suffix.example.test"
        val siteRoot = File(context.filesDir, "site")
        val siteBefore = File(context.cacheDir, "site-before-$suffix")
        val hadSiteBefore = siteRoot.isDirectory
        if (hadSiteBefore) siteRoot.copyRecursively(siteBefore, overwrite = true)
        val customRoot = File(context.filesDir, "site-roots/$siteId")
        val originalRoutes = SiteRegistry.list(context)
        val backup = File(context.cacheDir, "custom-root-$suffix.afphb")
        val rollbackBackup = File(context.cacheDir, "custom-root-rollback-$suffix.afphb")
        val invalidBackup = File(context.cacheDir, "custom-root-invalid-$suffix.afphb")

        try {
            siteRoot.deleteRecursively()
            check(siteRoot.mkdirs())
            File(siteRoot, "index.html").writeText("<h1>default</h1>")
            check(customRoot.mkdirs())
            File(customRoot, "index.html").writeText("<h1>from-backup</h1>")
            SiteRegistry.replaceAll(context, listOf(
                SiteRegistry.Site(siteId, "Custom backup site", "static", customRoot.absolutePath,
                    listOf(hostname), true)
            ))

            assertTrue(ApplicationBackupManager.export(context, Uri.fromFile(backup),
                "custom-backup-passphrase".toCharArray()))

            File(customRoot, "index.html").writeText("<h1>mutated-before-success</h1>")
            val restored = ApplicationBackupManager.restore(context, Uri.fromFile(backup),
                "custom-backup-passphrase".toCharArray())
            assertEquals(1, restored.hostnameRoutes)
            assertEquals("<h1>from-backup</h1>", File(customRoot, "index.html").readText())

            File(customRoot, "index.html").writeText("<h1>preserve-on-failure</h1>")
            assertTrue(ApplicationBackupManager.export(context, Uri.fromFile(rollbackBackup),
                "custom-backup-passphrase".toCharArray()))
            rewriteWithDuplicateHostnameRoute(rollbackBackup, invalidBackup,
                "custom-backup-passphrase".toCharArray(), "duplicate_$suffix")

            val failure = runCatching {
                ApplicationBackupManager.restore(context, Uri.fromFile(invalidBackup),
                    "custom-backup-passphrase".toCharArray())
            }.exceptionOrNull()
            assertNotNull("A duplicate-host restore must fail validation and trigger rollback.", failure)
            assertEquals("<h1>preserve-on-failure</h1>", File(customRoot, "index.html").readText())
            assertEquals("<h1>default</h1>", File(siteRoot, "index.html").readText())
            assertEquals(listOf(siteId), SiteRegistry.list(context).map { it.id })
            assertEquals(listOf(hostname), SiteRegistry.list(context).single().hostnames)
        } finally {
            siteRoot.deleteRecursively()
            if (hadSiteBefore && siteBefore.isDirectory) siteBefore.copyRecursively(siteRoot, overwrite = true)
            SiteRegistry.replaceAll(context, originalRoutes)
            customRoot.deleteRecursively()
            backup.delete()
            rollbackBackup.delete()
            invalidBackup.delete()
            siteBefore.deleteRecursively()
        }
    }

    @Test
    fun failedExportDoesNotTruncateExistingDestination() {
        val suffix = System.nanoTime().toString()
        val siteRoot = File(context.filesDir, "site")
        val previousSite = File(context.cacheDir, "site-export-before-$suffix")
        val hadSite = siteRoot.isDirectory
        if (hadSite) siteRoot.copyRecursively(previousSite, overwrite = true)
        val outside = File(context.cacheDir, "outside-export-$suffix.txt")
        val destination = File(context.cacheDir, "existing-export-$suffix.afphb")
        val sentinel = "previous-valid-backup-must-survive".toByteArray()
        try {
            siteRoot.deleteRecursively()
            check(siteRoot.mkdirs())
            File(siteRoot, "index.html").writeText("<h1>site</h1>")
            outside.writeText("outside private root")
            java.nio.file.Files.createSymbolicLink(
                File(siteRoot, "linked-outside.txt").toPath(), outside.toPath()
            )
            destination.writeBytes(sentinel)

            val failure = runCatching {
                ApplicationBackupManager.export(
                    context, Uri.fromFile(destination), "export-failure-passphrase".toCharArray()
                )
            }.exceptionOrNull()

            assertNotNull("A symbolic link in a project must fail backup export.", failure)
            assertTrue(
                "A failed archive build must leave the selected destination untouched.",
                destination.readBytes().contentEquals(sentinel)
            )
        } finally {
            siteRoot.deleteRecursively()
            if (hadSite && previousSite.isDirectory) previousSite.copyRecursively(siteRoot, overwrite = true)
            outside.delete()
            destination.delete()
            previousSite.deleteRecursively()
        }
    }

    @Test
    fun wrongPassphraseAndCiphertextTamperingFailBeforeChangingActiveSite() {
        val suffix = System.nanoTime().toString()
        val siteRoot = File(context.filesDir, "site")
        val before = File(context.cacheDir, "backup-before-$suffix")
        val backup = File(context.cacheDir, "backup-auth-$suffix.afbackup")
        val tampered = File(context.cacheDir, "backup-tampered-$suffix.afbackup")
        val hadSite = siteRoot.isDirectory
        if (hadSite) siteRoot.copyRecursively(before, overwrite = true)
        try {
            siteRoot.deleteRecursively()
            check(siteRoot.mkdirs())
            File(siteRoot, "index.html").writeText("<h1>active-before-restore</h1>")
            assertTrue(ApplicationBackupManager.export(context, Uri.fromFile(backup),
                "correct-backup-passphrase".toCharArray()))

            val wrongPassphraseFailure = runCatching {
                ApplicationBackupManager.restore(context, Uri.fromFile(backup),
                    "incorrect-backup-passphrase".toCharArray())
            }.exceptionOrNull()
            assertNotNull("A wrong passphrase must fail authenticated decryption.", wrongPassphraseFailure)
            assertEquals("<h1>active-before-restore</h1>", File(siteRoot, "index.html").readText())

            val damaged = backup.readBytes()
            check(damaged.size > 40)
            damaged[damaged.lastIndex] = (damaged.last().toInt() xor 0x01).toByte()
            tampered.writeBytes(damaged)
            val tamperFailure = runCatching {
                ApplicationBackupManager.restore(context, Uri.fromFile(tampered),
                    "correct-backup-passphrase".toCharArray())
            }.exceptionOrNull()
            assertNotNull("Modified ciphertext must fail AES-GCM authentication.", tamperFailure)
            assertEquals("<h1>active-before-restore</h1>", File(siteRoot, "index.html").readText())
        } finally {
            siteRoot.deleteRecursively()
            if (hadSite && before.isDirectory) before.copyRecursively(siteRoot, overwrite = true)
            backup.delete()
            tampered.delete()
            before.deleteRecursively()
        }
    }

    private fun rewriteWithDuplicateHostnameRoute(
        source: File,
        destination: File,
        passphrase: CharArray,
        duplicateId: String
    ) {
        val bytes = source.readBytes()
        val magic = "AFPHB02\n".toByteArray(Charsets.US_ASCII)
        require(bytes.size > magic.size + 28)
        require(bytes.copyOfRange(0, magic.size).contentEquals(magic))
        val salt = bytes.copyOfRange(magic.size, magic.size + 16)
        val nonce = bytes.copyOfRange(magic.size + 16, magic.size + 28)
        val cipherText = bytes.copyOfRange(magic.size + 28, bytes.size)
        val plainZip = crypt(Cipher.DECRYPT_MODE, cipherText, passphrase, salt, nonce)

        val entries = LinkedHashMap<String, Pair<Boolean, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(plainZip)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = entry.isDirectory to if (entry.isDirectory) ByteArray(0) else zip.readBytes()
                zip.closeEntry()
            }
        }
        val routeBytes = entries["routes.json"]?.second ?: error("routes.json missing")
        val manifest = JSONObject(routeBytes.toString(Charsets.UTF_8))
        val sites = manifest.getJSONArray("sites")
        check(sites.length() > 0)
        val duplicate = JSONObject(sites.getJSONObject(0).toString())
            .put("id", duplicateId)
            .put("name", "Duplicate hostname route")
            .put("runtime", "static")
            .put("archiveRoot", "site")
        sites.put(duplicate)
        entries["routes.json"] = false to manifest.toString().toByteArray(Charsets.UTF_8)

        val rewrittenZip = ByteArrayOutputStream().use { buffer ->
            ZipOutputStream(buffer).use { zip ->
                entries.forEach { (name, data) ->
                    zip.putNextEntry(ZipEntry(name))
                    if (!data.first) zip.write(data.second)
                    zip.closeEntry()
                }
            }
            buffer.toByteArray()
        }
        val newSalt = ByteArray(16).also(SecureRandom()::nextBytes)
        val newNonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val encrypted = crypt(Cipher.ENCRYPT_MODE, rewrittenZip, passphrase, newSalt, newNonce)
        DataOutputStream(destination.outputStream()).use { output ->
            output.write(magic)
            output.write(newSalt)
            output.write(newNonce)
            output.write(encrypted)
        }
        passphrase.fill('\u0000')
    }

    private fun crypt(
        mode: Int,
        input: ByteArray,
        passphrase: CharArray,
        salt: ByteArray,
        nonce: ByteArray
    ): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, 210_000, 256)
        val encoded = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(mode, SecretKeySpec(encoded, "AES"), GCMParameterSpec(128, nonce))
            cipher.doFinal(input)
        } finally {
            encoded.fill(0)
        }
    }
}
