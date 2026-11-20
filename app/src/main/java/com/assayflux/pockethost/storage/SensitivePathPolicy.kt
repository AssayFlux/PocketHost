package com.assayflux.pockethost.storage

/**
 * Shared deny-list for files that must never become public website content.
 *
 * Paths are interpreted as portable slash-separated relative paths so ZIP,
 * SAF, backup and in-app file operations apply the same policy on every OS.
 */
object SensitivePathPolicy {
    private val blockedDirectories = setOf(
        ".git", ".ssh", ".aws", ".docker", ".kube", ".pockethost", ".gnupg"
    )
    private val blockedNames = setOf(
        ".env", ".netrc", ".npmrc", ".pypirc", ".git-credentials", ".gitconfig",
        "credentials", "id_rsa", "id_ed25519", "id_ecdsa", "id_ed25519_sk",
        "authorized_keys", "known_hosts", "kubeconfig", "service-account.json",
        "serviceaccount.json", "secrets.json"
    )
    private val blockedExtensions = setOf("pem", "key", "p12", "pfx", "jks", "keystore")

    fun isSensitivePath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        val parts = normalized.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return false
        return parts.any { segment ->
            val name = segment.lowercase(java.util.Locale.ROOT)
            val extension = name.substringAfterLast('.', "")
            name in blockedDirectories ||
                name in blockedNames ||
                name == ".env" || name.startsWith(".env.") ||
                extension in blockedExtensions ||
                (name.startsWith("service-account-") && extension == "json") ||
                (name.startsWith("serviceaccount-") && extension == "json") ||
                name.endsWith("-service-account.json") ||
                name.endsWith("-serviceaccount.json")
        }
    }

    fun requireSafePath(path: String) {
        require(!isSensitivePath(path)) {
            "Sensitive credential and private-key files cannot be included in a public website."
        }
    }
}
