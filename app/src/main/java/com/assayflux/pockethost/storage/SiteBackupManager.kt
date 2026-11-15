package com.assayflux.pockethost.storage

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.SecureRandom
import java.util.zip.CipherInputStream
import java.util.zip.CipherOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable passphrase-encrypted backup for the active static website.
 * Cloudflare credentials, Python projects and runtime settings are intentionally excluded.
 */
object SiteBackupManager {
    private val MAGIC = "AFPHB01\n".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val ITERATIONS = 210_000
    private const val MAX_FILES = 5_000
    private const val MAX_BYTES = 50L * 1024L * 1024L
    private const val MAX_DEPTH = 32

    fun export(context: Context, destination: Uri, passphrase: CharArray): Boolean = runCatching {
        require(passphrase.size >= 12) { "Use a backup passphrase with at least 12 characters." }
        val root = File(context.filesDir, "site").canonicalFile
        if (!File(root, "index.html").isFile) throw IOException("There is no deployed website to back up.")
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, nonce))
        val output = context.contentResolver.openOutputStream(destination)
            ?: throw IOException("Cannot open the selected backup destination.")
        val header = DataOutputStream(output)
        header.write(MAGIC)
        header.write(salt)
        header.write(nonce)
        header.flush()
        ZipOutputStream(CipherOutputStream(header, cipher)).use { zip ->
            val counter = Counter()
            root.walkTopDown().forEach { file ->
                if (file == root) return@forEach
                val canonical = file.canonicalFile
                if (!canonical.toPath().startsWith(root.toPath())) {
                    throw IOException("Website contains a path outside its deployment directory.")
                }
                val relative = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                val depth = relative.split('/').size
                if (depth > MAX_DEPTH) throw IOException("Website folder nesting exceeds the backup limit.")
                if (file.isDirectory) {
                    zip.putNextEntry(ZipEntry("site/$relative/"))
                    zip.closeEntry()
                } else if (file.isFile) {
                    counter.files++
                    if (counter.files > MAX_FILES) throw IOException("Website contains too many files for backup.")
                    zip.putNextEntry(ZipEntry("site/$relative"))
                    file.inputStream().use { input ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            counter.bytes += count
                            if (counter.bytes > MAX_BYTES) throw IOException("Website exceeds the 50 MiB backup limit.")
                            zip.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
            }
            val metadata = "format=AssayFlux PocketHost encrypted site backup\nversion=1\n"
            zip.putNextEntry(ZipEntry("POCKETHOST_BACKUP.txt"))
            zip.write(metadata.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        true
    }.getOrElse { throw it }

    fun restore(context: Context, source: Uri, passphrase: CharArray): ImportReport {
        require(passphrase.size >= 12) { "Use the passphrase that was set when this backup was created." }
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("The selected backup cannot be opened.")
        val temporary = File(context.cacheDir, "pockethost-restore-${System.nanoTime()}.zip")
        try {
            input.use { raw ->
                val header = DataInputStream(raw)
                val magic = ByteArray(MAGIC.size)
                header.readFully(magic)
                if (!magic.contentEquals(MAGIC)) throw IOException("This is not a PocketHost encrypted backup.")
                val salt = ByteArray(SALT_BYTES)
                val nonce = ByteArray(NONCE_BYTES)
                header.readFully(salt)
                header.readFully(nonce)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, nonce))
                CipherInputStream(header, cipher).use { decrypted ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var total = 0L
                        while (true) {
                            val count = decrypted.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_BYTES) throw IOException("Decrypted backup exceeds the 50 MiB safety limit.")
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
            // Authentication is verified before SiteImporter is allowed to activate anything.
            return SiteImporter.importZipStream(context, FileInputStream(temporary))
        } finally {
            temporary.delete()
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private class Counter(var files: Int = 0, var bytes: Long = 0)
}
