# PocketHost implementation status

**Last updated:** 2026-11-14  
**Branch:** `main`  
**Current status:** Feature-complete source is not yet achieved; development build only.

## Implemented in source

- Kotlin + Jetpack Compose native Android application.
- Foreground hosting service with persistent notification and service-state reporting.
- JNI lifecycle bridge to Rust.
- Rust/Tokio/Hyper static server with streaming file delivery.
- Loopback binding by default; explicit user opt-in for LAN binding.
- Bounded 32 concurrent connections and 50 MiB per-file response limit.
- MIME mapping, GET/HEAD, content length, no directory listing.
- Canonical path checks, percent-decoding validation and traversal rejection.
- Startup readiness handshake and graceful connection shutdown.
- Android folder import and ZIP import with staging, path checks and bounded entry/size/depth limits.
- Deployment history retaining up to five prior releases and rollback while hosting is stopped.
- Persistent website import summary.
- React + TypeScript + Vite console foundation.

## Verified

- CI workflow [#43596745893](https://github.com/AssayFlux/PocketHost/actions/runs/43596745893) passed Rust tests, Android debug APK compilation and TypeScript console production build for commit `89011087da0f3e95d796bfb0a0b305cc2a030037`.
- ZIP import, release history/rollback and LAN opt-in were added after that verified commit. Their newer CI runs must pass before they are considered build-verified.
- CI compilation is not physical-device acceptance.

## Still incomplete / blocked

- Physical Vivo Y95 installation and real-device testing, including OS version, ABI, startup, file picker, ZIP import, rollback, LAN access, lifecycle, battery and thermal behavior.
- Cloudflare Tunnel Android connector, named tunnel credentials, DNS/domain configuration, external-network validation and recovery after network changes.
- Authenticated management API and connected TypeScript console.
- Python runtime and Node.js runtime.
- Multi-site management, per-site hostnames and routing.
- Metrics, traffic analytics, structured log viewer and redacted diagnostics export.
- Encrypted backup/restore and signed production release.
- HTTP byte-range media seeking and configurable caching/404 behavior.
- External security review and release acceptance.

## Security boundaries

- Loopback is the default listener.
- LAN mode is opt-in and binds to all local interfaces. It has no visitor authentication; only enable it on a trusted network.
- Public hosting and Cloudflare Tunnel are not available.
- The management console is not connected to a management API.
- Imported content is served as static files and is not executed by the Rust static server.
- Python and Node runtimes are not shipped.
- This build is not a production internet-facing server.

## Current acceptance state

| Gate | State |
|---|---|
| Rust unit tests | Passed on earlier engine revision; latest source CI pending |
| Android compilation | Passed on earlier app revision; latest source CI pending |
| TypeScript build | Passed on earlier console revision; latest source CI pending |
| Folder import | Source implemented; device test pending |
| ZIP import | Source implemented; CI/device test pending |
| Release rollback | Source implemented; CI/device test pending |
| LAN opt-in | Source implemented; CI/device test pending |
| Public tunnel | Not implemented |
| Python / Node | Not implemented |
| Production release | Not ready |

## Build

Requires JDK 17, Android SDK 35, NDK 27.2.12479018, Rust stable, Android Rust targets and `cargo-ndk`.

```bash
gradle :app:assembleDebug
```

The CI APK is a development artifact, not a signed production release.
