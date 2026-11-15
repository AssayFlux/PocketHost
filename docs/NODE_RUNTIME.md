# Embedded Node.js runtime

**Implementation status:** Integrated in PocketHost source; native build and physical-device acceptance are still being validated. Do not treat source integration as proof of successful runtime execution on every target phone.

## Runtime selected

PocketHost pins the full Android SDK from `fogtape/nodejs-mobile`, release `v24.22.1-0`, based on upstream Node.js 24.22.1. It is an independently maintained mobile embedding build, not an official Node.js Foundation Android SDK.

| Item | Pinned value |
|---|---|
| Release | `v24.22.1-0` |
| Upstream Node.js | `v24.22.1` |
| Upstream source commit | `ce7aaf1cee78b8cf0ce17364260535c418d3ed26` |
| Android archive | `nodejs-mobile-android-24.22.1-0.zip` |
| Compressed archive size | 112,853,733 bytes |
| SHA-256 | `d6fd9f610fe5d81fede8f0d400c5a62723247725a19831835f15abf4f0604250` |
| ABIs | `arm64-v8a`, `armeabi-v7a`, `x86_64` |

Gradle downloads the archive only during the native build, checks the complete SHA-256 digest, extracts `bin/<abi>/libnode.so` and the headers, and fails if any required ABI or `node/node.h` is missing. The binary archive is not committed to Git. The full variant is selected to retain the broadest runtime feature set, including built-in TypeScript type stripping.

## Implemented integration

- `app/build.gradle.kts`: pinned archive materialization, digest verification, ABI extraction and CMake dependency.
- `app/src/main/cpp/node-bridge.cpp`: C++ JNI adapter calling `node::Start`, configuring working directory, `HOME`, `TMPDIR`, compile cache, loopback defaults, thread-pool size and V8 heap cap.
- `NodeBridge.kt`: Kotlin JNI declaration.
- `NodeHostingService.kt`: foreground service in the dedicated Android `:node` process; readiness monitor, process metrics and process-boundary shutdown.
- `NodeHostRuntime.kt` and `NodeRuntimeStateStore.kt`: lifecycle API and atomic file-backed IPC between the main process and `:node` process. SharedPreferences is deliberately not used as cross-process state transport.
- `NodeProjectImporter.kt`: Storage Access Framework folder import with file-count, total-size and depth bounds; path validation; sensitive-file exclusion; package entrypoint validation.
- `pockethost-node-metrics.cjs`: request, response-byte, error, active-request, RSS and heap metrics; TCP listeners are forced to loopback so public ingress must pass through the Rust engine.
- Rust host routing: registered Node hostnames are reverse-proxied to the Node loopback port.
- HostingService supervisor: detects Node process exit and retries startup with bounded exponential backoff.
- Management API and TypeScript console: Node state, metrics and resource limits are exposed to the paired console.

## Runtime policy and constraints

1. **One Node process / one active Node project.** The embedded Node instance is isolated in Android process `:node`. Do not start a second Node instance in that process. Multiple hostnames may map to the same Node project.
2. **No desktop child-process assumptions.** Android mobile embedding does not provide the usual `child_process.spawn()` / `fork()` workflow. PM2, cluster workers, npm scripts that spawn subprocesses, and process-based build tools are unsupported.
3. **Dependencies are prebundled.** Import a prepared project containing its required `node_modules`. PocketHost does not run `npm install`, lifecycle scripts, native compilation or arbitrary shell commands on the phone. Native addons must be built for the exact Android ABI and Node ABI.
4. **Trusted code only.** Node projects execute with PocketHost's application UID and access to app-private files. The dedicated process improves lifecycle isolation; it is not a security sandbox or separate Android UID.
5. **Loopback ingress.** The metrics preload constrains Node TCP server listeners to `127.0.0.1`; Rust is the public/LAN-facing listener and reverse proxy. UDP listeners are not covered by this TCP guard. Public exposure still requires explicit LAN/tunnel configuration and app-level security.
6. **Resource ceilings.** V8 old-space is configurable from 96–512 MiB, with a device-RAM-based default. The Python WSGI worker cap is configurable from 2–16. These are application-level controls, not cgroup CPU quotas or hard total-RSS limits. Android may still kill a process under memory pressure.
7. **Filesystem.** `TMPDIR` and V8 compile cache use Android's app cache directory. `HOME` and project working directory are app-private. Do not assume `/tmp` exists.
8. **TypeScript.** Node 24's built-in type stripping supports erasable TypeScript syntax in supported entry files. It is not a full TypeScript compiler: it does not type-check or compile TS features that require transformation. Use a prebuilt JavaScript output when your project needs a bundler or compiler.
9. **Availability.** The full Node library materially increases APK size. The actual per-ABI and universal APK sizes must be measured after native packaging; this is not yet claimed as verified.

## Validation still required

- CI native compile for all configured ABIs, Rust tests, Python tests, console build and Node metrics-preload smoke.
- Android emulator smoke that loads the actual `libnode.so`, reports `process.version`, imports a bundled dependency, starts an HTTP server, verifies Rust reverse-proxy routing, stops the service and starts a fresh Node process.
- Android API 26, 33 and 35 lifecycle / notification / background-execution tests.
- Physical ARMv7 and ARM64 tests, including a representative 4 GB phone.
- Stress tests with Rust static hosting, Python and Node enabled together; low-storage, memory-pressure, thermal and screen-off behavior.
- Verify archive license notices and the final APK's ABI / 16 KB page-size metadata.

## Upstream references

- [Mobile recipe and release provenance](https://github.com/fogtape/nodejs-mobile/tree/recipe)
- [Pinned v24.22.1-0 release](https://github.com/fogtape/nodejs-mobile/releases/tag/v24.22.1-0)
- [Embedding environment requirements](https://github.com/fogtape/nodejs-mobile/blob/recipe/docs/EMBEDDING.md)
- [Mobile API compatibility](https://github.com/fogtape/nodejs-mobile/blob/recipe/docs/FAQ.md)
- [Build instructions](https://github.com/fogtape/nodejs-mobile/blob/recipe/docs/BUILDING.md)


## Trusted-code start gate

PocketHost requires an explicit local acknowledgement before starting Node.js. The acknowledgement is stored on-device and can be revoked; revocation stops optional runtimes. The dedicated `:node` process shares the app security identity and filesystem/network privileges: it is **not** an isolated UID or security sandbox. A V8 heap limit is not a hard process-memory or CPU quota. Never execute untrusted projects or expose arbitrary third-party code publicly until OS-backed isolation and network/filesystem restrictions are implemented and independently reviewed.
