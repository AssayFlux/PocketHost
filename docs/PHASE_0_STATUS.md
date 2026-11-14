# PocketHost implementation status

**Last updated:** 2026-11-14  
**Repository branch:** `main`  
**Status:** Early development build; not production-ready.

## Verified in repository and CI

- Kotlin/Jetpack Compose Android application scaffold.
- Foreground service and persistent hosting notification.
- JNI lifecycle bridge to the Rust library.
- Rust/Tokio/Hyper static HTTP server bound to `127.0.0.1:8080`.
- GET and HEAD request support.
- MIME type mapping, canonical path checks, traversal protection and disabled directory listing.
- Startup readiness handshake and graceful engine shutdown.
- Bounded active connections (64) and a 50 MiB per-file serving limit.
- Folder import using Android Storage Access Framework.
- Import staging and replacement rollback behavior.
- Import limits: 5,000 files, 10,000 total entries, 50 MiB and 32 levels of nesting.
- React + TypeScript + Vite console scaffold.
- GitHub Actions Rust tests, Android debug APK build and TypeScript production build.

## Latest CI result

- Workflow run: [PocketHost quality #43595705632](https://github.com/AssayFlux/PocketHost/actions/runs/43595705632)
- Commit: `d9d462e89332acd03b6a3fbabaaa9b29537777af`
- Rust tests: passed.
- Android debug APK build: passed.
- TypeScript console build: passed.
- Debug APK artifact: `pockethost-debug-apk` (14-day retention from the workflow run).

CI success proves compilation and the tests actually present in the workflow. It does not prove correct operation on a physical Vivo Y95.

## Not yet implemented or device-verified

- Physical Vivo Y95 installation, launch, service lifecycle, battery and thermal testing.
- Android 8.1/API 27 compatibility verification on the exact device variant.
- LAN listener and second-device access.
- Cloudflare Tunnel Android connector, named tunnel setup, domain mapping and public hosting.
- ZIP import.
- Immutable deployment history, user-facing rollback, backup and restore.
- Multi-site routing and custom hostnames.
- Authentication and management API.
- Connected TypeScript console.
- Python runtime and Node.js runtime.
- Runtime supervision, metrics, structured logs and resource dashboard.
- Release signing, reproducible release build and external security review.

## Safety boundaries in this build

- The Rust listener binds to loopback only.
- No public or LAN listener is enabled.
- No imported website code is executed by PocketHost.
- The TypeScript console is a visual scaffold, not a live management client.
- Do not use this build to claim public hosting or production availability.

## Build

The GitHub Actions workflow builds the debug APK and publishes it as a temporary artifact. Local build prerequisites are JDK 17, Android SDK 35, NDK 27.2.12479018, Rust stable, Android Rust targets and `cargo-ndk`.

```bash
gradle :app:assembleDebug
```

The APK produced by CI is a development artifact, not a signed production release.
