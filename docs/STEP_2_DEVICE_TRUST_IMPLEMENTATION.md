# Android Device Trust Implementation — v0.2.0 Workstream

**Status:** implementation foundation committed; end-to-end device-fabric acceptance remains open.  
**Release assignment:** v0.2.0.

## Existing Android foundation

### Operating modes
- Persisted SERVER_ONLY and COMMAND_CENTER_SERVER selection.
- Website serving remains enabled in both modes.
- Devices navigation is gated to Command Center + Server.
- Fresh install defaults to Server only.

### Owner identity
- P-256 ECDSA signing key in Android Keystore.
- Owner device ID derived from SHA-256 of DER SPKI.
- Private key is not exported through the app API.
- Encrypted local device registry stored under noBackupFilesDir.

### Enrollment and approval
- 256-bit URL-safe single-use nonce with five-minute lifetime.
- Signed challenge bound to owner, transport and candidate.
- Candidate SPKI fingerprint and metadata-bound proof-of-possession validation.
- Explicit owner approval separate from discovery.
- Role/capability allow-list validation.
- Signed grants bind target, owner, generation, expiry, policy and resource budget.

### Grant verification and revocation
- Candidate verifies owner challenge and device binding.
- Worker verifies owner, target, role, capabilities, generation, expiry and budget.
- Revocation increments generation and invalidates the prior grant.
- Approval/revocation require app-level biometric/device credential confirmation.
- Current P-256 signing key is not configured for per-use Keystore authentication.

### Control-plane preview
The app has a preview control-plane client foundation for bootstrap/session exchange, worker registration, signed grant/health-check command queueing and bounded command history/result display. This is not yet end-to-end production device enrollment.

## Remaining v0.2.0 work

1. Per-use owner key authentication and protected key lifecycle.
2. Authenticated transport/mutual device identity.
3. Automatic credential delivery or secure manual provisioning with rotation.
4. Grant renewal and revocation propagation.
5. Full mobile-to-control-plane device registration, signed job creation, acknowledgement and result flow.
6. Agent reconnect, durable state, update and rollback.
7. Physical-device end-to-end acceptance.

## Acceptance

Do not close the device-trust gate until a separately installed agent completes owner enrollment, role grant, job, revocation and reconnect over an authenticated transport. A Kotlin compile or UI demonstration alone is not acceptance. All G0–G9 apply.
