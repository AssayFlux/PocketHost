# PocketHost — verified project status

**Review date:** 2026-11-16 01:46 UTC
**Repository:** [AssayFlux/PocketHost](https://github.com/AssayFlux/PocketHost)
**Reviewed main SHA:** `6a2a54d76e8ae71c8abc4cc9d5f6f0e20a03b34f`
**Decision:** integrated development build; **not production-ready**.

## Exact current CI evidence

| Check | Result at review | Evidence |
|---|---|---|
| Quality workflow | Passed all six jobs: Rust, Python, Node, control plane, console and Android source compilation | [Run 43786695783](https://github.com/AssayFlux/PocketHost/actions/runs/43786695783) |
| Android cloudflared native build | Passed | [Run 43786695821](https://github.com/AssayFlux/PocketHost/actions/runs/43786695821) |
| Android debug APK packaging | Passed; three host ABI APKs and companion APK packaged; host/worker debug signing certificates matched | [Run 43786695821](https://github.com/AssayFlux/PocketHost/actions/runs/43786695821) |
| API 26 emulator | Running; companion capability instrumentation not yet confirmed | [Run 43786695821](https://github.com/AssayFlux/PocketHost/actions/runs/43786695821) |
| API 33 emulator | Queued behind API 26 | [Run 43786695821](https://github.com/AssayFlux/PocketHost/actions/runs/43786695821) |
| API 35 emulator | Queued behind API 26 and API 33 | [Run 43786695821](https://github.com/AssayFlux/PocketHost/actions/runs/43786695821) |

The debug APK artifact is [available here](https://github.com/AssayFlux/PocketHost/actions/runs/43786695821/artifacts/14190833098). It is a development artifact, not a signed production release.

## Implemented in the repository

- Native Kotlin/Jetpack Compose Android application, package `com.assayflux.pockethost`, with Dashboard, Sites, Activity, Learn and Settings destinations.
- Rust/Tokio/Hyper HTTP origin, hostname routing, static file serving, bounded admission controls and Cloudflare connector build.
- Python/Chaquopy and Node.js mobile runtime integrations, currently restricted to trusted owner-installed code because both still execute under the host app UID.
- Local authenticated management API and TypeScript/React management console; optional remote control-plane source is separate from the public website origin.
- Static website import/deployment, file management, backups/restore and operational telemetry/diagnostics.
- Runtime project-root canonicalization and symlink escape checks. This is path validation, not a same-UID security boundary.
- `:runtime-worker` companion APK prototype with separate package/UID, INTERNET permission and signature-protected Binder probe.
- Dashboard screenshot instrumentation has been added; its result is pending the current emulator run.

## Runtime security: exact boundary

The `android:isolatedProcess` probe confirmed separate UID and host-private-file denial on API 26 and API 33, but loopback TCP was blocked. That approach cannot serve requests from the Rust router.

The new companion package is the alternative capability prototype. Its test checks a distinct UID, inability to read a host-private marker, companion loopback listener reachability from the host, and companion connection to the host loopback listener. The current CI result for that test is not yet available at this snapshot.

**Not integrated yet:** Chaquopy/CPython and Node/V8 do not run inside the companion; there is no production worker protocol, project-scoped file-descriptor transfer, runtime lifecycle/recovery integration, egress policy, worker install/update/rollback UX or end-to-end Python/Node routing through the companion. Therefore the companion must not be described as a production sandbox. Keep Python/Node limited to trusted code.

## Remaining engineering work (assistant/repository work)

1. Complete the companion integration: authenticated/versioned IPC, bounded request protocol, scoped project data transfer, process lifecycle and crash recovery; load and run Chaquopy and Node/V8 in the companion; preserve WSGI, Node, multi-site routing and Rust ingress.
2. Enforce a deliberate worker egress policy. Keep management credentials, Cloudflare tokens and unrelated host files out of the worker. Add cross-project, traversal, symlink, SSRF, DNS-rebinding and resource-exhaustion tests.
3. Complete Rust-native rate limiting, per-site fairness, bounded waiting queues, in-flight graceful drain and overload/recovery tests. Existing admission controls are a first implementation, not the complete governor.
4. Expand Capacity Lab beyond static GET sweeps: cached/uncached and response-size workloads, runtime workload profiles, throughput and p50/p95/p99, repeatability, thermal/memory aborts and benchmark invalidation.
5. Complete operations hardening: request-level redacted logs, durable/tamper-evident audit verification, storage-pressure handling and full backup/restore coverage including interruption and custom roots.
6. Finish UI decomposition and acceptance: Settings extraction, responsive/landscape/accessibility checks, screenshot review and complete native account/session edge-case coverage.
7. Verify public HTTPS through the owner's existing Cloudflare Tunnel and confirm the public hostname points only to the Rust website listener, never the management API.
8. Complete dependency/security review and update all documentation against the exact tested commit.

## External/owner acceptance gates

- Physical Android 8+/API 26 and newer device acceptance on representative ARMv7/ARM64 phones: installation/upgrade, boot, foreground service, Doze, process death/recovery, battery, thermal, low-storage and long-uptime behavior. These checks cannot be proven by emulator CI.
- Public HTTPS and Cloudflare Tunnel acceptance requires the owner's domain/tunnel environment and external network test.
- Optional remote control-plane deployment and real-device pairing require an owner-controlled deployment environment and secrets. Local hosting must remain independent of this service.
- Production signing requires the four GitHub Actions secrets described in [RELEASE_SIGNING.md](RELEASE_SIGNING.md). Do not share signing material in chat or commit it.
- Independent security review and release approval remain outstanding.

## Release boundary

Static hosting is the primary feature and must continue to work without Python, Node, remote control or payments. PocketHost is intended for small sites, development and modest workloads—not cloud-region uptime or unbounded public code execution. No universal visitor count, hard CPU quota, complete sandboxing, production readiness or signed release is claimed.

## Next validation rule

Do not advance the project status to complete until the companion runtime migration, Rust governor, backups/operations, UI acceptance and security review are implemented and tested on the exact release SHA, and the physical-device/Cloudflare/owner-controlled deployment gates have recorded evidence.