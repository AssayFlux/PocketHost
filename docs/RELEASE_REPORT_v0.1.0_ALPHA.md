# Pocket Host v0.1.0 Alpha — Release and Acceptance Report

**Branch:** Alpha  
**Version:** 0.1.0  
**Channel:** Alpha  
**Current head:** `3b127e785224165c9f2415df9ce013e60b2160a7`  
**Signing:** Debug signing is intended only for owner testing. No production signing material is established by the repository workflow.  
**Status:** latest quality CI passed; post-fix Android/Chaquopy acceptance workflow is pending; emulator, physical-device and external-network acceptance remain open. APK packaging remains owner-gated and was not run.

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
