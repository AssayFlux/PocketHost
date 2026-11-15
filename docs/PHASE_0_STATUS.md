# PocketHost implementation and validation status

**Last reconciled:** 2026-11-15  
**Default branch:** `main`  
**Latest known main commit:** `cb6acbc7e2d9de5dd9fcdb6211330df349f9c6d0` (merge of PR #14)  
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

PR [#14](https://github.com/AssayFlux/PocketHost/pull/14) merged [ADAPTIVE_CAPACITY_SECURITY_OPERATIONS.md](ADAPTIVE_CAPACITY_SECURITY_OPERATIONS.md). The following remain planned/not implemented unless separate source evidence is added:

- Full device telemetry/hardware inventory.
- Capacity Lab, measured sustainable safe capacity and capacity graphs.
- Rust Resource Governor, bounded admission, 429/503, thermal/memory protection and recovery.
- Timed profiles such as 30% of calibrated safe capacity for three hours.
- Strong per-project sandbox/UID isolation, filesystem/network confinement and hard quotas.
- PSP integration, signed webhook processing, encrypted durable payment ledger and reconciliation.
- Hash-chained signed audit checkpoints and optional blockchain anchoring.
- Complete durable daily operations/financial reports and storage-bounded logs.

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
14. Implement tamper-evident audit and daily report retention; optional blockchain only after privacy review.
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
- Blockchain must never hold payment/personal data and must not be required for hosting.

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
