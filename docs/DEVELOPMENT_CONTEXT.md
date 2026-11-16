# Pocket Host — Development Context

**Baseline:** 2026-11-16 · `main` reviewed at `a8988f5068f4a65206b89451e8204b51b86abc55`  
**Canonical status:** [PROJECT_BASELINE.md](PROJECT_BASELINE.md)

## Product contract

- App: AssayFlux Pocket Host.
- Native Kotlin + Jetpack Compose.
- Host application ID: `com.assayflux.pockethost`.
- Companion application ID: `com.assayflux.pockethost.worker`.
- Rust/Tokio/Hyper is the public website HTTP origin.
- TypeScript/React management console.
- Chaquopy CPython 3.11 and Node.js mobile 24.22.1 are trusted owner-installed runtimes at this baseline.
- Android 8/API 26+, ARMv7/ARM64 device target; x86_64 for emulator.
- No Termux, root, mandatory VPS or mandatory remote management.
- Reuse existing Cloudflare Tunnel; never expose management API through public ingress.
- Static hosting continues when optional runtimes or remote services are stopped.

## UI contract

Five destinations: Dashboard, Sites, Activity, Learn and Settings. Card-first layout, clear hierarchy, restrained navy/neutral identity, light default with optional dark. No gradients, neon or glass effects. Preserve existing visual language. Settings extraction and responsive/accessibility visual acceptance remain unfinished.

## Architecture boundaries

**Website path:** visitor → Cloudflare edge → existing outbound tunnel → Rust listener → static site or runtime upstream.

**Management path:** owner/operator → authenticated local management API → app services and TypeScript/React console. This path is not part of public visitor origin.

**Runtime trust:** Python and Node currently execute under host app UID. Node's dedicated process is process isolation only. Canonical path validation does not prevent same-UID code from accessing other app-private files. Companion proves separate UID and selected IPC/network capabilities but does not host the runtimes.

Never describe the companion as a completed sandbox, expose arbitrary visitor-submitted code execution, claim egress enforcement before raw-socket/DNS-bypass tests, or store host credentials in worker-readable project directories.

## Engineering sequence

1. Companion runtime migration.
2. Filesystem/network isolation and adversarial tests.
3. Rust fairness and Capacity Lab evidence.
4. Backup/operations/Settings hardening.
5. Exact-SHA API 26/33/35 CI and APK matrix.
6. Signed release, existing-tunnel HTTPS and independent review.

## Build and test discipline

- Use repository Gradle wrapper and declared SDK/NDK/toolchain.
- CI is authoritative for reproducible quality/emulator evidence.
- Test reports must identify exact SHA, API, test count, failures and skips.
- Test harness changes must preserve the security property under test.
- Physical device acceptance is separate from emulator CI.
- Do not call an APK verified unless downloaded and checked for valid APK structure, package metadata and signature.
- Do not weaken production security to make tests pass.

## Security and release

Never commit keystores, passwords, private keys or Cloudflare tokens. Management API remains loopback-only and authenticated. Backup excludes device-bound credentials, but secrets embedded in ordinary project files may not be detected. Do not claim hard CPU quotas for ordinary Android processes. Keep runtime code trusted-only until independent security approval. Release signing uses [RELEASE_SIGNING.md](RELEASE_SIGNING.md).
