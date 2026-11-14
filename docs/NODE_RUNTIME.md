# Node.js runtime integration decision

**Decision status:** Runtime candidate selected for an implementation spike; not integrated into PocketHost yet.  
**Target line:** Node.js 24 LTS, upstream v24.22.1.  
**Candidate mobile recipe:** `fogtape/nodejs-mobile`, branch `recipe`, release `v24.22.1-0`.  
**PocketHost status:** Do not advertise Node.js hosting as available until native integration, lifecycle supervision, project import, memory controls and device tests pass.

## Why this candidate

The candidate builds an embeddable Android `libnode.so` for all three required ABIs:

- `arm64-v8a`
- `armeabi-v7a`
- `x86_64`

The recipe reconstructs Node.js from a pinned upstream source tag and a reviewed mobile patch series, verifies the reconstructed source tree hash, and provides Android test tooling. The mobile release is a prerelease of the embedding project, although it is based on the official Node.js v24.22.1 security release. It is not an official Node.js Foundation Android SDK distribution. PocketHost must independently verify the recipe, binary provenance, licensing and test coverage before shipping it.

Source references:
- [Mobile recipe](https://github.com/fogtape/nodejs-mobile/tree/recipe)
- [v24.22.1-0 release](https://github.com/fogtape/nodejs-mobile/releases/tag/v24.22.1-0)
- [Embedding environment requirements](https://github.com/fogtape/nodejs-mobile/blob/recipe/docs/EMBEDDING.md)
- [Mobile API compatibility FAQ](https://github.com/fogtape/nodejs-mobile/blob/recipe/docs/FAQ.md)
- [Build instructions](https://github.com/fogtape/nodejs-mobile/blob/recipe/docs/BUILDING.md)

## Pinned artifact metadata

| Item | Value |
|---|---|
| Release tag | `v24.22.1-0` |
| Upstream Node.js tag | `v24.22.1` |
| Upstream source commit | `ce7aaf1cee78b8cf0ce17364260535c418d3ed26` |
| Android full SDK archive | `nodejs-mobile-android-24.22.1-0.zip` |
| Archive size | 112,853,733 bytes |
| SHA-256 | `d6fd9f610fe5d81fede8f0d400c5a62723247725a19831835f15abf4f0604250` |
| License | MIT (upstream Node.js and mobile recipe; preserve notices) |

The archive digest is taken from the GitHub release asset metadata. The implementation pipeline must verify the downloaded archive against this exact digest before extraction. It must also verify each extracted ELF's ABI, minimum Android API and 16 KB page-size compatibility. Do not commit the binary archive or extracted `libnode.so` files to Git.

## Non-negotiable mobile constraints

1. **One Node instance per process.** The runtime cannot be started twice in the same process. PocketHost must run it in a dedicated Android service process, not in the main UI or Rust engine process.
2. **No child-process model.** Mobile does not support `child_process.spawn()` / `fork()` in the usual desktop sense. Do not build a process manager that depends on npm scripts spawning child processes, PM2, cluster or worker subprocesses.
3. **Lifecycle isolation.** A single user application is active at a time in the first Node implementation. The Android supervisor must detect runtime process death, stop/recreate the dedicated service process, and never attempt to initialize a second Node instance in the same process.
4. **Android filesystem environment.** Before Node starts, set `TMPDIR` to PocketHost's cache directory, `HOME` and working directory to the imported project/app-private data area, and `NODE_COMPILE_CACHE` to a cache subdirectory. Never assume `/tmp` exists.
5. **Memory controls.** Set a conservative V8 heap ceiling using `NODE_OPTIONS` and a configurable PocketHost memory budget. The 4 GB device baseline must be tested under concurrent static hosting, Python hosting and Android UI load. Node must be disabled by default and loaded only when explicitly enabled.
6. **Project dependency model.** Dependencies must be installed or bundled during project preparation. Native addons must be ABI-compatible Android builds. Arbitrary install scripts, native compilation and desktop-only dependencies cannot be promised.
7. **No public exposure by default.** Node origin binds to loopback. It is routed through the Rust engine only after per-site routing and access policy are implemented.

## Planned PocketHost integration boundary

- Kotlin owns the dedicated `:node` service process, foreground-service coordination, project selection, resource limits and status reporting.
- A small C++/JNI adapter links the pinned `libnode.so` and calls the documented Node embedding entry point on a dedicated thread.
- The service receives a validated app-private project directory, entrypoint, port and memory budget; it does not accept arbitrary shell command strings.
- A PocketHost bootstrap module owns the Node server lifecycle and exposes a private loopback shutdown/control channel.
- The parent hosting service supervises the dedicated process. It may recreate a dead Node service process, but it must never attempt a second Node initialization inside the same process.
- Rust remains the central HTTP/routing engine. Node is an optional application runtime behind a loopback origin, not a replacement for Rust.

## Required implementation and acceptance gates

- Reproducible, SHA-verified runtime materialization in the on-demand Android integration workflow.
- License and third-party notices included in the app and README.
- CMake/JNI bridge with deterministic startup error reporting.
- Dedicated Android service process with safe start, stop, crash detection and restart.
- Correct environment setup (`TMPDIR`, `HOME`, working directory, compile cache, CA certificates and heap limit).
- Node project importer with archive traversal protection, size/file/depth limits, entrypoint validation and dependency policy.
- Explicit single-active-Node-project constraint in UI and documentation.
- Loopback-only binding and Rust reverse-proxy integration.
- Instrumentation smoke tests for Node version, HTTP response, module loading, shutdown and process restart.
- Android API 26/33/35 emulator tests and physical ARMv7/ARM64 tests on representative 4 GB devices.
- Memory, thermal, battery and screen-off tests while static hosting and Python runtime are active.
- Security review before public tunnel exposure.

## Initial scope decision

The first Node release should support one trusted Node.js HTTP application at a time, with prebundled dependencies and one declared entrypoint. Do not promise arbitrary npm lifecycle scripts, PM2, cluster, child processes, arbitrary native addons or multiple concurrent Node projects. Broader support requires a separate compatibility and isolation design.
