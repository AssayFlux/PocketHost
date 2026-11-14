# Phase 0 implementation status

## Added in the initial implementation commit

- Native Android project scaffold using Kotlin and Jetpack Compose.
- Foreground hosting service and persistent low-importance notification.
- JNI lifecycle bridge for Rust engine start, stop and status.
- Rust static HTTP server bound to loopback only.
- GET/HEAD handling, MIME mapping, path canonicalization and no directory listing.
- Bundled sample website.
- TypeScript + React + Vite management-console visual scaffold.
- Rust unit-test starter and GitHub Actions quality workflow.

## Not implemented or not verified

- No APK has been built or installed on a physical device in this environment.
- Android NDK build and JNI integration have not been executed here.
- Cloudflare Tunnel is not integrated.
- LAN/public listener is not enabled.
- Folder/ZIP importer, deployment history, rollback and backups are not implemented.
- Python and Node runtimes are not integrated.
- The TypeScript console is not connected to a management API.
- Authentication, rate limiting, request quotas, analytics and runtime supervision remain future work.

## Build prerequisites

1. Install JDK 17, Android SDK 35 and Android NDK through Android Studio.
2. Install Rust stable and Android targets `aarch64-linux-android` and `armv7-linux-androideabi`.
3. Install cargo-ndk: `cargo install cargo-ndk`.
4. Run `gradle :app:assembleDebug` from the repository root (or use an installed Gradle 8.9-compatible distribution).
5. Install the generated APK on a test device and verify local preview.

The first release must not advertise public hosting until the Android tunnel connector passes the external-network acceptance test in `docs/MASTER_PLAN.md`.
