# PocketHost implementation and validation status

> **Current reconciliation — 2026-11-15**
>
> - **Current main:** `c252236f9815cc6a8ddba94d75966a2e4d1083e5` (documentation reconciliation commit). **Product-code baseline:** `a6bc0f77dac43d335bba36ad24e5d6e801e1b58e` (PRs #30–#36 merged; PR #31 removed obsolete blockchain references).
> - **Quality CI:** [run 43711272472](https://github.com/AssayFlux/PocketHost/actions/runs/43711272472) passed all six jobs on the product-code baseline: Rust, Node runtime, Python runtime, control plane, TypeScript console and Android source compilation.
> - **Android APK build:** [run 43711203835](https://github.com/AssayFlux/PocketHost/actions/runs/43711203835) passed cloudflared JNI build, three-ABI APK packaging and ABI-content verification. Emulator instrumentation was skipped by design; it is opt-in and physical-device acceptance is owner-run.
> - **APK artifact:** [GitHub Actions artifact 14161340221](https://github.com/AssayFlux/PocketHost/actions/runs/43711203835/artifacts/14161340221), SHA-256 `bd7e25439239f6c507b2c5762a2f8a8c353b37b8fdc129bfef10bd972deaa8c5`.
> - **ABI APK SHA-256:** ARM64 `13d972bf6ad6579d511c1df9bb8e359a3422c8f39b8cd8c99ef6d2a408c1438a`; ARMv7 `4b207bfee30cafec1700def36692a742b982f55f3e2a1eaa9aa632941a6948f5`; x86_64 `e4ac7779962b742406675ec235c783b34cf97765017ca142823d8cb183305cb8`.
> - **Implemented capacity protections:** bounded 16-active/32-waiting connection admission, 200 ms queue window, per-client token-bucket rate limit, per-site streaming concurrency caps, observable rejection counters, and up-to-three-second graceful shutdown drain.
> - **Capacity Lab:** bounded static-origin sweep reports request rate, byte throughput and average/p50/p95/maximum latency. It is still a microbenchmark, not long-duration or multi-workload device calibration.
> - **Blockchain references:** removed from the six project documents; no blockchain implementation/dependency is part of the repository.
> - **Release APK:** signed release is not built because release signing secrets are external. The supplied APKs are debug builds.
> - **Device acceptance:** physical installation, Android lifecycle, existing Cloudflare tunnel/public HTTPS, thermal, battery and OEM behavior are owner-run acceptance tasks.
> - **Release state:** integrated development build; not production approved. Python/Node run under the app UID and are not OS-sandboxed; execute only trusted code. Remote control-plane deployment and any payment-provider integration require their own external configuration.
>
> The following historical sections below retain earlier evidence and implementation notes. When historical results conflict with this reconciliation, use the current commit/run identifiers above.

**Last reconciled:** 2026-11-15  
**Default branch:** `main`  
**Latest implementation commit:** `34f8d5329223d59e984e98b977e92d86e6efd555`  
**Quality CI:** [run 43703703022](https://github.com/AssayFlux/PocketHost/actions/runs/43703703022) passed all six jobs, including Rust, Node, Python, console, control plane and Android source compilation. No APK packaging or emulator UI acceptance is implied.  
**Android integration:** [run 43703702835](https://github.com/AssayFlux/PocketHost/actions/runs/43703702835) pending at reconciliation time.  
**Release state:** integrated development build; not production approved  
**Compatibility target:** Android 8.0 / API 26+, 4 GB RAM baseline, ARMv7 + ARM64 phones, x86_64 emulator

This record separates source implementation, automated validation, emulator validation, physical-device validation and production deployment. Source presence is not production approval.

## Product and deployment model

PocketHost is a phone-hosted website origin. The Android phone runs the Rust server and optional Python/Node runtimes. Cloudflare Tunnel provides outbound connectivity to the configured Cloudflare edge hostname.

The owner has confirmed that their domain and Cloudflare Tunnel are already connected. Reuse the existing named tunnel and Public Hostname. Its origin must point to the PocketHost Rust website listener, never the management API. This repository does not have access to the owner's Cloudflare account and cannot independently certify DNS, ingress, Access policy or public reachability.

The separate Rust/PostgreSQL Remote Control Plane is optional remote management only. It is not the website origin and is not required for local hosting or the phone's Cloudflare Tunnel. Source presence does not prove deployment, HTTPS availability or physical-device pairing.

## Implemented in source (not equivalent to production approval)

### Android application
- Kotlin + Jetpack Compose with Simple and Advanced modes.
- Minimum SDK API 26 and configured ARMv7, ARM64 and x86_64 ABIs.
- User-started foreground HostingService, notification/status handling and bounded optional wake-lock lease.
- SAF folder and ZIP imports, validation, staging, retained releases and rollback.
- Python and Node project importers with bounded size/count/depth and path/sensitive-file checks.
- Encrypted static-site backup/restore and redacted diagnostics export.

### Rust hosting engine
- Tokio + Hyper HTTP engine; GET/HEAD, streaming responses, byte ranges and MIME handling.
- Canonicalized paths and traversal protection.
- Bounded concurrent connections and response sizes.
- Hostname registry routes to static roots or loopback Python/Node upstreams.
- Reverse proxy forwards host/protocol metadata.
- Static-server traffic metrics separated from runtime metrics.

### Python runtime
- CPython 3.11 embedded through Chaquopy; one WSGI application at a time.
- Root `wsgi.py` exports callable `application`; bounded 2–16 worker threads and request/byte/error/concurrency metrics.
- Loopback default with explicit LAN option; bounded restart backoff.
- No automatic dependency installation, ASGI/WebSocket support, per-project sandbox or hard CPU/memory quotas.

### Node.js runtime
- Pinned `fogtape/nodejs-mobile` `v24.22.1-0`, upstream Node.js 24.22.1; archive SHA-256 verified at build.
- ARM64, ARMv7 and x86_64; JNI `node::Start`; dedicated Android `:node` process/service.
- One active Node app/process; dependencies prebundled; desktop child-process workflows, on-device npm installation and PM2/cluster unsupported.
- TCP listeners constrained to loopback by preload; not a complete OS network sandbox.
- V8 old-space 96–512 MiB is not a hard total-RSS quota.

### Local management and console
- Authenticated loopback management API on port 17841.
- Six-digit pairing approval, expiring challenge, rate limits and bounded attempts.
- Memory-only bearer sessions with 12-hour expiry and individual revocation.
- Host/header/body validation, bounded worker queue, CORS allowlist and Private Network Access preflight.
- Site registry CRUD, runtime controls, status, metrics, logs, tunnel and resource controls.
- TypeScript/React console uses paired API and tab-scoped sessionStorage.

### Cloudflare and operations
- Android-targeted cloudflared build pipeline for ARM64, ARMv7 and x86_64.
- Tunnel token encrypted with Android Keystore; lifecycle/readiness monitoring and reconnect supervisor.
- Rotating redacted operational logs and user-triggered diagnostics bundle.
- Tunnel connector readiness is not equivalent to external hostname verification.

### Optional remote management
- Rust/Axum API with PostgreSQL migrations, hashed device/user tokens, registry and heartbeat.
- Desired configuration versioning and queued commands with leases/expiry and acknowledgements.
- Website ZIP artifact upload/download with checksum validation.
- Owner, Editor and Viewer roles; audit history.
- Android agent polls outbound HTTPS and stores remote token encrypted with Android Keystore.
- Source is separate from phone hosting; deployment and real-device pairing remain unverified.

## Automated validation evidence currently recorded

Quality run [43680399477](https://github.com/AssayFlux/PocketHost/actions/runs/43680399477) completed successfully for code commit `5d1a62bdabca590b2cf4ddc1312eb5ed7265faab`. Jobs included Rust tests, control-plane/PostgreSQL tests, Python syntax/WSGI tests, Node preload smoke, TypeScript/React build and Android native/Kotlin source compilation.

**This is evidence for the named code commit, not a fresh test of every later main commit.** PR #14 merged adaptive-capacity/security/operations documentation only; it did not implement those runtime features. Recheck current workflow status on the exact main SHA before release.

Android integration run [43678133737](https://github.com/AssayFlux/PocketHost/actions/runs/43678133737) was for PR #12's source head. Last recorded state: cloudflared and ABI APK assembly passed; API 26 instrumentation running; API 33/35 queued. Do not upgrade statuses without checking the actual run.

Earlier integration run [43677996409](https://github.com/AssayFlux/PocketHost/actions/runs/43677996409) completed cloudflared, ABI APK assembly and API 26/33/35 emulator instrumentation on earlier source SHA `dfab8966b9a8118e2ab92fe20b4bffb42040727f`. Historical evidence is not proof for current main.

## New adaptive-capacity/security workstream

PR [#14](https://github.com/AssayFlux/PocketHost/pull/14) merged [ADAPTIVE_CAPACITY_SECURITY_OPERATIONS.md](ADAPTIVE_CAPACITY_SECURITY_OPERATIONS.md). Source implementations now cover the first telemetry sampler, static GET benchmark, provisional/calibrated concurrency ceiling, timed profiles, device-side health derating, HTTP 503 admission rejection and daily aggregate reports. The following remain incomplete:

- Broader telemetry history, per-site/runtime attribution, uptime, latency and network-throughput measurements.
- Capacity Lab workload classes, repeated confidence tests, throughput/resource graphs, cancellation and long-duration thermal validation.
- Full Rust-native rate limiting, bounded waiting queues, per-site fairness, graceful drain and fault-injection recovery.
- Device/emulator acceptance of 30%-for-three-hours expiry/restoration and profile persistence.
- OS-enforced per-project sandbox/UID isolation, filesystem/network confinement and hard quotas.
- PSP integration, signed webhook processing, encrypted durable payment ledger and reconciliation.
- Conventional hash-chained signed audit checkpoints and local verification.
- Complete durable daily operations/financial reports, backups and storage-bounded retention.

Do not describe these as live, secure or production-ready until implemented and accepted.

## Not yet verified / production release gates

1. Complete APK assembly, APK content/signature checks and API 26/33/35 emulator instrumentation on current merged source.
2. Actual Node instrumentation: load `libnode.so`, confirm version, import bundled dependency, verify loopback ingress, Rust proxy, stop and fresh-process restart.
3. Foreground-service behavior, notification permission, Doze, screen-off, battery saver, Wi-Fi/mobile transitions, low memory and process death.
4. Physical testing on Vivo Y95 (Android 8.1 / 4 GB / ARM64), Moto G54 (Android 15 / ARM64), and representative Android 8 ARMv7 device.
5. Static/Python/Node combined resource use, storage pressure, thermal behavior, APK sizes, ABI contents, signatures and ELF 16 KB page-size compatibility.
6. Existing Cloudflare token configured in PocketHost, Public Hostname origin/port correct, expected content served over HTTPS from a separate network; Access behavior verified where used.
7. Independent security review of HTTP parsing, Host validation/DNS rebinding, CORS/PNA, pairing/session handling, archive paths, tunnel exposure, user-code execution and remote command authorization.
8. Route-level control-plane auth/RBAC, heartbeat, config apply, archive deployment, audit, leases, revocation and recovery tests.
9. Decide if optional remote control plane will be deployed on an existing trusted host, exposed through a separately routed hostname/tunnel, or omitted from first release.
10. Backup/restore failure paths; current documented backup scope is static-site-only.
11. Implement/test telemetry, capacity benchmark, governor and scheduled profiles before advertising automatic capacity protection.
12. Resolve runtime isolation before accepting untrusted or third-party code.
13. Implement PSP integration, durable payment events, reconciliation, encrypted backup and qualified payment-security review before accepting live payments.
14. Implement tamper-evident audit and daily report retention; local verification and signed checkpoint export.
15. Configure release signing, build signed APKs, verify upgrades and publish checksums only after prior gates pass.

## Explicit security and product boundaries

- Python and Node projects run with PocketHost app UID; neither is a hostile-code sandbox.
- Node supports one active application process; dependencies must be prebundled.
- Python supports one WSGI application, not ASGI or WebSockets.
- V8 and worker settings are not hard total memory/CPU isolation.
- LAN mode has no visitor authentication. Cloudflare Tunnel does not provide visitor authentication by itself.
- Android foreground services improve continuity but do not guarantee 24/7 uptime.
- Local management sessions are in-memory and require pairing after service/process restart.
- Static-site backup excludes runtime projects, credentials, settings and deployment history.
- Remote management API cannot browse phone filesystem or execute arbitrary shell commands.
- Never store raw card data. PSP integration does not equal PCI compliance.

## Build prerequisites

JDK 17; Android SDK 35; NDK 27.2.12479018; CMake 3.22.1; Rust stable and Android targets; cargo-ndk; Go 1.27.4 for cloudflared; Node.js/npm for console. Clean native build needs network access to retrieve the pinned Node mobile SDK archive.

Source compile without APK assembly:

```bash
gradle :app:externalNativeBuildDebug :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

Final release decision must use current workflow evidence and recorded physical-device results, not this document alone.

## Canonical project documents

- [North Star](NORTH_STAR.md)
- [Roadmap](ROADMAP.md)
- [Master Plan](MASTER_PLAN.md)
- [Development Context](DEVELOPMENT_CONTEXT.md)
- [Architecture Decisions](DECISIONS.md)
- [Completion Milestone](COMPLETION_MILESTONE.md)
- [Adaptive Capacity, Security, Payments and Operations](ADAPTIVE_CAPACITY_SECURITY_OPERATIONS.md)


## 2026-11-15 implementation pass — capacity, device telemetry and protective governor (source added; quality CI passed)

The following first implementations were added directly to the Android/Rust/TypeScript codebase:

- Rust admission ceiling is dynamically adjustable from 1–16 concurrent connections. Connections above the current ceiling receive HTTP 503 with Retry-After instead of being silently dropped. A rejection counter is exposed.
- Android Capacity Lab runs a bounded local static GET sweep at concurrency levels 1, 2, 4, 8, 12 and 16. Benchmark results persist across app restarts; a conservative provisional safe ceiling of four connections applies until a successful calibration. It records sample count, failures, elapsed time and p95 latency; it stops escalating on errors, p95 above 750 ms, Android low-memory state or severe thermal state.
- The selected safe concurrency ceiling and 1–100% capacity profile persist across restarts. Timed schedules (1–1440 minutes) restore the previous profile on expiry; the dashboard includes the requested 30%-for-three-hours action.
- Authenticated management API routes expose capacity state, profile changes, benchmark state and daily reports.
- Android dashboard and TypeScript console expose the profile, benchmark results, active/rejected connection counters and report summaries.
- Versioned Android device telemetry records manufacturer/model, API/ABIs/CPU cores, sampled aggregate CPU load, RAM/PSS/heaps, storage, battery/temperature, thermal state and network transport every 30 seconds in a bounded two-file history. The dashboard and authenticated API refresh live readings while hosting is stopped without writing a history row on every UI poll.
- The five-state device-side governor (NORMAL, CONSTRAINED, PROTECTIVE, RECOVERY, EMERGENCY) derates the connection ceiling using fresh CPU load, memory, storage, battery and thermal signals; protective/emergency conditions repeatedly enforce suspension of optional Python/Node runtimes while preserving the Rust static origin.
- Daily rollover archives aggregate static-engine request/byte totals and retained redacted operational events; hourly snapshots refresh the current day. Reports are stored privately with a 90-report retention cap and can be exported as JSON.

**Important limitations:** telemetry is a low-frequency device snapshot, not a complete profiler or reliable per-process CPU accounting. The CPU percentage is aggregate device utilization and can be unavailable during the first sample. This is a static GET concurrency smoke benchmark, not a complete Capacity Lab. It does not measure Python/Node, external Cloudflare/network throughput, sustained multi-hour thermal behavior, p99, CPU/RPS graphs or arbitrary website workloads. The limiter is a concurrency admission ceiling, not a full rate/fairness/queue governor. Daily reports do not yet contain complete uptime, battery, latency, tunnel or financial metrics. Quality CI [43688959461](https://github.com/AssayFlux/PocketHost/actions/runs/43688959461) passed all six jobs on exact main SHA `c6e07a7248a123aac757a7358dc2df13caec9e86` (Rust, Node runtime, Python runtime, TypeScript console, control plane and Android Kotlin plus Android-test source compilation). This workflow does not package APKs or run emulator instrumentation. Physical-device, emulator instrumentation and external Cloudflare hostname validation remain pending.

**Still not implemented/approved:** OS-enforced per-project runtime UID sandbox and network/filesystem isolation; full Rust-native rate/fairness/queue Resource Governor and long-duration device acceptance; PSP payment integration, webhook inbox, encrypted payment ledger and reconciliation; conventional hash-chained signed audit checkpoints and verification; external Cloudflare hostname validation; deployed remote control plane; physical-device acceptance; signed release. Do not run untrusted Python/Node projects or accept live payments under the current architecture.


## 2026-11-15 follow-up — optional-runtime trust interlock

- Added a persistent, explicit owner acknowledgement in Advanced Mode before Python or Node can start.
- Enforced the acknowledgement inside both runtime start APIs, so local management API and lifecycle-supervisor calls cannot bypass the guard.
- Startup recovery clears stale Python/Node enable flags if acknowledgement is absent; static hosting remains available.
- Host routing omits Python/Node routes unless acknowledgement and the corresponding runtime-enabled flag are both present.
- Revoking acknowledgement disables both optional runtimes, stops them and restarts the Rust origin with the remaining static routes.
- Updated Python/Node documentation to state clearly that the Node `:node` process shares the app UID and is not a security sandbox.
- Capacity Lab can now be run again after a completed or failed run; concurrent attempts still return the active result.
- Updated Android runtime smoke tests to explicitly opt into trusted-code execution.

**This is a safety interlock, not sandboxing.** Untrusted Python/Node remains prohibited. OS-enforced UID/process isolation, filesystem and network confinement, and enforceable CPU/memory quotas remain release blockers. PSP payments, signed conventional tamper-evident audit, complete operational reports, full Rust rate/fairness/queue governance, physical-device acceptance and signed release are still not integrated.


## 2026-11-15 UI implementation update

- Android app display name is **AssayFlux Pocket Host**; repository and package identifiers remain unchanged.
- Added the four-tab Dashboard / Files / Logs / Settings navigation, centralized navy light/dark theme, server status, single Start/Stop action, uptime and device telemetry, local/public URL display and copy/share, file metadata and guarded text-file create/edit/delete/index selection, website import/backup, operational log filters and clear action, and a loopback WebView preview. Settings includes opt-in boot start, battery settings, About and honest capability notes.
- Updated foreground notification branding and Cloudflare instructions to reuse the owner's existing named tunnel and Public Hostname.
- Quality CI run 43692174009 passed all six jobs, including Kotlin/Android-test source compilation. No APK was packaged and no emulator/physical-device UI test matrix was run.
- The UI is an initial implementation, not a claim that every item in the design brief is finished. Request-level IP/path/status logs, local TLS, built-in visitor passwords, full two-column tablet behavior and responsive screenshot acceptance remain outstanding. Boot start has not been validated on a physical device.


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


## Follow-up security hardening — 2026-11-15

- Commit `4a5284fc6f5cdc7481252445d05dfe721d17b027` bounds the in-memory management-login throttle map: expired entries are pruned, the map is capped at 256 attacker-controlled username keys, and oversized usernames are not retained as throttle keys.
- Quality workflow [43700940364](https://github.com/AssayFlux/PocketHost/actions/runs/43700940364) and Android integration workflow [43700940297](https://github.com/AssayFlux/PocketHost/actions/runs/43700940297) were queued at the time this status was updated. No pass is claimed for this commit until those runs complete.
- This narrow hardening does not resolve runtime isolation, native biometric unlock, physical-device acceptance, external Cloudflare validation, remote control-plane deployment, payment integration or production release signing.


## Latest implementation update — 2026-11-15

Main commit `34f8d5329223d59e984e98b977e92d86e6efd555` includes the merged work in PRs [#18](https://github.com/AssayFlux/PocketHost/pull/18), [#19](https://github.com/AssayFlux/PocketHost/pull/19), [#20](https://github.com/AssayFlux/PocketHost/pull/20), [#21](https://github.com/AssayFlux/PocketHost/pull/21), [#22](https://github.com/AssayFlux/PocketHost/pull/22) and [#23](https://github.com/AssayFlux/PocketHost/pull/23).

Implemented in this update:
- Management login throttle state is bounded; password character arrays and derived credential buffers are cleared after use.
- Static website file manager supports rename for files/folders and bounded single-file upload; the Rust server serves extensionless SPA routes from root `index.html` while preserving 404s for missing file assets.
- A website deployment guide explains frontend build outputs, Python WSGI and Node runtime boundaries.
- Android app-launch biometric gate uses AndroidX BiometricPrompt. Android 11+ can use a strong biometric or secure device credential; Android 8–10 uses strong biometrics only. This is a local UI gate, not API authentication.
- Owner/Operator accounts can change their password; previous sessions are revoked and a replacement token is issued.
- Owner password recovery requires a fresh pairing session approved on the Android device; recovery revokes all Owner and pairing sessions.

[Quality CI 43703703022](https://github.com/AssayFlux/PocketHost/actions/runs/43703703022) passed all six jobs on this main commit. [Android integration 43703702835](https://github.com/AssayFlux/PocketHost/actions/runs/43703702835) was pending at the time of this update.

Still open: OS-enforced Python/Node isolation, complete adaptive governor and multi-workload capacity acceptance, full runtime/configuration backup, physical-device and external Cloudflare acceptance, optional remote control-plane deployment, optional payment integration, and production signing/release.
