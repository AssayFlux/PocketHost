# PocketHost implementation status

**Last updated:** 2026-11-14  
**Branch:** `main`  
**Current status:** Development build; not production-ready.

## Implemented

- Native Android application scaffold using Kotlin and Jetpack Compose.
- User-started foreground hosting service with persistent notification.
- Dashboard reads service status from app-private preferences.
- JNI lifecycle bridge for Rust engine start, stop and status.
- Rust/Tokio/Hyper static HTTP server bound to `127.0.0.1:8080`.
- GET and HEAD requests.
- Streaming file responses (does not load each entire website asset into memory).
- 32 concurrent connection limit.
- 50 MiB per-file serving limit.
- MIME mapping for common web assets, fonts, WASM, media and documents.
- Canonical path validation, percent-decoding checks, traversal rejection and no directory listing.
- Startup readiness handshake and graceful engine shutdown.
- Android Storage Access Framework folder import.
- Import staging and replacement rollback.
- Import limits: 5,000 files, 10,000 total entries, 50 MiB total and 32 levels of nesting.
- React + TypeScript + Vite management-console visual scaffold.
- GitHub Actions quality workflow for Rust tests, Android debug APK build and TypeScript production build.

## Latest CI verification

- Workflow: [PocketHost quality #43596745893](https://github.com/AssayFlux/PocketHost/actions/runs/43596745893)
- Commit: `89011087da0f3e95d796bfb0a0b305cc2a030037`
- Rust tests: passed, including path normalization and traversal cases.
- Android debug APK: built successfully for configured arm64-v8a and armeabi-v7a ABIs.
- TypeScript console production build: passed.
- APK artifact: `pockethost-debug-apk`, available from the workflow run for 14 days.
- Artifact SHA-256: `04141285775b91d349b371e8a1e7a96b41792db9d5bad03a98581d177f5ac957`.

CI proves compilation and the tests currently present. It does not prove correct operation on a physical Vivo Y95.

## Not implemented or not verified

- Physical Vivo Y95 installation, launch, Android lifecycle, battery and thermal tests.
- Exact device variant, Android version and ABI verification.
- LAN listener and second-device access.
- Cloudflare Tunnel Android connector, named tunnel setup, domain mapping and public hosting.
- ZIP import.
- Immutable deployment history, user-facing rollback, backup and restore.
- Multi-site routing and custom hostnames.
- Authentication and management API.
- Connected TypeScript console.
- Python runtime and Node.js runtime.
- Runtime supervision, metrics, structured logs and resource dashboard.
- HTTP byte-range requests for media seeking.
- Release signing, reproducible release build and external security review.

## Safety boundaries

- The Rust listener binds to loopback only.
- No public or LAN listener is enabled.
- Imported website code is served as static files; PocketHost does not execute it.
- The TypeScript console is a visual scaffold, not a live management client.
- Cloudflare Tunnel has not been proven on Android. Do not advertise public hosting.
- Do not use this development build as a production internet-facing server.

## Build prerequisites

JDK 17, Android SDK 35, NDK 27.2.12479018, Rust stable, Android Rust targets and `cargo-ndk`.

```bash
gradle :app:assembleDebug
```

The CI APK is a development artifact, not a signed production release.
