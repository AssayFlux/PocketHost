# Pocket Host 0.1.0 — Host Agent and Role Contract

**Contract:** Host Agent API 1.0  
**Product baseline:** 0.1.0  
**Purpose:** securely enroll devices and assign bounded, independently managed jobs from a mobile command center.

## 1. Principals and identity

- `owner_id`: user/team.
- `mobile_client_id`: Android/iOS command-center installation.
- `device_id`: stable enrolled device identity.
- `device_instance_id`: current agent installation/boot instance.
- `role_grant_id`: one approved role assignment.
- `job_id`: individual job.
- `service_id` / `component_id`: website deployment identity.
- `edge_node_id`: MCU identity.

Device keys are unique and revocable. Use hardware-backed, non-exportable keys where available; clearly label software-backed fallback. Never reuse owner keys, device keys, runtime keys or firmware-signing keys.

## 2. Enrollment and verification

1. Candidate installs the correct signed node software.
2. Candidate creates a key pair and short-lived enrollment challenge.
3. Candidate displays device ID/fingerprint and one-time verification code or QR.
4. Mobile discovers candidate via LAN/BLE/USB-supported channel or manual remote enrollment.
5. Mobile verifies proof of possession over an authenticated encrypted channel.
6. Owner reviews device identity, measured capabilities, software provenance and requested role.
7. Owner explicitly approves exact role and permission set.
8. Registry issues a scoped revocable credential bound to device key and role grant.

Codes are one-time, short-lived, rate-limited and bound to the candidate key/challenge. They are not long-term passwords. Discovery, IP, MAC, BLE name, QR content or physical proximity alone never authorizes a device. Revocation invalidates active sessions and queued unstarted jobs.

## 3. Transport contract

- Wi-Fi/LAN: authenticated encrypted session; preferred for bulk data.
- BLE: discovery, proximity and small control/pairing messages; never assume bulk transfer capacity.
- USB: use only documented OS-supported APIs and explicit device permission. Android USB host/accessory capabilities vary by hardware; iOS generic arbitrary device control is not assumed.
- Remote: authenticated outbound HTTPS or peer channel with short-lived credentials.

Every transport maps to the same device identity and authorization model. Switching transport must not create a new trusted identity or bypass role policy.

## 4. Device capability profile

Signed profile includes OS/firmware, version, architecture, ABI, CPU, memory, storage, filesystem, network interfaces, power, battery, thermal limits, isolation mechanisms, supported role IDs, transport support, agent version and update capability. Every value carries source, timestamp and validity. Unknown means unsupported for automatic placement.

## 5. Role grant

A role grant includes:
- role ID and target device ID;
- owner approval record and policy version;
- explicit capabilities and denied capabilities;
- data namespaces, routes and peer identities;
- resource, power, storage and network budgets;
- expiry/renewal policy;
- artifact and software compatibility;
- health checks, restart, update and rollback policy.

Roles include storage, traffic, build/publish, native backend/job, backup/recovery, edge gateway and mobile website server. A device can hold multiple roles only when the OS isolation and resource budget support them. Each role is independently pauseable and revocable.

## 6. Job envelope

Every job contains contract version, message/job ID, idempotency key, correlation ID, owner/actor, target device, role grant, operation type, artifact digest, input/output references, permissions, budgets, issued/expiry times and audit metadata.

Commands are typed and allow-listed. No arbitrary shell, unrestricted script, arbitrary filesystem path, SQL or generic process execution. Device validates policy, signature, expiry, capability, resource budget and role grant before accepting.

## 7. Job lifecycle

```
proposed -> owner-approved -> queued -> leased -> running
                                      |-> succeeded
                                      |-> failed
                                      |-> interrupted
queued/leased -> expired or cancelled
```

Delivery is at-least-once. Mutations must be idempotent. Persist state before acknowledgement. On uncertain interruption, reconcile observed state; never blindly replay destructive work.

## 8. Desired and observed state

The mobile client/control plane proposes desired role state with monotonic generation. The target device stores accepted state durably and reconciles locally. The device reports observed state, health, resource use, last reconciliation, degraded reason and current artifact version.

If the phone or control plane is unavailable, the device continues only its last owner-approved assignments within their local policy and lease. It does not accept new privileged work from an unknown peer.

## 9. Role-specific capability boundaries

- Storage: named encrypted namespaces; no execution or visitor ingress by default.
- Traffic: exact hostname/site routes and bounded proxy/cache; no management/runtime/MCU endpoints.
- Build/publish: scoped source inputs and output artifact namespace; cannot sign or activate without approved signing and serving-node verification.
- Native backend/job: signed fixed-purpose packages and declared APIs; not arbitrary customer language runtime hosting.
- Backup: encrypted snapshot/restore capability; no live unrestricted filesystem browsing.
- Mobile website server: Rust ingress and owner-selected language runtime packs.
- Edge gateway: board-specific allow-listed DCP/MCU operations only.

## 10. Events and telemetry

Publish bounded enrollment, role-change, job lifecycle, capability, health, resource, update, backup, route, runtime and security events. Telemetry must not block workloads. Never emit secrets, private keys, raw visitor bodies or customer source by default.

## 11. Failure containment

- Mobile app/control-plane loss does not stop accepted local jobs.
- One role failure does not stop other roles on the device where isolation supports it.
- One device loss does not corrupt sibling devices.
- Transport loss does not change trust or grant new permissions.
- A stale/revoked role cannot accept new work.
- Telemetry loss does not block service work.
- Device restart recovers from durable accepted state and verifies artifacts before resuming.

## 12. Compatibility

Contract versions use major/minor compatibility. Devices advertise supported ranges. Breaking changes require an overlap and migration window. Every agent package declares OS/architecture, permissions, supported roles, protocol range and rollback path.

## 13. Acceptance

Test fake/expired/replayed code, wrong key, wrong owner, role escalation, revoked device, duplicate jobs, power loss during each lifecycle stage, transport switching, phone outage, control-plane outage, storage exhaustion, resource pressure, malicious artifact, unauthorized peer and role-specific privilege boundaries.
