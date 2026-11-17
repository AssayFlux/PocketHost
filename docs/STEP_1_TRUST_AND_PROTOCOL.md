# Step 1 — Frozen Contracts, Trust and Product Boundaries

**Status:** Step 1 contract gate closed on Alpha CI; cryptographic implementation and physical acceptance remain downstream gates.  
**Evidence:** [Protocol contract validation #16](https://github.com/AssayFlux/PocketHost/actions/runs/44059614650) — schema/policy validator and all 15 protocol/trust-state simulation tests passed.  
**Protocol:** `pockethost.device/1.0`  
**Product:** Pocket Host 0.1.0

This document is normative with `protocol/` schemas. A runtime implementation must not infer authorization from discovery, transport, UI state or a device label.

## 1. Product invariants

- Exactly two mobile modes: `server_only` and `command_center_server`. Website serving stays enabled in both. Only the latter exposes fleet administration. No command-center-only mode.
- Each device class receives a separate platform-appropriate product and installer. Products interoperate through this protocol; installing one does not imply installing another.
- Every device has a distinct cryptographic identity. Owner, mobile installation, device, role grant, job, runtime, artifact-signing and MCU identities are never reused.
- Discovery is untrusted. Explicit owner approval is required for each exact role grant.
- Default deny. Unknown protocol major, role, capability, job kind, transport or board operation is rejected.
- Customer-selected server-side runtimes execute on the mobile serving node, not general worker devices. The current Python/Node implementations live in the separate Android runtime companion; see the implementation status documents. Static serving does not depend on them.
- Control plane is optional for already-authorized local jobs. It is not the trust root for local serving availability.

## 2. Wire format and compatibility

All messages are UTF-8 JSON. The protocol identifier is `pockethost.device/1.0`. JSON Schema 2020-12 schemas under `protocol/schemas/` define the wire shape. Unknown major versions are rejected. Within a major version, additive optional fields only; a change that alters meaning, required fields, signature canonicalization, authorization, state transitions or enum semantics requires a new major version. Implementations advertise exact supported protocol ranges and product versions independently.

Signed messages use RFC 8785 JSON Canonicalization Scheme (JCS) over the complete message object with the `signature` member omitted. UTF-8 canonical bytes are signed; no reserialization with implementation-specific ordering or number formatting is permitted. Signatures use Ed25519 or ECDSA P-256 with SHA-256. ECDSA signatures use fixed DER encoding; verification must reject malformed/non-canonical encodings. Signature algorithm and key id are inside the signed envelope and must match the registered key record.

Device identity is lowercase hexadecimal SHA-256 of the DER SubjectPublicKeyInfo (SPKI) public key. The advertised algorithm must match the SPKI algorithm. The public key must verify proof of possession before owner approval. Key material remains on the device; hardware-backed non-exportable storage is preferred, with software-backed fallback clearly disclosed.

## 3. Enrollment trust ceremony

1. Agent creates its key pair and device ID from SPKI.
2. Agent creates a fresh 256-bit cryptographically random nonce and displays a QR/manual fingerprint. A challenge is owner-bound, device-bound, single-use and expires no later than five minutes after issuance.
3. Mobile Command Center obtains the candidate hello and challenge over the selected transport. It validates schema, version, key fingerprint, proof of possession and challenge signature.
4. Owner reviews device name, fingerprint, OS/architecture, product version, measured capabilities, isolation, requested role, permissions and resource budget.
5. Owner explicitly approves the exact device, role, capabilities and budget. Approval cannot be inferred from a scan or successful pairing.
6. Authority issues a signed role grant bound to owner, device fingerprint, role, policy version, capability set, budgets, expiry and monotonically increasing generation.
7. Agent verifies signature, owner, device binding, expiry, policy subset and generation; it persists the accepted grant before acknowledging.
8. Revoke increments generation and invalidates sessions, queued unstarted jobs and future renewals. A stale grant cannot be restored by reconnecting.

Verification code is only a short-lived pairing proof, never an ongoing bearer credential. Store only a hash of any human-entered code, rate-limit attempts per device and owner, and never log it.

## 4. Role and capability policy

`protocol/ROLE_CAPABILITY_POLICY.json` is the default-deny capability catalogue. A grant may contain only capabilities allowed for its one role. Denials override allows. Unknown capabilities are rejected. Multi-role devices require independent grants, credentials, queues, namespaces and isolation proof per role. If the target OS cannot enforce required separation, scheduler must refuse co-location.

Role semantics:
- `storage`: encrypted, named assigned namespaces; no execution, visitor ingress or management.
- `traffic`: exact owner-approved hostname/site routes and bounded cache/proxy; never management, runtime broker or MCU.
- `build_publish`: scoped source inputs and staging outputs; cannot read owner/device keys, sign or activate a release.
- `native_job`: signed fixed-purpose package and declared API; no arbitrary shell or customer language runtime.
- `backup_recovery`: encrypted snapshots and constrained restore staging; no unrestricted filesystem browser.
- `edge_gateway`: board-profile-specific allow-listed DCP operations; no arbitrary GPIO, shell or firmware.
- mobile website serving is a mobile product responsibility, not a worker grant in this protocol's worker-role enum.

## 5. Job semantics

Jobs are signed, typed, target-bound, grant-bound, expiring and idempotent. Delivery is at-least-once; mutation handlers must persist an idempotency result before acknowledging. State transitions:

`proposed -> owner_approved -> queued -> leased -> running -> succeeded | failed | interrupted`

`queued | leased -> cancelled | expired`

A lease has a bounded deadline. A worker must re-check grant generation, expiry, target identity, capability, budget and job signature immediately before side effects. Destructive jobs are never blindly replayed after uncertain interruption; the agent reconciles observed state. Arbitrary shell, unrestricted script, generic process, arbitrary filesystem path and open-ended SQL are not protocol job types.

## 6. Transport matrix

| Transport | Discovery/pairing | Control | Bulk data | Security and platform rule |
|---|---|---|---|---|
| Wi-Fi/LAN | Yes | Yes | Preferred | Mutually authenticated TLS; LAN presence is not trust; defend against DNS/IP spoofing and replay |
| BLE | Yes | Small messages | No | GATT data is untrusted; application-layer challenge and authenticated encryption required |
| USB | Supported paths only | Supported paths only | Where platform permits | Explicit OS permission; no assumed generic USB access; iOS arbitrary USB control is not promised |
| Remote HTTPS | Manual/relay-assisted | Yes | Via authorized transfer | TLS plus device-bound application identity, short-lived credentials and replay defense |

Transport switching preserves the same device identity, grant generation and authorization. It cannot enroll, elevate or bypass policy. No protocol relies on MAC address, IP address, BLE name, QR text or physical proximity as an identity.

## 7. Data, runtime and artifact boundaries

- Owner management/control plane is never reachable through visitor website ingress.
- Website static serving remains independent of optional language runtime availability.
- Customer-selected language runtime packs execute only on the mobile serving node in 0.1.0.
- Build workers emit immutable content-addressed artifacts to staging. The serving node verifies digest, signature/provenance, platform compatibility, paths, quotas and owner approval before atomic activation.
- Build workers do not receive owner identity keys, device private keys or release signing keys.
- Storage workers hold encrypted named namespaces and scoped data keys only.
- Traffic workers receive only explicit host routes and cannot access management, runtime or MCU endpoints.
- Backups are separately encrypted, verified and restore-staged; restore is an explicit privileged operation.

## 8. Threat model and required mitigations

| Threat | Required mitigation / rejection condition |
|---|---|
| Rogue or cloned device | SPKI fingerprint, proof of possession, owner approval, device-bound grant |
| Pairing replay/guessing | 256-bit nonce, <=5-minute expiry, single use, owner/device binding, rate limit |
| Stolen phone/session | owner re-authentication for sensitive grants, short-lived sessions, revocation, recovery audit |
| Role escalation | default deny, role allow-list, deny overrides allow, local agent revalidation |
| Compromised worker | scoped credentials, separate role queues/namespaces, no owner keys, bounded jobs |
| Malicious/replayed job | signature, target/grant binding, expiry, idempotency key, monotonic generation |
| Transport MITM/spoofing | mutually authenticated TLS or application-authenticated channel; transport is not trust |
| Control-plane outage | durable accepted local state and bounded leases; no new unknown privileged work |
| Malicious artifact/path traversal | content digest/signature, path normalization, quotas, staging and atomic activation |
| Storage disclosure | encrypted namespace, per-role/data keys, explicit retention |
| Traffic pivot to management | exact hostname routes and deny-by-default destination policy |
| Runtime escape/cross-project access | OS-enforced identity, filesystem/network sandbox, resource caps, adversarial tests |
| MCU unsafe command/OTA | board allow-list, safe pin profile, signed firmware, rollback, rate/duty-cycle limits |
| Resource exhaustion | per-role CPU/memory/storage/network budgets, bounded queues, backpressure and thermal/power policy |

## 9. DemonCore interoperability boundary

DemonCore remains an independently buildable and deployable repository. Pocket Host integrates through a separately supervised SBC edge adapter; it does not embed DemonCore firmware or assume that an MCU is a general-purpose host. DCP has its own version lifecycle. Adapter compatibility must declare DCP min/max version and board profile. Cross-network DCP requires authenticated encryption, replay protection, authorization, key rotation and revocation. Only allow-listed board-specific operations are exposed. No public visitor route reaches DCP, firmware update or GPIO administration. Physical board, watchdog, memory, safe-state, OTA rollback and revocation tests remain mandatory. See [DemonCore interop contract](https://github.com/AssayFlux/DemonCore/blob/main/docs/POCKETHOST_INTEROP.md).

## 10. Exit checklist and evidence

Step 1 is ready for implementation only when:
- schemas and compatibility rules are frozen;
- trust ceremony and state machine have executable positive/negative simulations;
- role capability policy is machine-readable and default-deny;
- threat model has a mitigation and test for each listed threat;
- transport, runtime, artifact and DemonCore boundaries are explicit;
- CI validates schemas and runs simulations.

This gate does not claim cryptographic production readiness. Real key storage, signature verification, authenticated transports, physical device pairing and adversarial hardware tests are implementation/release gates in later steps.
