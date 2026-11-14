# PocketHost implementation status

**Last updated:** 2026-11-14  
**Branch:** `main`  
**Release state:** Development prototype. Not approved for public production hosting.
**Compatibility target:** Android 8.0 (API 26)+, 4 GB RAM minimum target, ARMv7 + ARM64 phone ABIs; x86_64 for emulator tests.

## Implemented

### Android application
- Native Kotlin + Jetpack Compose application.
- Minimum Android API 26 (Android 8.0); ARMv7 and ARM64 native phone builds, plus x86_64 emulator ABI.
- Simple and Advanced dashboard modes.
- User-started foreground hosting service with persistent notification and status reporting.
- JNI bridge for Rust lifecycle and session metrics.
- Configurable HTTP port (1024–65535).
- Loopback listener by default; explicit LAN opt-in.
- LAN IPv4 address discovery for local preview when available.
- Website import summary persisted across app restarts.

### Rust static engine
- Tokio + Hyper asynchronous static HTTP server.
- GET and HEAD requests.
- Single-range HTTP byte serving (`bytes=start-end`, open-ended and suffix ranges), `206 Partial Content`, `Content-Range`, and `416 Range Not Satisfiable`; multi-range requests are intentionally rejected.
- Streaming file responses.
- Loopback default binding and explicit LAN bind mode.
- 16 concurrent-connection cap, selected as a conservative default for entry-level phones.
- 50 MiB per-file response cap.
- MIME types for common web assets, fonts, WASM, images, audio and video.
- Content-Length and nosniff response header.
- Canonical path validation, percent-decoding validation, traversal rejection and no directory listing.
- Startup readiness handshake and graceful connection shutdown.
- In-memory session metrics: request count, bytes served and active connections.

### Website import and deployment
- Android 13+ notification permission is requested; hosting can still start when notification permission is denied, with Android's foreground-service notification visibility limitation.
- Preview URL and startup status use the configured HTTP port.
- Android Storage Access Framework folder import.
- ZIP import with path validation and compressed/uncompressed 50 MiB caps.
- Maximum 5,000 files, 10,000 entries and 32 levels of nesting.
- Sensitive-name rejection for common secrets and repository metadata.
- Staging before activation.
- Retains up to five previous releases.
- One-tap rollback while hosting is stopped.
- A failed activation attempts to restore the previous active site.

### Companion console and CI
- React + TypeScript + Vite console foundation.
- GitHub Actions for Rust tests, Android debug APK and TypeScript production build.
- CI debug APK artifact upload.
- Manual signed-release workflow with keystore secrets supplied through GitHub Actions; APK signature verification is part of the workflow.

## Latest source changes (CI pending)

- `cf608ca`: added bounded single-byte-range HTTP responses and range parser tests.
- `bf894cb`: added Android 13+ notification permission onboarding and corrected preview/startup port handling.
- Added Android 8+ / 4 GB+ compatibility target, ARMv7 + ARM64 phone builds and x86_64 emulator ABI.
- Added native-engine instrumentation smoke test intended for Android API 26, API 33 and API 35 emulators.
- Reduced the Rust engine default concurrent-connection cap to 16 for entry-level devices.
- The latest changes are committed but not build-verified until CI completes.

## Latest verified build

- Workflow: [PocketHost quality #43598386277](https://github.com/AssayFlux/PocketHost/actions/runs/43598386277)
- Source commit: `c48ee3e1b18c84a787e3b8f686e195181aedb6d8`
- Rust tests: passed.
- TypeScript production build: passed.
- Android debug APK compilation: passed.
- Artifact: `pockethost-debug-apk`.
- Artifact SHA-256: `9e34ffa13bf7a02151dafa2a5bc5e31f9a66f090c1a09222c65ec58165744d48`.
- The workflow's APK is a debug build and is not a signed production release.

## Not implemented / not verified

- Representative physical-device acceptance testing across Android 8+ and 4 GB+ phones, including ARMv7 and ARM64 models.
- Real-device tests for service lifecycle, folder/ZIP import, rollback, LAN access, Wi-Fi transitions, battery and thermal behaviour.
- Cloudflare Tunnel Android connector and named-tunnel lifecycle.
- Cloudflare token onboarding and secure credential storage.
- DNS/domain mapping and external-network validation.
- Authenticated management API and connected TypeScript console.
- Multi-site hosting and host-based routing.
- Python runtime and Node.js runtime.
- Reverse proxy and per-site backend routing.
- Persistent analytics across engine restarts.
- Structured logs, filtering, rotation and redacted diagnostics export.
- Encrypted backup/restore.
- Configurable cache policies and custom 404 pages.
- HTTP multi-range requests (single-range requests are now implemented).
- Signed release workflow is implemented and requires repository Actions secrets; no user signing keystore has been supplied, so no signed release artifact has been produced.
- External security review and production release acceptance.

## Release signing

See [RELEASE_SIGNING.md](RELEASE_SIGNING.md) for keystore creation, secret names, and the manual release workflow. The repository contains no signing key or passwords.

## Safety boundaries

- Loopback binding is the default.
- LAN mode is opt-in and binds to all local interfaces. There is no visitor authentication; only enable it on a trusted network.
- Cloudflare Tunnel and public hosting are not available.
- The TypeScript console is a visual foundation, not a connected management client.
- Imported website content is served as static files; it is not executed by the Rust static server.
- Python and Node.js runtimes are not included.
- Android foreground-service behaviour and vendor battery policies require representative physical-device validation across the supported device class.
- A foreground service does not guarantee 24/7 uptime.

## Build prerequisites

JDK 17, Android SDK 35, NDK 27.2.12479018, Rust stable, Android Rust targets and `cargo-ndk`.

```bash
gradle :app:assembleDebug
```

The resulting debug APK is for development and testing only.
