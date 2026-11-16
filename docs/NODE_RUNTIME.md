# Node.js runtime

**Source integration:** Node.js 24.22.1 mobile runtime has been moved into the separate-UID companion package. Build and Android instrumentation for this migration are pending.

## Pinned runtime

Pocket Host pins the Android SDK from `fogtape/nodejs-mobile`, release `v24.22.1-0`, based on upstream Node.js 24.22.1.

| Item | Value |
|---|---|
| Release | `v24.22.1-0` |
| Archive | `nodejs-mobile-android-24.22.1-0.zip` |
| SHA-256 | `d6fd9f610fe5d81fede8f0d400c5a62723247725a19831835f15abf4f0604250` |
| ABIs | `arm64-v8a`, `armeabi-v7a`, `x86_64` |

The companion Gradle build verifies the archive digest and builds `libnodebridge.so`. Node native libraries are packaged in the companion APK, not the host APK.

## Runtime placement

- `NodeBridge`, `node::Start`, Node native libraries and the metrics preload live in `:runtime-worker`.
- `NodeHostingService` runs in the companion app's `:node` process.
- Node runs under the companion application's Android UID, which differs from the main Pocket Host UID.
- Project data is transferred to companion-owned storage before launch.
- The Rust engine remains the public/LAN-facing HTTP listener and reverse-proxies to Node's loopback port. The preload's listener guard applies to TCP APIs; it does not enforce UDP egress or general outbound-network policy.
- A foreground service, readiness monitor, metrics sampler and bounded host restart supervisor manage lifecycle.

The `:node` process is a separate process, **not a separate Android UID from the Python worker**. Both share the companion package identity.

## Runtime policy

1. One active Node project/process at a time.
2. Node listeners are forced to `127.0.0.1` by the metrics preload; Rust provides public/LAN ingress.
3. Projects must be prebundled with their dependencies. Pocket Host does not run `npm install` or arbitrary shell commands.
4. Node mobile embedding does not provide normal desktop `child_process.spawn()` / `fork()` workflows.
5. V8 heap is configurable from 96–512 MiB. This is not a hard total-RSS or CPU quota.
6. Project archives are bounded and common credential files are excluded/rejected.
7. Only trusted owner-controlled code is supported.

## Security limitations

The companion UID protects host-app-private files from ordinary Node project code. It does not isolate Node from other projects stored in companion-owned storage. Outbound network egress is not yet constrained by an enforceable firewall. Loopback services are reachable from the companion, so management API authentication remains mandatory.

This is not a complete sandbox or a multi-tenant execution environment. Do not run arbitrary third-party code or claim production isolation until filesystem and network restrictions pass adversarial review.

## Validation required

- Build and package `libnode.so` and `libnodebridge.so` for each ABI.
- Verify actual `process.getuid()` differs from the host app UID.
- Verify Node starts, reports `process.version`, serves HTTP and routes through Rust.
- Verify clean shutdown, process restart, companion death/rebind and static-origin continuity.
- Run API 26, 33 and 35 emulator tests and physical ARMv7/ARM64 acceptance.
- Complete cross-project filesystem and outbound network-egress security tests.

## Upstream references

- [Mobile recipe and release provenance](https://github.com/fogtape/nodejs-mobile/tree/recipe)
- [Pinned v24.22.1-0 release](https://github.com/fogtape/nodejs-mobile/releases/tag/v24.22.1-0)
- [Embedding environment requirements](https://github.com/fogtape/nodejs-mobile/blob/recipe/docs/EMBEDDING.md)
