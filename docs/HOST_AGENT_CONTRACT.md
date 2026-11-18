# Pocket Host — Host Agent and Role Contract

**Contract:** pockethost.device/1.0  
**Release assignment:** v0.1.2 — Connected Device Fabric

## 1. Principals and identity

- owner_id: user/team.
- mobile_client_id: owner mobile installation.
- device_id: stable enrolled device identity.
- device_instance_id: current agent installation/boot instance.
- role_grant_id: one approved role assignment.
- job_id: individual job.
- service_id/component_id: website deployment identity.
- edge_node_id: MCU identity.

Device keys are unique and revocable. Hardware-backed, non-exportable keys are preferred. Owner, device, runtime, artifact-signing and firmware keys are distinct.

## 2. Enrollment and verification

1. Candidate installs the correct platform-specific signed agent.
2. Candidate creates a key pair and device ID from public-key fingerprint.
3. Candidate presents a one-time challenge/QR/manual fingerprint.
4. Mobile verifies proof of possession over an authenticated channel.
5. Owner reviews identity, platform, software provenance, capabilities, requested role and resource budget.
6. Owner explicitly approves the exact role and permissions.
7. Authority issues a scoped grant bound to owner, device, role, policy, budgets, expiry and generation.
8. Agent verifies and persists the grant before acknowledging.
9. Revocation invalidates the grant generation and eligible sessions/jobs.

Discovery, IP, MAC, BLE name, QR content and proximity alone never authorize a device.

## 3. Transport contract

- Wi-Fi/LAN: authenticated encrypted session; preferred for bulk data.
- BLE: discovery and small control/pairing messages.
- USB: only documented OS-supported APIs with explicit permission.
- Remote HTTPS: TLS plus device-bound application identity, short-lived credentials and replay defense.

All transports map to the same identity and authorization. Switching transport cannot bypass policy.

## 4. Capability profile

A signed profile includes OS/firmware, version, architecture, ABI, CPU, memory, storage, filesystem, network, power, battery, thermal limits, isolation mechanisms, supported roles, transports and update capability. Unknown means unsupported for scheduling.

## 5. Role grant

A grant includes role, target device, owner approval, policy version, allowed/denied capabilities, data namespaces, routes, budgets, expiry/renewal and artifact compatibility. Multi-role co-location is allowed only where OS isolation and budgets are proven.

## 6. Typed job envelope

Every job contains contract version, ID, idempotency key, target, role grant, operation type, artifact digest, input/output references, permissions, budgets, issue/expiry time and audit metadata. No arbitrary shell, unrestricted script, arbitrary filesystem path or generic process execution.

## 7. Job lifecycle

~~~text
proposed -> owner_approved -> queued -> leased -> running -> succeeded | failed | interrupted
queued | leased -> cancelled | expired
~~~

Delivery is at-least-once. Mutations are idempotent and persisted before acknowledgement. Uncertain destructive operations are reconciled, not blindly replayed.

## 8. Desired and observed state

The mobile/control plane proposes desired state with monotonic generation. The agent persists accepted state and reports observed health, resource use, version and degraded reason. During control-plane outage, only already-approved assignments continue within their local lease and policy.

## 9. Role boundaries

- Storage: named encrypted namespaces only.
- Traffic: exact approved hostname/site routes and bounded proxy/cache; no management/runtime/MCU routes.
- Build/publish: scoped source input and staging output; no owner key or release activation authority.
- Native job: signed fixed-purpose package and declared API; no arbitrary shell.
- Backup: encrypted snapshot and constrained restore staging.
- Edge gateway: board-specific allow-listed operations.
- Website server: mobile-host responsibility.

## 10. Events, failure containment and acceptance

Publish bounded enrollment, role, job, capability, health, resource, update, backup, route and security events. Never log secrets, private keys, raw visitor bodies or customer source by default. Mobile/control-plane loss does not stop accepted local work, but cannot authorize new unknown privileged work.

Acceptance includes replay, revoked grant, expired lease, wrong target, unsupported role, resource exhaustion, reboot, network loss, update rollback and cross-role isolation. Apply G0–G9.
