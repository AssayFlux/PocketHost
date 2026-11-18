# Device Trust and Protocol Contract — v0.1.2 Foundation

**Protocol:** pockethost.device/1.0  
**Release assignment:** v0.1.2 — Connected Device Fabric  
**Normative machine-readable source:** protocol/

This document defines the shared trust contract for Android owner app, separately installed agents and the optional control plane. It is not a v0.1.0 static hosting dependency.

## 1. Product invariants

- Mobile modes are server_only and command_center_server; both serve websites.
- Device software is platform-specific and separately installed.
- Discovery is untrusted; explicit owner approval is required for every role grant.
- Every device, owner, role grant, job, runtime and signing identity is distinct.
- Default deny. Unknown major protocol, role, capability, job or board operation is rejected.
- Static website serving does not depend on device agents or control plane.

## 2. Wire format and compatibility

Messages are UTF-8 JSON. JSON Schema 2020-12 under protocol/schemas defines the wire shape. Unknown major versions are rejected. Within a major version, only additive optional fields are compatible; changes to required fields, signatures, authorization, state transitions or enum meaning require a new major version.

Signed messages use RFC 8785 JSON Canonicalization Scheme over the complete object with signature omitted. Signatures use Ed25519 or ECDSA P-256/SHA-256. ECDSA encoding must be validated. Key ID and algorithm are bound to the registered key.

Device ID is lowercase SHA-256 of DER SubjectPublicKeyInfo. Public key proof-of-possession is required. Hardware-backed non-exportable keys are preferred; software-backed fallback must be disclosed.

## 3. Enrollment ceremony

1. Agent creates key pair and device ID.
2. Agent creates a 256-bit random nonce and presents QR/manual fingerprint.
3. Challenge is owner-bound, device-bound, single-use and expires within five minutes.
4. Mobile validates schema, protocol, key fingerprint and proof of possession.
5. Owner reviews model, OS, architecture, product version, capabilities, requested role and budgets.
6. Owner explicitly approves the exact device and role.
7. Authority issues a signed grant with target, owner, role, policy, capabilities, budgets, expiry and generation.
8. Agent verifies and persists grant before acknowledgement.
9. Revocation advances generation and invalidates old grants and eligible sessions/jobs.

Human codes are one-time pairing proofs, never long-term bearer credentials. Store only a hash and rate-limit attempts.

## 4. Role policy

protocol/ROLE_CAPABILITY_POLICY.json is default-deny. A grant can contain only capabilities allowed for its role. Unknown capability is rejected. Roles include storage, traffic, build_publish, native_job, backup_recovery and edge_gateway. Mobile website serving is not a worker role.

## 5. Job semantics

Jobs are signed, typed, target-bound, grant-bound, expiring and idempotent. Delivery is at-least-once. The worker rechecks signature, target, grant generation, expiry, capability and budget immediately before side effects. Destructive work is reconciled after uncertain interruption.

## 6. Transport

Wi-Fi/LAN uses authenticated encrypted transport; BLE is for discovery/small control; USB requires documented platform support and explicit permission; remote HTTPS requires TLS and device-bound identity. Transport changes never create trust.

## 7. Data boundaries

Visitor ingress never reaches owner management, runtime broker or MCU controls. Build workers cannot access owner/device private keys or sign/activate releases. Storage workers receive encrypted named namespaces. Static hosting remains independent of optional runtimes.

## 8. Threat model

Rogue/cloned devices, pairing replay, stolen phone, role escalation, compromised worker, malicious jobs, MITM, control-plane outage, malicious artifacts, archive traversal, storage disclosure, traffic pivot, runtime escape and unsafe MCU commands are in scope.

## 9. Acceptance

Schema/policy validation is necessary but not sufficient. v0.1.2 must prove end-to-end owner enrollment, authenticated transport, role grant, job execution, revocation and reconnect on a real second device. Use G0–G9.
