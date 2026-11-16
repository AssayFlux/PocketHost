# Pocket Host — Current Implementation Status

**Baseline:** 2026-11-16 · `main` reviewed at `a8988f5068f4a65206b89451e8204b51b86abc55`  
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

- Python: Chaquopy CPython 3.11 with WSGI integration.
- Node: pinned Node.js mobile 24.22.1 integration and dedicated Android process/service.
- Both remain under the host application UID. Canonical path checks and a dedicated process are not a sandbox.
- No proven worker egress restrictions or hard CPU/memory quotas.
- Trusted owner-installed code only.

## Separate-UID companion

- Separate Android application package/UID.
- Signature-level permission protecting exported Binder service.
- INTERNET permission and capability probe for host-private-file denial and bidirectional loopback.
- API 26 capability test passed.
- Does not load Chaquopy, Node/V8 or project code; no production project-transfer protocol, supervisor, recovery contract or Rust integration.

## Isolation and security

Present: runtime trust acknowledgement, canonical project-root checks, symlink escape rejection, authenticated local management API and signature-protected companion service.

Open: worker-owned storage, scoped project capabilities, enforceable egress including raw sockets/DNS rebinding, SSRF/private-network/metadata denial, cross-project and exhaustion tests, independent review.

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

**Release decision:** not production-ready, not sandboxed, not signed and not externally verified. Static hosting is the strongest foundation; Python/Node remain trusted-code-only.
