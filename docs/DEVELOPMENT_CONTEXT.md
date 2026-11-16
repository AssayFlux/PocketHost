# ASSAYFLUX POCKET HOST — DEVELOPMENT CONTEXT

**Purpose:** mandatory handoff for every engineering session.  
**Product authority:** `docs/NORTH_STAR.md`  
**Evidence authority:** `docs/PROJECT_BASELINE.md` and the dated project status.

## Identity and target

- App: AssayFlux Pocket Host; developer: Designed by AssayFlux.
- Host package: `com.assayflux.pockethost`.
- Runtime companion: `com.assayflux.pockethost.worker`.
- Initial version target: 0.1.0.
- Native Kotlin + Jetpack Compose.
- Rust/Tokio/Hyper website engine.
- TypeScript + React + Vite management console.
- CPython 3.11 / Chaquopy and Node mobile 24.22.1 in companion package.
- Android 8 / API 26 minimum; ARMv7/ARM64 target; x86_64 emulator.
- No Termux, root, mandatory VPS or mandatory remote management.

## Product requirements

1. The phone is the website origin.
2. Static hosting is the core baseline and remains independent of optional runtimes and external networking.
3. Simple and Advanced modes; five destinations: Dashboard, Sites, Activity, Learn, Settings.
4. Reuse the owner's existing named Cloudflare Tunnel; never create a duplicate.
5. Add AssayFlux Relay as a first-party planned alternative, not as an implemented feature until verified.
6. Local Network mode remains available; direct port forwarding is not the default.
7. Keep website visitor authentication separate from Owner/Operator management authentication.
8. Never expose the management API through website ingress.
9. Python/Node remain trusted owner-controlled code until per-project OS isolation and outbound denial pass adversarial acceptance.
10. No fake live values, unverified capacity claims or unsupported security statements.

## UI contract

Classic, clean and timeless. Navy identity, neutral surfaces, monochrome icon style, card-based information hierarchy, light default and optional dark. No gradients, neon or glass. Preserve existing visual language and screen scope. Every control must reflect a real implemented backend state. Accessibility, responsive layout and screenshot review are release evidence.

## Architecture boundaries

- Kotlin: Android UI, foreground service, permissions, SAF, lifecycle and telemetry.
- Rust: website ingress, static serving, hostname routing, reverse proxy, admission and engine metrics.
- Companion broker: signature-protected versioned Binder, project transfer and runtime supervision; must not execute project code in target architecture.
- Isolated cells: one project per OS identity; planned until production migration and proof complete.
- Console: TypeScript/React client; no duplicate routing or secret-bearing public API.
- Cloudflare: optional outbound connector reusing existing named tunnel.
- AssayFlux Relay: planned independent gateway and Android outbound agent.
- Control plane: optional remote management only, never website origin.

## Current security truth

The runtime companion has a UID separate from the host app. Python and Node projects still share the companion UID and project storage. Companion INTERNET permission and language-level listener restrictions do not enforce outbound egress denial. The isolated UID, Unix socket and seccomp work is a feasibility probe. Do not describe it as a production sandbox. Do not run untrusted visitor-submitted code.

## Primary implementation sequence

0. Repository truth, architecture freeze and documentation reconciliation.
1. Complete Python/Node companion lifecycle and Rust end-to-end routing.
2. Implement per-project OS isolation, descriptor-only storage/network capabilities and adversarial tests.
3. Build AssayFlux Relay: gateway, enrollment, outbound agent, multiplexed streams, hostname routing and reconnect.
4. Separate Owner/Operator and visitor authentication, passkeys, site policy and revocation.
5. Complete Rust fairness, Resource Governor and Capacity Lab.
6. Fault-test backup, restore, logs, lifecycle and recovery.
7. Complete onboarding, Dashboard, Sites, Activity, Learn and Settings.
8. Exact-SHA API 26/33/35, ABI builds, signing, external tests and independent review.

The North Star defines each gate's full exit criteria. Do not reorder security-dependent work or represent proposals as complete.

## 2026-11-16 active Gate 2 implementation note

The isolated-UID probe now closes inherited socket descriptors other than the explicitly brokered visitor listener before applying seccomp. The source-level regression test passes an already-connected loopback TCP socket and expects the isolated process to close it. This closes one identified egress path in the probe only; it does not make the probe a production runtime cell. The seccomp policy is still a denylist with an allow fallback, and Python/Node remain in the shared companion UID until their runtimes and storage are migrated.

## Build and test discipline

- Use the checksum-verified Gradle 8.9 wrapper (`./gradlew`) and committed dependency lockfiles.
- Inspect manifests, permissions and ABI filters before changing packaging.
- Quality CI and Android integration CI are separate evidence sources.
- Every result is tied to exact SHA, workflow, API level, test counts, failures and skips.
- A compile pass is not runtime acceptance; emulator pass is not physical-device acceptance.
- Never weaken production policy to satisfy a test harness.
- Do not claim an APK is valid unless its exact artifact is downloaded and inspected for package metadata, ABI and signature.

## Secrets and release

Never commit keystores, passwords, tokens, private keys or tunnel credentials. Use Android Keystore for device-bound secrets where supported. Logs and support bundles must redact secrets. Backups exclude device-bound credentials. Release host and companion APKs must share the signer certificate because the Binder permission is signature-protected.

## Handoff rule

Before each task: inspect current `main`, recent commits, relevant source, workflow and docs. Implement on the authorized branch, add regression tests, update docs in the same change set, run relevant checks, then report exactly what changed and what remains. Never ask the owner to perform device tests while source/build/CI work remains incomplete; physical-device acceptance belongs at the appropriate release gate.


## Build sequencing — owner instruction

- Do not build host/companion APKs, compile Android source, run Android emulators or perform device tests during Gates 0–7.
- Continue source implementation, code review, non-Android quality CI and documentation normally.
- Android integration and signed-release workflows are manual-only. Invoke them only at Gate 8 after the remaining system implementation is complete.
- At Gate 8, build all declared ABIs, run API 26/33/35 instrumentation, inspect signer/package metadata and then hand the APKs to the owner for physical-device acceptance.
- Dependency resolution and Gradle wrapper validation are not APK builds and may run during implementation.
- Build inputs: Rust 1.88.0, Gradle wrapper 8.9 with checksum, Java 17, SDK 35, Build Tools 35.0.0, NDK 27.2.12479018, CMake 3.22.1, cargo-ndk 4.1.2, Python 3.11 and committed Cargo/Gradle/npm lockfiles.
