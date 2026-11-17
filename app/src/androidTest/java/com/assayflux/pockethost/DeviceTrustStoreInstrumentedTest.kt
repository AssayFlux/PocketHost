package com.assayflux.pockethost

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assayflux.pockethost.management.DeviceTrustStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

@RunWith(AndroidJUnit4::class)
class DeviceTrustStoreInstrumentedTest {
    @Test
    fun signedEnrollmentGrantAndRevocationRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val owner = DeviceTrustStore.ownerIdentity()
        val agentKeys = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val deviceId = DeviceTrustStore.deviceFingerprint(agentKeys.public.encoded)
        val capabilities = listOf("storage.read_assigned_namespace", "storage.write_assigned_namespace")
        val challenge = DeviceTrustStore.createChallenge(deviceId)
        val candidate = DeviceTrustStore.Candidate(
            deviceId = deviceId,
            publicKeySpki = Base64.encodeToString(agentKeys.public.encoded, Base64.NO_WRAP),
            displayName = "Instrumented worker",
            product = "Pocket Host test agent",
            os = "Android",
            architecture = "arm64-v8a",
            protocolVersion = "1.0",
            requestedCapabilities = capabilities
        )

        val challengeDocument = DeviceTrustStore.enrollmentChallengeDocument(challenge)
        assertTrue(DeviceTrustStore.verifyEnrollmentChallenge(challengeDocument, owner.second, deviceId))
        assertFalse(DeviceTrustStore.verifyEnrollmentChallenge(challengeDocument, owner.second, "0".repeat(64)))

        val proofSigner = Signature.getInstance("SHA256withECDSA")
        proofSigner.initSign(agentKeys.private)
        proofSigner.update(DeviceTrustStore.enrollmentProofPayload(challenge, candidate))
        val proof = Base64.encodeToString(proofSigner.sign(), Base64.NO_WRAP)

        val alteredMetadataRejected = runCatching {
            DeviceTrustStore.approveCandidate(
                context = context,
                challengeId = challenge.challengeId,
                candidate = candidate.copy(displayName = "Impersonated worker"),
                proofSignatureBase64 = proof,
                role = "storage",
                approvedCapabilities = capabilities
            )
        }.isFailure
        assertTrue(alteredMetadataRejected)

        val escalationRejected = runCatching {
            DeviceTrustStore.approveCandidate(
                context = context,
                challengeId = challenge.challengeId,
                candidate = candidate,
                proofSignatureBase64 = proof,
                role = "storage",
                approvedCapabilities = listOf("management.admin")
            )
        }.isFailure
        assertTrue(escalationRejected)

        val grant = DeviceTrustStore.approveCandidate(
            context = context,
            challengeId = challenge.challengeId,
            candidate = candidate,
            proofSignatureBase64 = proof,
            role = "storage",
            approvedCapabilities = capabilities
        )

        val grantDocument = DeviceTrustStore.roleGrantDocument(grant)
        assertTrue(DeviceTrustStore.verifyRoleGrant(grantDocument, owner.second, deviceId))
        assertTrue(DeviceTrustStore.isAuthorized(context, deviceId, "storage", grant.generation))
        assertFalse(DeviceTrustStore.verifyRoleGrant(grantDocument, owner.second, "f".repeat(64)))

        val alteredGrant = org.json.JSONObject(grantDocument.toString())
        alteredGrant.put("role", "edge_gateway")
        assertFalse(DeviceTrustStore.verifyRoleGrant(alteredGrant, owner.second, deviceId))
        assertFalse(DeviceTrustStore.verifyRoleGrant(grantDocument, owner.second, deviceId,
            minimumGeneration = grant.generation + 1L))

        // Approval consumes the challenge exactly once, even when the same proof is replayed.
        val replayRejected = runCatching {
            DeviceTrustStore.approveCandidate(
                context, challenge.challengeId,
                candidate,
                proof, "storage", capabilities
            )
        }.isFailure
        assertTrue(replayRejected)

        assertTrue(DeviceTrustStore.revoke(context, deviceId))
        assertFalse(DeviceTrustStore.isAuthorized(context, deviceId, "storage", grant.generation))
    }

    @Test
    fun mobileSignsTypedHealthJobsAndRejectsPayloadTampering() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val owner = DeviceTrustStore.ownerIdentity()
        val agentKeys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val deviceId = DeviceTrustStore.deviceFingerprint(agentKeys.public.encoded)
        val capabilities = listOf("native_job.report_health")
        val challenge = DeviceTrustStore.createChallenge(deviceId)
        val candidate = DeviceTrustStore.Candidate(
            deviceId, Base64.encodeToString(agentKeys.public.encoded, Base64.NO_WRAP),
            "Health worker", "Pocket Host Linux Agent", "Linux", "x86_64", "1.0", capabilities
        )
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(agentKeys.private)
        signer.update(DeviceTrustStore.enrollmentProofPayload(challenge, candidate))
        val proof = Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
        val grant = DeviceTrustStore.approveCandidate(context, challenge.challengeId, candidate, proof, "native_job", capabilities)
        val envelope = DeviceTrustStore.createSignedJobEnvelope(
            context, deviceId, "native.health_check", org.json.JSONObject().put("check_id", "instrumented-health-check")
        )
        assertTrue(DeviceTrustStore.verifyJobEnvelope(envelope, owner.second, deviceId, grant.grantId))
        val tampered = org.json.JSONObject(envelope.toString())
        tampered.getJSONObject("payload").put("check_id", "tampered")
        assertFalse(DeviceTrustStore.verifyJobEnvelope(tampered, owner.second, deviceId, grant.grantId))
        assertFalse(DeviceTrustStore.verifyJobEnvelope(envelope, owner.second, "f".repeat(64), grant.grantId))
        DeviceTrustStore.revoke(context, deviceId)
    }
    @Test
    fun expiredChallengeAndWrongDeviceBindingAreRejected() {
        val owner = DeviceTrustStore.ownerIdentity()
        val candidateId = "a".repeat(64)
        val expired = DeviceTrustStore.createChallenge(candidateId, System.currentTimeMillis() / 1000L - 600L)
        assertFalse(DeviceTrustStore.verifyEnrollmentChallenge(
            DeviceTrustStore.enrollmentChallengeDocument(expired), owner.second, candidateId
        ))
    }
}
