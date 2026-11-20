# Pocket Host v0.1.0 Alpha — Release and Acceptance Report

**Branch:** Alpha  
**Version:** 0.1.0  
**Release channel:** Alpha  
**Signing:** Debug signing only for owner testing; no production signing material was present in the repository workflow.  
**Status:** APK/CI acceptance run pending; physical-device and external-network owner acceptance remain mandatory before declaring the Alpha fully accepted.

## Source and workflow evidence

- Source baseline before Step 5: 78eccfc921d55a3dd44862bbe09228db7f66a360.
- Android native ABI and source validation: [run 44730595964](https://github.com/AssayFlux/PocketHost/actions/runs/44730595964) — passed native hosting and relay builds for arm64-v8a, armeabi-v7a and x86_64; ELF architecture checks and Android app/instrumentation/runtime-worker source compilation passed.
- Integrated quality workflow: [run 44730596020](https://github.com/AssayFlux/PocketHost/actions/runs/44730596020) — passed Rust hosting/relay tests, PostgreSQL-backed control-plane tests, Python tests and protocol validation, Node runtime metrics test, web-console build and script checks.
- Android instrumentation had not previously been executed; source compilation is not a test execution result.

The final workflow will append the exact run URL, commit SHA, instrumentation results, APK filenames, signer certificate digest, SHA-256 values and dependency audit evidence here or in its artifact bundle.

## Acceptance matrix

| Area | Evidence/status |
|---|---|
| Rust static hosting and relay tests | Automated CI passed |
| Control-plane tests and migrations | Automated CI passed |
| Python/protocol tests | Automated CI passed |
| Node runtime metrics test | Automated CI passed |
| Web-console production build | Automated CI passed; npm audit evidence still required |
| Android native libraries, three ABIs | Build and ELF verification passed |
| Android Kotlin and instrumentation source compilation | Passed |
| Android lint and JVM unit tests | Step 5 workflow execution required |
| API 35 x86_64 emulator instrumentation | Step 5 workflow execution required |
| Static hosting on physical phone, loopback/LAN | Owner device test required |
| Python/Node workloads on physical phone | Owner device test required |
| Android reboot/process-death/network recovery | Owner device test required |
| Owner pairing, credential revocation, TLS issuance/renewal | Real-device/control-plane acceptance required |
| External visitor over independent network | Owner-controlled external network test required |
| APK package/version/ABI/signature/SHA-256 | Step 5 artifact verification required |
| Upgrade/restore/crash recovery on device | Owner device test required |

## Security and distribution notes

- App and runtime-worker are separate APKs and must have the same signer certificate. Step 5 verifies that equality.
- The workflow produces debug-signed Alpha artifacts, not a Play Store or production release. Install only for owner-controlled testing.
- Android target SDK is 35 and minimum SDK is 26.
- The app requests Internet, network-state, boot, wake-lock, foreground-service/special-use, notification, and exact-alarm permissions. Exact alarm and notification behavior is subject to Android version and user settings.
- Main launcher activity is exported; hosting, relay and boot/schedule services/receivers are non-exported. Runtime companion Binder services are exported only behind a signature-level custom permission.
- App and companion manifests disable Android backup. No production keystore or credentials are committed.
- Python and Node runtimes are for owner-trusted code only; they are not hostile-code sandboxes and do not provide per-project UID isolation.
- Direct inbound reachability depends on carrier/NAT/firewall/DNS conditions. A successful build does not prove public reachability or TLS issuance.

## Owner device acceptance procedure

1. Download both the matching ABI app APK and the runtime-worker companion APK from the same workflow artifact. Keep both files from the same run.
2. On an Android 8.0+ device, install the runtime-worker APK and then the matching Pocket Host app APK. Allow installation from the chosen file source if Android asks.
3. Launch Pocket Host, review and grant only the permissions required for the selected test. Start local static hosting and import a small static website.
4. Open the app's local preview, then test the device's LAN address from another device on the same Wi-Fi. Confirm a second registered site cannot read the first site's files.
5. Exercise backup and restore using a disposable test site. Verify a failed/interrupted restore leaves the previous working site intact.
6. If using runtimes, test only trusted Python WSGI (wsgi.py, callable application) and the supported single Node app. Confirm start, request, stop and restart behavior.
7. Configure public relay only with a hostname/domain and relay credentials you control. Verify owner-approved pairing, external visitor access, revocation, TLS validity and network-change recovery. Do not treat localhost success as public-access proof.
8. Record device model, Android version, ABI, app/worker version, test network, pass/fail, logs with secrets redacted, and any crashes. Send the evidence back before the project is called fully accepted.

## Known limitations / not release promises

- No production signing key or production-signed APK.
- No physical-device or independent external-network evidence is generated by GitHub-hosted CI.
- No universal DNS-provider automation or guaranteed public inbound reachability.
- No arbitrary npm build/install workflow, multi-Node-app concurrency, ASGI support or hostile-code sandbox.
- No full fleet console, distributed hosting, billing or marketplace.
- Emulator coverage is one API 35 x86_64 target; other Android versions/vendors require owner testing.
