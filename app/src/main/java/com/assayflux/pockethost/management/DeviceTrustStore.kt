package com.assayflux.pockethost.management

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Local trust authority for the mobile Command Center.
 *
 * Device discovery is deliberately not authorization: a candidate is persisted only after
 * proof-of-possession verification and an explicit call to approveCandidate().
 */
object DeviceTrustStore {
    private const val SIGNING_ALIAS = "assayflux.pockethost.device-authority.ec.v1"
    private const val STORAGE_ALIAS = "assayflux.pockethost.device-registry.aes.v1"
    private const val REGISTRY_FILE = "trusted-device-registry.enc"
    private const val CHALLENGE_TTL_SECONDS = 300L
    private const val MAX_DEVICES = 256
    private val random = SecureRandom()
    private val pending = HashMap<String, EnrollmentChallenge>()

    data class EnrollmentChallenge(
        val challengeId: String,
        val ownerDeviceId: String,
        val candidateDeviceId: String,
        val nonce: String,
        val issuedAt: Long,
        val expiresAt: Long
    )

    data class Candidate(
        val deviceId: String,
        val publicKeySpki: String,
        val displayName: String,
        val product: String,
        val os: String,
        val architecture: String,
        val protocolVersion: String,
        val requestedCapabilities: List<String>
    )

    data class TrustedDevice(
        val deviceId: String,
        val displayName: String,
        val product: String,
        val os: String,
        val architecture: String,
        val publicKeySpki: String,
        val role: String,
        val capabilities: List<String>,
        val generation: Long,
        val approvedAt: Long,
        val revokedAt: Long
    )

    data class SignedRoleGrant(
        val deviceId: String,
        val ownerDeviceId: String,
        val role: String,
        val capabilities: List<String>,
        val generation: Long,
        val issuedAt: Long,
        val expiresAt: Long,
        val signature: String,
        val ownerPublicKeySpki: String
    )

    /** Creates or loads the non-exportable P-256 owner identity held by Android Keystore. */
    @Synchronized
    fun ownerIdentity(): Pair<String, String> {
        val pair = signingKeyPair()
        return fingerprint(pair.public.encoded) to Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
    }

    @Synchronized
    fun createChallenge(candidateDeviceId: String, nowSeconds: Long = System.currentTimeMillis() / 1000L): EnrollmentChallenge {
        require(candidateDeviceId.matches(Regex("[a-f0-9]{64}"))) { "candidate_device_id_invalid" }
        val ownerId = ownerIdentity().first
        val nonceBytes = ByteArray(32).also(random::nextBytes)
        val nonce = Base64.encodeToString(nonceBytes, Base64.NO_WRAP)
        nonceBytes.fill(0)
        val idBytes = ByteArray(16).also(random::nextBytes)
        val id = Base64.encodeToString(idBytes, Base64.NO_WRAP)
        idBytes.fill(0)
        val challenge = EnrollmentChallenge(id, ownerId, candidateDeviceId, nonce,
            nowSeconds, nowSeconds + CHALLENGE_TTL_SECONDS)
        pending[id] = challenge
        // Bound transient challenges to prevent memory exhaustion from repeated scans.
        while (pending.size > 32) pending.remove(pending.keys.first())
        return challenge
    }

    /**
     * Explicit owner approval boundary. The candidate must sign the exact challenge using
     * the private key corresponding to its advertised SPKI; no network discovery auto-approves.
     */
    @Synchronized
    fun approveCandidate(
        context: Context,
        challengeId: String,
        candidate: Candidate,
        proofSignatureBase64: String,
        role: String,
        approvedCapabilities: List<String>,
        nowSeconds: Long = System.currentTimeMillis() / 1000L,
        grantLifetimeSeconds: Long = 30L * 24L * 60L * 60L
    ): SignedRoleGrant {
        val challenge = pending[challengeId] ?: error("enrollment_challenge_missing_or_used")
        require(challenge.expiresAt > nowSeconds && challenge.issuedAt <= nowSeconds) { "enrollment_challenge_expired" }
        require(challenge.candidateDeviceId == candidate.deviceId) { "enrollment_device_binding_mismatch" }
        require(candidate.protocolVersion == "1.0") { "unsupported_device_protocol" }
        require(candidate.displayName.length in 1..80 && candidate.product.length in 1..80) { "device_metadata_invalid" }
        require(candidate.os.length in 1..40 && candidate.architecture.length in 1..40) { "device_platform_invalid" }
        val publicKey = Base64.decode(candidate.publicKeySpki, Base64.NO_WRAP)
        require(fingerprint(publicKey) == candidate.deviceId) { "device_fingerprint_mismatch" }
        val proof = Base64.decode(proofSignatureBase64, Base64.NO_WRAP)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(java.security.KeyFactory.getInstance("EC").generatePublic(
            java.security.spec.X509EncodedKeySpec(publicKey)))
        verifier.update(challengeBytes(challenge))
        require(verifier.verify(proof)) { "device_proof_of_possession_invalid" }

        val rolePolicy = ROLE_CAPABILITIES[role] ?: error("unknown_device_role")
        require(approvedCapabilities.isNotEmpty()) { "role_requires_explicit_capabilities" }
        require(approvedCapabilities.distinct().size == approvedCapabilities.size) { "duplicate_capability" }
        require(approvedCapabilities.all { it in rolePolicy }) { "capability_not_allowed_for_role" }
        require(approvedCapabilities.all { it in candidate.requestedCapabilities }) { "capability_not_advertised_by_device" }
        require(grantLifetimeSeconds in 60L..(90L * 24L * 60L * 60L)) { "grant_lifetime_out_of_bounds" }

        val registry = readRegistry(context)
        val existing = findDevice(registry, candidate.deviceId)
        val generation = (existing?.optLong("generation", 0L) ?: 0L) + 1L
        require(registry.length() < MAX_DEVICES || existing != null) { "trusted_device_limit_reached" }
        val owner = ownerIdentity()
        val unsigned = SignedRoleGrant(candidate.deviceId, owner.first, role,
            approvedCapabilities.sorted(), generation, nowSeconds, nowSeconds + grantLifetimeSeconds,
            "", owner.second)
        val signature = sign(grantBytes(unsigned))
        val grant = unsigned.copy(signature = Base64.encodeToString(signature, Base64.NO_WRAP))
        val record = JSONObject()
            .put("deviceId", candidate.deviceId)
            .put("displayName", candidate.displayName)
            .put("product", candidate.product)
            .put("os", candidate.os)
            .put("architecture", candidate.architecture)
            .put("publicKeySpki", candidate.publicKeySpki)
            .put("role", role)
            .put("capabilities", JSONArray(approvedCapabilities.sorted()))
            .put("generation", generation)
            .put("approvedAt", nowSeconds)
            .put("revokedAt", 0)
            .put("grant", grantJson(grant))
        if (existing != null) registry.put((0 until registry.length()).first { registry.getJSONObject(it).optString("deviceId") == candidate.deviceId }, record) else registry.put(record)
        writeRegistry(context, registry)
        pending.remove(challengeId)
        return grant
    }

    @Synchronized
    fun revoke(context: Context, deviceId: String, nowSeconds: Long = System.currentTimeMillis() / 1000L): Boolean {
        val registry = readRegistry(context)
        val existing = findDevice(registry, deviceId) ?: return false
        existing.put("generation", existing.optLong("generation", 0L) + 1L)
            .put("revokedAt", nowSeconds)
            .remove("grant")
        writeRegistry(context, registry)
        pending.entries.removeAll { it.value.candidateDeviceId == deviceId }
        return true
    }

    @Synchronized
    fun list(context: Context, includeRevoked: Boolean = false): List<TrustedDevice> {
        val array = readRegistry(context)
        return (0 until array.length()).map { array.getJSONObject(it) }
            .filter { includeRevoked || it.optLong("revokedAt", 0L) == 0L }
            .map { item ->
                val caps = item.optJSONArray("capabilities") ?: JSONArray()
                TrustedDevice(item.getString("deviceId"), item.getString("displayName"),
                    item.getString("product"), item.getString("os"), item.getString("architecture"),
                    item.getString("publicKeySpki"), item.getString("role"),
                    (0 until caps.length()).map { caps.getString(it) },
                    item.getLong("generation"), item.getLong("approvedAt"), item.optLong("revokedAt", 0L))
            }
    }

    @Synchronized
    fun isAuthorized(context: Context, deviceId: String, role: String, generation: Long, nowSeconds: Long = System.currentTimeMillis() / 1000L): Boolean {
        val record = findDevice(readRegistry(context), deviceId) ?: return false
        if (record.optLong("revokedAt", 0L) != 0L || record.optString("role") != role ||
            record.optLong("generation", -1L) != generation) return false
        val grant = record.optJSONObject("grant") ?: return false
        return grant.optLong("expiresAt", 0L) > nowSeconds &&
            grant.optLong("generation", -1L) == generation
    }

    // Fixed-field compact JSON with lexically ordered keys; matches the Step 1 signed payload profile.
    private fun challengeBytes(value: EnrollmentChallenge): ByteArray =
        ("{\"candidateDeviceId\":" + JSONObject.quote(value.candidateDeviceId) +
            ",\"challengeId\":" + JSONObject.quote(value.challengeId) +
            ",\"expiresAt\":" + value.expiresAt + ",\"issuedAt\":" + value.issuedAt +
            ",\"nonce\":" + JSONObject.quote(value.nonce) +
            ",\"ownerDeviceId\":" + JSONObject.quote(value.ownerDeviceId) + "}").toByteArray(Charsets.UTF_8)

    private fun grantBytes(value: SignedRoleGrant): ByteArray {
        val capabilities = value.capabilities.sorted().joinToString(",") { JSONObject.quote(it) }
        return ("{\"capabilities\":[" + capabilities + "]," +
            "\"deviceId\":" + JSONObject.quote(value.deviceId) +
            ",\"expiresAt\":" + value.expiresAt + ",\"generation\":" + value.generation +
            ",\"issuedAt\":" + value.issuedAt +
            ",\"ownerDeviceId\":" + JSONObject.quote(value.ownerDeviceId) +
            ",\"role\":" + JSONObject.quote(value.role) + "}").toByteArray(Charsets.UTF_8)
    }
    private fun sign(bytes: ByteArray): ByteArray {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(signingKeyPair().private)
        signer.update(bytes)
        return signer.sign()
    }

    private fun signingKeyPair(): KeyPair {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = store.getKey(SIGNING_ALIAS, null) as? java.security.PrivateKey
        val publicKey = store.getCertificate(SIGNING_ALIAS)?.publicKey
        if (privateKey != null && publicKey != null) return KeyPair(publicKey, privateKey)
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(KeyGenParameterSpec.Builder(SIGNING_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build())
        return generator.generateKeyPair()
    }

    private fun fingerprint(spki: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(spki).joinToString("") { "%02x".format(it) }

    private fun findDevice(array: JSONArray, id: String): JSONObject? =
        (0 until array.length()).map { array.getJSONObject(it) }.firstOrNull { it.optString("deviceId") == id }

    private fun grantJson(grant: SignedRoleGrant): JSONObject = JSONObject()
        .put("deviceId", grant.deviceId).put("ownerDeviceId", grant.ownerDeviceId)
        .put("role", grant.role).put("capabilities", JSONArray(grant.capabilities))
        .put("generation", grant.generation).put("issuedAt", grant.issuedAt)
        .put("expiresAt", grant.expiresAt).put("signature", grant.signature)
        .put("ownerPublicKeySpki", grant.ownerPublicKeySpki)

    private fun readRegistry(context: Context): JSONArray {
        val file = File(context.noBackupFilesDir, REGISTRY_FILE)
        if (!file.exists()) return JSONArray()
        val packed = file.readBytes()
        require(packed.size in 29..1_048_576) { "device_registry_invalid_size" }
        val iv = packed.copyOfRange(0, 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, storageKey(), GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(packed.copyOfRange(12, packed.size))
        return try { JSONArray(String(plain, Charsets.UTF_8)) } finally { plain.fill(0) }
    }

    private fun writeRegistry(context: Context, registry: JSONArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, storageKey())
        val iv = cipher.iv
        require(iv.size == 12) { "unexpected_registry_iv" }
        val plain = registry.toString().toByteArray(Charsets.UTF_8)
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        val target = File(context.noBackupFilesDir, REGISTRY_FILE)
        val temporary = File(context.noBackupFilesDir, "$REGISTRY_FILE.tmp")
        temporary.outputStream().use { stream -> stream.write(iv); stream.write(encrypted); stream.fd.sync() }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
    }

    private fun storageKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(STORAGE_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(STORAGE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    private val ROLE_CAPABILITIES = mapOf(
        "storage" to setOf("storage.namespace.read", "storage.namespace.write", "storage.snapshot.create"),
        "traffic" to setOf("traffic.route.serve", "traffic.cache.read", "traffic.cache.write"),
        "build_publish" to setOf("build.source.read", "build.artifact.write"),
        "native_job" to setOf("job.fixed.execute", "job.status.report"),
        "backup_recovery" to setOf("backup.snapshot.read", "backup.snapshot.write", "backup.restore.stage"),
        "edge_gateway" to setOf("edge.profile.read", "edge.operation.execute")
    )
}
