# Node.js Runtime — Existing Companion Implementation

**STATUS: IN PROGRESS** Node.js is already implemented in the repository as a native Android runtime companion integration. It is outside the main app's Step 4 scope, but it is not merely a future idea. Native integration, lifecycle controls and host-side smoke tests exist; Android device acceptance, production hardening and independent downloadable package distribution remain open.

## Placement

Current placement: Node.js is integrated in `runtime-worker/` through the Node.js mobile 24.22.1 native SDK and JNI bridge. It is delivered today inside the separate runtime-worker companion APK, not yet as its own independently downloadable Node.js package. A future packaging task will split it into its own signed/versioned add-on. It is intended for the mobile website-serving node, not storage, traffic or build workers.

## Implemented repository foundation

- Node.js mobile 24.22.1 is pinned and fetched by the Gradle build with SHA-256 verification.
- Native SDK extraction includes `libnode.so` and headers for `arm64-v8a`, `armeabi-v7a` and `x86_64`.
- CMake builds `nodebridge`; Kotlin `NodeBridge` invokes libnode through JNI.
- Node runs in the companion's dedicated `:node` Android process, with foreground-service lifecycle and readiness monitoring.
- Launch validates project root/entry, loopback port and bounded V8 heap setting (96–512 MB).
- Metrics preload captures request/response counters; Android monitor samples process PSS/native heap.
- CI runs `tests/node/metrics-smoke.cjs` under Node 24.

This is an implemented integration foundation, not a fully accepted production runtime. Desktop package managers, arbitrary shell commands, process managers and unrestricted child processes are not implied.

## Security status

The Android Node process shares the runtime companion application UID with other runtime projects. A preload/listener restriction is not an OS-enforced network sandbox and does not block native/outbound socket bypasses. Run only owner-controlled trusted code until per-project OS isolation and egress enforcement pass adversarial tests.

## Runtime pack contract

Declare Node version, Android ABI, native dependency compatibility, license inventory, digest, signature, startup adapter, health check, CPU/memory limits and rollback version. User installs Node only when a site needs it.

## Remaining work / acceptance

- Verify Node startup, HTTP serving, graceful stop/restart, crash recovery and foreground-service behavior on Android API/device matrix and physical devices.
- Verify native library loading and runtime behavior on all three packaged ABIs; CI's host-side metrics smoke test does not prove Android JNI operation.
- Independent Node.js package artifact, signing, download/install/update/rollback/removal are not implemented yet; current Node.js ships inside the companion APK.
- The seccomp/network boundary and per-project filesystem isolation require adversarial Android testing; do not claim a complete hostile-code sandbox yet.
- Static site works when Node is absent or stopped.
- Process lifecycle, HTTP adapter, cancellation and shutdown are bounded.
- Node crash does not stop Python or static serving.
- Test project cross-access, native socket escape, malicious dependency, resource exhaustion and interrupted update.
- Upgrade and rollback preserve project data.
