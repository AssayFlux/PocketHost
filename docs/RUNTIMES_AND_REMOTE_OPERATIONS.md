# Runtime and Remote Operations — Alpha v0.1.0

**Branch:** `Alpha`  
**Status:** scope and engineering boundaries defined; implementation hardening underway.  
**Important:** source presence and source compilation do not prove physical-device workload acceptance.

## Supported runtime contract

### Python

- Embedded CPython **3.11** through the runtime-worker companion APK.
- Application contract: a project supplies `wsgi.py` with a callable named `application`.
- Single-project WSGI and multi-host WSGI routing are supported; the multi-host registry is capped at 100 hostnames.
- Listener binds to `127.0.0.1`; visitor/LAN/public ingress is expected to pass through Pocket Host's Rust front door.
- The threaded WSGI server has a bounded queue of 32, socket timeout of 15 seconds, a request-body ceiling of 10 MiB, and a hard maximum of 16 concurrent request workers. The host chooses a conservative automatic worker count of 2–8 based on device RAM; an explicit configured value is constrained to 2–16.
- Metrics are process-local counters and timings, not OS resource quotas.

Not supported as a v0.1.0 promise: ASGI, arbitrary Python server frameworks, background job orchestration, multiple independent Python interpreter processes, or hostile multi-tenant code isolation.

### Node.js

- Embedded Node.js mobile runtime **24.22.1-0** in the runtime-worker companion APK.
- One Node application at a time. Entry file is resolved from the imported project (package main or supported server entry).
- Listener port must be 1024–65535. The native bridge supplies `HOST=127.0.0.1`, `PORT`, `NODE_ENV=production`, and `UV_THREADPOOL_SIZE=2`; an application can ignore environment hints, so this is not a security-enforced loopback-only sandbox.
- V8 old-space heap is explicitly bounded to 96–512 MiB; automatic selection is based on device RAM and capped at 384 MiB.
- Runtime runs in the companion app's dedicated `:node` process. It shares the companion package UID with Python and the companion controller.

Not supported as a v0.1.0 promise: arbitrary npm installation/build pipelines, multiple concurrent Node apps, cluster mode, arbitrary native add-ons, or hostile-code sandboxing.

## Runtime project transfer policy

The host creates a bounded ZIP transfer and the companion independently validates extraction paths and expanded content.

| Boundary | Alpha policy |
|---|---:|
| Per-file size | 64 MiB |
| Aggregate expanded project | 256 MiB |
| Archive entries | 10,000 |
| Relative path depth | 32 |
| Relative path length | 1,024 characters |
| Symbolic links | Rejected |
| Case-colliding paths | Rejected |
| Sensitive credential/key files | Excluded or rejected |

The host rejects invalid paths and file sizes before archive transfer. The companion validates the archive again before activating the staged project. Replacement is staged and the previous project is retained for rollback if activation fails.

## Trust and isolation model

- Runtime code must be explicitly acknowledged by the device owner before start.
- Python and Node execute under the companion APK's Android UID, not the main Pocket Host APK UID.
- Python projects share the companion process/UID; Node has its own process but shares the same companion UID. This is a package boundary, not a per-project identity boundary.
- No claim is made that the current runtime worker is a hardened sandbox for hostile or mutually untrusted code. Imported applications must be code the owner trusts.
- The optional runtime companion may be absent or stopped; static Rust website hosting must continue independently.
- Runtime metrics and configured concurrency/heap ceilings are useful guardrails, not complete OS-enforced CPU and memory isolation.

## Runtime-worker trust boundary

- The host binds to the explicit companion service component.
- The companion verifies the caller UID maps to the expected Pocket Host package and checks the host/companion signing certificates match.
- The Binder interface enforces its descriptor and a protocol version; a Binder death recipient invalidates a stale cached connection.
- Project archives are extracted into companion-private storage and checked for traversal, symlinks, sensitive paths, collisions and size limits.
- Node runs in a dedicated companion process and is stopped through its service/process lifecycle.

The app and companion APKs must be signed with the same certificate and compatible protocol versions. A debug-signed pair is suitable only for development testing; production signing must be verified in Step 5.

## Minimum remote operations retained

- Android owner device identity is held by Android Keystore; enrollment uses a time-limited owner challenge and candidate proof-of-possession. Discovery alone never approves a device.
- Role grants are owner-signed, capability-scoped, generation-bound and time-limited. Revocation increments the device generation and removes its grant; authorization checks reject revoked or stale generations.
- The local management API binds only to loopback, requires explicit owner approval for pairing, uses expiring in-memory bearer sessions and applies recent-owner-auth requirements to sensitive mutations.
- The outbound remote-control agent runs only in Command Center server mode. It uses an HTTPS control-plane endpoint, stores its device token encrypted with an Android Keystore AES-GCM key, polls commands and sends acknowledgements; it does not open an inbound Internet listener.
- Retained remote command scope is limited to site actions/deployment, tunnel actions, a small allowlist of runtime/config settings and runtime restart. Deployment archives are bounded to 50 MiB and checked against the supplied SHA-256.
- Remote command execution records a durable start marker before side effects and a durable result before acknowledgement. A command found in an interrupted `started` state is not silently replayed; it returns an operator-retry-required result.
- Corrupt command idempotency state and failed persistence now fail closed rather than resetting the record and risking duplicate execution.

Remote control is optional. Loss of the control plane, web console, Linux agent or runtime-worker must not disable local static website hosting.

## Verification status and remaining acceptance

Implemented/covered in source:
- Python WSGI and Node runtime smoke tests exist for companion execution, loopback routing and independent static hosting.
- Runtime worker checks host package identity, signature equality, Binder protocol and connection death.
- Transfer path and file-size policy now has additional Android instrumentation regression coverage.
- Remote command start/result persistence is checked before execution/acknowledgement.

Still required:
- Execute Android instrumentation tests on emulator/device; source compilation alone is not execution.
- Run Python and Node workloads on a physical Android device and record model/API/ABI, memory, startup, stop, crash and recovery behavior.
- Verify configured resource ceilings under load and show that runtime failures do not disrupt static hosting.
- Test companion APK upgrade/signature mismatch and protocol mismatch handling.
- Exercise owner pairing, capability denial, session expiry, device revocation and remote command retry on the retained flows.
- Record the physical-device and external-network evidence in Step 5.

## Explicit v0.1.0 exclusions

No hostile-code sandbox claim, per-project UID isolation, arbitrary package build system, full fleet console, distributed runtime scheduler, or broad language catalogue.
