# Pocket Host Android Device Mesh — Architecture and Delivery Plan

**Status:** planned; not implemented or production-approved  
**Sequence:** begin after Gates 3–8 have been implemented and independently verified, then after Gate 2 production isolation is closed.  
**Constraint:** mesh membership must never weaken the single-device security boundary or make one phone's private project data available to another node.

## 1. Product objective

Allow an Owner to explicitly pair multiple Android devices over a trusted Wi-Fi LAN and form a cooperative hosting pool. A mesh may increase aggregate request capacity for workloads that can be safely replicated or partitioned. It must not imply that arbitrary Android phones can transparently share RAM, CPU or storage, or that every workload scales horizontally.

The mesh is an optional layer. A single phone must continue hosting when the mesh is unavailable. Cloudflare Tunnel and AssayFlux Relay remain independent ingress choices.

## 2. Topology

- **Owner/controller node:** creates the mesh, approves membership, assigns site placement and policy, and maintains the authoritative configuration.
- **Worker nodes:** explicitly enrolled Android devices that advertise measured capacity and execute only assigned workloads.
- **Ingress node(s):** accept visitor connections and distribute eligible requests to healthy workers.
- **Local discovery:** mDNS/DNS-SD on the selected Wi-Fi network as an untrusted discovery hint only. Discovery never grants membership or authorization.
- **Control plane:** authenticated node sessions, heartbeats, leases, configuration epochs, revocation and audit.
- **Data plane:** mutually authenticated, encrypted, bounded streams over the LAN; no public management listener.

A mesh can be a single controller with workers initially. Multi-controller consensus/failover is a later design and must not be simulated by loosely synchronized owner state.

## 3. Enrollment and trust

1. Owner creates a short-lived, one-use pairing challenge in the host app.
2. The candidate worker scans a QR code or enters a short code while on the same LAN.
3. Both devices exchange public-key fingerprints and display a human-verifiable confirmation.
4. Each device generates a non-exportable identity key in Android Keystore where available.
5. Owner approves the exact device, role and capabilities; the controller issues a scoped, expiring membership credential.
6. Every connection uses mutual TLS or a reviewed equivalent with replay-resistant session binding.
7. Revocation immediately invalidates the node lease, closes streams and removes it from scheduling.
8. A worker never receives the Owner's management credential, Relay device token, or another worker's private keys.

Wi-Fi possession, SSID membership, mDNS visibility, IP address and a successful TCP connection are not trust signals.

## 4. Workload placement and data model

Initial supported workload should be **replicated, stateless static-site serving** only:

- Controller distributes a signed, content-addressed immutable site release to each approved worker.
- Worker verifies manifest signature, hash, size, file count and path policy before activation.
- Each worker serves only its local verified read-only release.
- Ingress routes requests by site and health, using weighted least-load selection with bounded retries only for idempotent requests.
- Dynamic Python/Node workloads remain pinned to one node until Gate 2 isolation, state handling, and workload-specific distributed semantics have passed separate reviews.
- Writable application state, databases, uploads, background jobs and session affinity are not transparently replicated in the first release.

Never copy secrets or device-bound credentials as part of a site release. Site data transfer must be encrypted, integrity-checked, resumable, quota-limited and revocable.

## 5. Capacity and scheduling

Advertised capacity is measured, not guessed. Each node reports bounded telemetry: CPU saturation, memory pressure, thermal state, battery/charging state, storage headroom, active requests, queue depth, network RTT/loss and runtime health.

The controller computes a conservative effective capacity from recent measurements and policy caps. Scheduling must:

- apply per-site and per-node concurrency ceilings;
- account for CPU, memory, thermal, battery and network pressure;
- avoid sending more work than a worker's bounded queue can accept;
- return explicit overload/retry responses when no safe capacity remains;
- use circuit breakers and short health leases;
- drain a node before planned removal;
- stop assigning work immediately on lease expiry or revocation;
- avoid retrying non-idempotent requests after uncertain delivery;
- reserve capacity for controller health and management traffic.

Aggregate throughput is not assumed to equal the sum of device benchmarks. Wi-Fi airtime, ingress bottlenecks, flash I/O, thermal throttling and workload synchronization can reduce or reverse scaling gains.

## 6. Failure and consistency behavior

- Worker disconnect: expire its lease, stop new routing, let in-flight requests fail predictably, and retry only safe idempotent work.
- Controller disconnect: workers stop accepting new configuration and follow a bounded lease policy; no indefinite stale membership.
- Wi-Fi network change: pause routing, re-discover, authenticate and rejoin; do not trust a new IP address automatically.
- Partial release rollout: retain last-known-good release and use a configuration epoch so ingress never mixes incompatible versions silently.
- Clock skew: use server-issued leases and monotonic local deadlines where possible.
- Split brain: initial single-controller architecture rejects competing controllers; do not permit two authorities to assign conflicting ownership.
- Low battery/thermal pressure: worker self-derates and may gracefully leave the mesh.
- Storage exhaustion or interrupted update: preserve prior release and recover atomically.

## 7. Security boundaries

- Mesh membership is not a substitute for Gate 2 per-project isolation.
- Worker processes must run with the same least-privilege and project-isolation requirements as local hosting.
- Workers receive only the project/release and capabilities assigned to them.
- No remote shell, arbitrary command execution, package installation or unrestricted filesystem access.
- Control messages are typed, versioned, bounded, authenticated and auditable.
- All listeners bind only to the intended LAN interface and are protected by mutual authentication; never expose the management API publicly.
- Rate-limit pairing, enrollment, artifact transfer and per-node streams.
- Owner can inspect, pause, drain and revoke every member.
- Provide explicit privacy disclosure: enrolled workers receive the site content assigned to them and can observe the requests they serve.

## 8. Delivery phases and acceptance

### M0 — Discovery and threat model
Document supported Android Wi-Fi APIs, multicast restrictions, OEM behavior, network transitions, battery/Doze constraints and threat model. Exit: reviewed protocol and abuse cases.

### M1 — Secure pairing and membership
Implement Keystore identity, one-time pairing, mutual authentication, membership leases, owner approval and revocation. Exit: unapproved, expired, replayed and revoked nodes cannot join or retain streams.

### M2 — Health and capacity telemetry
Implement authenticated heartbeats, conservative capacity estimates and self-derating. Exit: stale, overloaded, hot, low-battery and disconnected nodes are removed from scheduling within a bounded interval.

### M3 — Static release replication
Implement signed content-addressed releases, bounded transfer, verification, atomic activation and rollback. Exit: tampered, oversized, traversal, interrupted and stale releases are rejected without losing the active release.

### M4 — LAN ingress distribution
Implement site-aware routing, least-load selection, idempotency-aware retries, backpressure and draining. Exit: multi-node static load tests show improved sustained throughput over a single-node baseline without violating error, latency, memory or thermal budgets.

### M5 — Resilience and operations
Test Wi-Fi loss/roam, worker process death, controller restart, split-brain rejection, revocation, low storage, Doze and mixed Android versions. Exit: fault injection and recovery evidence pass.

### M6 — Dynamic workloads (separate approval)
Only after Gate 2 is closed, evaluate project-pinned Python/Node workers, stateful session routing and safe data synchronization. No dynamic workload is distributed merely because static routing works.

## 9. Required benchmark evidence

For one, two and progressively more devices on representative Wi-Fi hardware, record exact device models/API levels, Wi-Fi band and topology, workload mix, duration, request/response sizes, RPS, throughput, p50/p95/p99 latency, error rate, retries, queue depth, CPU, memory, temperature, battery drain, network loss and recovery time. Repeat tests and report confidence intervals. Do not publish a universal device count or visitor guarantee.

## 10. Explicit non-goals for the first mesh release

- No Android OS-level distributed memory or CPU pooling.
- No transparent database replication.
- No arbitrary workload migration while executing.
- No public Internet exposure of worker management ports.
- No automatic trust based on LAN discovery.
- No claim that adding phones always improves throughput.
- No production readiness claim without physical-device, security and fault-injection acceptance.
