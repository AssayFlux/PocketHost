# Runtime isolation and companion migration

**Source migration:** integrated in main source at `bd227b0e5564ad25c88064cfc36796469390b773`; subsequent fixes are in later main commits.
**Verification:** companion build and API 26/33/35 instrumentation for the migrated implementation are pending.

## Why the host UID was a problem

Android assigns each installed application a Linux UID. Before migration, Chaquopy Python ran inside the host app and Node ran in a secondary process that still shared the host app UID. Customer code could potentially access any host-private file available to that UID. Canonical project-root checks did not create a process security boundary.

## Isolated-process experiment

The original `android:isolatedProcess="true"` probe proved that a fully isolated service could not access host-private files, but it also could not bind/connect to the loopback TCP sockets required by Rust reverse proxying. That experiment remains useful evidence; it is not the runtime implementation.

## Separate-UID companion now integrated in source

The `:runtime-worker` Android application has package `com.assayflux.pockethost.worker` and its own Android UID.

- Chaquopy CPython 3.11 and WSGI now live in the companion.
- Node.js 24 native libraries, JNI bridge, Node foreground service and metrics preload now live in the companion.
- Python runs in companion process `:runtime_worker`; Node runs in companion process `:node`. Both processes share the companion UID.
- The host uses signature-protected Binder IPC, host-package/signature checks and protocol version 1.
- Projects are transferred by bounded ZIP archive through a file descriptor and extracted into companion-owned storage.
- Rust remains in the host app and routes to companion loopback listeners.
- Tests now assert actual Python/Node runtime UIDs differ from the host UID.

## What this boundary does and does not provide

**It does:** prevent ordinary companion runtime code from directly reading files protected by the host app UID, subject to Android's package/UID model.

**It does not:** isolate one site from another site in the companion package; prevent all access to companion-owned files; enforce outbound network restrictions; prevent access to reachable loopback services; or impose hard CPU quotas.

Common secret files are excluded/rejected during project transfer, but filename filters are not a complete secret scanner. Host management credentials and Cloudflare tokens must never be copied into runtime project roots.

Until per-project filesystem isolation and enforceable network egress controls are implemented and adversarially tested, runtimes remain trusted-code-only. Do not call this a complete sandbox or support arbitrary third-party code.

## Remaining gates

1. Pass companion build and actual runtime UID tests on API 26, 33 and 35.
2. Test project transfer bounds, archive traversal, corrupted archives, interrupted transfer and replacement rollback.
3. Prove Rust-to-Python and Rust-to-Node proxy routing and static-host continuity under runtime failure.
4. Implement per-project filesystem isolation or a documented owner-trust model with explicit consent.
5. Implement enforceable network egress policy, including raw sockets, DNS rebinding, management loopback, private/link-local and metadata targets.
6. Test process death, reconnection, update/rollback, low memory and Android foreground-service lifecycle.
7. Complete independent adversarial review before production claims.
