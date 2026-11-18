# Node.js Runtime — Existing Companion Implementation

**Status:** IN PROGRESS  
**Release assignment:** v0.1.1 — Workbench, Runtime & Linux Relay Installation  
**Location:** runtime-worker/

## 1. Placement

Node.js is an existing native Android runtime integration. The repository uses Node.js mobile 24.22.1 through JNI/libnode and a pinned native SDK. It is delivered inside the separate runtime-worker companion APK alongside Python, not inside the main Pocket Host APK.

## 2. Implemented repository foundation

The source includes:
- JNI bridge to the native Node.js runtime.
- Kotlin runtime service/lifecycle integration.
- Pinned native SDK assets for arm64-v8a, armeabi-v7a and x86_64.
- Foreground-service and bounded heap/metrics foundations.
- Host-side Node metrics smoke test.

A host-side metrics smoke test does not prove Android JNI operation or device compatibility.

## 3. Security status

Node projects currently share the runtime companion application UID. A preload/listener restriction is not an OS-enforced network sandbox and cannot prevent native/outbound socket bypasses. Treat code as trusted owner-controlled code until per-project isolation and egress controls pass adversarial tests.

## 4. v0.1.1 completion requirements

- Verify native library loading on every packaged ABI.
- Verify start/stop, health, process crash, service restart and Binder death.
- Bound heap, CPU, disk, open files, request handling and execution time.
- Restrict project filesystem access and document network policy.
- Verify app/companion signature-level IPC and version compatibility.
- Prove Node failure does not interrupt static hosting or Python.
- Package and update the companion safely; separate per-language package delivery is a further packaging milestone, not assumed to exist today.

## 5. Runtime project contract

A Node project declares runtime version, entrypoint, host/port, project namespace, environment-secret references, resource budget, health check and package compatibility. Do not imply support for desktop package managers, unrestricted shell commands, generic process managers or arbitrary child processes.

## 6. Acceptance

Run the Node smoke suite and Android instrumentation on physical devices. Test all declared ABIs, JNI load, HTTP response, invalid entrypoint, resource limits, crash/restart, network policy, cross-project access and static-host independence. Record v0.1.1 G0–G9 evidence.
