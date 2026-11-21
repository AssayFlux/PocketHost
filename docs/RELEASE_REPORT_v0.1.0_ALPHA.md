# Pocket Host v0.1.0 Alpha — Release and Acceptance Report

**Branch:** `Alpha`  
**Version:** `0.1.0`  
**Channel:** `Alpha`  
**Audited source snapshot:** `218d25793b7b7104e4c5583c716bb53586cdf156`  
**Current audit:** [Engineering audit and remediation (2026-11-21)](ENGINEERING_AUDIT_2026-11-21.md)  
**Signing:** Debug signing is intended only for owner testing. No production signing material is established by the repository workflow.  
**Status:** substantial implementation is present; post-remediation CI confirmation and owner device acceptance remain open. APK packaging was not run and remains manual-only. No API emulator or external-network tests were run.

> Historical workflow IDs and checkpoint claims in the sections below describe the state at the time they were recorded. They are not evidence that the current Alpha head passed.
## Latest workflow evidence

- Latest quality workflow [44737757283](https://github.com/AssayFlux/PocketHost/actions/runs/44737757283) completed successfully on head `3b127e785224165c9f2415df9ce013e60b2160a7`.
- Acceptance workflow [44737757242](https://github.com/AssayFlux/PocketHost/actions/runs/44737757242) was observed pending/queued, with no jobs reported at the time of inspection. It is not a pass.
- That acceptance workflow includes Android lint and JVM unit tests, then `:runtime-worker:generateDebugPythonMiscAssets` to validate Chaquopy runtime dependency resolution without assembling an APK.
- The earlier worker packaging attempt failed to resolve `com.chaquo.python.runtime:bootstrap:17.2.0`; `https://chaquo.com/maven` has since been added to dependency resolution.
- APK assembly, emulator instrumentation and APK verification are conditional on manual `confirm_build=true`. No APK was built in this pass.

Historical automated evidence:
- Quality [44730596020](https://github.com/AssayFlux/PocketHost/actions/runs/44730596020): recorded Rust hosting/relay, PostgreSQL control-plane, Python/protocol, Node, web-console and script checks passed.
- Android [44730595964](https://github.com/AssayFlux/PocketHost/actions/runs/44730595964): three-ABI native build/ELF checks and Android app/instrumentation/runtime-worker source compilation passed. Source compilation is not instrumentation execution.

## Acceptance matrix

| Area | Status |
|---|---|
| Rust static hosting/relay automated tests | Historical automated CI passed |
| Control-plane tests/migrations | Historical automated CI passed |
| Python/protocol tests | Historical automated CI passed |
| Node runtime metrics test | Historical automated CI passed |
| Web-console build | Historical automated CI passed; npm audit disposition open |
| Android native libraries, three ABIs | Historical build and ELF verification passed |
| Android Kotlin/instrumentation source compilation | Passed; not device execution |
| Post-fix Android lint/JVM tests | Acceptance workflow pending |
| Chaquopy runtime asset dependency gate | Acceptance workflow pending |
| API 35 x86_64 emulator instrumentation | Not run; packaging/test authorization gate |
| Physical local hosting/import/isolation/backup | Owner device acceptance required |
| Python/Node workloads and resource behavior | Owner device acceptance required |
| Android reboot/process-death/network recovery | Owner device acceptance required |
| Pairing, credential rotation/revocation | Real-device integrated acceptance required |
| Real DNS ownership and TLS issuance/renewal | Controlled hostname/network acceptance required |
| External visitor over independent network | Owner-controlled network acceptance required |
| APK metadata/ABI/signature/SHA-256 | Not run; APK packaging held |
| Upgrade/restore/crash recovery on device | Owner device acceptance required |

## Security and distribution notes

- App and runtime-worker are separate APKs and must use the same signer. Matching signer verification is part of the gated packaging workflow.
- Workflow output is debug-signed Alpha testing material, not a production/Play Store release.
- Android target SDK 35 and minimum SDK 26.
- Runtime Python/Node code is owner-trusted code only. It is not a hardened hostile-code sandbox and does not provide per-project UID isolation.
- Direct inbound reachability depends on carrier/NAT/firewall/DNS. Successful compilation does not prove public reachability or TLS issuance.
- No production keystore, passwords or credentials should be committed.

## Owner acceptance procedure

1. After APK packaging is explicitly authorized and succeeds, download the matching ABI app APK and companion APK from the same workflow run.
2. Install the companion and app on an Android 8.0+ device; record model, Android version and ABI.
3. Import a small static site, verify local preview and LAN access from a second device, and test cross-site isolation.
4. Test backup/restore using a disposable site; verify interrupted restore preserves the previous usable site.
5. If enabled, test only trusted Python WSGI (`wsgi.py`, callable `application`) and the supported single Node app. Record start/request/stop/restart, crashes and resource behavior.
6. On a hostname and relay endpoint the owner controls, verify owner-approved pairing, credential rotation/revocation, valid TLS, renewal/failure behavior and external visitor access.
7. Test reboot, process death, Wi-Fi/mobile transitions and recovery. Redact credentials from all logs.
8. Record pass/fail, device/network details and logs. Keep failed/skipped checks open.

## Known limitations / not release promises

- No production-signed APK.
- No physical-device or independent external-network evidence is generated by GitHub-hosted CI.
- No universal DNS-provider automation or guaranteed public inbound reachability.
- No arbitrary npm install/build pipeline, multi-Node-app concurrency, ASGI support or hostile-code sandbox.
- No full fleet console, distributed hosting, billing or marketplace.
- Emulator coverage, once run, is API 35 x86_64 only; other Android versions/vendors need owner testing.


## Additional Step 3 code-review correction

Commit [f2f6ec5](https://github.com/AssayFlux/PocketHost/commit/f2f6ec518062b0f6b5ed169cd046b8f1c9b50193) fixes configured relay credential rotation persistence across process restart by synchronizing explicit owner-configured host verifiers into SQLite on load. A regression test verifies old-token rejection and new-token acceptance after restart. The corresponding quality and Android native/source workflows are pending/in progress; this is not yet a passing test result.


## Step 1 source changes — 2026-11-20

Commit `8c95a42afe12ebceffeaede2807e702a02b71ce8` updates ZIP picker MIME handling, nested built-site root detection, ambiguous-root rejection, activated-site import summary counts, and visible import status in the Sites overview. New instrumentation test cases cover project metadata plus a nested `dist/` output and ambiguous `dist/`/`build/` roots.

These are committed source changes, not verified runtime behavior. Android compilation and test execution are still required. The designated Pocket Host 7 SVG has not yet been converted into launcher and in-app assets; no alternative mark has been substituted. No APK was built.

## Current audit and remediation — 2026-11-21

The Cloudflared integration gap found in the repository audit has been addressed in source and workflow configuration:
- The pinned `2026.11.0` Cloudflared Android executable is built for arm64-v8a, armeabi-v7a and x86_64 by the Android native engineering workflow.
- The engineering workflow validates executable presence/permissions and ELF headers, uploads the generated executable and license notices separately, and restores them before Android Kotlin/source compilation.
- The manual APK workflow builds the same binaries and checks the selected ABI's `libcloudflared.so` entry and ELF type in the APK.
- The APK workflow is manual-dispatch only; pushes and workflow-file edits cannot start an APK build.
- Generated `.build/` workspaces are ignored by Git.

These are committed workflow changes, not successful build evidence. The first post-remediation CI run must pass before Cloudflared integration is marked verified. No APK was assembled or uploaded during this repair pass.

## Current acceptance boundary

| Area | Current status |
|---|---|
| General engineering tests | Awaiting post-remediation CI result |
| Android native builds including Cloudflared | Awaiting post-remediation CI result |
| Android Kotlin and instrumentation-source compilation | Awaiting post-remediation CI result |
| Android lint and JVM unit tests | Awaiting post-remediation CI result |
| API emulator tests | Not run; owner authorization required |
| APK assembly/signature/ABI/artifact checks | Not run; owner authorization required |
| Physical-device and independent-network acceptance | Owner testing required |

Do not infer production readiness or physical-device acceptance from source presence or workflow configuration.