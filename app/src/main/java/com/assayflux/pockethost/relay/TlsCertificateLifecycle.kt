package com.assayflux.pockethost.relay

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Shared certificate validity and key-pair checks for PocketHost TLS listeners.
 * This does not issue certificates; ACME challenge strategy is selected by the
 * caller and the current Relay issuer remains HTTP-01 only.
 */
object TlsCertificateLifecycle {
    fun needsRenewal(configJson: String): Boolean {
        val config = JSONObject(configJson)
        val bindAddress = config.optString("bind_address", "127.0.0.1")
        if (InetAddress.getByName(bindAddress).isLoopbackAddress) return false
        val certificatePem = config.optString("tls_certificate_pem")
        val privateKeyPem = config.optString("tls_private_key_pem")
        if (certificatePem.isBlank() || privateKeyPem.isBlank()) return true
        return runCatching {
            val certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certificatePem.toByteArray(Charsets.UTF_8))) as X509Certificate
            certificate.checkValidity()
            if (certificate.notAfter.time <= System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30)) {
                return@runCatching true
            }

            // A parseable certificate with a mismatched private key would otherwise
            // fail every TLS handshake indefinitely without entering the ACME path.
            val keyBody = privateKeyPem
                .substringAfter("-----BEGIN PRIVATE KEY-----", "")
                .substringBefore("-----END PRIVATE KEY-----")
            require(keyBody.isNotBlank()) { "Relay private key must use an unencrypted PKCS#8 PEM block." }
            val keyBytes = Base64.getMimeDecoder().decode(keyBody)
            val privateKey = KeyFactory.getInstance(certificate.publicKey.algorithm)
                .generatePrivate(PKCS8EncodedKeySpec(keyBytes))
            val signatureAlgorithm = when (certificate.publicKey.algorithm.uppercase()) {
                "EC" -> "SHA256withECDSA"
                "RSA" -> "SHA256withRSA"
                else -> return@runCatching true
            }
            val challenge = "PocketHost Relay certificate key match".toByteArray(Charsets.UTF_8)
            val proof = Signature.getInstance(signatureAlgorithm).run {
                initSign(privateKey)
                update(challenge)
                sign()
            }
            val keyMatches = Signature.getInstance(signatureAlgorithm).run {
                initVerify(certificate.publicKey)
                update(challenge)
                verify(proof)
            }
            if (!keyMatches) return@runCatching true

            val configuredHosts = JSONObject(configJson).optJSONArray("routes")
                ?.let { routes -> (0 until routes.length()).mapNotNull { routes.optJSONObject(it)?.optString("hostname")?.lowercase() } }
                .orEmpty()
            val certificateHosts = certificate.subjectAlternativeNames
                ?.mapNotNull { entry -> if (entry.size >= 2 && entry[0] == 2) entry[1] as? String else null }
                ?.map { it.lowercase() }
                .orEmpty()
            fun covers(pattern: String, hostname: String): Boolean =
                pattern == hostname || (pattern.startsWith("*.") &&
                    hostname.count { it == '.' } == pattern.count { it == '.' } &&
                    hostname.substringAfter('.') == pattern.removePrefix("*."))
            configuredHosts.any { host -> certificateHosts.none { san -> covers(san, host) } }
        }.getOrDefault(true)
    }
}
