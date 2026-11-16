# ASSAYFLUX POCKET HOST — CANONICAL PROJECT BASELINE

**Baseline review:** 2026-11-16  
**Source reviewed:** `b7775a2450c0633aaa44b58cb2cdb468a47ea0ca`  
**Release:** 0.1.0 target · **Status:** pre-release · **Production approval:** no.

This is the current evidence baseline. Source implementation is unchanged since the wrapper/dependency freeze; this commit records the six-job exact-SHA quality result.

## Product contract

Native Android phone-hosted website origin; API 26+; ARMv7/ARM64; no Termux/root; Rust/Tokio/Hyper request engine; static hosting independent of optional services; TypeScript/React console; existing Cloudflare Tunnel reused; AssayFlux Relay planned; management API never public; Python/Node trusted code only until per-project OS isolation is proven.

## Current implementation truth

| Subsystem | Source-level position | Verification / limitation |
|---|---|---|
| Android host | Kotlin/Compose app, five destinations, foreground hosting service | Android compilation and visual/device acceptance deferred to Gate 8 |
| Rust engine | Tokio/Hyper, static files, hostname routing, reverse proxy, bounded admission | Non-Android Rust tests pass; fairness and sustained-load evidence remain |
| Python | CPython 3.11/Chaquopy moved into companion package | Python-through-Rust instrumentation test is written; not run yet |
| Node | Node mobile 24.22.1 native runtime in companion, dedicated process | Node-through-Rust and static-origin continuity tests are written; not run yet |
| Companion | Separate package/UID, signature Binder, caller checks, protocol negotiation, Binder death handling, rebind and orderly shutdown | Instrumentation tests written; API 26/33/35 execution deferred |
| Project transfer | Bounded archive, staged extraction and recoverable previous-project rename | Source hardening present; Android transfer/rollback tests await Gate 8 |
| Isolation | Isolated-service, Unix-domain and native seccomp proof source exists | Not a production per-project sandbox; Python/Node remain trusted-only |
| Management | Authenticated loopback API and TypeScript/React console | External deployment and full security acceptance remain |
| Cloudflare | Android cloudflared build integration; existing named tunnel is reused | External hostname/TLS verification remains |
| AssayFlux Relay | Defined in North Star | Not implemented |
| Capacity | Device telemetry, first Capacity Lab and admission derating | Fair governor, workload coverage and safe-envelope evidence remain |
| Backup | Encrypted .afbackup and staged restore foundations | Full fault matrix remains |
| Control plane | Axum/PostgreSQL source and deployment workflow | Deployment and device E2E not proven |
| Release | Manual signed-release workflow source | No verified signed production APK pair |

## Reproducible build inputs

- Rust toolchain/MSRV: 1.88.0; `.cargo/config.toml` selects Rust-version-compatible dependency resolution.
- Cargo locks: `rust-engine/Cargo.lock`, `control-plane/Cargo.lock`.
- Gradle: wrapper 8.9, distribution SHA-256 pinned in `gradle/wrapper/gradle-wrapper.properties`; app and companion Gradle dependency lockfiles committed.
- Android: SDK platform 35, Build Tools 35.0.0, NDK 27.2.12479018, CMake 3.22.1.
- Native Rust Android tool: cargo-ndk 4.1.2 installed with `--locked`.
- Console: exact direct package versions and `web-console/package-lock.json`; CI uses `npm ci`.
- Python: 3.11; Node console CI: 22; Node runtime smoke CI: 24; cloudflared build Go toolchain: 1.27.4.
- Dependency lock workflow resolves graphs and commits lockfiles; it does not build APKs.

## CI and Android build policy

- Quality workflow: Rust, control plane/database migrations, Python, Node, console and Gradle wrapper validation. It does not compile Android or package APKs.
- Android integration and signed-release workflows are manual-only.
- Owner instruction: no Android APK build, emulator run or device test during Gates 0–7. All Android build/ABI/API 26/33/35 validation is deferred to Gate 8.
- A legacy Android integration run `43824043298` on SHA `12eaf3ba700b6e0c37ecc14f72e633c5800fd431` started before the manual-only change and is now cancelled. Its cloudflared job succeeded, APK job failed, API 26/33 emulator jobs failed, and API 35 was cancelled. It produced no accepted APK or API-matrix result. The replacement push-triggered run `43828040040` skipped all Android jobs.

## Verification evidence

- Quality run `43827669251`: all six jobs passed on `9f249f0faf3f103bcee9aaa763adf3c275211951` (Rust 1.88 locked tests, control-plane migrations and Compose profiles, Python, Node, `npm ci`, Gradle wrapper validation).
- Dependency-lock workflow `43826809601`: completed successfully; generated Cargo/npm/Gradle locks and the checksum-verified Gradle wrapper.
- No successful Android APK artifact, accepted API matrix or physical-device result is claimed on this baseline.

## Gate status

| Gate | Status |
|---|---|
| 0 — Repository truth and architecture freeze | Complete |
| 1 — Runtime migration | Source implementation complete; Android verification deferred to Gate 8 |
| 2 — Per-project isolation and adversarial security | Open; shared companion UID is not a sandbox |
| 3 — AssayFlux Relay | Planned; not implemented |
| 4 — Owner/visitor authentication | Open |
| 5 — Rust governor and Capacity Lab | Open |
| 6 — Backup, operations and lifecycle | Open |
| 7 — Product UX and onboarding | Open |
| 8 — Android builds, API matrix, release and independent review | Deferred until all earlier implementation work is complete |

No current percentage is published. Source presence, compilation, automated testing, emulator verification, physical-device verification and production approval are separate states.

## Non-negotiable security limitations

- Companion UID separation is host-vs-companion only; it is not per-project isolation.
- Python/Node share companion UID and storage.
- Outbound egress is not OS-enforced per project.
- Seccomp/isolated UID/Unix-domain probes are not production runtime confinement.
- Keep runtimes limited to owner-controlled trusted code.
- Relay must not expose management API and must disclose that gateway-terminated TLS allows relay inspection.
- A relay hides the phone's IP from ordinary visitors, not from relay infrastructure or carrier.

Update this file with exact evidence whenever the baseline changes.

## 2026-11-16 incremental source and CI evidence

- Latest previously completed quality run: `43836090906` on `5de2ff28b880119de379a52885b17abecec23e06`; all six jobs completed successfully (Rust, control-plane, Python, Node, console and Gradle wrapper).
- Subsequent control-plane source increment: `dc18daa9855debdeb8fe7b5ed291b9ee8365bdcb` adds exclusive create-new deployment artifact writes, file and parent-directory fsync, and cleanup of partial files or database-transaction failures, plus a regression test for cleanup, committed-file retention and overwrite rejection. The control-plane README documents the transactional audit and artifact lifecycle.
- CI for that source increment was queued at review time; do not treat the earlier successful run as verification of the new code.
- Gate 2 remains open: no production per-project isolated runtime migration or accepted Android API matrix.
- Gate 4 remains open: remote control-plane token rotation, role checks and atomic audit writes are implemented, but passkeys and site-scoped visitor authorization are not complete.
