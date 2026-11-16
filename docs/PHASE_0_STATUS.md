# Pocket Host — Current Implementation Status

**Baseline:** 2026-11-16 · companion source integration `bd227b0e5564ad25c88064cfc36796469390b773`  
**Release status:** integrated development software; not production approved.

## Android application

- Kotlin/Jetpack Compose; minimum SDK API 26; configured ARMv7, ARM64 and x86_64 targets.
- Foreground hosting service and notification.
- Dashboard, Sites, Activity and Learn extracted into dedicated screens; Settings and remaining orchestration still need decomposition.
- Site folder/ZIP import, staged deployment, retained releases, rollback and file operations.
- Owner/Operator account and local authenticated management foundations.
- Device health sampling, operational logs, metrics and diagnostics.

## Rust website origin

- Tokio/Hyper server with hostname routing.
- Static GET/HEAD, streaming, MIME handling and byte ranges.
- Canonical path validation and traversal protection.
- Runtime upstream reverse proxy.
- Bounded connection admission/queue, per-client rate controls, per-site concurrency limits, overload responses and graceful drain code are present.
- Fairness under contention, fault injection and sustained-load evidence remain incomplete.

## Python and Node runtimes

- Python: Chaquopy CPython 3.11 / WSGI packaged in the separate-UID companion.
- Node: pinned Node.js mobile 24.22.1, JNI bridge and dedicated `:node` process inside the companion package.
- Host-to-companion Binder protocol version 1, signature permission/caller checks and bounded ZIP/file-descriptor project transfer are integrated in source.
- Project data is extracted into companion-owned storage; common credential paths are excluded/rejected.
- Both runtimes share the companion UID. Per-site filesystem isolation and enforceable network egress remain incomplete; trusted owner-installed code only.
- Build and migrated API 26/33/35 instrumentation evidence are pending.

## Separate-UID companion

- Separate Android application package/UID with foreground control service and Node `:node` service.
- Chaquopy Python 3.11, Node 24 native libraries/JNI bridge and metrics preload moved into the companion module.
- Versioned signature-protected Binder IPC; caller package and signing identity are checked.
- Bounded file-descriptor project transfer, staging extraction, path traversal checks and worker-owned project storage integrated in source.
- Host runtime facades and tests now target companion execution; build/API-matrix verification is pending.
- Companion UID protects host-private files, but projects share the companion UID and network egress is not enforced.

## Isolation and security

Present: runtime trust acknowledgement, canonical project-root checks, symlink escape rejection, authenticated local management API and signature-protected companion service.

Open: per-project filesystem isolation, enforceable egress including raw sockets/DNS rebinding, SSRF/private-network/metadata denial, cross-project and exhaustion tests, independent review.

## Capacity and operations

- Device sampler captures API/ABI/cores, CPU estimate, memory/PSS/heaps, storage, battery/temperature, thermal state and network transport.
- Bounded operational logs and aggregate metrics; daily/hourly report foundation.
- Initial loopback static GET Capacity Lab with concurrency sweep, sample/error counts, p95 latency and low-memory/severe-thermal abort.
- Rust admission governor includes bounded admission/queue/rate/site controls and drain logic.
- Missing: fairness/stress acceptance, cached/uncached and payload matrix, runtime workload class, p50/p99 and throughput/resource correlation, repeatability and benchmark invalidation.

## Backup and UI

- Passphrase-encrypted backup/restore covers active website, configured project roots, hostname routes and selected non-secret settings; device-bound credentials excluded.
- Restore interruption/corruption/low-storage/rollback fault matrix remains open.
- Five destinations: Dashboard, Sites, Activity, Learn and Settings. Settings extraction and responsive/accessibility visual acceptance remain open.
- Dashboard screenshot instrumentation passed on API 26 in the latest integration run; visual review is distinct from screenshot generation.

## CI evidence

- Quality [43789396734](https://github.com/AssayFlux/PocketHost/actions/runs/43789396734): six jobs passed.
- Android [43789396568](https://github.com/AssayFlux/PocketHost/actions/runs/43789396568): API 26 passed 31 tests with zero failures; APK packaging and cloudflared build passed. API 33 was in progress and API 35 queued at last check.
- Re-run the full matrix after source changes; only exact-SHA results count.
- Previous artifact retrieval returned a truncated ZIP and is not a valid deliverable.

## External acceptance

Existing Cloudflare Tunnel HTTPS, physical device lifecycle/thermal/battery/storage tests, owner-configured signing secrets and independent security review are not yet evidenced.

**Release decision:** not production-ready, not fully sandboxed, not signed and not externally verified. Companion runtime source integration is present but not yet CI-verified; Python/Node remain trusted-code-only.
