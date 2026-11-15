# PocketHost implementation status

**Last updated:** 2026-11-15  
**Branch:** `main`  
**Release state:** Development prototype. Not approved for public production hosting.  
**Compatibility target:** Android 8.0 (API 26)+, 4 GB RAM minimum target, ARMv7 + ARM64 phone ABIs; x86_64 for emulator tests.

## Implemented in source

### Android application
- Native Kotlin + Jetpack Compose application.
- Minimum Android API 26; ARMv7 and ARM64 phone ABIs, plus x86_64 emulator ABI.
- Simple and Advanced dashboard modes.
- User-started foreground hosting service with persistent notification and status reporting.
- Optional Advanced Mode partial CPU wake lock while hosting; off by default, renewed with a bounded ten-minute lease and released on service shutdown, with explicit battery-use warning.
- JNI bridge for Rust lifecycle and session metrics.
- Configurable HTTP port (1024–65535).
- Loopback listener by default; explicit LAN opt-in.
- LAN IPv4 address discovery for local preview when available.
- Website import summary persisted across app restarts.
- Embedded CPython 3.11 via Chaquopy, with Android 8+ ARMv7/ARM64 ABI support.
- Python WSGI project import from SAF with staged activation, root `wsgi.py` validation, 5,000-file / 50 MiB / depth-32 limits, and rejection of common secret files.
- Bounded threaded WSGI HTTP server (maximum 16 concurrent worker threads), loopback default and optional LAN bind.
- Encrypted Cloudflare tunnel-token storage using Android Keystore AES-256-GCM.
- Android-targeted `cloudflared` child-process integration, status/readiness monitoring and bounded reconnect attempts. A remotely managed named tunnel and Public Hostname must be configured in Cloudflare separately.
- Cloudflare tunnel executable source build for arm64-v8a, armeabi-v7a and x86_64; binaries are packaged into the Android app.

### Rust static engine
- Tokio + Hyper asynchronous static HTTP server.
- GET and HEAD requests.
- Single-range HTTP byte serving (`bytes=start-end`, open-ended and suffix ranges), `206 Partial Content`, `Content-Range` and `416 Range Not Satisfiable`; multi-range requests are intentionally rejected.
- Streaming file responses.
- Loopback default binding and explicit LAN bind mode.
- 16 concurrent-connection cap and 50 MiB per-file response cap.
- MIME types for common web assets, fonts, WASM, images, audio and video.
- Content-Length, nosniff and no-cache response headers.
- Canonical path validation, percent-decoding validation, traversal rejection and no directory listing.
- Startup readiness handshake and graceful connection shutdown.
- In-memory session metrics: request count, bytes served and active connections; persistent aggregate traffic totals and daily counters survive engine/service restarts.

### Website import and deployment
- Android 13+ notification permission onboarding; hosting can still start when permission is denied.
- Android Storage Access Framework folder import.
- ZIP import with path validation and compressed/uncompressed 50 MiB caps; a stream-based import path is covered by an Android instrumentation smoke test and is reused by encrypted backup restore.
- Maximum 5,000 files, 10,000 entries and 32 levels of nesting.
- Sensitive-name rejection for common secrets and repository metadata.
- Staging before activation, up to five previous releases retained, and rollback while hosting is stopped.
- A failed activation attempts to restore the previous active site.
- Portable static-website backup and restore using AES-256-GCM, PBKDF2-HMAC-SHA-256 and authenticated-before-activation ZIP validation; credentials, Python projects and device settings are excluded.

### Python runtime
- Embedded CPython 3.11 and WSGI entrypoint contract: root `wsgi.py` exporting `application`.
- Bounded 16-worker threaded server, separate configurable port, loopback default and optional LAN binding.
- Host-side syntax and WSGI lifecycle tests, plus a minimal starter project.
- This is a trusted-code preview runtime, not a sandbox or production multi-tenant runtime.

### Operations and diagnostics
- App-private structured JSONL service-status event log.
- 1 MiB log rotation with three retained archives.
- Redaction for token, authorization, secret, password and API-key assignments and bearer credentials.
- Dashboard display of the latest eight matching events with case-insensitive filtering across level, event name and message; log reads run off the main thread.
- User-controlled export of redacted operational logs as JSONL through Android's Storage Access Framework.
- User-controlled diagnostics ZIP containing redacted logs, app/Android compatibility metadata, non-secret hosting configuration and aggregate traffic counters; excludes project contents and tunnel credentials.

### Companion console and CI
- React + TypeScript + Vite console foundation.
- GitHub Actions for Rust tests, Python runtime tests, TypeScript build and Kotlin source compilation on routine commits; Android packaging and emulator smoke tests are separated into a manual integration workflow.
- Manual signed-release workflow; signing requires the owner's keystore and Actions secrets.

## Latest validation snapshot

Workflow: [PocketHost quality run 43610902054](https://github.com/AssayFlux/PocketHost/actions/runs/43610902054)

| Check | Result | Interpretation |
|---|---|---|
| Rust tests | Passed | Current engine unit tests pass |
| Python runtime tests | Passed | Host-side WSGI lifecycle tests pass |
| TypeScript console build | Passed | Console compiles; it is still a disconnected visual scaffold |
| cloudflared Android build | Passed | Android executables built for arm64-v8a, armeabi-v7a and x86_64 |
| Android debug APK compilation | Passed | Debug APK assembled; not a signed release |
| Android API 26 emulator smoke | Passed | Instrumentation smoke completed |
| Android API 33 emulator smoke | Running at last check | Result not yet available |
| Android API 35 emulator smoke | Emulator-runner failure | Emulator booted, then adb/settings/input calls failed with broken-pipe errors before a test result; this is an infrastructure failure, not a confirmed app test failure |

A successful source build is not proof of physical-device compatibility. Do not interpret the API 35 emulator-runner failure as either an app pass or an app regression.


**Validation note (2026-11-15):** The status snapshot above is the last recorded run before the 2026-11-15 source changes. Encrypted website backup/restore, diagnostics ZIP export, searchable recent logs, the stream-import smoke test and the bounded wake-lock lease have been committed since then. Their CI results have not yet been confirmed. No APK packaging was run for this batch, as requested.

## Not yet implemented or not yet verified

- Node.js application runtime and process supervision.
- Authenticated, versioned Android management API.
- Connected TypeScript console, pairing flow and remote-control authorization.
- Multi-site hosting, hostname routing and per-site listener/origin configuration.
- Rust reverse proxy and runtime-to-site routing.
- Advanced event search, date-range filtering and pagination are not implemented; the dashboard has a basic recent-event text filter and redacted diagnostics ZIP export.
- Configurable cache policies, custom 404 pages and site-level request policies.
- Python resource quotas, request timeouts, stronger isolation and dependency management.
- Tunnel external-hostname/DNS validation and real-device network recovery testing.
- Representative physical-device acceptance testing across Android 8+ / 4 GB+ ARMv7 and ARM64 phones, including screen-off, Doze, battery saver, thermal pressure and Wi-Fi/mobile transitions.
- Security review of public exposure, management authentication and user-code execution.
- Signed production release; no user signing keystore has been supplied.

## Safety boundaries

- Static and Python listeners bind to loopback by default.
- LAN mode is opt-in and binds to local interfaces. There is no visitor authentication; only enable it on a trusted network.
- Cloudflare connector code and binaries are integrated, but public hosting is not approved until an externally configured hostname, Access policy where needed, physical-device tunnel validation and external-network tests pass.
- The TypeScript console is a visual foundation, not a connected management client.
- Python WSGI executes trusted imported code inside the app process; it is not sandboxed. Do not expose it publicly.
- Node.js runtime and multi-site routing are not included. A pinned Node 24 Android embedding candidate and its process/memory constraints are documented in [NODE_RUNTIME.md](NODE_RUNTIME.md).
- A foreground service does not guarantee 24/7 uptime; Android and OEM power policies still apply.

## Build prerequisites

JDK 17, Android SDK 35, NDK 27.2.12479018, Rust stable, Android Rust targets, `cargo-ndk`, Go 1.27.4 for the pinned cloudflared source build, and Node.js/npm for the console.

Build the Android app only after the system-level implementation gates are ready:

```bash
gradle :app:assembleDebug
```

The resulting debug APK is for development and testing only.
