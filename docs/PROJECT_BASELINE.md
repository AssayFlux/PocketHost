# Pocket Host 0.1.0 — Repository Baseline

**Repository:** [AssayFlux/PocketHost](https://github.com/AssayFlux/PocketHost)  
**Default branch:** `Alpha`  
**Product baseline:** `0.1.0`  
**Audit scope:** source tree at the Alpha branch, 236 tracked files at the audit snapshot.  
**Release state:** pre-release engineering baseline; not production certified.

## Product identity and invariants

Pocket Host is a mobile-first personal infrastructure system. The mobile device is always the website-serving node. It has exactly two mutually exclusive operating modes:

| Mode | Website server | Fleet administration |
|---|---|---|
| Server only | Enabled | Disabled |
| Command Center + Server | Enabled | Enabled |

There is no Command Center-only mode. External devices use separate platform-specific software and are integrated only after cryptographic verification and owner approval. A device receives narrowly scoped, revocable roles rather than unrestricted remote access.

## Repository implementation inventory

| Component | Source location | What is actually present | Current status |
|---|---|---|---|
| Android Pocket Host app | `app/` | Kotlin, Jetpack Compose, hosting foreground service, Rust JNI engine integration, site import/registry/backup, local management API, account/biometric flows, diagnostics, tunnel controls, runtime companion clients and device-trust UI | Implemented foundation; product acceptance open |
| Rust website engine | `rust-engine/` | Tokio/Hyper HTTP server, static site delivery, host routing and native Android library | Source and host tests present; Android device acceptance open |
| Runtime companion | `runtime-worker/` | Separate APK/package and UID; CPython 3.11 via Chaquopy; Node.js mobile 24.22.1 via JNI/libnode; Binder services, project transfer, process lifecycle and network/runtime probes | Both integrations in progress; not production sandboxed |
| Python runtime | `runtime-worker/src/main/python/` | WSGI adapter, loopback serving, multi-host routing, bounded workers/body/timeouts and metrics | Existing implementation; Android acceptance and independent package split open |
| Node.js runtime | `runtime-worker/src/main/cpp/`, `src/main/java/` | JNI/libnode bridge, pinned native SDK for arm64-v8a/armeabi-v7a/x86_64, foreground service, bounded heap and metrics | Existing implementation; Android acceptance and independent package split open |
| Website file foundation | app/.../storage/SiteFileManager.kt, PocketHostSitesScreen.kt | Bounded list/read/write/create/upload/rename/delete/index operations and a Websites & Files screen; current editing targets the deployed site tree | Useful foundation only; isolated Workbench workspace, full editor, diffs/commits, preview separation and atomic release publishing are not implemented |
| Android trust | `app/.../management/DeviceTrustStore.kt`, Devices UI | Keystore P-256 owner identity, signed short-lived challenges, metadata-bound candidate proof, role grants, encrypted local registry and generation revocation | Foundation implemented; transport and end-to-end acceptance open |
| Local management API | `ManagementApiServer.kt` | Loopback management API with security/instrumentation tests | Implemented foundation; full product/API acceptance open |
| Remote control agent | `RemoteControlAgent.kt`, `FleetControlPlaneClient.kt` | Android control-plane client/agent foundation | Partial integration; end-to-end fleet workflows open |
| AssayFlux Relay | `RelayAgent.kt`, `control-plane/src/relay.rs` | Bounded HTTP request/response visitor relay path | Source implementation; public deployment and external acceptance not proven |
| Cloudflare Tunnel | `tunnel/`, Android JNI assets/build script | Android cloudflared connector, encrypted token storage, lifecycle integration; Android ABI build helper | Integration present; external/physical acceptance open |
| Remote control plane | `control-plane/` | Rust/Axum API, PostgreSQL migrations, sessions/passkeys foundation, device commands, audit, artifact metadata and Compose/Caddy deployment | Backend foundation; production deployment and end-to-end acceptance open |
| Browser operator console | `web-console/` | React 18, TypeScript 5.7, Vite 6, separate browser UI | Builds independently in CI; not bundled in Android app |
| Linux Node Agent | `agents/linux/` | Python package, P-256 identity, owner challenge/grant/job verification, SQLite durable jobs, HTTPS polling preview and allow-listed health/storage/backup snapshot executors | Preview foundation; production/physical acceptance open |
| Protocol contracts | `protocol/` | Versioned JSON Schemas, default-deny role/job policies and validator/tests | Contract implementation present; gate evidence tracked separately |
| Future Runtime Library | `runtime-library/catalog/` | 20 JSON manifests; Python and Node marked in progress, 18 others planned | Catalog metadata only; no independent runtime package artifacts or Runtime Manager |
| DemonCore | External project | Link to separate AssayFlux/DemonCore firmware and board profiles | External dependency; Pocket Host integration/board acceptance open |

## Technology boundaries

- Main app default web capability: HTML, CSS and browser JavaScript static serving through the bundled Rust engine.
- Main app TypeScript authoring: a bundled TypeScript-to-JavaScript compiler is a required Step 4 feature, **not present/verified in the current Android dependency graph**.
- React + TypeScript + Vite belong only to the separate `web-console/` operator client.
- Python and Node.js are already implemented in the separate `runtime-worker/` companion APK, currently shipped together. They are optional server-side runtimes and are not required for static sites.
- The 20-language Runtime Library is future work after the five-step baseline. Its manifests are not runtime binaries or downloadable packages.

## Platform and role coverage

| Target | Evidence in this repository | Honest support status |
|---|---|---|
| Android phone/tablet | Main app and runtime companion Gradle modules; app compile/instrumentation sources; ABI-targeted native build | Android foundation only; physical-device acceptance still required |
| iOS | No iOS source project or native application in this repository | Planned, not implemented |
| Linux | Independently installable Python Node Agent preview package and tests | Preview only; not production-certified |
| Windows/macOS | No platform agent installers/services in this repository | Planned |
| Raspberry Pi/SBC | Linux agent may be a starting point, but no named board acceptance profile | Planned/conditional |
| MCU | DemonCore is a separate repository/project | External integration foundation; per-board acceptance open |

## Verification evidence and limitations

The quality workflow includes:
- Rust engine tests.
- Python runtime and Linux agent unit tests.
- React/TypeScript/Vite console build.
- Node runtime metrics smoke test.
- Gradle wrapper validation.
- Control-plane tests, database migrations and deployment configuration checks.

Historical CI evidence: quality run [44046355538](https://github.com/AssayFlux/PocketHost/actions/runs/44046355538) completed successfully across six jobs at the earlier main-branch snapshot. Alpha branch workflows have since been reconfigured; consult the live Alpha workflow history for current evidence. CI is not proof of Android runtime behavior or physical-device acceptance.

The Android integration workflow has compiled app and instrumentation sources in earlier runs. Emulator and APK packaging jobs were skipped in the compile-only run. The release workflow is manual and has not established a signed production release in this audit. No APK is claimed as built or physically tested here.

## Known open engineering gates

1. Step 1: preserve schema/policy parity and confirm protocol CI evidence on Alpha.
2. Step 2: complete owner step-up key authentication, authenticated transport, remote control-plane integration, agent provisioning/revocation and physical Android tests.
3. Step 3: close Linux agent security/lifecycle gaps and implement other platform packages and missing role adapters.
4. Step 4: bundle and test TypeScript compiler; implement Workbench workspace isolation, full project editor, diffs/commits, isolated preview, immutable staged releases, owner-approved atomic deploy/rollback and Android acceptance.
5. Step 5: public ingress/relay operations, backup/restore rehearsals, resilience, security review, platform matrix and signed release evidence.
6. Post-baseline Runtime Library: split Python and Node into independently signed packages; then implement and distribute further language packs.

## Normative documents

Start with [System Architecture](SYSTEM_ARCHITECTURE.md), [Master Plan](MASTER_PLAN.md), [Development Context](DEVELOPMENT_CONTEXT.md), [Device Software Matrix](DEVICE_SOFTWARE_MATRIX.md), [Step 1](STEP_1_TRUST_AND_PROTOCOL.md), [Step 2](STEP_2_DEVICE_TRUST_IMPLEMENTATION.md), [Step 3](STEP_3_DEVICE_AGENTS.md), [Step 4](STEP_4_WEB_BASELINE.md) and [Web Toolchain Boundaries](WEB_TOOLCHAIN_BOUNDARIES.md). This baseline inventory describes source presence; the acceptance documents determine whether a capability is supported.
