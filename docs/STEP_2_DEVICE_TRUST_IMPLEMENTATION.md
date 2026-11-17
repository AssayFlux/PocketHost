# Step 2 — Mobile Command Center and Device Trust Implementation

**Status:** implementation foundation committed; Step 2 gate remains open.

## Implemented in the Android application

### Two mobile operating modes
- The Home screen now persists one of the two product modes: `SERVER_ONLY` and `COMMAND_CENTER_SERVER`.
- Website serving remains available in both modes; the mode selector does not stop or silently disable hosting.
- The Devices navigation entry and device-management screen are exposed only in Command Center + Server mode.
- The default for a fresh installation is Server only.

### Android owner identity
- The Command Center creates or loads a P-256 ECDSA signing key in Android Keystore.
- The owner device ID is SHA-256 over the DER SubjectPublicKeyInfo (SPKI).
- The private key is not exported by the app API.
- The device registry is encrypted with an Android Keystore AES-256/GCM key and stored under `noBackupFilesDir`.

### Enrollment and approval
- The mobile app creates a 256-bit, URL-safe, single-use nonce with a five-minute lifetime.
- The challenge uses the Step 1 `enrollment-challenge.schema.json` field names, UUID challenge ID, protocol version, transport, owner key ID and ECDSA signature.
- The candidate's advertised SPKI must hash to its claimed device ID. The proof request document binds all displayed metadata and requested capabilities to the candidate signature.
- The candidate must prove possession by signing the exact canonical unsigned challenge followed by canonical candidate metadata (device fingerprint, SPKI, display name, product, OS, architecture, protocol version and requested capabilities). The Linux agent verifies the owner's challenge signature and signs the same metadata-bound payload; changing an advertised field invalidates the proof.
- Owner approval is a separate UI action. Discovery or challenge creation alone never adds a device.
- Requested role capabilities are checked against the frozen Step 1 role allow-list. Unknown roles and capabilities outside that role's allow-list are rejected.
- Role grants include a target device, owner identity, monotonically increasing generation, expiry, policy version, resource budget and owner signature using the Step 1 schema.

### Grant verification and revocation
- Candidate-side challenge verification checks owner fingerprint, device binding, protocol, nonce format, transport, single-use flag, expiry and owner signature.
- Worker-side grant verification checks owner fingerprint, target device, role/capability allow-list, generation floor, resource budget, expiry, policy version and signature.
- Revocation increments the stored generation, removes the current grant and makes the prior grant fail the local authorization check.
- Local authorization re-verifies the persisted grant signature, target, role, generation, expiry, capability allow-list and bounded role resource budget.
- Device approval and revocation require a fresh Android biometric/device-credential confirmation through the existing app authentication flow. This is currently an application-level confirmation gate; the P-256 signing key is not yet configured for per-use Keystore authentication.
- Approval and revocation are written to the operational audit log.
- The UI lists approved/revoked devices and exposes explicit revocation.

### Automated coverage added
- Android instrumentation coverage exercises owner identity creation, signed challenge verification, wrong-device rejection, capability-escalation rejection, candidate proof-of-possession, single-use challenge replay rejection, signed grant verification, altered-grant rejection, stale-generation rejection, authorization and revocation.
- Android integration workflow now checks Kotlin role-policy parity against the shared JSON policy and compiles app and Android instrumentation Kotlin sources on pushes without packaging APKs.
- APK packaging and emulator APK installation are gated behind an explicit workflow dispatch. The final APK build remains deferred until the five-step implementation is ready.

## Step 2 work still required — gate not closed

This commit is a real Android trust foundation, not a claim of complete fleet integration.

1. Implement and test LAN discovery and authenticated TLS transport, then BLE and supported USB paths; the current enrollment UI is manual payload exchange and the challenge transport defaults to LAN.
2. The Android Command Center now has a preview Control Plane client: it exchanges an owner/bootstrap credential for a short-lived admin session, registers an approved worker, queues its owner-signed role grant as `install_grant`, and can queue a typed owner-signed native health-check job for a registered `native_job` worker. The worker receives a one-time device token for manual secure configuration and polls over HTTPS. This closes part of the integration gap. The Control Plane also exposes a read-only, bounded latest-50 command history endpoint per device, and the Android Devices screen can refresh and display linked-worker heartbeat state, grant state and last-seen timestamp, plus command state, delivery attempts, acknowledgement timestamp and returned result. Native passkey session exchange, automatic token delivery, transport mutual authentication and end-to-end physical acceptance remain open.
3. Implement agent-side durable identity/key provisioning, challenge consumption, grant signature verification, generation persistence, renewal and revocation polling on each supported OS.
4. Upgrade the current app-level biometric/device-credential confirmation to a Keystore key that requires per-use user authentication, and test lockout, cancellation, key invalidation and background transitions.
5. Add device capability attestation, health/connection state, audit events, role changes and multi-role isolation.
6. Expand durable typed job queues, leases, idempotency and cancellation against `JOB_KIND_POLICY.json`; the initial mobile-signed health-check path is implemented for `native_job` workers.
7. Add physical Android device tests and transport adversarial tests, including replay, MITM, stale generation, clock rollback, storage corruption, Keystore invalidation and reboot/network-loss recovery.
8. Continue expanding parity checks to job-kind policy, schema versions and grant resource-budget constraints. The role capability allow-list itself is now mechanically checked against `protocol/ROLE_CAPABILITY_POLICY.json` in Android CI.

## Acceptance rule

Do not mark Step 2 complete until the app, control plane and at least one independently installable agent complete an end-to-end enrollment, role grant, job, revocation and reconnect scenario over an authenticated transport. A successful Kotlin compilation alone does not close this gate.
