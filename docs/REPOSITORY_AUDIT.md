# Pocket Host — Repository Deep Audit

**Audit branch:** `Alpha` (confirmed repository default branch)  
**Repository:** [AssayFlux/PocketHost](https://github.com/AssayFlux/PocketHost)  
**Product baseline:** 0.1.0  
**Audit method:** recursive Git tree inventory, source/config inspection, cross-document consistency review, workflow inspection and available CI evidence.  
**Scope snapshot:** 236 tracked files before this audit's documentation updates; the count is a snapshot, not a permanent repository invariant.

## 1. Executive finding

Pocket Host is not a blank scaffold. It contains a meaningful Android hosting application, a native Rust static web engine, a separate Android Python/Node runtime companion, a partial cryptographic device-trust implementation, a Linux agent preview, an optional Rust control plane, a separate React/Vite browser console, and visitor-ingress integrations.

However, the repository is not yet a complete production device-fabric platform. Several architecture documents had drifted beyond the code: they described iOS as first-class, described the TypeScript compiler as if already built in, or implied that Python/Node were independent packages. This audit aligns those statements to the source.

## 2. Product boundaries confirmed

1. Mobile has exactly two modes: **Server only** and **Command Center + Server**. Website serving remains enabled in both.
2. There is no Command Center-only mode.
3. The main Android app owns the website-serving UI, hosting lifecycle, Rust ingress, site management and owner-facing controls.
4. The separate `runtime-worker/` APK contains the current Python and Node.js runtime implementations. It is optional for static websites.
5. The browser operator console is separate from the Android app.
6. External devices are intended to run separate OS/architecture-specific agent software.
7. The 20-language Runtime Library is post-five-step work; current catalog entries are manifests, not runtime downloads.

## 3. Component audit

| Component | Verified source footprint | What can be claimed | What must not be claimed |
|---|---|---|---|
| Android main app | `app/src/main`, `app/build.gradle.kts` | Kotlin/Compose app; foreground hosting service; site UI/import/registry; local API; diagnostics; tunnel and runtime client integration | Production-ready mobile hosting across all devices; completed full fleet command path |
| Native website engine | `rust-engine/`, JNI bridge | Rust Tokio/Hyper native library with static serving foundation | Complete web build pipeline or TypeScript compiler |
| Site lifecycle | `SiteImporter`, `SiteFileManager`, `SiteRegistry`, backup managers, site screens | Folder/ZIP/file import, hostname registry, staging/recovery and backup foundations | Full production CMS, arbitrary npm build, complete preview/atomic release acceptance |
| TypeScript authoring | No compiler dependency/source found in main app Gradle graph or app source tree | Requirement is defined in Step 4 | Built-in compiler currently bundled or working |
| Browser console | `web-console/package.json`, lockfile, Vite config and TSX source | React 18.3.1, TypeScript 5.7.2, Vite 6.0.5; independent CI build | Console is part of the Android app |
| Python | `runtime-worker/src/main/python/pockethost_runtime.py`, Chaquopy module | CPython 3.11 WSGI integration foundation, loopback serving, bounded workers/requests, host tests | Independent Python APK/package, hostile-code sandbox, physical acceptance |
| Node.js | `runtime-worker/src/main/cpp`, JNI/Kotlin worker services and pinned SDK fetch | Node.js mobile 24.22.1 native integration for arm64-v8a, armeabi-v7a and x86_64; host metrics smoke test | Independently downloadable Node package, complete Android ABI/device acceptance or secure sandbox |
| Runtime IPC | `app/.../runtime/`, `runtime-worker/.../worker/` | Typed Binder client/provider foundation, project transfer and lifecycle controls | Separate OS identity per project; package UID boundary is not a per-project sandbox |
| Android trust | `DeviceTrustStore.kt`, Devices screen and instrumentation source | P-256 owner key, signed five-minute challenges, candidate metadata proof, role grants, encrypted registry, generation revocation | End-to-end LAN/BLE/USB pairing, per-use Keystore key auth, production mTLS or agent automation |
| Control plane | `control-plane/src`, migrations, OpenAPI, Compose/Caddy | Rust/Axum + PostgreSQL service foundation, sessions/passkeys-related tables, commands, audit and deployment config | Production deployment or full mobile-to-agent fleet orchestration |
| Linux agent | `agents/linux/`, Python tests | P-256 identity, signed grant/job validation, durable SQLite jobs, HTTPS poll/ack preview, allow-listed health and local snapshot/backup operations | Production-grade mTLS, automatic grant renewal/revocation, full mobile provisioning, Windows/macOS/SBC support |
| AssayFlux Relay | `control-plane/src/relay.rs`, `RelayAgent.kt` | Bounded HTTP request/response visitor relay source path | General TCP/WebSocket tunnel or publicly accepted service |
| Cloudflare Tunnel | Android tunnel classes, token store, build helper | Android cloudflared integration foundation and encrypted token storage | External DNS/TLS reachability or 24/7 Android uptime guarantee |
| Backup | Site/application backup managers and tests | Android backup/restore and interrupted-recovery foundations | Universal cross-host backup or full restore certification |
| DemonCore | External repository reference only | Separate MCU/edge project dependency | Embedded DemonCore source or board acceptance in Pocket Host repository |

## 4. Build and workflow audit

| Workflow | Purpose | Observed limitations |
|---|---|---|
| `quality.yml` | Rust, Python/Linux agent, console, Node smoke, Gradle wrapper and control-plane checks | Does not build APKs or run Android instrumentation |
| `android-integration.yml` | Builds Android cloudflared native libraries and compiles app/instrumentation Kotlin | APK packaging and emulator tests are manual workflow-dispatch paths |
| `protocol-contracts.yml` | JSON Schema/policy validation and protocol trust simulations | Must be checked independently from quality workflow |
| `lock-dependencies.yml` | Resolve and commit dependency locks | Must push to the current default branch |
| `release.yml` | Manual signed Android release workflow | Requires protected signing secrets; no release is implied by workflow presence |
| `deploy-control-plane.yml` | Manual deployment to configured production host | Must fetch/clone the current default branch, Alpha |

Branch rename correction: repository metadata reports `default_branch=Alpha` and the only listed branch at the audit snapshot is `Alpha`. Workflow branch filters, dependency-lock pushes, deployment fetch/reset and clone operations must all target `Alpha`, not the former `main`.

## 5. CI evidence interpretation

The quality workflow at commit `4372d1c58eaa68514456e446729db2277967a59c` completed successfully across its six jobs: Rust, control plane, Python runtime/Linux agent, console, Node runtime smoke and Gradle wrapper. See [run 44046355538](https://github.com/AssayFlux/PocketHost/actions/runs/44046355538).

Earlier Android integration run `44031177422` compiled the app and instrumentation sources, but its emulator smoke and APK packaging jobs were skipped. Therefore it is not evidence of a completed Android instrumentation run or a distributable APK.

A green quality workflow is useful repository evidence, not proof of:
- Android foreground-service lifecycle on physical devices;
- Python/Node native runtime operation on every declared ABI;
- complete end-to-end device enrollment, remote grant, job, revocation and reconnect;
- public Relay/Cloudflare visitor reachability;
- production isolation of untrusted runtime projects;
- signed release or platform certification.

## 6. Cross-document corrections made in this audit

- Set repository canonical/default branch to `Alpha`.
- Changed workflow triggers, dependency-lock push target and control-plane deployment checkout to `Alpha`.
- Clarified that iOS is planned and absent from the current source tree.
- Clarified that React/TypeScript/Vite are only in `web-console/`.
- Clarified that the main-app TypeScript compiler is a Step 4 requirement, not an implemented dependency.
- Documented Python and Node.js as existing in-progress integrations inside one separate companion APK.
- Clarified that the companion package UID is not per-project isolation.
- Distinguished the Runtime Library's 20 manifests from actual downloadable runtime packages.
- Updated the implementation inventory and README to avoid conflating code presence, CI success and device acceptance.

Workbench source added since the initial audit snapshot: `WorkbenchWorkspaceManager.kt`, `PocketHostWorkbenchScreen.kt` and `WorkbenchWorkspaceManagerTest.kt`. The app navigation now exposes Workbench in both operating modes. The first implementation isolates editable drafts from the live website, provides bounded text editing and local immutable commit snapshots. Android integration run [44050320129](https://github.com/AssayFlux/PocketHost/actions/runs/44050320129) compiled the app and instrumentation sources successfully; emulator execution and APK packaging were skipped. This does not establish physical-device acceptance or production deployment readiness.

## 7. Priority engineering risks

1. **Android lifecycle and device acceptance:** compile success is not runtime acceptance; verify foreground service, Doze, battery saver, screen-off, reboot and process death.
2. **Runtime isolation:** Python and Node share the companion UID; do not market this as a hostile multi-tenant sandbox.
3. **Trust integration:** mobile trust store, control plane and Linux agent need a complete authenticated end-to-end lifecycle, including credential delivery, revocation and status.
4. **Transport gap:** LAN mutual authentication and discovery are incomplete; BLE/USB are not implemented acceptance claims.
5. **Step 4 gap:** TypeScript compiler, build diagnostics, Workbench diff/preview, validated release staging, owner-approved atomic publish and rollback must be implemented and tested.
6. **Visitor ingress:** Relay is bounded HTTP request/response; public deployment, TLS/DNS, abuse controls and external acceptance remain.
7. **Release engineering:** manual release workflow needs protected signing credentials and verification; no production release should be claimed before the full matrix passes.

## 8. Gate discipline

- Keep Step 1–5 open until their own exit criteria have evidence.
- Keep the future Runtime Library separate from the five-step baseline.
- Do not build the final APK until all five steps pass their implementation and acceptance gates, as required by the project delivery plan.
- Update this audit when the source tree or platform claims materially change.
