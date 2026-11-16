# Runtime companion architecture

**Source integration:** implemented on main at `bd227b0e5564ad25c88064cfc36796469390b773`.
**Verification:** CI/build and Android API 26/33/35 instrumentation for this revision are pending. This is not a production security approval.

## Why a second Android application

Android assigns an application UID to each installed package. Python and Node code previously executed with the main Pocket Host application UID, which could expose host-private files to customer code.

Pocket Host now has a second application package:

- Host: `com.assayflux.pockethost`
- Runtime companion: `com.assayflux.pockethost.worker`

The companion package has its own Android UID and private data directory. Python and Node execute only in companion-owned processes. This prevents ordinary runtime code from directly reading files protected by the host application's UID.

## Runtime placement

| Component | Package / process | Responsibility |
|---|---|---|
| Rust HTTP engine | Host app UID | Public/LAN listener, static files and reverse proxy |
| Management API | Host app UID | Authenticated owner/operator controls |
| Companion control service | Worker UID, `:runtime_worker` | Binder commands, project transfer, CPython WSGI |
| Node service | Worker UID, `:node` | Node 24 native runtime and HTTP listener |

The two companion processes share the companion application's UID. They are not separate per-site UIDs.

## Authenticated, versioned IPC

The host binds explicitly to `RuntimeWorkerService`. The service:

- Is protected by the signature-level `BIND_RUNTIME_WORKER` permission.
- Verifies the calling UID contains the expected host package.
- Verifies the host and companion packages have matching signatures.
- Negotiates Binder protocol version 1 before accepting host operations.
- Exposes bounded operations for project transfer, Python lifecycle, Node lifecycle, metrics and coordinated shutdown.

Install host and companion APKs signed with the same certificate. A mismatched or stale companion is rejected rather than silently trusted.

## Project transfer

The host creates a ZIP archive in its cache and passes a read-only file descriptor through Binder. The archive is not placed in a Binder byte-array transaction.

The companion validates and extracts to its private `files/projects/<site-id>` directory using a staging directory and rename-based activation.

Current enforced bounds:

- 10,000 archive entries.
- 64 MiB maximum extracted size per file.
- 256 MiB maximum total extracted project size.
- Absolute paths, traversal segments, empty path segments and canonical-path escapes are rejected.
- Symlinks in source projects are rejected by the host packager.
- Common secret-bearing files and directories are excluded by the host and rejected by the companion (including `.env*`, credential files, private-key extensions and common credential directories).
- Active Python/Node project roots cannot be replaced while their runtime is active.

These filters reduce accidental secret transfer; they are not a complete secret scanner. Runtime projects should not contain credentials.

## Network and filesystem boundaries

The current companion UID prevents ordinary runtime code from directly reading host-app-private files. It does not isolate hosted projects: Python multi-site applications share one interpreter/process, and all Python/Node projects reside under the same companion UID and private directory.

Python WSGI is pinned to `127.0.0.1`; Node's preload constrains ordinary TCP listener binds. These are listener controls only. They do not block outbound TCP/UDP/raw sockets, DNS bypasses, native networking, private/link-local/metadata destinations or loopback management access. The current companion manifest still grants Android `INTERNET`.

The isolated-process probe previously showed a distinct UID and denied host-private file reads, but its loopback attempt failed. The new design does not ask an isolated cell to create or connect a loopback socket: a trusted broker creates the listener and passes its already-open file descriptor to the isolated cell. Instrumentation now probes simultaneous isolated UIDs, Binder-transferred sockets, and a project-directory FD with sibling traversal denial. The API 26/33/35 results are still pending; these probes are not yet proof of a completed sandbox.

**Security status: not a complete sandbox; Gate 2 remains open.** Python/Node are owner-trusted-code-only. Never run visitor-submitted, marketplace or other untrusted code. Do not put host credentials, Cloudflare tokens or other secrets in project roots.

The chosen target is one `android:isolatedProcess=true` execution cell per project, with no permissions, a project-only directory/runtime descriptor and a host-created Unix-domain listener descriptor. The host app's Rust engine connects through the private socket path; the cell verifies `SO_PEERCRED` is the host UID. A TSYNC seccomp-BPF filter denies new sockets/connect/bind and allows accept only on that listener FD; filter failure prevents project startup. The normal companion becomes a trusted broker and must never execute project code. See [Security Gate 2 Execution Plan](SECURITY_GATE_2_EXECUTION_PLAN.md) and [Security Isolation Design](SECURITY_ISOLATION_DESIGN.md).

## APK installation and updates

The host and companion are separate Android packages and must both be installed:

1. Install the matching `pockethost-runtime-worker` APK first.
2. Install the host APK for the device ABI.
3. Keep both packages signed with the same certificate.
4. Update the pair together. The host rejects an unsupported Binder protocol version.

The Android integration workflow publishes the companion APK alongside ABI-specific host APKs. Debug APKs are for integration testing, not production distribution. An owner-facing in-app companion installer/update/rollback flow remains to be completed.

## Lifecycle and failure behavior

The companion control service is a foreground service. Python runs inside its companion process; Node runs in the companion's dedicated `:node` process because the Node mobile embedding supports one `node::Start` call per process. The host supervises runtime status and can request coordinated shutdown.

Static serving remains in the host Rust engine and does not require Python or Node. Runtime crash recovery and host/worker process-death behaviour still require full API-matrix and device validation.

## Verification gates

- Build both APKs and inspect ABI contents.
- Confirm companion UID differs from host UID.
- Confirm companion cannot read a host-private marker.
- Confirm actual Python `os.getuid()` and Node `process.getuid()` differ from host UID.
- Verify Binder version/signature enforcement.
- Verify bounded transfer and traversal rejection.
- Verify Python/Node loopback listeners are reverse-proxied through Rust.
- Verify static hosting continues through runtime failure.
- Test API 26, 33 and 35; then physical ARMv7/ARM64 devices.
- Complete filesystem cross-project and network-egress adversarial tests before any sandbox or production-safety claim.
