# Runtime isolation architecture probe

**Status:** capability probes only; no customer project code is executed by either probe.

## Existing same-UID runtime risk

Python (Chaquopy) currently executes in the PocketHost application process. Node executes in a secondary process, but that process still has the application's Android UID. App-private project-root validation limits accidental path selection; it does not stop hostile project code from reading other files available to that UID or using the app's permissions.

## Isolated-process result

`RuntimeIsolationProbeService` uses `android:isolatedProcess="true"` and executes no project code. Instrumentation on API 26 and API 33 passed and recorded:

- Worker UID differs from the PocketHost UID.
- Host-private marker file is unreadable from the isolated process.
- **Loopback TCP networking is unavailable** from the isolated process: it cannot bind/connect to the host listener.

This makes Android's isolated-process service unsuitable as the live Python/Node hosting worker: it blocks the network capability required by the local website router. API 35 had no result in that run because the emulator went offline before instrumentation started.

## Companion-package prototype

A new `:runtime-worker` Android application module is now present. It has a distinct package/UID, INTERNET permission, and a service protected by a signature-level permission. The main app and worker share the build signing identity for local/debug integration. A capability test checks UID separation, denial of host-private file access, and loopback bind/connect.

The companion is currently a **probe only**. It does not yet load Chaquopy, Node/V8, customer code, project files, or production credentials. The test must pass on API 26/33/35 before runtime migration begins. A production design must additionally implement authenticated/versioned IPC, scoped project transfer, lifecycle supervision, worker crash recovery, resource monitoring, explicit network-egress policy, package install/update/rollback and owner-visible recovery when the companion is missing or outdated.

## Migration gates

1. Verify companion UID, filesystem isolation and loopback networking on API 26, 33 and 35.
2. Test Binder lifecycle, process death/rebind and narrow content/file descriptor transfer.
3. Prototype Chaquopy and Node native runtime loading inside the companion package, retaining Python WSGI, Node, multi-site hostname routing and Cloudflare connectivity.
4. Keep management credentials, Cloudflare tokens and unrestricted app storage out of the worker. Apply an explicit egress policy and project-scoped filesystem access.
5. Add bounded worker/request limits, memory caps where available, monitoring, termination and restart. Do not claim arbitrary hard CPU quotas that ordinary unrooted Android apps cannot enforce.

## Current security posture

Python and Node production hosting remain trusted-code-only until the companion runtime migration is fully implemented and tested. Neither the isolated service nor the companion probe is currently the production execution boundary.
