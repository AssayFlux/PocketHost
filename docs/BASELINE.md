# Pocket Host — Repository Baseline

**Baseline date:** 2026-11-20  
**Active branch:** `Alpha`  
**Baseline commit:** `8879ead946fe985d056970d1614068d1baea562b`  
**Original pre-reset snapshot:** `7d829e09162882dbbd467cc6e6a4a9cdfadcf028`  
**Archive branch:** `archive/pre-replan-2026-11-20`

This document records the repository state at the start of the fresh preliminary plan. It is an inventory, not a claim that every listed feature is production-ready.

## 1. Repository condition

The Alpha branch contains the application and supporting source code after a documentation/workflow reset. The source tree was preserved during that reset; 52 documentation/workflow files were removed. The original state is retained on the archive branch above.

The current tree was previously counted at 238 files. Main areas:

| Area | Current contents |
|---|---|
| `app/` | Android Kotlin/Jetpack Compose application, services, UI, native integration and Android tests |
| `rust-engine/` | Rust static website hosting engine and JNI integration |
| `relay-node/` | Rust native relay/gateway foundation and JNI integration |
| `runtime-worker/` | Separate Android companion APK with Python/Node runtime components |
| `runtime-library/` | Runtime catalogue and related assets |
| `control-plane/` | Rust/Axum remote control-plane service, database migrations and deployment configuration |
| `web-console/` | React/Vite/TypeScript web console |
| `agents/` | Linux agent and installation/operation code |
| `protocol/` | Device protocol schemas and role/capability policy |
| `tests/`, `scripts/` | Cross-component tests and build/validation helpers |
| `Assets/` | Application assets |

## 2. Android application baseline

Verified configuration:
- Application ID and namespace: `com.assayflux.pockethost`.
- Android compile SDK 35, minimum SDK 26, target SDK 35.
- Version code 1 and version name `0.1.0`.
- Java/Kotlin target 17.
- Kotlin, Jetpack Compose and Android instrumentation-test dependencies are present.
- Rust native build integration targets `arm64-v8a`, `armeabi-v7a` and `x86_64`.
- Release signing is conditional on protected keystore environment variables; production signing is not established by the repository configuration alone.

The app has substantial UI and service foundations: hosting, website/file management, workbench, deployments, devices, network/tunnel controls, runtimes, activity/logs, learning and settings. Source presence does not establish end-to-end readiness.

## 3. Hosting and relay foundations

### Static hosting
The Rust engine and Android integration provide a foundation for local static hosting, site storage, import and file operations. Existing code includes bounded import/serving limits, backup/restore and release-history concepts. These limits and policies must be checked for consistency across every entry point.

### Native relay
The relay is a native Android/Kotlin foreground-service plus Rust/JNI direction. Source includes foundations for device identity, encrypted configuration, SQLite state, pairing and credential operations, route handling, TLS/ACME and network recovery.

**Architecture decision:** do not restore the old Termux relay approach. Continue with native Android and Rust/JNI components.

The remaining relay proof is significant: owner-approved pairing, credential lifecycle, route isolation, durable revocation, real DNS/TLS issuance and renewal, network-change recovery, and public visitor acceptance must be demonstrated together. Public reachability also depends on the network path; CGNAT without a usable public edge or global IPv6 prevents ordinary direct inbound hosting.

## 4. Runtime, control plane and connected-device foundations

- A separate runtime-worker Android application exists and includes Python 3.11, a pinned Node.js mobile SDK, native bridges and isolation-related components.
- The current companion design is not yet evidence of a hardened hostile-code sandbox. Do not advertise arbitrary untrusted code execution as securely isolated.
- A Rust/Axum control plane, PostgreSQL migrations, WebAuthn/session/audit foundations and deployment files exist.
- A React/Vite/TypeScript web console exists.
- Linux agent and shared device protocol foundations exist.

These are useful foundations, but deployment, end-to-end authorization, lifecycle and failure recovery still require validation. They are not a reason to expand the first milestone into a full fleet-management platform.

## 5. Build and test baseline

Build configuration exists for Gradle, Rust, Android native libraries and the separate worker APK. Android instrumentation tests and cross-component tests are present in source.

Historical evidence before the reset:
- A quality CI run passed on the original Alpha commit `7d829e0...`.
- A debug APK workflow previously built app APKs for three ABIs and a separate runtime-worker APK.
- An integration workflow compiled Android instrumentation sources, but its API 26/33/35 emulator execution was skipped because the opt-in emulator input was not enabled.

Important current limitation:
- At the initial fresh baseline, the previous `.github/workflows/` files had been removed during the requested reset. Step 1 restored the engineering workflows; see the verification update below.
- Historical workflow results are evidence about the old commit and old workflow definitions only; they do not prove the new baseline or future changes pass.
- No physical-device acceptance or full public visitor journey is established by this baseline record.
- A successful build is not the same as a completed or secure release.

## 6. Known gaps to carry into the plan

1. Restore a small, reproducible CI/build pipeline and run it against the current source.
2. Establish reliable local hosting and consistent import, storage, path, quota, backup and restore policies.
3. Prove Android foreground-service lifecycle, boot/schedule behavior, process-death recovery and network transitions on devices.
4. Complete and test the native relay security and public-access path end to end.
5. Define and enforce the supported runtime trust boundary; do not claim hostile-code isolation without evidence.
6. Validate the minimum required remote-management integration without letting the control plane/fleet scope block the Android host.
7. Complete integrated tests, physical-device acceptance and real-network visitor tests before calling v0.1.0 complete.

## 7. Step 1 verification update

- **Automated verified:** restored quality workflow passed on Alpha (run `44721249398`), including Rust engine/relay tests, PostgreSQL-backed control-plane tests/migrations, 26 Python tests, protocol checks, web-console build, Node smoke test and script/proxy checks.
- **Automated verified:** Android native engine and relay cross-compiled for `arm64-v8a`, `armeabi-v7a` and `x86_64`; ELF architecture checks and Android app/instrumentation/runtime-worker source compilation passed (run `44721249324`).
- **Not performed:** APK packaging is intentionally deferred to Step 5. Emulator execution and physical-device acceptance are not claimed.
- **Open engineering issue:** web-console dependency installation reported one high and one moderate npm advisory; resolve or explicitly disposition before final acceptance.

## 8. Step 2 verification update

- **Automated verified:** website path, symlink, sensitive-file, site-root and persisted-route regression coverage is included in the Alpha source.
- **Automated verified:** Rust hosting/relay tests, Android app/instrumentation/runtime-worker source compilation and three-ABI native builds passed on `fe2af2277bf85eba16e2d3b67ec95d86acf56637` (quality run `44726189328`; Android run `44726189341`).
- **Not yet device verified:** representative folder/ZIP import, loopback/LAN serving, multi-site isolation, interrupted restore and backup integrity on a physical phone. These remain Step 5 acceptance checks.
- **No APK packaging:** deliberately deferred until Step 5.

## 9. Baseline rule

When implementation begins, update this file only when repository facts change or a verification result is obtained. Mark every claim as one of:
- **Present:** source/configuration exists.
- **Automated verified:** a named automated test/build passed on a recorded commit.
- **Device verified:** tested on a physical Android device with model/API/build recorded.
- **Operational verified:** tested through the intended real network/public visitor path.

Never promote a feature from “present” to “verified” based only on code inspection or UI presence.
