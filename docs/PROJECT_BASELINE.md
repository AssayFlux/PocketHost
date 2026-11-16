# AssayFlux Pocket Host — Project Baseline

**Baseline date:** 2026-11-16 · **companion source integration:** `bd227b0e5564ad25c88064cfc36796469390b773`  
**Status:** integrated development software; not production approved.

This is the canonical current baseline. Superseded progress snapshots belong in Git history, not active project documentation.

## Product contract

Native Android phone-hosted website origin; Android 8/API 26+, ARMv7/ARM64; no Termux or root; Rust public HTTP origin; static hosting independent of optional runtimes and remote services; reuse existing Cloudflare Tunnel; never expose management API; Python/Node trusted owner-installed code only until a genuine security boundary is proven.

## Current architecture and limits

| Layer | Current implementation | Remaining limit |
|---|---|---|
| Android | Kotlin/Compose, foreground service | Physical/OEM acceptance |
| HTTP | Rust/Tokio/Hyper, static serving, host routing, runtime proxy | Full fairness/fault acceptance |
| Python | Chaquopy CPython 3.11 / WSGI in companion package | Build/API-matrix verification pending; trusted code only |
| Node | Node.js mobile 24.22.1, companion `:node` process | Shares companion UID; build/API-matrix verification pending |
| Companion | Separate package/UID, versioned signature Binder IPC, bounded project transfer, Python/Node source integration | Build, API 26/33/35 and lifecycle verification pending |
| Management | Authenticated loopback API, TypeScript/React | External deployment not proven |
| Edge | cloudflared Android connector | Existing tunnel/public HTTPS not verified |
| Backup | Encrypted backup/restore | Full interruption/rollback/storage-pressure matrix open |
| Capacity | Rust admission controls, initial static GET benchmark | Representative workload/fairness evidence incomplete |

## Weighted readiness: 37%

This is the last verified weighted estimate from before the companion runtime migration source changes. It is retained at 37% until the new companion build and instrumentation evidence are available. It is a judgement-based estimate against six release gates, not code volume, test coverage or a claim that 37% of features are usable.

| Gate | Weight | Estimated completion | Contribution |
|---|---:|---:|---:|
| Companion runtime migration | 25 | 15 | 3.75 |
| Isolation and adversarial security | 20 | 25 | 5.00 |
| Rust governor and Capacity Lab | 15 | 60 | 9.00 |
| Backup, operations and Settings | 15 | 65 | 9.75 |
| Full API matrix, CI and APK evidence | 15 | 55 | 8.25 |
| Signing, external HTTPS and independent review | 10 | 15 | 1.50 |
| **Total** | **100** | | **37.25 → 37%** |

## Gate 1 — Companion runtime migration (25%)

Present in source: CPython/Chaquopy and Node/V8 are packaged in the companion; host-side Binder client negotiates protocol version 1; signature permission and caller package/signature checks protect control calls; bounded ZIP/file-descriptor transfer extracts into companion-owned storage; Python and Node lifecycle/status/metrics operations route through the companion; Rust remains the loopback reverse-proxy front door.

Required: successful native/Chaquopy build; API 26/33/35 runtime tests; robust worker process-death/rebind/update rollback; end-to-end Rust upstream tests; prove static-origin continuity under runtime failure. The separate UID protects host-private files but all companion projects still share one UID, and network egress is not yet enforced.

Exit: Python WSGI and Node HTTP end-to-end through Rust on API 26/33/35; host-private file denial, transfer bounds, lifecycle recovery and documented limitations demonstrated. Cross-project isolation and egress enforcement remain security gates.

## Gate 2 — Isolation and adversarial security (20%)

Present: runtime trust acknowledgement, canonical project-root and archive-path checks, authenticated management API, signature-protected companion service, host-vs-companion UID separation, and Python loopback-only binding. These controls do **not** isolate projects from one another.

Verified design gaps: Python multi-site projects share a process/interpreter; all Python/Node projects share one companion UID and files directory; the companion has `INTERNET`; ordinary Node TCP listener pinning does not block outbound TCP/UDP/raw sockets, DNS bypasses or loopback management access. The Android isolated-process probe cannot use the existing loopback TCP architecture.

Required: a per-project OS identity/private storage boundary; brokered inbound sockets from Rust; OS-enforced outbound denial; adversarial tests for cross-project file/process access, IPv4/IPv6 TCP/UDP/raw sockets, DNS, rebinding, loopback management, private/link-local/metadata targets, archive attacks, resource exhaustion and restart/reuse. Test API 26/33/35 and obtain independent security review.

Exit: two projects cannot read or interfere with one another; project code cannot initiate outbound connections or reach management/private targets; brokered inbound hosting still works; the OS boundary is demonstrated by adversarial tests. A process boundary or path validation alone is not sandboxing.

## Gate 3 — Rust governor and Capacity Lab (15%)

Present: bounded connection admission/queue, per-client rate controls, per-site concurrency caps, overload responses, drain logic and initial static GET sweep with thermal/memory aborts.

Required: prove fair scheduling and static-origin reserve; test saturation, cancellation, slow clients, upstream failure and drain; benchmark cached/uncached assets, payload sizes and optional trusted runtimes; record throughput, p50/p95/p99, errors, CPU/PSS, temperature/battery and queue pressure; repeat and invalidate stale benchmarks.

Exit: repeatable representative safe-envelope report; no universal visitor-count claim; static origin survives overload.

## Gate 4 — Backup, operations and Settings (15%)

Present: encrypted backup/restore, selected project/configuration coverage, bounded logs/metrics, device sampler/report foundation; Dashboard/Sites/Activity/Learn screen decomposition.

Required: restore fault matrix for wrong passphrase, corruption, interruption, low storage, custom roots and rollback; prove safe staging/recovery; secret redaction and bounded retention; durable reports; Settings extraction and responsive/accessibility/screenshot review.

Exit: restore cannot destroy last known-good site; diagnostics redact secrets; Settings acceptance passes.

## Gate 5 — Full CI matrix and APK evidence (15%)

Present: quality workflow 43789396734 passed six jobs; Android workflow 43789396568 API 26 passed 31 tests with zero failures; APK packaging and cloudflared build passed.

Required: API 26/33/35 on same candidate SHA; host/companion ABI packaging; reports/screenshots/provenance/checksums; download and validate artifacts.

Exit: all jobs green on one exact SHA and artifacts parse/signature checks pass.

## Gate 6 — Release preparation (10%)

Present: debug APK packaging and signing documentation.

Required: owner-controlled signing secrets; signed host/companion APKs and checksums; external HTTPS through existing Cloudflare Tunnel; public hostname maps only to Rust listener; management API unreachable publicly; physical-device acceptance; independent security review and disposition.

Exit: owner approval, signed artifacts, external HTTPS/device evidence and independent review.

## CI snapshot

- Quality [43789396734](https://github.com/AssayFlux/PocketHost/actions/runs/43789396734): six jobs passed.
- Android [43789396568](https://github.com/AssayFlux/PocketHost/actions/runs/43789396568): API 26 31/31 passed; API 33 in progress and API 35 queued at last observation.
- Prior APK artifact download was truncated and is not a valid deliverable.

## Explicit non-claims

No verified companion runtime build yet for this source snapshot; no per-project sandbox, completed egress enforcement, hard CPU quota, representative sustained-capacity certification, fully fault-hardened backup, external HTTPS verification, signed production release, independent security approval or physical-device certification.

## Baseline maintenance

Any code change updates tests, exact-SHA CI, this gate assessment, README and active docs. Documentation-only changes do not increase implementation readiness. Keep superseded snapshots in Git history only.
