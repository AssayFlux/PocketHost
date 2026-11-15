# PocketHost implementation and validation status

**Last updated:** 2026-11-15  
**Branch:** `main`  
**Release state:** Integrated development build; not approved for public production hosting.  
**Compatibility target:** Android 8.0 (API 26)+, 4 GB RAM baseline, ARMv7 + ARM64 phones; x86_64 emulator ABI.

## Implemented in source

### Native Android application
- Kotlin + Jetpack Compose application with Simple and Advanced modes.
- Android API 26 minimum, ARMv7/ARM64/x86_64 ABI configuration.
- User-started foreground hosting service, notifications, service status, optional bounded wake-lock lease.
- SAF folder/ZIP website import, staging, retained releases and rollback.
- Python project import and Node project folder import with bounded size, file count and depth, path checks and sensitive-file exclusions.
- Encrypted static-site backup/restore (AES-256-GCM, PBKDF2-HMAC-SHA-256); authenticated archive validation before restore activation.
- User-mediated diagnostics ZIP and redacted operational log export.
- Separate ABI APK split configuration so each phone build packages only its matching native runtime libraries.

### Rust hosting engine and multi-site routing
- Tokio + Hyper static HTTP server with GET/HEAD, streaming, byte ranges, MIME types, path canonicalization and traversal protection.
- Loopback by default; explicit LAN opt-in.
- Bounded concurrent connections and per-file response size limits.
- Hostname registry maps domains to static roots or loopback Python/Node upstream ports.
- Reverse proxy preserves the original Host header and adds forwarded host/protocol metadata.
- Persistent static-server traffic metrics are labelled separately from runtime metrics.

### Python WSGI runtime
- Embedded CPython 3.11 via Chaquopy; trusted projects provide a root `wsgi.py` with callable `application`.
- Multi-host routing for registered Python projects.
- Bounded threaded WSGI server, configurable 2–16 worker ceiling, request/byte/error/concurrency metrics.
- Loopback default, optional explicit LAN binding.
- HostingService supervisor retries failed starts with bounded exponential backoff.
- Host-side syntax and lifecycle tests pass in the latest completed relevant CI job.

### Node.js runtime
- Pinned Node.js 24.22.1 mobile SDK, release `v24.22.1-0`, SHA-256 checked during build; supports ARMv7, ARM64 and x86_64.
- CMake/JNI adapter embeds `libnode.so` and invokes `node::Start`.
- Dedicated foreground Android service in the isolated `:node` process; a new process is the restart boundary because the embedded Node instance is single-use per process.
- SAF project importer validates `package.json` and safe entrypoints; supports JS and erasable TypeScript entry files. Dependencies must already be bundled.
- Node preload collects HTTP metrics and forces TCP listeners to loopback behind Rust ingress.
- Device-RAM-aware V8 heap default with configurable 96–512 MiB ceiling; Node thread pool is capped.
- File-backed, OS-locked state/metrics IPC between main and `:node` processes.
- Parent HostingService supervises Node process startup and recovery.
- Current source includes an Android instrumentation smoke test for embedded Node startup and Rust reverse-proxy routing. It has not yet been run on a device/emulator.

### Management API and browser console
- Loopback management API on port 17841, started with HostingService.
- Six-digit pairing approval displayed in the Android app; challenge expiry, attempt limit and rate limiting.
- Memory-only bearer sessions with 12-hour expiry and individual revocation.
- Host-header validation, duplicate-header rejection, no transfer-encoding request bodies, bounded header/body sizes, bounded worker queue, Origin allowlist and Private Network Access preflight.
- Authenticated site registry CRUD, runtime start/stop actions, status, source-scoped metrics, recent logs, tunnel controls and resource settings.
- TypeScript/React console connects to the live API, pairs with the device, displays real status and metrics, controls registered runtimes and tunnel, reads logs and updates resource ceilings.
- Bearer tokens are held in tab-scoped sessionStorage rather than persistent localStorage.

### Cloudflare and operations
- Cloudflare Tunnel connector source and ABI binaries, encrypted token at rest with Android Keystore, lifecycle/readiness monitoring and reconnect supervisor.
- Rotating redacted operational logs and diagnostics bundle.
- Tunnel status does not prove public hostname reachability. Configure the named tunnel/Public Hostname in Cloudflare separately and use Cloudflare Access for private visitor authentication.

## Resource and security boundaries

- Python and Node user projects are trusted code running under PocketHost's application UID. Neither runtime is a hostile-code sandbox or multi-tenant isolation boundary.
- Node is one active project/process at a time. Multiple hostnames may map to the same project.
- Node mobile does not support the usual desktop child-process workflow; no on-device npm install, install scripts, PM2, cluster or child-process build tooling.
- Native Node addons must be built for the Android ABI and matching Node ABI.
- Node V8 heap ceiling is configurable; it is not a hard total-RSS or OS cgroup limit. Python worker ceilings bound concurrency, not CPU time or memory per request.
- Node TCP listeners are forced to loopback by the preload; UDP sockets are not covered by that TCP policy.
- LAN mode has no visitor authentication. Use only on trusted networks. For public sites requiring private access, configure Cloudflare Access.
- Android foreground services do not guarantee 24/7 uptime. OEM power management, Doze, battery saver, thermal throttling and low-memory process kills remain device-specific risks.
- Management API sessions are in-memory and are cleared when the hosting service stops or process restarts. Pair again after restart.
- The browser console is CORS-allowlisted to localhost development and explicit AssayFlux origins; other deployment origins must be deliberately reviewed before adding.

## Latest source validation

The GitHub Actions quality workflow now covers:
- Rust unit tests.
- Python syntax and WSGI lifecycle tests.
- TypeScript console production build.
- Node metrics/loopback preload smoke test.
- Android native CMake/Rust and Kotlin source compilation, including Android instrumentation-test source compilation.

The latest workflow run is still being processed while these commits are landing. Do not infer a green result from earlier partial/cancelled runs. Use the live [quality workflow](https://github.com/AssayFlux/PocketHost/actions/workflows/quality.yml) for the exact current commit result.

## Not yet verified / release gates

1. Complete the current CI run and fix every compile/test failure.
2. Execute the Node instrumentation smoke on API 26, API 33 and API 35 emulators: actual `libnode.so` load, Node version, bundled module import, loopback enforcement, Rust reverse proxy, service stop and fresh-process restart.
3. Run Android service lifecycle, notification, Doze, screen-off, battery saver, Wi-Fi/mobile transition and low-memory tests.
4. Test ARMv7 and ARM64 physical phones, including a representative 4 GB device with static + Python + Node workloads concurrent.
5. Measure each ABI APK size and verify APK signatures and ELF 16 KB page-size compatibility.
6. Verify Cloudflare tunnel connection and public hostname from an external network; verify Access behavior.
7. Complete a security review of request parsing, pairing/session handling, CORS/PNA, path/archive import, backups, user-code execution and public exposure.
8. Add integration coverage for failure recovery, malformed archives, backup authentication failure, and simultaneous runtime load.
9. Build one final APK set only after source implementation and CI are stable. No APK has been built for the current implementation batch.

## Build prerequisites

JDK 17, Android SDK 35, NDK 27.2.12479018, CMake 3.22.1, Rust stable and Android targets, `cargo-ndk`, Go 1.27.4 for cloudflared source build, and Node.js/npm for the console. The Node SDK archive is downloaded and verified during native Android builds; network access is required on a clean build.

Debug source/build commands:

```bash
gradle :app:externalNativeBuildDebug :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

Final ABI-specific APK assembly is intentionally deferred until the source and device acceptance gates above are satisfied.
