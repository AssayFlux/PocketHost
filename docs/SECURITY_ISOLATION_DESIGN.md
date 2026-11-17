# Pocket Host 0.1.0 — Security and Isolation Design

## Security objective

Only owner-approved devices may join a Pocket Host fleet. Each enrolled device receives explicit, revocable role grants. A compromise in one device, role, job, runtime or transport must not grant control over the fleet or unrelated components.

## Trust hierarchy

1. Owner identity and recovery.
2. Mobile command-center client identity.
3. Enrolled device identity bound to a cryptographic key.
4. Individual role grant with least-privilege capabilities.
5. Individual job/deployment identity.
6. Website component/runtime identity.
7. Edge/MCU identity and board-level capability.

These identities are distinct. A pairing code is not a credential for ongoing access. A device key does not imply every role. A role grant does not authorize another role.

## Enrollment

- Device generates a key pair; hardware-backed storage is used where supported.
- A short-lived, single-use, rate-limited verification code is bound to the candidate key and enrollment challenge.
- Owner reviews fingerprint, software version/provenance, capability profile, requested role and permissions.
- Approval issues a scoped revocable credential.
- Discovery by LAN, BLE, USB, QR, IP or MAC is never sufficient.
- Replayed, expired, guessed, wrong-owner and revoked challenges are rejected.
- Revocation invalidates sessions, queued jobs and future role renewals.

## Transport security

Wi-Fi/LAN, BLE, USB and remote transport all map to the same device identity and authorization policy. Use reviewed standard cryptography and authenticated encryption. Do not invent cryptographic protocols. Bluetooth is not treated as a secure authorization boundary. USB access requires explicit OS permission and a supported API. iOS arbitrary USB device control is not assumed.

## Role isolation

- Storage: encrypted named namespaces; no execution, proxy or management access by default.
- Traffic: exact hostname/site routes, bounded proxy/cache; no management, runtime broker or MCU control.
- Build/publish: scoped source inputs and artifact outputs; no owner/device identity keys; no direct release activation.
- Backend/job: signed fixed-purpose native packages and declared APIs; no unrestricted shell or customer language runtime.
- Backup: encrypted snapshots and constrained restore staging.
- Mobile website server: Rust ingress and user-selected runtime packs.
- Edge gateway: board-specific allow-listed DCP operations.

Each role has its own credential, queue, resource budget, health and audit stream. If the OS cannot enforce adequate separation, do not co-locate mutually untrusted roles on that device.

## Runtime security

Python 3.11 and Node.js 24.22.1 currently execute through the separate Android `runtime-worker` companion APK on the mobile website-serving node. Both integrations are in progress and share the companion application UID; therefore the package boundary is not per-project isolation and untrusted customer code must not be treated as sandboxed. The main app's static HTML/CSS/JavaScript serving remains available without the companion. The built-in TypeScript compiler is a separate Step 4 authoring tool, not a server-side runtime. Any future per-language runtime package must prove OS-enforced project isolation, scoped filesystem/network capabilities, resource limits, supervision and adversarial tests.

## Artifact and update security

- Signed agent, runtime, firmware and workload artifacts.
- Verify signature, digest, platform, ABI, version and compatibility before activation.
- Stage in restricted storage; validate paths, quotas and metadata.
- Atomic activation and last-known-good rollback.
- Build worker cannot sign or activate a release without explicit approved flow.
- MCU OTA verifies signatures and board compatibility before flashing.

## Threat tests

Rogue device, replayed code, device cloning, stolen credentials, role escalation, malicious worker, compromised storage, malicious artifact, archive traversal, runtime escape, cross-project access, proxy route abuse, secret exfiltration, denial of service, control-plane outage, phone loss and unsafe MCU command.

## Protocol source of truth

The machine-readable device hello, enrollment challenge, role grant and job envelope schemas plus default-deny role/job policies live in [`../protocol/`](../protocol/README.md). The normative ceremony, canonical signing rules, transport matrix and threat mitigations are in [`STEP_1_TRUST_AND_PROTOCOL.md`](STEP_1_TRUST_AND_PROTOCOL.md). Platform code must enforce the same role allow-list locally; UI visibility is not an authorization boundary.

## Acceptance

Security claims require adversarial tests on named devices and OS versions. A successful pairing UI, signature check or process start alone is not proof of fleet isolation.
