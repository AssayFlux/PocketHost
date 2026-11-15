# PocketHost Product & Engineering Roadmap

**Roadmap status:** living plan. Planned work is not implemented until source and acceptance evidence are recorded in [PHASE_0_STATUS.md](PHASE_0_STATUS.md).

## Destination

Affordable phone-first hosting that is transparent about capacity, automatically protects the device, offers a smooth static website experience, and provides carefully bounded backend, operations and payment capabilities.

The phone is the website origin. No Termux, root, mandatory VPS or mandatory remote control plane. Android 8+ and low-cost 4 GB-class hardware are core constraints. See [NORTH_STAR.md](NORTH_STAR.md).

## Priority legend

- **P0 — release blocker:** required for a safe first public release or to avoid a dangerous product claim.
- **P1 — core product:** differentiating functionality after safe baseline.
- **P2 — expansion:** optional capability after core reliability.
- **Gate:** measurable evidence required before proceeding.

## Stage 0 — Preserve and verify the foundation

**Priority: P0**

- Kotlin/Compose native Android app and foreground hosting lifecycle.
- Rust/Tokio/Hyper website engine and hostname routing.
- TypeScript/React local console.
- Static import, staged deployment and rollback.
- Cloudflare connector and compatibility with the existing named tunnel.
- Python WSGI and Node mobile runtimes, clearly trusted-code preview until isolated.
- Local pairing/management API.
- Optional remote control-plane source separate from website hosting.

**Gate:** exact-SHA quality and Android integration workflows, API 26/33/35 emulator matrix, physical ARMv7/ARM64 checks, external HTTPS test of the existing hostname, and security review. Do not add features at the expense of the static hosting baseline.

## Stage 1 — Device intelligence and operations

**Priority: P0**

**Implementation update:** bounded rotating logs and aggregate counters already exist. A versioned Android device sampler now records model/API/ABIs/cores, sampled CPU load, RAM/PSS/heaps, storage, battery/temperature, thermal state and network transport into a two-file bounded history. A first daily rollover/hourly snapshot report is persisted (90-report retention), and the dashboard/console expose device health. This is partial: there is no complete uptime, latency, network-throughput, tunnel or financial report yet.

1. Versioned hardware/device profile.
2. CPU, RAM/PSS, storage, battery, thermal, network and runtime telemetry.
3. Rust metrics: RPS, concurrency, queue, latency percentiles, errors and bytes.
4. Bounded structured logs, redaction, rotation, retention and storage reserve.
5. Daily operations report: uptime, traffic, errors, resource events, tunnel, deployments, backups and alerts.
6. Dashboard with health states, trends, export and actionable warnings.

**Gate:** telemetry correctness and missing-value handling; no secrets in logs; rotation cannot fill storage; reports survive restart and are reproducible.

## Stage 2 — Capacity Lab and measured safe envelope

**Priority: P0**

**Implementation update:** a first bounded loopback GET sweep tests concurrency levels 1, 2, 4, 8, 12 and 16, records sample/error counts and p95 latency, aborts on Android low-memory/severe-thermal signals, and persists a conservative concurrency ceiling. It is a static-route smoke benchmark only; full workload classes, repeated statistical confidence, throughput/resource graphs and benchmark invalidation remain open.

1. Loopback benchmark workloads: static cached/uncached, response sizes, concurrency and optional trusted runtimes.
2. Record throughput, p50/p95/p99 latency, errors, CPU, memory, temperature, battery and queue behavior.
3. Abort on thermal, memory, battery, timeout or cancellation thresholds.
4. Persist test conditions, device/app versions, sample counts and benchmark validity.
5. Display observed peak, sustainable safe capacity and current safe admission budget separately.
6. Graph throughput/latency versus concurrency and resource trends; mark tested safe region and overload boundary.

**Gate:** repeatable results on target phones; no invented universal visitor count; safety abort works.

## Stage 3 — Resource Governor and capacity customization

**Priority: P0**

**Implementation update:** a first admission limiter applies the calibrated connection ceiling and 1–100% profile in Rust; excess accepted sockets receive HTTP 503 with Retry-After. The Android app and TypeScript console support manual profiles, custom timed schedules and the 30%-for-180-minutes workflow with automatic expiry/restore. Android memory, storage, battery and thermal signals drive NORMAL, CONSTRAINED, PROTECTIVE, RECOVERY and EMERGENCY admission states; protective/emergency states suspend optional Python/Node runtimes while preserving the Rust static origin. This remains a device-side first governor: it lacks Rust-native rate limiting, per-site fairness, bounded waiting queues and graceful in-flight drain.

1. Rust Resource Governor with NORMAL, CONSTRAINED, PROTECTIVE, RECOVERY and EMERGENCY states.
2. Global/per-site/per-route concurrency and rate budgets.
3. Bounded queues and controlled 429/503 responses.
4. Fair site scheduling, static asset priority, upstream circuit breakers and bounded in-flight drain.
5. Live thermal/memory/battery/storage reductions with hysteresis and cooldown.
6. Presets: Development, Balanced, Performance, Battery Saver and Custom.
7. Scheduled capacity percentage/duration, manual overrides and expiry.
8. Exact user workflow: 30% of calibrated safe capacity for three hours, then restore previous profile.

**Gate:** load/fault tests prove bounded queues and recovery; three-hour schedule persists and restores; static hosting and management/logging reserve survive overload.

## Stage 4 — Runtime security and sandboxing

**Priority: P0 before untrusted or third-party code**

1. Threat model for Python, Node, importers, filesystem, network and management.
2. Evaluate separate Android UID / isolated execution package architecture.
3. Filesystem confinement and project-specific storage.
4. Egress policy blocking management loopback, metadata, private networks and DNS-rebinding bypass.
5. OS-enforced CPU/memory/process/file/socket quotas where supported.
6. Remove unsafe execution/package installation paths; pin and scan dependencies.
7. Adversarial archive, symlink, traversal, SSRF, DNS rebinding, cross-project and resource-exhaustion tests.
8. If robust isolation cannot be implemented, maintain trusted-code-only positioning and disable public multi-tenant claims.

**Gate:** independent security review and adversarial tests. A dedicated process or V8 heap cap alone is not a sandbox.

## Stage 5 — Payment security and financial operations

**Priority: P0 only if payment features are enabled; otherwise defer**

1. Select a reputable payment service provider and hosted/tokenized checkout.
2. Provider adapter, test/live separation and least-privilege secrets.
3. Verify raw-body webhook signatures, timestamp tolerance, replay and idempotency.
4. Durable provider-event inbox and payment state machine.
5. Encrypted transaction-reference ledger; integer minor-unit amounts and ISO currency.
6. Refund/dispute events, provider reconciliation and mismatch alerts.
7. Durable encrypted backup/restore, retention and daily financial reports.
8. Admin MFA/RBAC, session revocation and auditable sensitive actions.
9. Qualified legal/accounting/PCI-scope review before accepting live payments.

**Gate:** invalid/stale/duplicate/out-of-order webhook tests, reconciliation, backup restore and independent security assessment. Never store PAN/CVV/PIN. PSP integration does not itself confer PCI compliance.

## Stage 6 — Tamper-evident audit and optional blockchain

**Priority: P1**

1. Append-only structured audit events.
2. Cryptographic hash chain and verification tooling.
3. Signed periodic checkpoints using Android Keystore or remote KMS.
4. Offline checkpoint queue and recovery.
5. Optional blockchain anchoring of checkpoint hashes only, with cost/finality indicators.

**Gate:** tamper/reordering/deletion detection and privacy review. Never publish personal data, payment data, IPs, secrets or full logs on-chain. Blockchain is not the source of truth and is not required for hosting.

## Stage 7 — Website performance and product polish

**Priority: P1**

- Static caching, validators, streaming, range and compression selected by benchmark.
- Bounded keep-alive, connection pools and timeouts.
- Multi-site fault isolation, atomic deployment and rollback.
- WebSocket/streaming only when resource-accounted and tested.
- Per-site analytics, alerts, health/readiness checks and diagnostics.
- CDN guidance for large assets.
- Modern dashboard and customization UX with safe defaults.

**Gate:** representative workload benchmarks and no regressions to static origin under optional-runtime failure.

## Stage 8 — Optional remote management and ecosystem

**Priority: P2**

- Real-device pairing, configuration sync, command idempotency, role enforcement and revocation.
- Multi-device management, team audit and alerts.
- Optional remote deployment and documented topology.
- Remote backend never becomes a prerequisite for local website hosting.

**Gate:** deployed endpoint health, real phone pairing, command/recovery tests and security review.

## Stage 9 — Release and maintenance

- Signed ABI APKs, upgrade tests, checksums and reproducible release notes.
- API 26+ and physical ARMv7/ARM64 compatibility matrix.
- Document device/OEM/network limits.
- Dependency scanning, secret scanning, pinned dependencies and vulnerability response.
- Maintain one canonical status document and decision log.

## Definition of a completed roadmap item

An item is complete only when:
1. Code exists on a named branch/commit.
2. Tests cover success, failure, abuse and recovery.
3. CI passes on the exact commit.
4. Required emulator/physical-device evidence exists.
5. Security/privacy implications are reviewed.
6. Documentation and limitations match behavior.
7. Status is updated with evidence links.

A plan, mockup, passing compile or documentation PR is not implementation evidence.
