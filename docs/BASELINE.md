# Pocket Host — Repository Baseline

**Baseline date:** 2026-11-21  
**Active branch:** `Alpha`  
**Audited source snapshot:** `218d25793b7b7104e4c5583c716bb53586cdf156`  
**Original planning baseline:** `8879ead946fe985d056970d1614068d1baea562b`  
**Original pre-reset snapshot:** `7d829e09162882dbbd467cc6e6a4a9cdfadcf028`  
**Archive branch:** `archive/pre-replan-2026-11-20`

This document contains historical checkpoints followed by the current audit addendum in Section 16. Section 16 and the linked engineering audit supersede earlier status snapshots for current-branch state. It distinguishes source presence, automated verification, device verification and operational verification. It does not treat source presence as proof of a working release.

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


## 8. Step 1 implementation checkpoint — 2026-11-20

**Implementation commit:** `8c95a42afe12ebceffeaede2807e702a02b71ce8`

- ZIP document selection now offers all MIME types because Android document providers inconsistently label ZIP archives. The importer remains responsible for content validation; selecting an arbitrary file does not bypass archive checks.
- ZIP website-root discovery now supports a built website nested under project metadata folders, including common `dist/` and `build/` layouts. A root-level `index.html` remains preferred. Equally shallow competing entry folders are rejected with an actionable ambiguity message instead of guessing.
- Empty or unreadable ZIP archives receive a specific validation error.
- ZIP import summaries report the files and bytes in the activated website root, not unrelated archive metadata files.
- The Sites overview now displays import-related progress and result/error status.
- Instrumentation coverage was extended for a project archive containing README plus `dist/index.html`, and for ambiguous build roots.
- Existing path traversal, sensitive-path, case-collision, file/directory collision, entry-count, file-count and 500 MB bounds remain in place.

**Not yet verified:** No Android compilation, unit/instrumentation execution, provider-level picker test, APK assembly, emulator run or physical-device test has been run for this checkpoint. The added instrumentation tests are source changes only.

**Branding gate (implemented in Step 3):** The canonical `Assets/Pocket Host 7.svg` is the only artwork source. A Gradle `buildSrc` task uses Apache Batik to rasterize it at build time into density-specific launcher PNGs; the Compose owner header/sidebar reuse the generated artwork resource. The manifest declares the Pocket Host launcher/round icon and app label. Runtime SVG parsing and a duplicate SVG asset in the APK are avoided. These are source/build-pipeline changes; CI compilation is required before treating them as verified.


## 9. Workbench file-management Step 2 checkpoint — 2026-11-20

Implementation commits on `Alpha`:
- `b0b6ab3` — rollback Workbench rename when post-move validation fails; add safe recursive draft-entry deletion.
- `199d4a1` — expose folder rename and delete actions in Explorer.
- `e83c36e` — add regression coverage for folder deletion isolation.
- `2e82bed` — route the Explorer confirmation flow through the safe file-or-folder deletion operation.
- `197da44` — document scope and verification boundary in `docs/WORKBENCH_FILE_MANAGEMENT.md`.

The Workbench now allows folder rename and recursive folder deletion in addition to file operations. Folder deletion is limited to the selected site's private draft workspace; the operation rejects symlinks and invalid targets, validates the subtree before deletion and revalidates the workspace afterward. Rename attempts rollback if validation fails. The Android instrumentation regression asserts that deleting a draft folder leaves live website content unchanged.

**Verification boundary:** source and test changes are committed; no Android build, JVM/instrumentation run, emulator, physical-device acceptance or APK build has been performed for this checkpoint. Keep these as open verification items.


## 10. Final integration — Step 3 checkpoint (2026-11-20)

Implementation commits on `Alpha` include:
- `986a3ce`, `0c6a2d9`, `c8bb01a`, `ece9832` — initial canonical SVG launcher and app-chrome integration.
- `a30f148` — record the final integration and canonical SVG branding.
- `1580855` — add CI validation coverage for buildSrc and brand assets.
- `53ff9df` — document the canonical branding pipeline.
- `d61c09c`, `29542ea` — use build-time pre-rasterized artwork in Compose; no runtime SVG parsing.

The current implementation uses `Assets/Pocket Host 7.svg` as the single source of truth. Apache Batik in `buildSrc` rasterizes the SVG during Android resource generation into density-qualified PNG resources for mdpi through xxxhdpi. Legacy launcher artwork is generated at 48dp; adaptive foreground artwork is generated at 72dp and inset by 18dp in the 108dp adaptive-icon viewport. The adaptive icon background is the documented brand navy. Compose owner header and sidebar render the generated `R.mipmap.ic_launcher_artwork`; the manifest declares `@mipmap/ic_launcher` and the Pocket Host app label. Generated PNGs are build outputs, not manually maintained files.

**Verification boundary:** Android integration run `44745388758` is still running its three-ABI native build; its dependent Android source-compile job has not started yet. Quality run `44745388887` was cancelled before producing a result. APK-gated acceptance run `44745388739` remains queued with no jobs reported. Earlier runs were cancelled as newer commits superseded their heads. Therefore there is no current-head quality or Android app compile pass yet; do not claim the integrated branch is CI-verified until successful runs complete. No APK was assembled. Launcher mask, in-app logo appearance and physical-device behavior remain device-acceptance checks.

## 11. Step 1–3 integrated recheck (2026-11-20)

| Step | Implementation recheck | Remaining verification |
|---|---|---|
| 1 — ZIP import and brand asset | ZIP picker accepts provider-agnostic file types; importer validates archive content, handles built-site `dist/` and `build/` roots, rejects ambiguous roots and empty/unreadable archives, reports activated-root counts, and retains path/sensitive-file/collision/size bounds. Canonical SVG now feeds launcher and in-app artwork through the build-time rasterizer. | Current-head Android CI must compile the Batik buildSrc task, generate PNG resources and compile app code. Provider-level picker behavior and launcher rendering need emulator/device checks. |
| 2 — Workbench file management | Explorer supports file/folder create, rename and delete flows; recursive deletion is confined to the private per-site draft workspace, rejects unsafe targets and does not mutate the live site. Rename validation failure attempts rollback. Regression coverage checks folder deletion isolation. | Current-head Android compile/instrumentation execution and hands-on file/folder/editor workflows remain to be recorded. The supplied visual reference has not been independently confirmed pixel-for-pixel; this checkpoint is a functional file-management implementation, not a claim of exact visual reproduction. |
| 3 — Final integration | Canonical brand source, Android launcher resources, in-app mark, Workbench file-operation wiring and implementation documentation are integrated on `Alpha`. | Wait for a successful quality run and for Android integration run `44745388758` (including its dependent Android source-compile job) to finish. No APK, emulator, physical-device or external-network acceptance is claimed. |

**Overall status:** Steps 1–3 implementation changes are present on `Alpha`; current-head automated verification is pending. This is still v0.1.0 Alpha, not a verified release. Do not build/package APKs until the owner explicitly moves to the APK phase. No APK build was run during this implementation pass.

## 12. Android build-pipeline repair pass — 2026-11-20

After the Step 1–3 integrated recheck, the latest Android acceptance logs identified two build-pipeline blockers:

- `buildSrc` could not resolve `org.apache.xmlgraphics:batik-transcoder:1.18` from Maven Central. The buildSrc dependency is now pinned to Batik 1.19, which is published in Maven Central.
- Launcher icon rasterization failed with a generic task message. The task now includes the underlying exception class/message and explicitly fails if the JDK has no PNG ImageIO writer, so the next CI run can identify the actual SVG/transcoder cause instead of hiding it.
- The APK acceptance workflow now watches `buildSrc/**` and the canonical SVG asset, ensuring changes to launcher generation trigger the acceptance validation job.

Commits on `Alpha`:
- `2a8746929a699bd4251e4a06a32848318ce5fb23` — Batik dependency resolution.
- `d35725cc0dd0304b8438d42b29f0f83683a407ea` — actionable rasterizer diagnostics and PNG writer validation.
- `40137c61add97edca2f0d010bb9ec6f52f9a599a` — acceptance workflow path coverage for buildSrc and canonical SVG.

**Verification boundary:** These are committed build-pipeline repairs, not yet successful build evidence. Wait for the new Alpha CI runs. APK packaging remains explicitly gated and was not triggered by these pushes.


## 13. Batik PNGTranscoder codec dependency repair — 2026-11-20

The latest Android acceptance failure occurred inside Batik's `PNGTranscoder` before the generated raster could be read/scaled by the JDK ImageIO path. The diagnostic was `Could not write PNG file because no WriteAdapter is available`. The `batik-transcoder` artifact alone did not provide the PNG write adapter at runtime.

`buildSrc/build.gradle.kts` now declares `org.apache.xmlgraphics:batik-codec:1.19` explicitly alongside `batik-transcoder:1.19`. The versions are aligned. This is intended to supply Batik's PNG ImageIO write adapter; it is a dependency-level repair, not yet a verified build result.

Implementation commit: `2980fec7df4e5f2a92fe4996b812c612cb2087b2`.

The Android acceptance workflow watches `buildSrc/**`, so this Alpha push should start a fresh workflow run automatically. Confirm that the icon-generation task passes before treating the repair as verified or proceeding to APK assembly.


## 14. Batik artifact resolution follow-up — 2026-11-20

The first codec-dependency repair did not reach lint or icon generation: Gradle reported that Maven Central could not resolve `org.apache.xmlgraphics:batik-codec:1.19` from its requested POM URL. Although artifact indexes list the 1.19 module, the actual Alpha runner did not resolve it. To avoid repeating that failing resolution, `buildSrc` now aligns both `batik-transcoder` and `batik-codec` on the published Batik 1.20 release.

Commit: `0efb6a379d7345131d4b30a91f6ba8956e524f60`.

This is a second dependency-resolution attempt, not a verified fix. The next Android acceptance run must demonstrate successful dependency resolution, `:buildSrc:compileJava`, launcher PNG generation, and the lint/JVM test tasks before the rasterizer issue can be considered closed.


## 15. Separate Android quality and APK packaging workflows — 2026-11-20

The Android CI responsibilities are now split into two independent workflows:

- `.github/workflows/android-debug-apk.yml` is the push-triggered Android quality workflow. It runs Android lint, JVM unit-test tasks and the non-packaging Python runtime asset prerequisite check. It does not assemble APKs or run emulator instrumentation.
- `.github/workflows/android-alpha-apk.yml` is the manually dispatched APK/ABI builder. Its ABI selector supports `all`, `arm64-v8a`, `armeabi-v7a` and `x86_64`. It builds the companion runtime-worker APK, builds the app APK for each selected ABI, verifies package metadata, debug signatures and native library payloads, calculates SHA-256 checksums and uploads a dedicated 30-day artifact.

To build all app ABIs, open **Actions → PocketHost v0.1.0 Alpha APK and ABI packaging → Run workflow**, select branch `Alpha`, and leave ABI set to `all`. A single-ABI build can be selected when only one app target is needed. The companion worker APK is built on every invocation and is checked for all three Node native ABI payloads.

The packaging workflow uses the Android debug signing identity and is for development/owner testing only. It does not create a production-signed release. Emulator instrumentation and physical-device acceptance remain separate verification activities.

**Workflow separation commit:** this documents the intended split; a successful workflow run is required before claiming that any APK artifact exists.

## 16. Current engineering audit and Cloudflared CI remediation — 2026-11-21

Use [ENGINEERING_AUDIT_2026-11-21.md](ENGINEERING_AUDIT_2026-11-21.md) as the current audit summary. Earlier checkpoint sections above are historical records and must not be read as the current branch status.

The current source snapshot inspected for the audit was `218d25793b7b7104e4c5583c716bb53586cdf156`. The following engineering corrections were committed directly to `Alpha`: 
- `ca62487` — harden the pinned Cloudflared Android build script, validate toolchain prerequisites, remove stale ABI outputs and verify executable outputs.
- `f954703`, `439c5da`, `fb8de4e` — integrate Cloudflared build, ELF/permission checks, dedicated CI artifact upload/download and restoration before Android source compilation.
- `7b886df` — keep APK packaging manual-only and require Cloudflared in the selected APK ABI payload, with ELF and license checks.
- `f2a9305` — ignore generated `.build/` workspaces.
- `effa271` — add the current engineering audit and remediation record.

**Important verification boundary:** the pipeline is now configured to build and verify Cloudflared, but configuration is not a passing CI result. The post-remediation GitHub Actions runs must complete successfully before this integration can be marked verified. No APK was assembled, signed, uploaded or installed. No API emulator or external-network tests were run.

**Current v0.1.0 status:** implementation and CI wiring are substantially present; current-head automated evidence and owner device acceptance remain outstanding. This is not a production-ready certification.