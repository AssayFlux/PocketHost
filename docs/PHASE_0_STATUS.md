# PocketHost implementation and validation status

**Last reconciled:** 2026-11-15  
**Repository branch:** `main`  
**Main commit at reconciliation:** `5d1a62bdabca590b2cf4ddc1312eb5ed7265faab`  
**Release state:** integrated development build; not production approved  
**Compatibility target:** Android 8.0 / API 26+, 4 GB RAM baseline, ARMv7 + ARM64 phones, x86_64 emulator

This document distinguishes source implementation, automated validation, physical-device validation and production deployment. A feature is not production-ready merely because its code or documentation exists.

## Product and deployment model

PocketHost is a phone-hosted website origin. The Android phone runs the Rust server and optional Python/Node runtimes. Cloudflare Tunnel provides outbound connectivity to the configured Cloudflare edge hostname.

The user has confirmed that their domain and Cloudflare Tunnel are already connected. The expected workflow is to reuse that named tunnel and its existing Public Hostname configuration, then verify that its origin points to the PocketHost listener. This repository does not have access to the user's Cloudflare account and cannot independently certify DNS, ingress, Access policy or public reachability.

The separate Rust/PostgreSQL Remote Control Plane is optional and is not the website origin. It provides remote device registration, heartbeat, queued commands, team roles and audit history. Its current deployment workflow expects an independently reachable Linux/Docker host. That service is not required for local hosting or for the phone to connect to Cloudflare Tunnel. Do not describe it as deployed until its health endpoint and device pairing have been verified.

## Implemented in source

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
- CPython 3.11 embedded through Chaquopy.
- One WSGI application at a time; root `wsgi.py` exports callable `application`.
- Bounded threaded worker pool (2–16) and request/byte/error/concurrency metrics.
- Loopback default with explicit LAN option.
- HostingService supervisor with bounded restart backoff.
- No automatic dependency installation, ASGI/WebSocket support, per-project sandbox or hard CPU/memory quotas.

### Node.js runtime
- Pinned `fogtape/nodejs-mobile` release `v24.22.1-0`, upstream Node.js 24.22.1; archive SHA-256 verified at build.
- Android ABIs: ARM64, ARMv7 and x86_64.
- JNI bridge invokes `node::Start`; Node runs in dedicated Android `:node` process/service.
- Importer validates package metadata and safe JS/erasable-TS entrypoints; dependencies must be prebundled.
- One active Node app/process. Normal desktop child-process workflows, on-device npm installation and PM2/cluster are unsupported.
- TCP listeners are constrained to loopback by the preload; this is not a complete OS network sandbox.
- V8 old-space limit is configurable (96–512 MiB) but is not a hard total-RSS quota.

### Local management and console
- Authenticated loopback management API on port 17841.
- Six-digit pairing approval, expiring challenge, rate limits and bounded attempts.
- Memory-only bearer sessions with 12-hour expiry and individual revocation.
- Host/header/body validation, bounded worker queue, CORS allowlist and Private Network Access preflight.
- Site registry CRUD, runtime controls, status, metrics, logs, tunnel and resource controls.
- TypeScript/React console uses the paired API and tab-scoped sessionStorage for bearer credentials.

### Cloudflare and operations
- Android-targeted cloudflared build pipeline for ARM64, ARMv7 and x86_64.
- Tunnel token encrypted with Android Keystore; lifecycle/readiness monitoring and reconnect supervisor.
- Rotating redacted operational logs and user-triggered diagnostics bundle.
- Tunnel connector readiness is not equivalent to external hostname verification.

### Optional remote management
- Rust/Axum API with PostgreSQL migrations, administrator/device bearer authentication, hashed device/user tokens, device registry and heartbeat.
- Desired configuration versioning and queued commands with leases/expiry and acknowledgements.
- Website ZIP artifact upload/download with checksum validation on the phone.
- Owner, Editor and Viewer roles; audit history.
- Android agent polls outbound HTTPS while HostingService is running and stores remote token encrypted with Android Keystore.
- Device persists command start/result markers to avoid blindly replaying interrupted side effects.
- This service is separately deployed; its source presence does not prove deployment, external HTTPS availability or phone pairing.

## Current automated validation evidence

### Quality workflow
The latest completed quality run on `main` before this documentation branch, run [43680399477](https://github.com/AssayFlux/PocketHost/actions/runs/43680399477) for commit `5d1a62bdabca590b2cf4ddc1312eb5ed7265faab`, completed successfully. The earlier PR #12 quality run [43678133728](https://github.com/AssayFlux/PocketHost/actions/runs/43678133728) on commit `cf268a4991b82fb428569f20cbc523464a91c362` also passed. Jobs included:
- Rust engine unit tests.
- Control-plane Rust tests with PostgreSQL service and migrations.
- Python syntax and WSGI lifecycle tests.
- Node metrics/loopback preload smoke test.
- TypeScript/React console production build.
- Android CMake/Rust native and Kotlin source compilation, including instrumentation-test source compilation.

PR #12 was merged into `main` as commit `5d1a62bdabca590b2cf4ddc1312eb5ed7265faab`.

### Android integration workflow
Run [43678133737](https://github.com/AssayFlux/PocketHost/actions/runs/43678133737) is the integration run for PR #12's source head. At the last live check:
- cloudflared Android ABI build: passed.
- ABI-specific debug APK assembly and verification: passed.
- API 26 emulator instrumentation: running.
- API 33 and API 35 emulator instrumentation: queued.

An earlier integration run [43677996409](https://github.com/AssayFlux/PocketHost/actions/runs/43677996409) completed successfully for cloudflared, ABI APK assembly and API 26, 33 and 35 emulator instrumentation on an earlier source SHA (`dfab8966b9a8118e2ab92fe20b4bffb42040727f`). That proves the earlier source passed the matrix, not that the latest Node-locking changes have passed it. Recheck run #43678133737 before changing the latest-source gates to PASS.

## Not yet verified / production release gates

1. Complete APK assembly, APK content/signature checks and all API 26/33/35 emulator instrumentation on the merged source.
2. Run actual Node instrumentation: load `libnode.so`, confirm Node version, import a bundled dependency, verify loopback ingress, Rust reverse proxy, service stop and fresh-process restart.
3. Test Android foreground-service behavior, notification permission, Doze, screen-off, battery saver, Wi-Fi/mobile transitions, low memory and process death.
4. Install and test on the actual target phones: Vivo Y95 (Android 8.1 / 4 GB / ARM64) and Moto G54 (Android 15 / ARM64), plus a representative ARMv7 Android 8 device.
5. Measure static/Python/Node combined resource use, storage pressure, thermal behavior, APK sizes, ABI contents, signatures and ELF 16 KB page-size compatibility.
6. Verify the existing Cloudflare named tunnel token is configured in PocketHost, the Public Hostname origin/port is correct, and the website returns expected content over HTTPS from a separate network. Verify Access behavior where used.
7. Complete an independent security review of HTTP parsing, Host validation/DNS rebinding, CORS/PNA, pairing/session handling, import/archive paths, tunnel exposure, user-code execution and remote command authorization.
8. Add or complete end-to-end route-level tests for control-plane auth/RBAC, device heartbeat, configuration queue/apply, archive deployment, audit events, leases, revocation and recovery.
9. Decide and document whether the optional remote control plane will be deployed on an existing trusted host, exposed through a separately routed hostname/tunnel, or omitted from the first release. Do not imply that it runs on the phone.
10. Verify backup/restore failure paths and clearly retain the current static-site-only backup scope.
11. Configure release signing secrets, produce a signed release build, verify upgrades and publish checksums only after the previous gates pass.

## Explicit security and product boundaries

- Python and Node user projects are trusted code running with PocketHost's app UID; neither is a hostile-code sandbox.
- Node supports one active application process. Prebundled dependencies are required.
- Python supports one WSGI application, not ASGI or WebSockets.
- V8 and worker settings are ceilings/concurrency controls, not hard total memory/CPU isolation.
- LAN mode has no visitor authentication. Cloudflare Tunnel does not provide visitor authentication by itself.
- Android foreground services improve continuity but do not guarantee 24/7 uptime.
- Local management sessions are in-memory and require pairing again after service/process restart.
- Static-site backup excludes runtime projects, credentials, settings and deployment history.
- The remote management API cannot browse the phone filesystem or execute arbitrary shell commands.

## Build prerequisites

JDK 17; Android SDK 35; NDK 27.2.12479018; CMake 3.22.1; Rust stable and Android targets; cargo-ndk; Go 1.27.4 for cloudflared; Node.js/npm for the console. A clean native build needs network access to retrieve the pinned Node mobile SDK archive.

Source compile without APK assembly:

```bash
gradle :app:externalNativeBuildDebug :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

The final release decision must be based on current workflow evidence and recorded physical-device results, not this document alone.
