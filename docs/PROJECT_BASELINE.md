# Pocket Host — Repository Baseline

**Repository:** AssayFlux/PocketHost  
**Branch audited:** Alpha  
**Audit baseline commit:** afb0cceadfabca840590eb42eb4b5f3aaf9eac3e  
**Release target:** v0.1.0 — First Hosting  
**Certification state:** pre-release; physical Android acceptance not yet established by this audit

This file records source presence and known workflow evidence. It is not a claim that all listed features are complete or production-ready.

## Product invariants

| Mode | Website serving | Fleet administration |
|---|---|---|
| Server only | Enabled | Disabled |
| Command Center + Server | Enabled | Enabled when fleet implementation is accepted |

There is no Command Center-only mode. The phone is the serving node in both modes. Device enrollment and fleet administration are later-release functionality.

## Implementation inventory

| Component | Source location | Source-audited implementation | Honest status / release |
|---|---|---|---|
| Android main app | app/ | Kotlin/Compose UI, hosting foreground service, Rust JNI engine, site import/registry/backup, local API, account/biometric flows, diagnostics, tunnel and runtime client integration, device-trust UI | Foundation; v0.1.0 hosting acceptance open |
| Rust website engine | rust-engine/ | Tokio/Hyper static HTTP serving, host routing, bounded connections and native Android library | Source/tests present; v0.1.0 device acceptance open |
| Website importer | app/.../storage/SiteImporter.kt | Folder and ZIP import, staging, path/name checks, index validation, activation/recovery | v0.1.0; 500 MB limits being aligned with code/UI/tests |
| Full-app backup | app/.../storage/ApplicationBackupManager.kt | Passphrase-encrypted static site, optional runtime projects, routes and allow-listed settings | Current limits: 50 MiB static site, 50 MiB Python, 100 MiB Node, 200 MiB total uncompressed, 220 MiB encrypted archive; does not cover a 500 MB imported site |
| Website file UI | PocketHostSitesScreen.kt and SiteFileManager.kt | Bounded list/read/write/create/upload/rename/delete/index operations | Existing site-file foundation; not a complete Workbench |
| Workbench | WorkbenchWorkspaceManager.kt, PocketHostWorkbenchScreen.kt | Per-site private draft workspace, nested explorer, basic text editor, draft save, file/folder operations, change digest and immutable local commit snapshots | Initial foundation; v0.1.1 completion |
| Runtime companion | runtime-worker/ | Separate APK/UID; CPython 3.11 via Chaquopy; Node.js mobile 24.22.1 via JNI/libnode; Binder IPC, project transfer and lifecycle controls | In progress; v0.1.1 acceptance |
| Python | runtime-worker/src/main/python/ | WSGI adapter, loopback serving, host routing, bounded workers/body/timeouts and metrics | Existing integration; Android acceptance and isolation open |
| Node.js | runtime-worker/src/main/cpp and src/main/java | JNI/libnode integration, pinned mobile SDK and native ABI support | Existing integration; Android acceptance and isolation open |
| TypeScript authoring | Main app | No verified bundled compiler in current Android dependency graph | v0.1.1 requirement, not v0.1.0 |
| Browser operator console | web-console/ | React, TypeScript and Vite browser application | Separate console; not an Android runtime |
| Android device trust | DeviceTrustStore.kt and Devices UI | P-256 owner key, short-lived challenge, proof checks, role grants, encrypted local registry and generation revocation | Foundation; v0.2.0 acceptance |
| Linux agent | agents/linux/ | Python package, P-256 identity, grant/job checks, SQLite durable jobs, HTTPS polling preview and allow-listed health/storage/backup snapshot tasks | Preview; v0.2.0 acceptance |
| Control plane | control-plane/ | Rust/Axum API, PostgreSQL migrations, session/passkey foundations, device commands, audit and deployment configuration | Foundation; v0.2.0 acceptance |
| AssayFlux Relay | RelayAgent.kt and control-plane/src/relay.rs | Bounded HTTP request/response visitor path | Source foundation; public deployment/acceptance v0.2.0 |
| Cloudflare Tunnel | tunnel/ and Android native assets | Android cloudflared connector build/lifecycle and encrypted token store | Integration source present; external acceptance v0.2.0 |
| Protocol | protocol/ | Versioned JSON Schemas, role/job policy and validator/tests | Contract foundation; production device integration v0.2.0 |
| Runtime Library | runtime-library/catalog/ | 20 JSON catalog manifests; Python/Node in progress, remaining 18 planned | Metadata only; no independent package artifacts |
| DemonCore | External project | Separate firmware and board profiles | External dependency; adapter acceptance later |

## Technology boundaries

- v0.1.0 default: HTML, CSS and browser JavaScript through the Rust static server.
- The current Rust engine has a 50 MiB per-file response cap. It is distinct from website import limits.
- v0.1.0 import contract: 500,000,000 bytes maximum for the ZIP input and independently 500,000,000 bytes maximum for expanded site contents; 5,000 files, 10,000 entries and 32 directory levels.
- React + TypeScript + Vite belong to web-console/, not the main Android application.
- Built-in TypeScript-to-JavaScript compilation is assigned to v0.1.1 and is not verified in the current app dependency graph.
- Python and Node.js are in the separate runtime-worker companion APK, currently shipped together and not independently packaged.
- Python and Node share the companion app UID. This is not an OS-enforced per-project sandbox.
- Static website hosting must work when the runtime companion is absent or stopped.

## Platform matrix

| Target | Repository evidence | Support status |
|---|---|---|
| Android | Main app, companion module, Rust JNI build | Foundation; named physical-device acceptance open |
| Linux x86_64 | Preview agent package and tests | Preview only |
| Windows/macOS | No production agent package found in audited tree | Planned |
| Raspberry Pi/SBC | No named board acceptance profile | Planned |
| iOS | No iOS source project | Planned |
| MCU | Separate DemonCore project | External integration; board acceptance open |

## Build and CI interpretation

The quality workflow contains Rust, control-plane, Python/Linux agent, console, Node smoke and Gradle wrapper checks. Earlier CI runs are historical evidence, not proof of the current commit's Android runtime behavior.

The Android integration workflow has a compile-only path. Its manual APK job builds the main APK and companion APKs and can run emulator instrumentation when explicitly dispatched. Compile-only success is not an APK artifact. APK packaging is not physical-device acceptance.

## Known open items for the new roadmap

1. v0.1.0: prove 500 MB ZIP and expanded import boundaries, folder import, archive/path safety, correct static serving, local preview and same-Wi-Fi LAN access.
2. v0.1.0: verify foreground service, notification, stop/restart, network changes, process death, backup/restore limits and Android permission/power behavior; resolve backup/import capacity mismatch.
3. v0.1.0: build, install and test APK on named physical hardware; attach artifact hash and G0–G9 evidence.
4. v0.1.1: finish Workbench preview, diff, compiler, release staging, owner-approved activation and rollback.
5. v0.1.1: finish Python/Node Android acceptance, optional installation lifecycle and isolation hardening.
6. v0.2.0: finish device enrollment, authenticated transport, worker agents, control-plane integration, public ingress and cross-device recovery.

See [Repository Deep Audit](REPOSITORY_AUDIT.md), [Roadmap](ROADMAP.md) and [Release Gates](RELEASE_GATES.md).
