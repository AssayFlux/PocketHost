package com.assayflux.pockethost

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.assayflux.pockethost.management.FleetControlPlaneClient
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.assayflux.pockethost.management.DeviceTrustStore

@Composable
internal fun PocketHostDevicesScreen(context: Context) {
    var fingerprint by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var product by remember { mutableStateOf("Pocket Host Agent") }
    var os by remember { mutableStateOf("Linux") }
    var architecture by remember { mutableStateOf("x86_64") }
    var protocol by remember { mutableStateOf("1.0") }
    var capabilities by remember { mutableStateOf("storage.read_assigned_namespace,storage.write_assigned_namespace") }
    var role by remember { mutableStateOf("storage") }
    var proof by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf<DeviceTrustStore.EnrollmentChallenge?>(null) }
    var message by remember { mutableStateOf("Discovery never grants access. Verify the fingerprint, then approve the exact role and capabilities.") }
    var refresh by remember { mutableIntStateOf(0) }
    var controlPlaneEndpoint by remember { mutableStateOf("") }
    var ownerCredential by remember { mutableStateOf("") }
    var fleetMapText by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getString("fleet_device_map", "{}") ?: "{}") }
    var fleetMessage by remember { mutableStateOf("Register an approved agent with the remote control plane to issue its separate device token and deliver the signed role grant.") }
    var oneTimeToken by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val fleetMap = remember(fleetMapText) { runCatching { JSONObject(fleetMapText) }.getOrDefault(JSONObject()) }
    val palette = MaterialTheme.colorScheme
    val devices = remember(refresh) { runCatching { DeviceTrustStore.list(context, includeRevoked = true) }.getOrDefault(emptyList()) }
    val owner = remember { runCatching { DeviceTrustStore.ownerIdentity() }.getOrNull() }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = palette.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("COMMAND CENTER IDENTITY", fontWeight = FontWeight.Bold, color = palette.onSurface)
                Text("Owner fingerprint", color = palette.onSurfaceVariant)
                Text(owner?.first ?: "Keystore identity unavailable", fontWeight = FontWeight.SemiBold)
                Text("Owner public key · Base64 DER SPKI", color = palette.onSurfaceVariant)
                Text(owner?.second ?: "Unavailable", color = palette.onSurfaceVariant)
                Text("P-256 signing key · Android Keystore · encrypted local registry", color = palette.onSurfaceVariant)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = palette.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("ENROL A DEVICE", fontWeight = FontWeight.Bold)
                Text("Enter the device's advertised identity and requested permissions. The agent must sign the challenge with its matching private key.", color = palette.onSurfaceVariant)
                OutlinedTextField(fingerprint, { fingerprint = it.trim() }, label = { Text("Device ID · SHA-256 SPKI fingerprint") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(publicKey, { publicKey = it.trim() }, label = { Text("Public key · Base64 DER SPKI") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(displayName, { displayName = it }, label = { Text("Device display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(product, { product = it }, label = { Text("Agent product") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(os, { os = it }, label = { Text("Operating system") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(architecture, { architecture = it }, label = { Text("Architecture") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(protocol, { protocol = it }, label = { Text("Protocol version") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(role, { role = it.trim() }, label = { Text("Requested role (storage, traffic, build_publish, native_job, backup_recovery, edge_gateway)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(capabilities, { capabilities = it }, label = { Text("Requested capabilities · comma separated") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Button(onClick = {
                    runCatching { DeviceTrustStore.createChallenge(fingerprint.lowercase()) }
                        .onSuccess { challenge = it; proof = ""; message = "Challenge created. Send the exact challenge fields to the candidate agent and obtain its signature." }
                        .onFailure { message = it.message ?: "Could not create challenge." }
                }, modifier = Modifier.fillMaxWidth()) { Text("Create 5-minute challenge") }
                challenge?.let {
                    Text("Challenge ID: ${it.challengeId}", fontWeight = FontWeight.SemiBold)
                    Text("Nonce: ${it.nonce}")
                    Text("Owner: ${it.ownerDeviceId}")
                    Text("Transport: ${it.transport} · single-use · expires: ${it.expiresAt}")
                    val proofCandidate = DeviceTrustStore.Candidate(
                        fingerprint.lowercase(), publicKey, displayName.trim(), product.trim(),
                        os.trim(), architecture.trim(), protocol.trim(),
                        capabilities.split(',').map { value -> value.trim() }.filter { value -> value.isNotEmpty() }.distinct()
                    )
                    Text("Signed challenge + candidate metadata proof request · send this exact document to the agent:", color = palette.onSurfaceVariant)
                    Text(DeviceTrustStore.enrollmentProofRequestDocument(it, proofCandidate).toString(), color = palette.onSurfaceVariant)
                    OutlinedTextField(proof, { proof = it.trim() }, label = { Text("Agent proof signature · Base64 ECDSA") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Button(onClick = {
                        val activity = context as? MainActivity
                        if (activity == null) {
                            message = "Owner authentication is unavailable; device approval was blocked."
                        } else {
                            message = "Confirm owner identity to approve this device."
                            activity.confirmBiometricUnlock { confirmed ->
                                if (confirmed) {
                                    val requested = capabilities.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                                    val candidate = DeviceTrustStore.Candidate(fingerprint.lowercase(), publicKey, displayName.trim(),
                                        product.trim(), os.trim(), architecture.trim(), protocol.trim(), requested)
                                    runCatching {
                                        DeviceTrustStore.approveCandidate(context, challenge?.challengeId ?: error("challenge_missing"),
                                            candidate, proof, role.trim(), requested)
                                    }.onSuccess { grant ->
                                        challenge = null
                                        proof = ""
                                        refresh++
                                        message = "Device approved. Signed role grant generation ${grant.generation} issued."
                                    }.onFailure { message = it.message ?: "Device approval failed." }
                                } else {
                                    message = "Owner authentication failed or was cancelled; device was not approved."
                                }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Verify owner and approve device") }
                }
                Text(message, color = palette.onSurfaceVariant)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = palette.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("REMOTE FLEET CONNECTION", fontWeight = FontWeight.Bold)
                Text("Use the Control Plane owner/bootstrap credential. It is exchanged for a short-lived session and is not saved by this screen.", color = palette.onSurfaceVariant)
                OutlinedTextField(controlPlaneEndpoint, { controlPlaneEndpoint = it.trim() }, label = { Text("Control Plane HTTPS URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(ownerCredential, { ownerCredential = it.trim() }, label = { Text("Owner / bootstrap credential") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text(fleetMessage, color = palette.onSurfaceVariant)
                if (oneTimeToken.isNotBlank()) {
                    Text("ONE-TIME WORKER TOKEN · copy this to the worker's POCKETHOST_CONTROL_TOKEN configuration:", fontWeight = FontWeight.SemiBold)
                    Text(oneTimeToken, color = palette.primary)
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = palette.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TRUSTED DEVICES", fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { refresh++ }) { Text("Refresh") }
                }
                if (devices.isEmpty()) Text("No enrolled devices yet.", color = palette.onSurfaceVariant)
                devices.forEach { device ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(device.displayName, fontWeight = FontWeight.SemiBold)
                        Text("${device.os} · ${device.architecture} · ${device.role} · generation ${device.generation}", color = palette.onSurfaceVariant)
                        Text(device.deviceId, color = palette.onSurfaceVariant)
                        Text(if (device.revokedAt == 0L) "Approved" else "Revoked", color = if (device.revokedAt == 0L) palette.primary else palette.error)
                        if (device.revokedAt == 0L) {
                            val controlPlaneId = fleetMap.optString(device.deviceId)
                            if (controlPlaneId.isBlank()) {
                                OutlinedButton(
                                    enabled = ownerCredential.length >= 32 && controlPlaneEndpoint.startsWith("https://"),
                                    onClick = {
                                        scope.launch {
                                            fleetMessage = "Registering worker and queuing its owner-signed role grant…"
                                            oneTimeToken = ""
                                            val result = runCatching {
                                                withContext(Dispatchers.IO) {
                                                    FleetControlPlaneClient.registerWorker(
                                                        controlPlaneEndpoint, ownerCredential, device.displayName,
                                                        DeviceTrustStore.signedGrantDocument(context, device.deviceId),
                                                        DeviceTrustStore.ownerIdentity().second
                                                    )
                                                }
                                            }
                                            result.onSuccess { registered ->
                                                fleetMap.put(device.deviceId, registered.controlPlaneId)
                                                fleetMapText = fleetMap.toString()
                                                context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
                                                    .edit().putString("fleet_device_map", fleetMapText).apply()
                                                oneTimeToken = registered.oneTimeDeviceToken
                                                fleetMessage = "Worker registered. The signed grant is queued; configure its one-time token and HTTPS endpoint on the worker, then wait for it to poll."
                                            }.onFailure { fleetMessage = it.message ?: "Worker registration failed." }
                                        }
                                    }, modifier = Modifier.fillMaxWidth()
                                ) { Text("Register worker & send signed grant") }
                            } else {
                                Text("Control Plane device: $controlPlaneId", color = palette.onSurfaceVariant)
                                if (device.role == "native_job") OutlinedButton(
                                    enabled = ownerCredential.length >= 32 && controlPlaneEndpoint.startsWith("https://"),
                                    onClick = {
                                        scope.launch {
                                            fleetMessage = "Signing and queueing a health-check job…"
                                            val result = runCatching {
                                                val envelope = DeviceTrustStore.createSignedJobEnvelope(
                                                    context, device.deviceId, "native.health_check",
                                                    JSONObject().put("check_id", "mobile-command-center-health-check")
                                                )
                                                withContext(Dispatchers.IO) {
                                                    FleetControlPlaneClient.queueSignedJob(
                                                        controlPlaneEndpoint, ownerCredential, controlPlaneId, envelope
                                                    )
                                                }
                                            }
                                            fleetMessage = result.fold(
                                                { "Signed health-check job queued. Worker acknowledgement is visible in the Control Plane device command history." },
                                                { it.message ?: "Could not queue signed job." }
                                            )
                                        }
                                    }, modifier = Modifier.fillMaxWidth()
                                ) { Text("Queue signed health check") }
                            }
                            OutlinedButton(onClick = {
                            val activity = context as? MainActivity
                            if (activity == null) {
                                message = "Owner authentication is unavailable; revocation was blocked."
                            } else {
                                message = "Confirm owner identity to revoke this device."
                                activity.confirmBiometricUnlock { confirmed ->
                                    if (confirmed) {
                                        runCatching { DeviceTrustStore.revoke(context, device.deviceId) }
                                            .onSuccess { refresh++; message = "Device revoked; prior grant generation is no longer accepted." }
                                            .onFailure { message = it.message ?: "Could not revoke device." }
                                    } else {
                                        message = "Owner authentication failed or was cancelled; device access remains unchanged."
                                    }
                                }
                            }
                        }) { Text("Verify owner and revoke access") }
                        }
                    }
                }
            }
        }
    }
}
