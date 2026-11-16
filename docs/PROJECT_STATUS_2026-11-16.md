# Pocket Host — Verified Project Status

**Review date:** 2026-11-16  
**Latest baseline before the current security fixes:** `e06b23aae056383478b4b3c092887163859d3c8c`  
**Previous verified readiness baseline:** **37%**  
**Release decision:** not production approved.

[PROJECT_BASELINE.md](PROJECT_BASELINE.md) is the canonical definition of scope, weighted readiness and completion criteria.

## Companion runtime migration — source implementation

The following changes are now present in `main`:

- Chaquopy CPython 3.11 and the WSGI module moved from the host app module into `:runtime-worker`.
- Node.js 24 native libraries, JNI bridge, Node metrics preload and Node foreground service moved into the companion APK.
- Host `PythonHostRuntime` and `NodeHostRuntime` now call a host-side Binder client.
- Binder IPC uses a signature-level permission, caller package/signature checks and protocol version 1.
- Project transfer uses a bounded ZIP archive and file descriptor, extracts into companion-owned storage and rejects path traversal/common credential paths.
- Python and Node status/lifecycle/metrics calls now route through the companion.
- Instrumentation tests now assert actual Python and Node process UIDs differ from the host UID.
- Android CI packaging checks were changed to expect Node native libraries in the companion APK and host Rust/cloudflared libraries in host APKs.

**Important:** source integration is not build verification. The Android source build, native companion packaging and API 26/33/35 instrumentation for this migration have not yet been confirmed in this status record.

## Security boundary and remaining limitations

The companion UID prevents ordinary runtime code from directly reading host-app-private files, but all hosted projects still share that UID and its private files directory. Python multi-site applications are imported into one interpreter/process, so cross-project access is possible. The companion has Android `INTERNET` permission; raw sockets, DNS bypasses, private/link-local/metadata destinations and loopback management reachability are not blocked by an OS-enforced per-project policy.

Python WSGI is now hard-pinned to `127.0.0.1`; its runtime API no longer accepts a LAN-bind override. This is listener exposure hardening only, not outbound-egress control. Node's preload has the same listener-only limitation.

The isolated-process probe showed that a distinct UID can deny host-private file access, but that process could not use the current loopback TCP route. `isolatedProcess=true` is not a drop-in fix. The target architecture and acceptance matrix are recorded in [SECURITY_ISOLATION_DESIGN.md](SECURITY_ISOLATION_DESIGN.md).

Common credential files are filtered during project transfer, but this is not a complete secret scanner. Host management credentials and Cloudflare tokens must never be copied into runtime project roots. Python and Node remain owner-trusted-code-only. Do not call this a complete sandbox or expose arbitrary third-party code. Gate 2 is incomplete.

## Security architecture proof spike — native policy probe added; API 26 failure under repair

The host-app instrumentation covers distinct isolated UIDs, project-directory read/write versus sibling traversal, and a host-owned abstract Unix-domain listener FD. A runtime-worker isolated process now contains a native `libpockethost_sandbox` TSYNC seccomp-BPF probe intended to test socket creation denial, outbound connect denial, accept on the brokered listener and `SO_PEERCRED` host identity. The previous API 26 run on commit `e06b23a` failed: SELinux denied the Binder-passed TCP visitor socket, and Python status returned `UNAVAILABLE` after companion shutdown. The source is being corrected to use Unix-domain sockets and non-starting status queries. The new policy probe has not yet run in CI; no successful kernel-policy test or Gate 2 completion is claimed.

The selected architecture and full implementation sequence are in [SECURITY_GATE_2_EXECUTION_PLAN.md](SECURITY_GATE_2_EXECUTION_PLAN.md).

## Existing foundations

Native Kotlin/Compose app; Rust/Tokio/Hyper static website origin; authenticated local management API and TypeScript/React console; Android cloudflared connector; encrypted backup/restore foundation; operational metrics/logs; initial Capacity Lab; separate-UID runtime companion source integration.

## Previous CI evidence (pre-migration source)

- Quality [43789396734](https://github.com/AssayFlux/PocketHost/actions/runs/43789396734): all six jobs passed on the earlier source baseline.
- Android [43789396568](https://github.com/AssayFlux/PocketHost/actions/runs/43789396568): API 26 passed 31 tests with zero failures; APK packaging and cloudflared build passed. API 33 was in progress and API 35 queued at last observation.
- These runs predate the current companion runtime migration and do not verify the new source.
- Previous artifact retrieval returned a truncated ZIP and is not a valid APK deliverable.

## Open gates

1. Compile host and companion APKs, verify all ABIs and signing identity.
2. Pass Python and Node companion runtime smoke tests on API 26, 33 and 35.
3. Verify project transfer bounds, failure rollback, service death/rebind and coordinated shutdown.
4. Verify Rust reverse proxy routing and static-origin continuity when either runtime fails.
5. Implement per-project OS identities/private storage and Rust-brokered inbound sockets, then prove OS-enforced egress denial and cross-project isolation using [SECURITY_ISOLATION_DESIGN.md](SECURITY_ISOLATION_DESIGN.md).
6. Complete Rust fairness/stress acceptance, Capacity Lab, backup/restore fault matrix, Settings visual acceptance and operations hardening.
7. Verify signed release pair, existing Cloudflare Tunnel HTTPS, physical-device behaviour and independent security review.

## Readiness method

The 37% score is the last weighted estimate before the companion source migration. It is retained until the new build and API-matrix evidence are available. It is not test coverage or a claim of production readiness. See [PROJECT_BASELINE.md](PROJECT_BASELINE.md).

