# AssayFlux PocketHost — Master Engineering Plan

**Product:** AssayFlux PocketHost  
**Steward:** AssayFlux  
**Developer identity:** Designed by AssayFlux  
**Package:** `com.assayflux.pockethost`

## 1. Product definition

PocketHost is a standalone native Android application that hosts websites from the phone itself. It targets Android 8.0 (API 26)+, a 4 GB RAM baseline and ARMv7/ARM64 phones, without Termux or root.

The phone is the website origin. Rust is the central HTTP engine. Kotlin owns Android lifecycle and native UI. TypeScript/React is the management console. Cloudflare Tunnel provides outbound public connectivity. Python WSGI and Node.js are optional trusted application runtimes. An independently hosted Rust/PostgreSQL control plane is optional remote management only.

The owner already has a domain and Cloudflare Tunnel. Reuse the existing named tunnel; verify the Public Hostname maps to the Rust website listener. Never create a duplicate or route public traffic to the management API.

## 2. Product modes and user journeys

### Simple Mode
- Import a static site from folder/ZIP.
- Validate and stage before activation.
- Preview locally; start/stop with clear service state.
- Use the existing tunnel and verify public HTTPS externally.
- Update, rollback and inspect a daily summary.

### Advanced Mode
- Manage multiple sites/hostnames and routing.
- Configure trusted Python WSGI and Node apps.
- Set per-site budgets, concurrency, rate and timeouts.
- View hardware health, capacity benchmarks, graphs, logs and alerts.
- Configure backup, retention, schedules and remote management.
- Review payment records only when a PSP integration has been deliberately enabled.

## 3. Architecture and responsibility boundaries

| Layer | Technology | Owns | Must not own |
|---|---|---|---|
| Android app | Kotlin + Jetpack Compose | UI, SAF, permissions, lifecycle, notifications, device telemetry | HTTP request routing logic duplicated from Rust |
| Hosting service | Android foreground service | User-visible host lifecycle, runtime/tunnel supervision | Unbounded background work |
| Core engine | Rust, Tokio, Hyper | HTTP ingress, host routing, static serving, reverse proxy, request admission, metrics | Android permission/UI concerns |
| JNI | Kotlin/C++/Rust boundary | Versioned coarse-grained start/stop/config/status | Per-request callbacks |
| Local console | TypeScript, React, Vite | Owner-facing local management | Secret-bearing unauthenticated public endpoints |
| Python | CPython 3.11 / Chaquopy in companion UID | WSGI applications with host routing; trusted code only | Arbitrary untrusted multi-tenant code |
| Node.js | Pinned Node 24 mobile SDK in companion UID | One active embedded app in companion `:node` process | Desktop process manager assumptions |
| Tunnel | cloudflared | Outbound Cloudflare connection | Visitor authorization or overload control |
| Remote control plane | Rust/Axum + PostgreSQL | Optional remote device registry, command queue, roles and audit | Serving the user's website |
| Resource Governor (planned) | Rust policy engine + Android telemetry | Safe admission budgets, throttling, queues and recovery | Claiming to reserve CPU or override Android |
| Payment adapter (planned) | Provider-neutral service boundary | PSP sessions, verified events, reconciliation and ledger | Raw card processing/storage |
| Audit service (planned) | Append-only hash-chained records and signed checkpoints | Integrity and accountability | Treating cryptographic integrity as proof that source events were truthful |

TypeScript is the authored console language; browser bundles emit JavaScript as required by browsers.

## 4. Traffic and control paths

### Website traffic
```text
Visitor → Cloudflare edge → existing named tunnel → Rust listener on phone
        → hostname/site router → static files OR loopback Python/Node upstream
        → response through tunnel
```

Rust is the only intended public ingress. The Cloudflare Public Hostname must target the Rust website port, never the loopback management API. Tunnel connectivity is not visitor authentication; use Cloudflare Access for private sites.

### Local management
```text
PocketHost Console → pairing challenge → owner approval in Android app
                  → expiring bearer session → loopback Management API
                  → Android service / Rust engine / runtime supervisors
```

### Optional remote management
```text
Remote console ↔ HTTPS control plane ↔ PostgreSQL
                         ↑ outbound polling / heartbeat
                    Android phone agent
```

The control plane is not required for local hosting or Cloudflare connectivity. Its source tree is not proof of deployment.

## 5. Current runtime contracts

### Static website
Rust serves GET/HEAD, streams files, supports ranges/MIME, canonicalizes paths, validates hosts and bounds connections. Static hosting must remain available when optional runtimes fail or are reconfigured.

### Python WSGI
- CPython 3.11 via Chaquopy is packaged in `:runtime-worker`, not the host APK.
- Host transfers a bounded project archive over signature-protected, versioned Binder IPC; companion extracts to its own private project directory.
- Root `wsgi.py` exports callable `application`; multi-host routing supports up to 100 registered hostnames.
- Bounded 2–16 worker threads; no automatic pip install, ASGI/WebSockets or hard CPU/RAM quota.
- Executes under companion UID, distinct from host UID; projects still share that UID and outbound egress is not yet enforced. Trusted code only; not a complete sandbox.
- See [Python Runtime](PYTHON_RUNTIME.md) and [Runtime Companion](RUNTIME_COMPANION.md).

### Node.js
- Pinned `fogtape/nodejs-mobile` `v24.22.1-0`, upstream Node 24.22.1, packaged in companion APK.
- JNI `node::Start`; dedicated companion `:node` process, same companion UID as Python.
- One active app; dependencies prebundled; no normal npm install/PM2/cluster/desktop child process.
- TCP loopback preload constrains listener exposure but is not a complete network sandbox.
- V8 old-space 96–512 MiB is not a hard RSS/CPU quota.
- Executes under companion UID, distinct from host UID; cross-project access and outbound egress remain unresolved. Trusted code only.
- See [Node Runtime](NODE_RUNTIME.md) and [Runtime Companion](RUNTIME_COMPANION.md).

## 5.1 Security execution-cell target (Gate 2 — policy probe implemented; production migration open)

The current companion process is a trusted broker only. User project code must move into one Android `isolatedProcess=true` service per project, with unique process names/UIDs. The broker passes a per-project directory FD, a separate scratch/runtime FD and a host-created Unix-domain listener FD. The host app retains the private socket path for Rust's Tokio `UnixStream` upstream. A native TSYNC seccomp-BPF policy and an isolated-UID probe service now exist in the runtime-worker source. The probe denies socket/socketpair/connect/bind and io_uring socket paths, while permitting listen/accept only on the transferred Unix-domain listener FD and checking `SO_PEERCRED`. Its instrumentation test must still pass on API 26/33/35. This is not yet the production cell: Python and Node still run in the companion UID. The production cell must install the filter before importing project code, and filter failure must prevent startup. Rust remains the only public ingress.

The first acceptance tests must prove that the directory FD allows project-local reads/writes while sibling and host paths remain inaccessible, and that a listener FD can accept multiple visitor connections from an isolated process. Do not migrate Chaquopy or Node until these primitives pass API 26/33/35. See [Security Gate 2 Execution Plan](SECURITY_GATE_2_EXECUTION_PLAN.md).

## 6. Adaptive hardware and capacity architecture (partially implemented)

### Telemetry
**Implemented first pass:** Android records versioned low-frequency snapshots of manufacturer/model, API/ABIs/CPU cores, aggregate CPU utilization sampled from /proc/stat, RAM/PSS/heaps, storage, battery/temperature, thermal state and network transport. Samples are bounded in a two-file history and are also read live while hosting is idle. Rust contributes request/byte totals, active connections and rejected connections. This is not yet a complete RPS/queue/latency/per-site telemetry pipeline. Every sample includes timestamp and schema version; unavailable values remain null.

### Capacity Lab
**Implemented first pass:** a bounded loopback static GET sweep tests concurrency levels 1, 2, 4, 8, 12 and 16, records sample/error counts, elapsed time and p95 latency, and stores the last result with the calibrated ceiling. It stops escalating on errors, p95 above 750 ms, Android low-memory or severe thermal status. The provisional ceiling is four concurrent connections until calibration. It does not yet test response-size/cache classes, Python/Node, throughput, p50/p99 or sustained multi-hour behavior. Expand to controlled workloads for static cached/uncached, response sizes, concurrency and optional trusted runtimes. Record throughput, p50/p95/p99 latency, errors, CPU, memory, thermal and battery impact. Stop on safety thresholds. Persist benchmark version and device/app/workload metadata.

Display separately:
- Observed peak under a named test.
- Sustainable safe capacity under thermal, memory and latency bounds.
- Current admission budget after live health reductions and owner-selected percentage.

Do not convert this into an unconditional visitor-count guarantee.

### Resource Governor
**Implemented first pass:** Android derives NORMAL, CONSTRAINED, PROTECTIVE, RECOVERY and EMERGENCY from fresh CPU load, memory, storage, battery and thermal signals. It reduces the effective connection ceiling and repeatedly suspends optional Python/Node runtimes in PROTECTIVE/EMERGENCY while preserving the Rust static origin. Rust enforces a dynamic 1–16 concurrent-connection cap and returns HTTP 503 with Retry-After when the cap is reached. **Still required:** move policy decisions into Rust, add request-rate limits, bounded waiting queues, per-site/per-route budgets, fair scheduling, circuit breakers, bounded drain, cooldown and independently validated hysteresis. Keep emergency reserve for management and log persistence. Unknown critical telemetry reduces admission rather than assuming spare capacity.

### Schedules
Capacity percentage means a fraction of calibrated safe capacity. The app supports 1–100% profiles and timed schedules from 1–1440 minutes, including 30% for three hours, with expiry and restoration of the previous profile. The provisional ceiling is four until the first successful benchmark. This is not a CPU-utilization control or an OS reservation.

Full requirements: [Adaptive Capacity, Security, Payments and Operations](ADAPTIVE_CAPACITY_SECURITY_OPERATIONS.md).

## 7. Security architecture and release boundaries

- Separate public website traffic from local/remote administration.
- Pairing and session authorization for local management; route-level auth tests required.
- Protect credentials with Android Keystore or a suitable remote secrets manager.
- Validate imported archives, paths, sizes, counts and activation boundaries.
- The companion UID isolates runtime code from the host app UID only. Python multi-site projects share one interpreter/process; Python and Node projects share the companion UID and private filesystem.
- Python WSGI is hard-pinned to loopback; Node's preload only constrains ordinary TCP listener binds. Neither is outbound-egress enforcement.
- The companion has `INTERNET`; raw sockets, DNS rebinding, private/link-local/metadata destinations and loopback management reachability are not OS-blocked per project.
- Keep Python/Node restricted to owner-trusted code. Do not execute visitor-submitted or third-party untrusted code.
- The target architecture must provide a distinct OS identity and private storage per project, with network egress denied by the OS and inbound connections brokered from Rust. See [Security Isolation Design](SECURITY_ISOLATION_DESIGN.md).
- Do not expose LAN mode without an explicit owner decision and clear visitor-auth limitations.
- Use a payment provider's hosted/tokenized checkout; never store PAN/CVV/PIN.
- Verify raw-body webhook signatures, timestamps, idempotency and replay; reconcile provider events.
- Store an encrypted event ledger with provider references and integer minor-unit amounts.
- Keep append-only hash-chained audit records and signed checkpoints with local verification.
- No PCI/security certification claim without qualified independent assessment.

## 8. Logs, reporting and recovery (partially implemented; expansion planned)

**Implemented first pass:** redacted operational event logs rotate within bounded app-private files. Daily snapshots store aggregate static-engine requests/bytes and retained redacted events, refresh hourly, archive the previous day at rollover, retain up to 90 reports and export the latest JSON report. Device telemetry history is separately bounded. **Still required:** complete uptime, latency, network throughput, tunnel, deployment, security and financial reconciliation reporting; storage reserve policies; tamper-evident audit; encrypted report backup/restore.

## 9. Website performance principles

- Correct ETag/Last-Modified, conditional GET, range and MIME handling.
- Stream large files and bound connection pools/queues.
- Compression only where benchmarked beneficial.
- Keep-alive and timeouts; no unbounded buffering.
- Cache immutable fingerprinted assets safely.
- Health/readiness endpoints separate from public site routes.
- Site-level failure containment, staged activation and rollback.
- WebSocket/streaming only with bounded sessions and tested resource accounting.
- CDN guidance for large media; phone storage is not durable object storage.
- Claim HTTP/2/HTTP/3 only after end-to-end evidence.

## 10. Repository boundaries

- `app/`: Android UI, lifecycle, management API, import, backup, runtime/tunnel supervisors.
- `rust-engine/`: on-device HTTP engine and future Resource Governor.
- `web-console/`: TypeScript/React local console.
- `control-plane/`: optional Rust/PostgreSQL remote management.
- `tests/`: host-side Python/Node smoke tests and future integration tests.
- `scripts/`: native build helpers.
- `.github/workflows/`: quality, Android integration, signed release and optional control-plane deployment.
- `docs/`: canonical product, architecture, roadmap, status, security and acceptance records.

## 11. Engineering execution order

Follow [ROADMAP.md](ROADMAP.md). Do not start payment acceptance before the security foundations. Do not enable untrusted code before runtime isolation. Do not claim adaptive capacity before calibration and governor acceptance. Static hosting must remain independently usable throughout.

## 12. Validation and source of truth

Validation sequence: unit/static checks → Android source compile → ABI APK packaging → API 26/33/35 emulator instrumentation → physical ARMv7/ARM64 devices → external Cloudflare hostname checks → security/fault/load tests → signed release and upgrade verification.

The latest exact status and evidence live in [PHASE_0_STATUS.md](PHASE_0_STATUS.md). Release definition lives in [COMPLETION_MILESTONE.md](COMPLETION_MILESTONE.md). [DEVELOPMENT_CONTEXT.md](DEVELOPMENT_CONTEXT.md) is the mandatory handoff for every future coding session.


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
