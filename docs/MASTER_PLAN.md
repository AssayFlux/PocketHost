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
| Python | CPython 3.11 / Chaquopy | One WSGI application | Arbitrary untrusted multi-tenant code |
| Node.js | Pinned Node 24 mobile SDK | One active embedded app in `:node` process | Desktop process manager assumptions |
| Tunnel | cloudflared | Outbound Cloudflare connection | Visitor authorization or overload control |
| Remote control plane | Rust/Axum + PostgreSQL | Optional remote device registry, command queue, roles and audit | Serving the user's website |
| Resource Governor (planned) | Rust policy engine + Android telemetry | Safe admission budgets, throttling, queues and recovery | Claiming to reserve CPU or override Android |
| Payment adapter (planned) | Provider-neutral service boundary | PSP sessions, verified events, reconciliation and ledger | Raw card processing/storage |
| Audit service (planned) | Append-only hash-chained records | Integrity and accountability | Treating blockchain as proof that events are truthful |

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
- CPython 3.11 via Chaquopy.
- Root `wsgi.py` exports callable `application`.
- One active application; bounded 2–16 worker threads.
- No automatic pip install, ASGI/WebSockets or hard CPU/RAM quota.
- Runs with PocketHost app UID: trusted code only; not a sandbox.
- See [Python Runtime](PYTHON_RUNTIME.md).

### Node.js
- Pinned `fogtape/nodejs-mobile` `v24.22.1-0`, upstream Node 24.22.1.
- JNI `node::Start`; dedicated Android `:node` process.
- One active app; dependencies prebundled; no normal npm install/PM2/cluster/desktop child process.
- TCP loopback preload is not a complete network sandbox.
- V8 old-space 96–512 MiB is not a hard RSS/CPU quota.
- Runs with PocketHost app UID: trusted code only; not a sandbox.
- See [Node Runtime](NODE_RUNTIME.md).

## 6. Adaptive hardware and capacity architecture (planned)

### Telemetry
Android collects device profile, RAM/PSS, storage, battery/charging, thermal state, CPU/core data and network changes where APIs permit. Rust contributes RPS, connection/concurrency, queue, latency, errors, bytes and per-site/runtime counters. Every sample includes timestamp, source, interval and validity; unknown values remain unknown.

### Capacity Lab
Run controlled loopback tests for static cached/uncached, response sizes, concurrency and optional trusted runtimes. Record throughput, p50/p95/p99 latency, errors, CPU, memory, thermal and battery impact. Stop on safety thresholds. Persist benchmark version and device/app/workload metadata.

Display separately:
- Observed peak under a named test.
- Sustainable safe capacity under thermal, memory and latency bounds.
- Current admission budget after live health reductions and owner-selected percentage.

Do not convert this into an unconditional visitor-count guarantee.

### Resource Governor
Rust policy state machine: NORMAL → CONSTRAINED → PROTECTIVE → RECOVERY, with EMERGENCY stop behavior. Use bounded queues, per-site/per-route limits, fair scheduling, circuit breakers, bounded drain, cooldown and hysteresis. Keep emergency reserve for management and log persistence. Unknown critical telemetry reduces admission rather than assuming spare capacity.

### Schedules
Capacity percentage means a fraction of calibrated safe capacity. Support presets and custom schedules, including Development at 30% for three hours, with expiry and restoration. It is not a CPU-utilization control or an OS reservation.

Full requirements: [Adaptive Capacity, Security, Payments and Operations](ADAPTIVE_CAPACITY_SECURITY_OPERATIONS.md).

## 7. Security architecture and release boundaries

- Separate public website traffic from local/remote administration.
- Pairing and session authorization for local management; route-level auth tests required.
- Protect credentials with Android Keystore or a suitable remote secrets manager.
- Validate imported archives, paths, sizes, counts and activation boundaries.
- Python/Node are trusted-code only until distinct UID isolation, filesystem/network confinement and enforceable quotas are proven.
- Prevent runtime access to management endpoints, payment secrets, other projects and private network targets.
- Do not expose LAN mode without an explicit owner decision and clear visitor-auth limitations.
- Use a payment provider's hosted/tokenized checkout; never store PAN/CVV/PIN.
- Verify raw-body webhook signatures, timestamps, idempotency and replay; reconcile provider events.
- Store an encrypted event ledger with provider references and integer minor-unit amounts.
- Keep append-only hash-chained audit records; optional blockchain stores only batched checkpoint hashes.
- No PCI/security certification claim without qualified independent assessment.

## 8. Logs, reporting and recovery (planned expansion)

Structured redacted logs cover access summaries, runtime lifecycle, device health, tunnel, config changes, deployments, backups, security and payment events. Apply rotation, bounded retention, storage reserve and secret redaction before persistence.

Daily reports summarize uptime, traffic, latency, errors, resource/thermal trends, restarts, tunnel state, deployments, security events and reconciled payment totals. Reports and audit records must survive app restart and support export/restore.

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
