package com.assayflux.pockethost.management

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.assayflux.pockethost.diagnostics.OperationalLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.UUID
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
        val expiresAt: Long,
        val transport: String,
        val ownerSignature: String
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
        val ownerPublicKeySpki: String,
        val grantId: String,
        val resourceBudget: Map<String, Long>
    )

    /** Creates or loads the non-exportable P-256 owner identity held by Android Keystore. */
    @Synchronized
    fun ownerIdentity(): Pair<String, String> {
        val pair = signingKeyPair()
        return fingerprint(pair.public.encoded) to Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
    }

    fun deviceFingerprint(publicKeySpki: ByteArray): String = fingerprint(publicKeySpki)

    /** Exact canonical challenge plus advertised candidate metadata that the agent signs. */
    fun enrollmentProofPayload(challenge: EnrollmentChallenge, candidate: Candidate): ByteArray =
        challengeBytes(challenge) + byteArrayOf(10) + candidateBytes(candidate)

    /** Copy this request to the independent agent; its signature covers both sections. */
    fun enrollmentProofRequestDocument(challenge: EnrollmentChallenge, candidate: Candidate): JSONObject =
        JSONObject().put("challenge", enrollmentChallengeDocument(challenge))
            .put("candidate", candidateJson(candidate))

    /** Wire document for the shared signed role-grant schema. */
    fun roleGrantDocument(grant: SignedRoleGrant): JSONObject = grantJson(grant)

    /** Wire document for the shared enrollment-challenge schema. */
    fun enrollmentChallengeDocument(value: EnrollmentChallenge): JSONObject = JSONObject()
        .put("protocol", "pockethost.device/1.0")
        .put("challenge_id", value.challengeId)
        .put("owner_id", value.ownerDeviceId)
        .put("device_id", value.candidateDeviceId)
        .put("challenge_nonce", value.nonce)
        .put("issued_at", Instant.ofEpochSecond(value.issuedAt).toString())
        .put("expires_at", Instant.ofEpochSecond(value.expiresAt).toString())
        .put("single_use", true)
        .put("transport", value.transport)
        .put("signature", JSONObject().put("algorithm", "ecdsa-p256-sha256")
            .put("key_id", value.ownerDeviceId).put("value", value.ownerSignature))

    /** Candidate-side verifier: checks owner identity, target binding, expiry and owner signature. */
    fun verifyEnrollmentChallenge(document: JSONObject, ownerPublicKeySpki: String,
                                  expectedDeviceId: String, nowSeconds: Long = System.currentTimeMillis() / 1000L): Boolean = runCatching {
        require(document.optString("protocol") == "pockethost.device/1.0")
        require(document.optBoolean("single_use", false))
        val ownerId = document.getString("owner_id")
        val deviceId = document.getString("device_id")
        UUID.fromString(document.getString("challenge_id"))
        require(document.getString("challenge_nonce").matches(Regex("[A-Za-z0-9_-]{43}")))
        require(document.getString("transport") in setOf("lan", "ble", "usb", "remote_https"))
        require(deviceId == expectedDeviceId)
        val publicBytes = Base64.decode(ownerPublicKeySpki, Base64.NO_WRAP)
        require(fingerprint(publicBytes) == ownerId)
        val issued = Instant.parse(document.getString("issued_at")).epochSecond
        val expires = Instant.parse(document.getString("expires_at")).epochSecond
        require(issued <= nowSeconds && expires > nowSeconds && expires - issued <= CHALLENGE_TTL_SECONDS)
        val signature = document.getJSONObject("signature")
        require(signature.optString("algorithm") == "ecdsa-p256-sha256" && signature.optString("key_id") == ownerId)
        val challenge = EnrollmentChallenge(document.getString("challenge_id"), ownerId, deviceId,
            document.getString("challenge_nonce"), issued, expires, document.getString("transport"),
            signature.getString("value"))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(java.security.KeyFactory.getInstance("EC").generatePublic(
            java.security.spec.X509EncodedKeySpec(publicBytes)))
        verifier.update(challengeBytes(challenge))
        verifier.verify(Base64.decode(challenge.ownerSignature, Base64.NO_WRAP))
    }.getOrDefault(false)

    /** Worker-side grant verifier. Call immediately before accepting a role or side effect. */
    fun verifyRoleGrant(document: JSONObject, ownerPublicKeySpki: String, expectedDeviceId: String,
                        nowSeconds: Long = System.currentTimeMillis() / 1000L,
                        minimumGeneration: Long = 1L): Boolean = runCatching {
        require(document.optString("protocol") == "pockethost.device/1.0")
        require(document.optString("device_id") == expectedDeviceId)
        val ownerId = document.getString("owner_id")
        val publicBytes = Base64.decode(ownerPublicKeySpki, Base64.NO_WRAP)
        require(fingerprint(publicBytes) == ownerId)
        val role = document.getString("role")
        val allowed = ROLE_CAPABILITIES[role] ?: error("unknown_role")
        val capsArray = document.getJSONArray("capabilities")
        val caps = (0 until capsArray.length()).map { capsArray.getString(it) }
        require(caps.isNotEmpty() && caps.distinct().size == caps.size && caps.all { it in allowed })
        require(document.getJSONArray("denied_capabilities").length() == 0)
        val generation = document.getLong("generation")
        require(generation >= minimumGeneration)
        UUID.fromString(document.getString("grant_id"))
        val issued = Instant.parse(document.getString("issued_at")).epochSecond
        val expires = Instant.parse(document.getString("expires_at")).epochSecond
        require(issued <= nowSeconds && expires > nowSeconds)
        require(expires - issued in 60L..(90L * 24L * 60L * 60L))
        require(document.optString("policy_version") == "1.0")
        val budgetJson = document.getJSONObject("resource_budget")
        val budget = mapOf("cpu_millis" to budgetJson.getLong("cpu_millis"),
            "memory_bytes" to budgetJson.getLong("memory_bytes"),
            "storage_bytes" to budgetJson.getLong("storage_bytes"),
            "network_bytes_per_day" to budgetJson.getLong("network_bytes_per_day"))
        val maximum = budgetFor(role)
        require(budget.values.all { it >= 0L } && budget.getValue("cpu_millis") > 0L &&
            budget.getValue("memory_bytes") > 0L)
        require(budget.all { (key, value) -> value <= maximum.getValue(key) })
        require(!budgetJson.optBoolean("allow_battery_operation", false))
        val signed = document.getJSONObject("signature")
        require(signed.optString("algorithm") == "ecdsa-p256-sha256" && signed.optString("key_id") == ownerId)
        val grant = SignedRoleGrant(expectedDeviceId, ownerId, role, caps.sorted(), generation,
            issued, expires, signed.getString("value"), ownerPublicKeySpki,
            document.getString("grant_id"), budget)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(java.security.KeyFactory.getInstance("EC").generatePublic(
            java.security.spec.X509EncodedKeySpec(publicBytes)))
        verifier.update(grantBytes(grant))
        verifier.verify(Base64.decode(grant.signature, Base64.NO_WRAP))
    }.getOrDefault(false)

    @Synchronized
    fun createChallenge(candidateDeviceId: String, nowSeconds: Long = System.currentTimeMillis() / 1000L): EnrollmentChallenge {
        require(candidateDeviceId.matches(Regex("[a-f0-9]{64}"))) { "candidate_device_id_invalid" }
        val ownerId = ownerIdentity().first
        val nonceBytes = ByteArray(32).also(random::nextBytes)
        val nonce = Base64.encodeToString(nonceBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        nonceBytes.fill(0)
        val challenge = EnrollmentChallenge(
            UUID.randomUUID().toString(), ownerId, candidateDeviceId, nonce,
            nowSeconds, nowSeconds + CHALLENGE_TTL_SECONDS, "lan", ""
        )
        val ownerSignature = Base64.encodeToString(sign(challengeBytes(challenge)), Base64.NO_WRAP)
        val signedChallenge = challenge.copy(ownerSignature = ownerSignature)
        pending[signedChallenge.challengeId] = signedChallenge
        // Bound transient challenges to prevent memory exhaustion from repeated scans.
        while (pending.size > 32) pending.remove(pending.keys.first())
        return signedChallenge
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
        verifier.update(enrollmentProofPayload(challenge, candidate))
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
        val grantId = UUID.randomUUID().toString()
        val issuedAtIso = Instant.ofEpochSecond(nowSeconds).toString()
        val expiresAtIso = Instant.ofEpochSecond(nowSeconds + grantLifetimeSeconds).toString()
        val budget = budgetFor(role)
        val unsigned = SignedRoleGrant(candidate.deviceId, owner.first, role,
            approvedCapabilities.sorted(), generation, nowSeconds, nowSeconds + grantLifetimeSeconds,
            "", owner.second, grantId, budget)
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
            .put("ownerPublicKeySpki", grant.ownerPublicKeySpki)
        if (existing != null) registry.put((0 until registry.length()).first { registry.getJSONObject(it).optString("deviceId") == candidate.deviceId }, record) else registry.put(record)
        writeRegistry(context, registry)
        pending.remove(challengeId)
        OperationalLog.record(context, "INFO", "DEVICE_ENROLLED",
            "Approved device ${candidate.deviceId} for role ${role} at grant generation ${generation}.")
        return grant
    }

    /** Returns the exact owner-signed grant document for one currently approved worker. */
    @Synchronized
    fun signedGrantDocument(context: Context, deviceId: String): JSONObject {
        val record = findDevice(readRegistry(context), deviceId) ?: error("trusted_device_not_found")
        require(record.optLong("revokedAt", 0L) == 0L) { "device_revoked" }
        val grant = record.optJSONObject("grant") ?: error("active_role_grant_missing")
        val ownerKey = record.optString("ownerPublicKeySpki")
        require(verifyRoleGrant(grant, ownerKey, deviceId, System.currentTimeMillis() / 1000L, record.optLong("generation", 1L))) { "active_role_grant_invalid" }
        return JSONObject(grant.toString())
    }

    /** Creates a short-lived owner-signed typed job; arbitrary commands are never accepted. */
    @Synchronized
    fun createSignedJobEnvelope(context: Context, deviceId: String, kind: String, payload: JSONObject, timeoutSeconds: Long = 30L, memoryBytes: Long = 16777216L, nowSeconds: Long = System.currentTimeMillis() / 1000L): JSONObject {
        require(kind in setOf("native.health_check", "storage.snapshot", "backup.create", "backup.verify")) { "job_kind_not_supported_by_mobile_preview" }
        require(payload.length() <= 32) { "job_payload_too_large" }
        require(timeoutSeconds in 1L..3600L && memoryBytes in 1L..1073741824L) { "job_resource_budget_out_of_bounds" }
        val grant = signedGrantDocument(context, deviceId)
        val role = grant.getString("role")
        val requiredCapability = when (kind) {
            "native.health_check" -> "native_job.report_health"
            "storage.snapshot" -> "storage.read_assigned_namespace"
            "backup.create", "backup.verify" -> "backup.create_assigned_namespace"
            else -> error("job_kind_not_supported")
        }
        require(role in setOf("native_job", "storage", "backup_recovery")) { "role_cannot_execute_job_kind" }
        require((0 until grant.getJSONArray("capabilities").length()).any { grant.getJSONArray("capabilities").getString(it) == requiredCapability }) { "active_grant_does_not_allow_job" }
        val budget = grant.getJSONObject("resource_budget")
        val envelope = JSONObject()
            .put("protocol", "pockethost.device/1.0")
            .put("job_id", UUID.randomUUID().toString())
            .put("idempotency_key", UUID.randomUUID().toString())
            .put("correlation_id", UUID.randomUUID().toString())
            .put("owner_id", grant.getString("owner_id"))
            .put("target_device_id", deviceId)
            .put("role_grant_id", grant.getString("grant_id"))
            .put("kind", kind)
            .put("payload", JSONObject(payload.toString()))
            .put("issued_at", Instant.ofEpochSecond(nowSeconds).toString())
            .put("expires_at", Instant.ofEpochSecond(nowSeconds + timeoutSeconds + 300L).toString())
            .put("resource_budget", JSONObject().put("timeout_seconds", timeoutSeconds).put("memory_bytes", memoryBytes).put("storage_bytes", budget.optLong("storage_bytes", 0L)).put("network_bytes", budget.optLong("network_bytes_per_day", 0L)))
        envelope.put("signature", JSONObject().put("algorithm", "ecdsa-p256-sha256").put("key_id", grant.getString("owner_id")).put("value", Base64.encodeToString(sign(canonicalJson(envelope)), Base64.NO_WRAP)))
        return envelope
    }

    private fun canonicalJson(value: Any): ByteArray = canonicalValue(value).toByteArray(Charsets.UTF_8)
    private fun canonicalValue(value: Any): String = when (value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { key -> JSONObject.quote(key) + ":" + canonicalValue(value.get(key)) }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonicalValue(value.get(it)) }
        JSONObject.NULL -> "null"
        is String -> JSONObject.quote(value)
        is Boolean, is Number -> value.toString()
        else -> error("unsupported_signed_json_value")
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
        OperationalLog.record(context, "WARN", "DEVICE_REVOKED",
            "Revoked device ${deviceId}; previous grant generations are no longer authorized.")
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
        if (grant.optLong("generation", -1L) != generation) return false
        val ownerKey = record.optString("ownerPublicKeySpki")
        return ownerKey.isNotBlank() && verifyRoleGrant(grant, ownerKey, deviceId, nowSeconds, generation)
    }

    // RFC 8785 canonical JSON for the fixed protocol objects; signature is excluded.
    private fun challengeBytes(value: EnrollmentChallenge): ByteArray {
        val issued = Instant.ofEpochSecond(value.issuedAt).toString()
        val expires = Instant.ofEpochSecond(value.expiresAt).toString()
        return ("{\"challenge_id\":" + JSONObject.quote(value.challengeId) +
            ",\"challenge_nonce\":" + JSONObject.quote(value.nonce) +
            ",\"device_id\":" + JSONObject.quote(value.candidateDeviceId) +
            ",\"expires_at\":" + JSONObject.quote(expires) +
            ",\"issued_at\":" + JSONObject.quote(issued) +
            ",\"owner_id\":" + JSONObject.quote(value.ownerDeviceId) +
            ",\"protocol\":\"pockethost.device/1.0\",\"single_use\":true" +
            ",\"transport\":" + JSONObject.quote(value.transport) + "}").toByteArray(Charsets.UTF_8)
    }

    private fun candidateJson(value: Candidate): JSONObject = JSONObject()
        .put("architecture", value.architecture)
        .put("device_id", value.deviceId)
        .put("display_name", value.displayName)
        .put("os", value.os)
        .put("product", value.product)
        .put("protocol_version", value.protocolVersion)
        .put("public_key_spki", value.publicKeySpki)
        .put("requested_capabilities", JSONArray(value.requestedCapabilities.sorted()))

    private fun candidateBytes(value: Candidate): ByteArray {
        val caps = value.requestedCapabilities.sorted().joinToString(",") { JSONObject.quote(it) }
        return ("{\"architecture\":" + JSONObject.quote(value.architecture) +
            ",\"device_id\":" + JSONObject.quote(value.deviceId) +
            ",\"display_name\":" + JSONObject.quote(value.displayName) +
            ",\"os\":" + JSONObject.quote(value.os) +
            ",\"product\":" + JSONObject.quote(value.product) +
            ",\"protocol_version\":" + JSONObject.quote(value.protocolVersion) +
            ",\"public_key_spki\":" + JSONObject.quote(value.publicKeySpki) +
            ",\"requested_capabilities\":[" + caps + "]}").toByteArray(Charsets.UTF_8)
    }

    private fun grantBytes(value: SignedRoleGrant): ByteArray {
        val caps = value.capabilities.sorted().joinToString(",") { JSONObject.quote(it) }
        val budget = value.resourceBudget
        val budgetJson = "{\"allow_battery_operation\":false" +
            ",\"cpu_millis\":" + budget.getValue("cpu_millis") +
            ",\"memory_bytes\":" + budget.getValue("memory_bytes") +
            ",\"network_bytes_per_day\":" + budget.getValue("network_bytes_per_day") +
            ",\"storage_bytes\":" + budget.getValue("storage_bytes") + "}"
        val issued = Instant.ofEpochSecond(value.issuedAt).toString()
        val expires = Instant.ofEpochSecond(value.expiresAt).toString()
        return ("{\"capabilities\":[" + caps + "],\"denied_capabilities\":[]," +
            "\"device_id\":" + JSONObject.quote(value.deviceId) +
            ",\"expires_at\":" + JSONObject.quote(expires) +
            ",\"generation\":" + value.generation +
            ",\"grant_id\":" + JSONObject.quote(value.grantId) +
            ",\"issued_at\":" + JSONObject.quote(issued) +
            ",\"owner_id\":" + JSONObject.quote(value.ownerDeviceId) +
            ",\"policy_version\":\"1.0\",\"protocol\":\"pockethost.device/1.0\"," +
            "\"resource_budget\":" + budgetJson +
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
        .put("protocol", "pockethost.device/1.0")
        .put("grant_id", grant.grantId)
        .put("owner_id", grant.ownerDeviceId)
        .put("device_id", grant.deviceId)
        .put("role", grant.role)
        .put("generation", grant.generation)
        .put("capabilities", JSONArray(grant.capabilities))
        .put("denied_capabilities", JSONArray())
        .put("resource_budget", JSONObject()
            .put("cpu_millis", grant.resourceBudget.getValue("cpu_millis"))
            .put("memory_bytes", grant.resourceBudget.getValue("memory_bytes"))
            .put("storage_bytes", grant.resourceBudget.getValue("storage_bytes"))
            .put("network_bytes_per_day", grant.resourceBudget.getValue("network_bytes_per_day"))
            .put("allow_battery_operation", false))
        .put("issued_at", Instant.ofEpochSecond(grant.issuedAt).toString())
        .put("expires_at", Instant.ofEpochSecond(grant.expiresAt).toString())
        .put("policy_version", "1.0")
        .put("signature", JSONObject()
            .put("algorithm", "ecdsa-p256-sha256")
            .put("key_id", grant.ownerDeviceId)
            .put("value", grant.signature))

    private fun budgetFor(role: String): Map<String, Long> = when (role) {
        "storage" -> mapOf("cpu_millis" to 250L, "memory_bytes" to 268435456L, "storage_bytes" to 10737418240L, "network_bytes_per_day" to 1073741824L)
        "traffic" -> mapOf("cpu_millis" to 500L, "memory_bytes" to 536870912L, "storage_bytes" to 2147483648L, "network_bytes_per_day" to 10737418240L)
        "build_publish" -> mapOf("cpu_millis" to 1000L, "memory_bytes" to 1073741824L, "storage_bytes" to 5368709120L, "network_bytes_per_day" to 2147483648L)
        "native_job" -> mapOf("cpu_millis" to 250L, "memory_bytes" to 268435456L, "storage_bytes" to 536870912L, "network_bytes_per_day" to 536870912L)
        "backup_recovery" -> mapOf("cpu_millis" to 500L, "memory_bytes" to 536870912L, "storage_bytes" to 10737418240L, "network_bytes_per_day" to 5368709120L)
        "edge_gateway" -> mapOf("cpu_millis" to 100L, "memory_bytes" to 134217728L, "storage_bytes" to 268435456L, "network_bytes_per_day" to 536870912L)
        else -> error("unknown_device_role")
    }

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
        try {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
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
        "storage" to setOf("storage.read_assigned_namespace", "storage.write_assigned_namespace", "storage.snapshot", "storage.verify"),
        "traffic" to setOf("traffic.route.read_assigned", "traffic.proxy.assigned_hosts", "traffic.cache.assigned_public_content"),
        "build_publish" to setOf("build.read_assigned_source", "build.execute_declared_build", "artifact.write_staging", "artifact.verify"),
        "native_job" to setOf("native_job.execute_signed_package", "native_job.read_declared_inputs", "native_job.write_declared_outputs", "native_job.report_health"),
        "backup_recovery" to setOf("backup.read_assigned_namespace", "backup.create_snapshot", "backup.verify_snapshot", "backup.restore_staging"),
        "edge_gateway" to setOf("edge.read_claimed_node_telemetry", "edge.invoke_allowlisted_operation", "edge.stage_signed_firmware")
    )
}
