# Pocket Host 0.1.0 — Host Agent Contract

**Contract family:** Pocket Host Host Agent API  
**Contract version:** 1.0  
**Product baseline:** 0.1.0  
**Transport:** authenticated HTTPS for control-plane exchange; authenticated encrypted peer channels for direct host-to-host data paths.

## 1. Purpose

The Host Agent Contract connects the Android/iOS command center, optional remote control plane, host agents, website components and edge gateways without sharing unrestricted process or filesystem access.

A mobile client issues owner-authorized intent. The target host validates and applies that intent locally. The host agent is the authority for local observed state and service lifecycle.

## 2. Identity separation

Every message identifies its principal and target:
- `owner_id`: account/team owner namespace.
- `client_id`: mobile/browser control client installation.
- `host_id`: stable enrolled host identity.
- `host_instance_id`: current host-agent installation/boot identity.
- `service_id` and `component_id`: deployed workload identities.
- `edge_node_id`: MCU/device identity managed through an edge gateway.

A host key is unique to that host and revocable. A client key is not a host key. A service identity is not an owner credential. Restoring a backup must not clone a host identity.

## 3. Common message metadata

All commands/events use a versioned envelope:
- `contract_version`
- `message_id` (unique identifier)
- `correlation_id` (request/response correlation)
- `idempotency_key` for mutating operations
- `issued_at` and `expires_at`
- `owner_id`, `actor_id`, `target_host_id`
- `scope` and requested capability
- `payload` validated against the operation schema

Reject unknown required versions, malformed fields, expired messages, oversized payloads and unauthorized scopes. Unknown optional fields may be ignored only when the contract version explicitly permits it.

## 4. Host registration and enrollment

Enrollment is owner-approved:
1. Host creates or receives a short-lived one-use challenge.
2. Mobile client discovers or scans the candidate host.
3. Candidate displays its device identity fingerprint and capability summary.
4. Owner approves exact identity, role and requested capabilities.
5. Host proves possession of its device-bound private key.
6. Registry binds host identity to owner/team and issues a scoped revocable credential.
7. Host records enrollment and current policy locally.

Discovery mechanisms (mDNS/DNS-SD, BLE, QR, manual address) are hints only. No network location or device name grants trust.

## 5. Capability profile

A host advertises a signed profile containing:
- OS/firmware and version.
- CPU architecture, ABI and instruction features.
- RAM, storage, filesystem and accelerators.
- Network interfaces and supported connectivity adapters.
- Power, battery, thermal and lifecycle constraints where available.
- Runtime provider IDs, versions and compatibility ranges.
- Service types, isolation mechanisms and resource ceilings.
- Agent version, contract range, update channel and signature identity.

Capability fields include value, source, timestamp and validity. Unknown is not equivalent to zero or supported.

## 6. Desired and observed state

The control plane stores desired state with a monotonically increasing generation and signed policy. Each host stores the latest accepted desired state and reports observed state.

Host state includes:
- accepted desired generation;
- observed generation;
- active deployment/component release IDs;
- service health and lifecycle;
- resource budgets and actual usage;
- host-agent and provider versions;
- last successful reconciliation time;
- degraded reason and recovery status.

A host applies only an authenticated, unexpired and policy-compatible generation. A newer generation supersedes older pending intent. If the control plane is unavailable, the host continues the last accepted generation and reports stale status after reconnection.

## 7. Command lifecycle

Commands are typed and allow-listed. No arbitrary shell, SQL, script, filesystem path or generic process-execution command exists.

State machine:

```
queued -> leased -> started -> succeeded
                    |-> failed
                    |-> interrupted
queued/leased -> expired or cancelled
```

- Delivery is at-least-once; exactly-once side effects are not assumed.
- Every mutation has an idempotency key and durable result record.
- Host validates actor, scope, expiry, capability, resource budget and local policy.
- Host persists start/result before acknowledgement.
- Uncertain operations are reported as interrupted and require reconciliation, not blind replay.
- Owner-sensitive operations require explicit approval and reauthentication.
- Revocation closes sessions and invalidates outstanding leases.

## 8. Core operation families

- `host.inspect`: capability, health and status.
- `host.configure`: apply versioned, allow-listed host settings.
- `host.update`: stage and verify a signed host/provider update.
- `service.deploy`: stage signed service/component release.
- `service.lifecycle`: start, stop, restart, drain and rollback.
- `service.inspect`: health, logs and resource status.
- `network.configure`: enable/disable approved ingress providers and site routes.
- `storage.backup`, `storage.restore`: scoped backup/restore workflows.
- `edge.invoke`: call an explicitly allow-listed edge capability through its gateway.
- `host.revoke`: invalidate host identity and access.

Each family has a strict JSON schema, maximum payload size, rate limit, timeout, audit policy and required capability. The first implementation may support only a subset.

## 9. Events and telemetry

Host agents publish bounded events:
- enrollment, heartbeat and revocation;
- capability changes;
- desired-state accepted/rejected/reconciled;
- service deployment, health and lifecycle;
- resource pressure and admission changes;
- tunnel provider connection/route/certificate state;
- backup/update/rollback result;
- security and audit events.

Telemetry is advisory and never blocks the service request path. Sampling and retention are bounded. Missing values remain unknown. Sensitive data, source code, secrets and raw visitor payloads are not emitted by default.

## 10. Direct host-to-host interconnection

Direct peer communication is optional and authenticated:
- Registry or control plane provides peer identity and connection hints.
- Peers authenticate using host-bound credentials and standard encrypted transport.
- Each peer connection is scoped to named service identities and capabilities.
- The host firewall/OS policy enforces permitted routes.
- Direct LAN transport is preferred when reachable; an authenticated relay path may be used when permitted.
- Peer discovery never authorizes access.
- Peer loss triggers bounded retry and explicit degraded state.
- No host receives another host's owner credential or unrestricted filesystem access.

## 11. Offline behavior and leases

- A host may continue its last accepted configuration while offline.
- New owner-sensitive changes require a valid authorization path.
- Commands expire; they are not applied after their validity window.
- The host must have a bounded offline policy and maximum credential/authorization lease.
- If the lease expires, only explicitly permitted local workloads continue; privileged fleet changes remain disabled.
- Reconnection reconciles by generation and idempotency key.
- The mobile client can operate locally over an authenticated LAN connection without the public control plane.

## 12. Failure isolation

- Control-plane loss does not stop host supervision.
- Mobile client loss does not stop host workloads.
- One host agent failure does not affect another host.
- One service component failure does not affect sibling components.
- One peer link failure does not corrupt desired state or other links.
- Telemetry/event delivery failure does not block service traffic.
- Relay/tunnel failure does not expose management or stop local ingress.

## 13. Compatibility and rollout

- Contract versions use explicit major/minor semantics.
- Breaking changes require a new major contract version and an overlap window.
- Hosts advertise supported ranges and reject incompatible commands.
- Roll out control plane, mobile client and host-agent changes with compatibility tests.
- Configuration schemas carry their own versions and migration functions.
- Updates are canary-first, staged and rollback-capable.

## 14. Acceptance

- Forged, replayed, expired, wrong-scope and revoked commands are rejected.
- Duplicate command delivery produces one logical side effect.
- Host restart during every command state recovers deterministically.
- Phone and control-plane outages leave existing services running.
- LAN discovery cannot enroll or authorize a host.
- Direct peer traffic cannot exceed granted service capabilities.
- Host capability profile matches measured device resources.
- Desired/observed generation reconciliation is deterministic.
- Host, service, provider and peer failures remain isolated.
