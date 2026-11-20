# Pocket Host — Repository Baseline

**Baseline date:** 2026-11-20  
**Active branch:** `Alpha`  
**Current head at this checkpoint:** `3b127e785224165c9f2415df9ce013e60b2160a7`  
**Original planning baseline:** `8879ead946fe985d056970d1614068d1baea562b`  
**Original pre-reset snapshot:** `7d829e09162882dbbd467cc6e6a4a9cdfadcf028`  
**Archive branch:** `archive/pre-replan-2026-11-20`

This is the current engineering baseline for Pocket Host v0.1.0 Alpha. It distinguishes source presence, automated verification, device verification and operational verification. It does not treat source presence as proof of a working release.

## 1. Repository condition

The Alpha branch contains the Android application and supporting source after a documentation/workflow reset. The source tree was preserved during that reset; 52 documentation/workflow files were removed. The original state is retained on the archive branch.

Main areas:

| Area | Current contents |
|---|---|
| `app/` | Android Kotlin/Jetpack Compose app, services, UI, native integration and Android tests |
| `rust-engine/` | Rust static website hosting engine and JNI integration |
| `relay-node/` | Rust native relay/gateway and JNI integration |
| `runtime-worker/` | Separate Android companion APK with Python/Node runtime components |
| `runtime-library/` | Runtime catalogue and related assets |
| `control-plane/` | Rust/Axum control-plane service, migrations and deployment configuration |
| `web-console/` | React/Vite/TypeScript web console |
| `agents/` | Linux agent and installation/operation code |
| `protocol/` | Device protocol schemas and role/capability policy |
| `tests/`, `scripts/` | Cross-component tests and validation helpers |
| `Assets/` | Application assets |

## 2. Android application baseline

- Application ID/namespace: `com.assayflux.pockethost`.
- Compile SDK 35, minimum SDK 26, target SDK 35.
- Version code 1, version name `0.1.0`.
- Java/Kotlin target 17.
- Native build matrix: `arm64-v8a`, `armeabi-v7a`, `x86_64`.
- Release signing is conditional on protected keystore environment variables. Production signing is not established by repository configuration alone.

The app includes hosting, website/file management, workbench, deployments, devices, network/tunnel controls, runtimes, activity/logs, learning and settings. UI/source presence does not establish end-to-end readiness.

## 3. Hosting and relay architecture

### Static hosting

The Rust engine and Android integration provide local static hosting, site storage, import and file operations. Source includes bounded import/serving limits, backup/restore and release-history concepts. Physical-device loopback/LAN, multi-site isolation and backup recovery remain owner acceptance requirements.

### Native relay

The selected architecture is native Android/Kotlin foreground service plus Rust/JNI. Do not restore the old Termux relay approach.

Source includes device identity, encrypted configuration, SQLite-backed relay state, pairing, credential operations, route handling, TLS/ACME and network recovery. Rust relay source includes HTTP-01 ACME issuance and persistent relay state. These implementations still require end-to-end evidence for owner-approved pairing, credential rotation/revocation, DNS/TLS issuance and renewal, route isolation, restart persistence, network changes and external visitor access.

Public reachability depends on the network path. CGNAT/firewall restrictions without a usable public edge or global IPv6 can prevent ordinary direct inbound hosting. The product must report this rather than implying universal reachability.

## 4. Runtime and remote-operation baseline

- Separate runtime-worker Android application includes Python 3.11, pinned Node.js mobile runtime, native bridges and IPC.
- Runtime trust boundary is owner-trusted application code; this is not a hardened hostile-code sandbox and does not provide per-project UID isolation.
- Runtime transfer limits, companion caller/signature checks and remote command idempotency are documented in `docs/RUNTIMES_AND_REMOTE_OPERATIONS.md`.
- Rust/Axum control plane, PostgreSQL migrations, WebAuthn/session/audit foundations, React web console and Linux agent exist.

Remote management is optional to local static hosting. It must not become a dependency for local site serving.

## 5. Verification history

### Step 1

- Quality workflow run [44721249398](https://github.com/AssayFlux/PocketHost/actions/runs/44721249398) passed Rust engine/relay tests, PostgreSQL-backed control-plane tests/migrations, Python tests, protocol checks, web-console build, Node smoke test and script/proxy checks.
- Android native and source validation run [44721249324](https://github.com/AssayFlux/PocketHost/actions/runs/44721249324) passed three-ABI native compilation/ELF checks and Android app/instrumentation/runtime-worker source compilation.
- APK packaging, emulator execution and physical-device acceptance were not part of those passes.

### Step 2

- Website path, symlink, sensitive-file, site-root and persisted-route regression coverage was added.
- Quality run [44726189328](https://github.com/AssayFlux/PocketHost/actions/runs/44726189328) and Android run [44726189341](https://github.com/AssayFlux/PocketHost/actions/runs/44726189341) passed their recorded hosting/relay tests, Android source compilation and three-ABI native builds.
- Physical-device import, loopback/LAN, multi-site and backup/restore acceptance remains unverified.

### Steps 3–4

- Relay boot recovery, transactional configuration use, TLS certificate reuse/startup checks and bounded network reconnection are implemented in source.
- Runtime project transfer boundaries, companion IPC identity/signature checks, supported Python/Node scope and remote-command durable idempotency are implemented/documented.
- Quality workflow run [44730596020](https://github.com/AssayFlux/PocketHost/actions/runs/44730596020) passed the recorded Rust hosting/relay and control-plane checks.
- Android run [44730595964](https://github.com/AssayFlux/PocketHost/actions/runs/44730595964) passed three-ABI native builds and app/instrumentation/runtime-worker source compilation.
- These are implementation and automated CI evidence, not device or public-network acceptance.

### Step 5 and latest checkpoint

- API-26 lint and Compose source issues were corrected in commit `e4fc23129c1bf2758d73bf2b00358dba6e2d0804`.
- Chaquopy's Maven repository was added to dependency resolution after the earlier worker packaging attempt could not resolve `com.chaquo.python.runtime:bootstrap:17.2.0`.
- The Alpha acceptance workflow now runs Android lint/JVM tests and a non-packaging `generateDebugPythonMiscAssets` dependency gate. APK assembly, emulator instrumentation and APK verification require the explicit manual `confirm_build=true` input.
- Latest head: `3b127e785224165c9f2415df9ce013e60b2160a7`.
- Latest quality workflow [44737757283](https://github.com/AssayFlux/PocketHost/actions/runs/44737757283) completed successfully on that head.
- Acceptance workflow [44737757242](https://github.com/AssayFlux/PocketHost/actions/runs/44737757242) was observed pending/queued with no jobs reported at the time of this checkpoint. Therefore the post-fix Android lint/JVM and Chaquopy asset-task gate are not yet evidenced as passed.
- No APK was built by this checkpoint. No emulator execution, physical-device test, real DNS/TLS issuance, external visitor journey or production signing verification is claimed.

## 6. Remaining release gates

1. Obtain a completed result for the post-fix Android lint/JVM and Chaquopy runtime asset task.
2. Review dependency/licence/security audit outputs, including the previously reported web-console npm advisories.
3. Execute API 35 x86_64 emulator instrumentation when owner-authorized packaging/test execution is permitted; record skips and failures accurately.
4. On a physical Android device, test install, foreground-service lifecycle, reboot/process-death recovery, network changes, local hosting, import, multi-site isolation, backup/restore and Python/Node trusted workloads.
5. On a controlled real hostname/network, test owner pairing, credential rotation/revocation, DNS proof, TLS issuance/renewal, route isolation and independent external visitor access.
6. Review signing, exported components, permissions, secrets and log redaction. Production signing requires owner-provided protected signing material; debug signing is for owner testing only.
7. Only after explicit owner authorization, package and verify the Alpha APKs. Packaging is not performed in this pass.

## 7. Evidence vocabulary

- **Present:** source/configuration exists.
- **Automated verified:** a named automated check passed on a recorded commit.
- **Device verified:** tested on a physical Android device with model/API/build recorded.
- **Operational verified:** tested through the intended real network/public visitor path.

Never promote a feature to verified from source inspection or UI presence alone.


## 8. Credential-rotation persistence correction (2026-11-20)

A focused source review found that configured relay-host credential verifiers were only inserted when absent. Updating the owner-managed encrypted relay configuration therefore did not replace an existing SQLite verifier, so a rotated token could fail after relay restart. Commit [f2f6ec5](https://github.com/AssayFlux/PocketHost/commit/f2f6ec518062b0f6b5ed169cd046b8f1c9b50193) changes configured-host loading to an SQLite upsert and adds a regression test that rotates the configured token, restarts the relay state and asserts the old token is rejected while the new token authenticates. Paired hosts not represented in the explicit config remain database-managed. The fix is committed; its CI run is pending/in progress and is not yet marked verified.
