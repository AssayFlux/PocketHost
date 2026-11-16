# NODE.JS RUNTIME — EMBEDDED MOBILE NODE

**Source position:** Node mobile 24.22.1 native runtime and JNI bridge are integrated in companion source.  
**Release position:** API/ABI runtime proof and per-project isolated execution are not accepted. Trusted owner code only.

## 1. Pinned upstream runtime

| Item | Value |
|---|---|
| Distribution | fogtape/nodejs-mobile |
| Release | `v24.22.1-0` |
| Upstream Node | 24.22.1 |
| Archive | `nodejs-mobile-android-24.22.1-0.zip` |
| SHA-256 | `d6fd9f610fe5d81fede8f0d400c5a62723247725a19831835f15abf4f0604250` |
| Declared ABIs | arm64-v8a, armeabi-v7a, x86_64 |

The companion build verifies the archive digest and builds the native JNI bridge. Confirm actual Gradle ABI filters and CI packaging before claiming a target ABI is released.

## 2. Current source architecture

- NodeBridge, native libraries, JNI `node::Start`, preload and Node service live in companion package.
- Node service runs in companion `:node` process.
- Node process and Python worker share the companion application UID.
- Project is transferred to companion-owned storage before startup.
- Rust remains the website ingress and reverse-proxies to Node's local listener.
- One active Node app is supported.
- Dependencies must be prebundled; no on-device `npm install`, PM2, desktop cluster or arbitrary child-process workflow.
- V8 heap configuration is not a hard process RSS or CPU quota.

## 3. Listener and network limitation

The preload may constrain ordinary Node TCP listener binding. It is not an OS security boundary and does not block outbound TCP/UDP, DNS, native socket calls or child/native bypasses. Do not claim egress denial.

## 4. Security status

Node shares companion UID/storage with Python and other projects. It is not a per-project sandbox. Only owner-controlled trusted projects may run. Never host arbitrary third-party or visitor-submitted code.

## 5. Target isolated-cell contract

One Android isolated service process/UID per project. Broker transfers only project/scratch descriptors and a host-created visitor listener capability. Native seccomp policy is installed before Node initialization; failure prevents startup. Node must use the existing listener capability rather than creating arbitrary sockets. Prove native modules cannot bypass policy.

See `docs/SECURITY_GATE_2_EXECUTION_PLAN.md` and `docs/ASSAYFLUX_RELAY.md`.

## 6. Acceptance

- Native libraries load on each declared ABI.
- `process.version` reports pinned Node version.
- Real HTTP request routes through Rust to Node and back.
- Two Node/Python projects have distinct UIDs and cannot access sibling/host data.
- Outbound TCP/UDP/DNS/native socket bypasses fail.
- Process death, restart, rebind and static-origin continuity pass.
- API 26/33/35 and physical ARMv7/ARM64 acceptance recorded.
- Independent security review approves before untrusted code support.
