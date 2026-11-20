package com.assayflux.pockethost.storage

/**
 * Shared deny-list for files that must never become public website content.
 *
 * Paths are interpreted as portable slash-separated relative paths so ZIP,
 * SAF, backup and in-app file operations apply the same policy on every OS.
 * Keep this policy aligned with rust-engine's static-serving deny-list and the
 * runtime-worker transfer filter.
 */
object SensitivePathPolicy {
    private val blockedDirectories = setOf(
        ".git", ".ssh", ".aws", ".azure", ".docker", ".kube", ".pki",
        ".pockethost", ".gnupg", ".terraform", ".pulumi", "secrets"
    )
    private val blockedNames = setOf(
        ".env", ".netrc", ".npmrc", ".pypirc", ".git-credentials", ".gitconfig",
        "credentials", "credentials.json", "application_default_credentials.json",
        "service_account.json", "secrets.json", "id_rsa", "id_dsa", "id_ed25519",
        "id_ecdsa", "id_ed25519_sk", "identity", "authorized_keys", "known_hosts",
        "kubeconfig", "service-account.json", "serviceaccount.json"
    )
    private val blockedExtensions = setOf(
        "pem", "key", "p12", "pfx", "jks", "keystore", "mobileprovision"
    )

    fun isSensitivePath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        val parts = normalized.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return false
        val lowerParts = parts.map { it.lowercase(java.util.Locale.ROOT) }
        if (lowerParts.windowed(2).any { it[0] == ".config" && it[1] == "gcloud" }) {
            return true
        }
        return lowerParts.any { name ->
            val extension = name.substringAfterLast('.', "")
            name in blockedDirectories ||
                name in blockedNames ||
                name == ".env" || name.startsWith(".env.") ||
                extension in blockedExtensions ||
                (name.startsWith("service-account-") && extension == "json") ||
                (name.startsWith("serviceaccount-") && extension == "json") ||
                (name.startsWith("firebase-adminsdk") && extension == "json") ||
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
