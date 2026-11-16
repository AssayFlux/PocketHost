# Pocket Host — Verified Project Status

**Review date:** 2026-11-16  
**Companion source integration:** `bd227b0e5564ad25c88064cfc36796469390b773`  
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

The separate companion UID prevents ordinary Python/Node runtime code from directly reading files protected by the host app UID. It does not yet provide per-project isolation: all projects in the companion package share the companion UID. Outbound network egress is not yet enforced against raw sockets, DNS rebinding, private/link-local/metadata targets or loopback management services.

Common credential files are filtered during project transfer, but this is not a complete secret scanner. Host management credentials and Cloudflare tokens must never be copied into runtime project roots.

Python and Node remain trusted-code-only. Do not call this a complete sandbox or expose arbitrary third-party code.

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
5. Implement per-project filesystem isolation and enforceable outbound network policy; complete adversarial tests.
6. Complete Rust fairness/stress acceptance, Capacity Lab, backup/restore fault matrix, Settings visual acceptance and operations hardening.
7. Verify signed release pair, existing Cloudflare Tunnel HTTPS, physical-device behaviour and independent security review.

## Readiness method

The 37% score is the last weighted estimate before the companion source migration. It is retained until the new build and API-matrix evidence are available. It is not test coverage or a claim of production readiness. See [PROJECT_BASELINE.md](PROJECT_BASELINE.md).

