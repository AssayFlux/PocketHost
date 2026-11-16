# ASSAYFLUX POCKET HOST — CANONICAL PROJECT BASELINE

**Baseline review:** 2026-11-16  
**Source reviewed:** `6c1ba2fe2274b3137dc7e0b62bdb1f50f151bade`  
**Release:** 0.1.0 target · **Status:** pre-release · **Production approval:** no.

This document records what is evidenced at the reviewed source head. A later commit or workflow must not be silently treated as covered by this baseline.

## Product contract

Native Android phone-hosted website origin; API 26+; ARMv7/ARM64; no Termux/root; Rust/Tokio/Hyper request engine; static hosting independent of optional services; TypeScript/React console; existing Cloudflare Tunnel reused; AssayFlux Relay planned as an alternative; management API never public; Python/Node trusted code only until per-project OS isolation is proven.

## Current implementation truth

| Subsystem | Source-level position | Verification / limitation |
|---|---|---|
| Android host | Kotlin/Compose app, five destinations, foreground hosting service | Physical device and full visual acceptance remain |
| Rust engine | Tokio async runtime, Hyper HTTP, static files, hostname routing, reverse proxy, bounded admission | Fairness and sustained-load evidence remain |
| Python | CPython 3.11/Chaquopy moved into companion package | Real end-to-end and per-project isolated execution not proven |
| Node | Node mobile 24.22.1 native runtime moved into companion package | Real end-to-end and per-project isolated execution not proven |
| Companion | Separate package/UID, signature Binder permission, caller checks, versioned IPC, bounded archive transfer | Companion lifecycle/API matrix still requires exact-head proof |
| Isolation | Isolated-service, Unix-domain and native seccomp probe source exists | Probe/runtime results are not accepted as production sandbox evidence |
| Management | Authenticated loopback API and TypeScript/React console | External deployment and full security acceptance remain |
| Cloudflare | Android cloudflared build integration; owner has existing named tunnel | External hostname/TLS verification remains |
| AssayFlux Relay | Defined in North Star | Not implemented; no gateway or agent availability claim |
| Capacity | Device telemetry, first Capacity Lab and admission derating foundations | Fair governor, workload coverage and safe-envelope evidence remain |
| Backup | Encrypted .afbackup and staged restore foundations | Full fault matrix remains |
| Control plane | Axum/PostgreSQL source and deployment workflow | Deployment and device E2E not proven |
| Release | Debug packaging workflow | No verified signed production APK pair |

## CI evidence at reviewed head

- Source head: `6c1ba2fe2274b3137dc7e0b62bdb1f50f151bade`.
- Quality run `43813160835`: completed success.
- Android integration run `43813160678`: queued when this baseline was reviewed; therefore no API 26/33/35 pass is claimed from that run.
- Previous API 26 failures on earlier commits are historical evidence and remain relevant until the corrected exact-head run passes.
- Do not infer Android emulator success from quality CI or APK packaging.

Recheck the live workflow state before using this as a current release report.

## Weighted readiness

The previous estimate of 37% predates the latest runtime/security source changes. It is retained as a historical baseline only; it is not recalculated here because current gate completion evidence is incomplete and the new Relay scope has not been implemented. Do not present 37% as a current audited readiness score.

Use the following release gates as the denominator for a future re-score:
1. Runtime migration and lifecycle.
2. Per-project isolation and adversarial security.
3. AssayFlux Relay and device identity.
4. Owner/visitor authentication.
5. Rust governor and Capacity Lab.
6. Backup, operations and lifecycle recovery.
7. Product UX and onboarding.
8. Exact-SHA CI, release signing, external and physical-device acceptance.

## Non-negotiable security limitations

- Companion UID separation is host-vs-companion only; it is not per-project isolation.
- Python/Node share companion UID and storage.
- Outbound egress is not OS-enforced.
- Seccomp/isolated UID/Unix-domain probes are not proof of production runtime confinement.
- Keep runtimes limited to owner-controlled trusted code.
- Relay must not expose management API and must disclose that gateway-terminated TLS allows relay inspection.
- A relay hides the phone's IP from ordinary visitors, not from relay infrastructure or carrier.

## Evidence vocabulary

- **Source present:** code exists.
- **Compiled:** relevant target compiled.
- **Automated tested:** named tests passed on exact SHA.
- **Emulator verified:** Android instrumentation passed on named API level.
- **Device verified:** physical-device test evidence exists.
- **Externally verified:** real public DNS/TLS/request tested outside the device network.
- **Production approved:** all release gates and independent review accepted.

These terms are not interchangeable. Update this file with exact evidence whenever the baseline changes.
