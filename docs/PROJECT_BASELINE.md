# AssayFlux Pocket Host — Project Baseline

**Baseline date:** 2026-11-16 · **reviewed source:** `a8988f5068f4a65206b89451e8204b51b86abc55`  
**Status:** integrated development software; not production approved.

This is the canonical current baseline. Superseded progress snapshots belong in Git history, not active project documentation.

## Product contract

Native Android phone-hosted website origin; Android 8/API 26+, ARMv7/ARM64; no Termux or root; Rust public HTTP origin; static hosting independent of optional runtimes and remote services; reuse existing Cloudflare Tunnel; never expose management API; Python/Node trusted owner-installed code only until a genuine security boundary is proven.

## Current architecture and limits

| Layer | Current implementation | Remaining limit |
|---|---|---|
| Android | Kotlin/Compose, foreground service | Physical/OEM acceptance |
| HTTP | Rust/Tokio/Hyper, static serving, host routing, runtime proxy | Full fairness/fault acceptance |
| Python | Chaquopy CPython 3.11, WSGI | Host app UID |
| Node | Node.js mobile 24.22.1, dedicated process | Same host app UID |
| Companion | Separate package/UID, signature Binder probe | Does not host runtimes or receive project payloads |
| Management | Authenticated loopback API, TypeScript/React | External deployment not proven |
| Edge | cloudflared Android connector | Existing tunnel/public HTTPS not verified |
| Backup | Encrypted backup/restore | Full interruption/rollback/storage-pressure matrix open |
| Capacity | Rust admission controls, initial static GET benchmark | Representative workload/fairness evidence incomplete |

## Weighted readiness: 37%

This is a judgement-based estimate against six release gates, not code volume, test coverage or a claim that 37% of features are usable.

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

Present: separate-UID companion APK, signature-protected Binder probe and API 26 capability test.

Required: move CPython/Chaquopy and Node/V8 into companion UID; authenticated/versioned IPC; bounded project transfer and worker-owned storage; lifecycle/readiness/crash recovery/update rollback; Rust upstream routing; static-origin continuity under worker failure.

Exit: Python WSGI and Node HTTP end-to-end through Rust on API 26/33/35; cross-project and host-secret access denied.

## Gate 2 — Isolation and adversarial security (20%)

Present: runtime trust acknowledgement, canonical project-root and symlink checks, authenticated management API, signature-protected companion service.

Required: enforce filesystem capabilities and network egress covering raw sockets, DNS rebinding, management loopback, private/link-local/metadata addresses; test archive traversal, decompression bombs, SSRF, cross-project and exhaustion; document Android resource-control limits; obtain independent security review.

Exit: adversarial tests demonstrate the actual boundary. A process boundary or path validation alone is not sandboxing.

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

No companion-hosted runtimes, arbitrary-code sandbox, completed egress enforcement, hard CPU quota, representative sustained-capacity certification, fully fault-hardened backup, external HTTPS verification, signed production release, independent security approval or physical-device certification.

## Baseline maintenance

Any code change updates tests, exact-SHA CI, this gate assessment, README and active docs. Documentation-only changes do not increase implementation readiness. Keep superseded snapshots in Git history only.
