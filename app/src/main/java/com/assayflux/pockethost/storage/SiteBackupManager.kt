package com.assayflux.pockethost.storage

import android.content.Context
import android.net.Uri
import com.assayflux.pockethost.engine.NativeEngine
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.io.IOException
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
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
    private const val MAX_FILES = WebsiteLimits.MAX_FILES
    private const val MAX_BYTES = WebsiteLimits.MAX_TOTAL_BYTES
    private const val MAX_DEPTH = WebsiteLimits.MAX_DEPTH

    fun export(context: Context, destination: Uri, passphrase: CharArray): Boolean {
        val temporary = File(context.cacheDir, "pockethost-site-backup-${UUID.randomUUID()}.tmp")
        try {
        require(passphrase.size >= 12) { "Use a backup passphrase with at least 12 characters." }
        if (NativeEngine.status() == "RUNNING") throw IOException("Stop hosting before exporting a website backup.")
        val siteDirectory = File(context.filesDir, "site")
        if (java.nio.file.Files.isSymbolicLink(siteDirectory.toPath())) throw IOException("Website root cannot be a symbolic link.")
        val root = siteDirectory.canonicalFile
        if (root.parentFile != context.filesDir.canonicalFile) throw IOException("Website root must remain inside PocketHost private storage.")
        if (!File(root, "index.html").isFile) throw IOException("There is no deployed website to back up.")
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, nonce))
        val header = DataOutputStream(temporary.outputStream())
        header.write(MAGIC)
        header.write(salt)
        header.write(nonce)
        header.flush()
        ZipOutputStream(BoundedOutputStream(CipherOutputStream(header, cipher), MAX_BYTES)).use { zip ->
            val counter = Counter()
            root.walkTopDown().onEnter { file ->
                if (file != root && java.nio.file.Files.isSymbolicLink(file.toPath())) {
                    throw IOException("Website contains an unsupported symbolic link.")
                }
                true
            }.forEach { file ->
                if (file == root) return@forEach
                if (java.nio.file.Files.isSymbolicLink(file.toPath())) throw IOException("Website contains an unsupported symbolic link.")
                val canonical = file.canonicalFile
                if (!canonical.toPath().startsWith(root.toPath())) {
                    throw IOException("Website contains a path outside its deployment directory.")
                }
                val relative = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                SensitivePathPolicy.requireSafePath(relative)
                val parts = relative.split('/')
                val depth = parts.size
                if (depth > MAX_DEPTH) throw IOException("Website folder nesting exceeds the backup limit.")
                parts.forEach(::validateBackupName)
                counter.entries++
                if (counter.entries > WebsiteLimits.MAX_ENTRIES) throw IOException("Website contains too many files and folders for backup.")
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
                            if (counter.bytes > MAX_BYTES) throw IOException("Website exceeds the 500 MB website limit.")
                            zip.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        // Build the complete archive before opening the destination, so validation failures
        // never truncate a previously valid backup selected by the owner.
        val output = context.contentResolver.openOutputStream(destination)
            ?: throw IOException("Cannot open the selected backup destination.")
        output.use { destinationStream ->
            temporary.inputStream().use { source -> source.copyTo(destinationStream) }
        }
        return true
        } finally {
            temporary.delete()
            passphrase.fill('\u0000')
        }
    }

    fun restore(context: Context, source: Uri, passphrase: CharArray): ImportReport {
        val temporary = File(context.cacheDir, "pockethost-restore-${System.nanoTime()}.zip")
        try {
            require(passphrase.size >= 12) { "Use the passphrase that was set when this backup was created." }
            if (NativeEngine.status() == "RUNNING") throw IOException("Stop hosting before restoring a website backup.")
            val input = context.contentResolver.openInputStream(source)
                ?: throw IOException("The selected backup cannot be opened.")
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
                            if (total > MAX_BYTES) throw IOException("Decrypted backup exceeds the 500 MB website limit.")
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
            // Authentication is verified before SiteImporter is allowed to activate anything.
            return SiteImporter.importZipStream(context, FileInputStream(temporary))
        } finally {
            temporary.delete()
            passphrase.fill('\u0000')
        }
    }

    private fun validateBackupName(name: String) {
        val lower = name.lowercase()
        if (name.isBlank() || name == "." || name == ".." || name.contains('/') ||
            name.contains('\\') || name.contains('\u0000') ||
            lower == ".env" || lower.startsWith(".env.") ||
            lower in setOf(".git", ".ssh", ".pockethost", "id_rsa", "id_ed25519", "authorized_keys", "known_hosts") ||
            lower.endsWith(".pem") || lower.endsWith(".key") || lower.endsWith(".p12") || lower.endsWith(".pfx")) {
            throw IOException("Website contains a sensitive or unsafe filename: $name")
        }
        SensitivePathPolicy.requireSafePath(name)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            try {
                SecretKeySpec(encoded, "AES")
            } finally {
                encoded.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    private class BoundedOutputStream(output: OutputStream, private val limit: Long) : FilterOutputStream(output) {
        private var written = 0L

        override fun write(value: Int) {
            ensureCapacity(1)
            out.write(value)
            written++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            ensureCapacity(length)
            out.write(buffer, offset, length)
            written += length
        }

        private fun ensureCapacity(length: Int) {
            if (length < 0 || written + length > limit) {
                throw IOException("Encrypted backup exceeds the 500 MB archive limit.")
            }
        }
    }

    private class Counter(var files: Int = 0, var entries: Int = 0, var bytes: Long = 0)
}
