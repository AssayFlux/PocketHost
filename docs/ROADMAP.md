# Pocket Host — Active Release Sequence

**Baseline:** 2026-11-16 · companion source integration `bd227b0e5564ad25c88064cfc36796469390b773`  
**Canonical status:** [PROJECT_BASELINE.md](PROJECT_BASELINE.md)

Only the current release sequence is retained here. Superseded snapshots belong in Git history.

## Product constraints

Native Kotlin/Compose Android app; Rust public HTTP origin; Android 8/API 26+, ARMv7/ARM64 target; no Termux or root; static hosting independent of runtimes and remote services; reuse existing Cloudflare Tunnel; never expose management API; trusted owner-installed Python/Node code only until security boundary is proven.

## Gate 1 — Companion runtime migration (P0)

Source integration now moves Chaquopy/CPython and Node/V8 into the separate-UID companion. Versioned signature-protected IPC, bounded project transfer and companion-owned storage are implemented in source. Complete and verify lifecycle supervision, crash recovery/rebind/update rollback, Rust upstream routing and static-origin continuity.

**Exit:** Python WSGI and Node HTTP work end-to-end through Rust on API 26/33/35; host-private file denial and transfer bounds are demonstrated; static origin survives worker failure. Per-project isolation and egress remain Gate 2.

## Gate 2 — Isolation and adversarial security (P0)

Enforce filesystem capabilities and network egress restrictions that cover raw sockets, DNS rebinding, loopback management, private/link-local/metadata destinations. Add archive traversal, symlink, cross-project, SSRF and exhaustion tests. Obtain independent security review.

**Exit:** tests prove the actual boundary. A separate process or path validation alone is not sandboxing.

## Gate 3 — Rust governor and Capacity Lab (P0)

Validate fair per-site scheduling, bounded queues, static-origin reserve, overload responses, cancellation, slow clients and graceful drain. Expand Capacity Lab to cached/uncached assets, payload sizes and trusted runtime profiles; record throughput, latency percentiles, errors and device-resource evidence.

**Exit:** repeatable safe-envelope reports on target hardware; no universal visitor-count claim; static hosting remains available under runtime overload.

## Gate 4 — Backup, operations and Settings (P0/P1)

Fault-test backup restore for interruption, corruption, wrong passphrase, low storage, custom roots and rollback failure. Verify secret exclusion, bounded logs and durable reports. Extract Settings and complete responsive/accessibility/screenshot review.

**Exit:** restore remains recoverable without losing the last known-good site; diagnostics redact secrets; Settings acceptance passes.

## Gate 5 — Full CI matrix and APK evidence (P0)

Run API 26/33/35 on the same release-candidate SHA. Build host and companion APKs for declared ABIs; retain test reports, screenshots, provenance and checksums; validate downloaded artifacts.

**Exit:** all required jobs green on one SHA and artifacts independently validated.

## Gate 6 — Release preparation (P0)

Configure owner-controlled signing secrets; create signed host/companion APKs; verify signatures/checksums/upgrades; test existing Cloudflare Tunnel over external HTTPS; confirm management API is not exposed; complete physical-device acceptance and independent security review.

**Exit:** signed artifacts, owner approval, external HTTPS evidence, device evidence and independent review disposition.

## Current readiness

| GATE | COMPLETION ESTIMATE |
|---|---:|
| Companion runtime migration | 15% (pre-migration verified baseline; pending re-score after CI) |
| Isolation and adversarial security | 25% |
| Rust governor and Capacity Lab | 60% |
| Backup, operations and Settings | 65% |
| Full CI matrix and APK evidence | 55% |
| Release preparation | 15% |
| **Weighted overall** | **37%** |

Weights and evidence are defined in [PROJECT_BASELINE.md](PROJECT_BASELINE.md). These are engineering estimates, not test coverage.
