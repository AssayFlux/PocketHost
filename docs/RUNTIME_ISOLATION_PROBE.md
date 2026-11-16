# Runtime isolation architecture probe

**Status:** prototype instrumentation only; not a production sandbox and not a runtime migration.

## Why this exists

Python (Chaquopy) currently executes in the PocketHost application process. Node executes in a secondary process, but that process still has the application's Android UID. App-private project-root validation limits accidental path selection; it does not stop hostile project code from reading other files available to that UID or using the app's permissions.

## Probe implementation

`RuntimeIsolationProbeService` is declared with `android:isolatedProcess="true"` and executes no project code. The instrumentation test measures:

- Whether its Android UID differs from the host app UID.
- Whether it can read a marker file in the host app's private files directory.
- Whether it can connect to a host-process loopback TCP listener.

The loopback result is recorded in instrumentation status/log output rather than assumed. This matters because a worker that cannot reach the Rust router cannot host a website, even if its UID is isolated.

## Decision gates before migration

1. Run the probe on API 26, 33 and 35 and retain the per-API loopback result.
2. Verify Binder lifecycle, process death/rebind, file transfer via narrowly scoped descriptors/URI grants, and runtime library loading.
3. Separately prototype Chaquopy and Node native initialization. The probe intentionally does not load either runtime.
4. If isolated services cannot support required network access, evaluate a separately installed same-signature companion package with its own UID and signature-protected IPC. Do not silently fall back to same-UID execution and call it sandboxed.
5. Define explicit egress policy, project-scoped file access, worker limits, crash recovery and secret separation before enabling untrusted projects.

## Current security posture

The production Python and Node runtimes remain trusted-code-only. This probe is evidence gathering, not a security boundary for customer workloads. Android ordinary applications cannot promise arbitrary hard CPU quotas for another process; memory caps, worker limits, monitoring, termination and restart must be described according to what is actually enforceable.
