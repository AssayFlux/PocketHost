# PocketHost Product & Engineering Roadmap

**Roadmap status:** living plan. Planned work is not implemented until source and acceptance evidence are recorded in [PHASE_0_STATUS.md](PHASE_0_STATUS.md).

## Destination

Affordable phone-first hosting that is transparent about capacity, automatically protects the device, offers a smooth static website experience, and provides carefully bounded backend, operations and payment capabilities.

The phone is the website origin. No Termux, root, mandatory VPS or mandatory remote control plane. Android 8+ and low-cost 4 GB-class hardware are core constraints. See [NORTH_STAR.md](NORTH_STAR.md).

## Current implementation checkpoint — 2026-11-15

The following foundation upgrades are now present in source: the Activity Result request-code crash correction, five-destination card-first Android navigation, grouped Settings, collapsed Learn guides, and encrypted full-app backup/restore for the active website plus supported Python/Node roots and hostname routes. The latest quality CI passed Android source compilation, and Android integration run 43720035704 produced verified ARM64, ARMv7 and x86_64 debug APKs (artifact 14164002620).

These changes do **not** close the remaining P0 gates: OS-backed Python/Node isolation and enforceable quotas; remote control-plane deployment and real-device pairing if that feature is included; payment-provider integration; independent security review; and physical Android 8/15 lifecycle, public HTTPS, thermal and battery acceptance. Release signing remains owner-controlled. Custom static roots outside the main website directory are not yet included in portable full backups.

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

**Implementation update:** a first admission limiter applies the calibrated connection ceiling and 1–100% profile in Rust; excess accepted sockets receive HTTP 503 with Retry-After. The Android app and TypeScript console support manual profiles, custom timed schedules and the 30%-for-180-minutes workflow with automatic expiry/restore. Android CPU load, memory, storage, battery and thermal signals drive NORMAL, CONSTRAINED, PROTECTIVE, RECOVERY and EMERGENCY admission states; protective/emergency states suspend optional Python/Node runtimes while preserving the Rust static origin. This remains a device-side first governor: it lacks Rust-native rate limiting, per-site fairness, bounded waiting queues and graceful in-flight drain.

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

## Stage 6 — Conventional tamper-evident audit

**Priority: P1**

1. Append-only structured audit events.
2. Cryptographic hash chain and verification tooling.
3. Signed periodic checkpoints using Android Keystore or remote KMS.
4. Offline checkpoint queue and recovery.
5. Provide local verification, signed checkpoint export and controlled retention.

**Gate:** detect tampering, reordering and deletion; verify signatures and checkpoint continuity; review privacy and recovery.

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


## Current repository evidence snapshot — 2026-11-15

This snapshot is anchored to main commit `671f3f731f4bf093449a95e285327115f41bb75b`. Re-check the linked workflow runs before using this document as a release approval; statuses can change after this snapshot.

### Merged implementation

- PR [#16 — interactive Learn section and card-first navigation](https://github.com/AssayFlux/PocketHost/pull/16) is merged. The app/console include the Learn experience and an initial measured-capacity visualization. The chart is based on recorded static benchmark samples; it is not synthetic live telemetry.
- PR [#17 — Owner and Operator host accounts](https://github.com/AssayFlux/PocketHost/pull/17) is merged. The source includes an encrypted app-private account registry, password verifiers, Owner/Operator authorization, account revocation and console sign-in/bootstrap flows. The PR describes instrumentation coverage, but this snapshot does not assert that the latest main-branch emulator workflow has passed.
- The app has a Learn destination and documentation at [LEARN_GUIDE.md](LEARN_GUIDE.md). The UI remains an initial implementation requiring responsive and device acceptance.

### Exact CI evidence at review time

- Main commit: `671f3f731f4bf093449a95e285327115f41bb75b`.
- [Quality workflow 43699334752](https://github.com/AssayFlux/PocketHost/actions/runs/43699334752): completed successfully. Its six source-quality jobs include Rust, control plane, Python, Node, TypeScript console and Android source compilation.
- [Android integration workflow 43699334785](https://github.com/AssayFlux/PocketHost/actions/runs/43699334785): pending when inspected. Do not infer APK assembly or emulator instrumentation success from the quality workflow.

### Remaining release gates — not completed by these merges

1. **Android acceptance:** finish integration workflow; verify APK packaging/signatures/ABI contents; pass API 26/33/35 instrumentation on the exact release SHA; test real ARMv7/ARM64 phones, foreground service, Doze, reboot, battery/thermal behavior, keyboard, accessibility, landscape and responsive layouts.
2. **Native authentication polish:** Android BiometricPrompt/Keystore unlock, secure Owner recovery, complete native login/logout/expired/revoked-session UX and end-to-end instrumentation remain unverified or incomplete. Console sign-in and API role enforcement do not by themselves prove native biometric login.
3. **Runtime isolation:** Python and Node execute under the Pocket Host app UID. The Node process and V8 heap limit are not a sandbox. Keep optional runtimes restricted to trusted code until OS-enforced filesystem/network isolation and enforceable resource quotas pass adversarial review.
4. **Cloudflare:** configure/reuse the owner's existing named tunnel on a physical device and verify the Public Hostname serves the intended site over HTTPS from an external network. The public hostname must never target the loopback management API.
5. **Capacity:** current benchmark and admission controls are first implementations. Complete representative cached/uncached and payload-size workloads, Python/Node trusted workload measurements, throughput and p50/p95/p99, Rust-native rate/fairness/bounded-queue controls, graceful drain, long-duration thermal tests and schedule restart/expiry acceptance.
6. **Operations:** extend backup beyond static-site scope; test restore and interruption; complete request-level redacted logs, durable/tamper-evident audit verification, storage-pressure behavior and reproducible daily reports.
7. **Optional remote management:** deploy the control plane on a trusted host, pair the real phone, then test desired configuration, leased commands, acknowledgements, revocation and recovery. Source code alone is not deployment evidence.
8. **Payments:** no live payment processing is implemented/approved. If enabled for launch, select a PSP and implement hosted/tokenized checkout, signed/replay-protected webhooks, idempotent durable ledger, reconciliation, encrypted backups and qualified security review. Never store PAN/CVV/PIN.
9. **Release:** configure production signing, verify upgrade paths and checksums, review dependencies/secrets and resolve all critical/high security findings.

### Product boundaries

- Static hosting remains the primary independent feature; optional runtimes, payments and remote management must not be prerequisites.
- Android foreground services improve continuity but cannot guarantee 24/7 uptime or override OEM/OS battery policies.
- Cloudflare Tunnel connectivity is not visitor authentication; use Cloudflare Access for private sites.
- Do not claim universal visitor counts, complete sandboxing, payment compliance, deployed remote control, production readiness or a signed release without the corresponding evidence.

**Release decision:** integrated development build; not production approved. This status changes only when the relevant code, tests, device/network evidence and security review are recorded against the exact release commit.
