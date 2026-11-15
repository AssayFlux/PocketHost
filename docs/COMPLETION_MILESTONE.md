# PocketHost — Completion and production-readiness milestone

**Purpose:** single actionable definition of completion. This document supersedes the original pre-integration blocker list. Source implementation is now substantially integrated; the remaining work is validation, hardening, deployment decisions and release evidence.

## Product baseline

- Standalone Kotlin/Jetpack Compose Android app; no Termux and no root.
- Android 8.0 / API 26+, 4 GB RAM baseline.
- ARMv7 + ARM64 phones; x86_64 emulator.
- Rust/Tokio/Hyper owns the website HTTP request path.
- Static sites plus optional embedded Python WSGI and Node.js runtimes.
- TypeScript/React local console.
- Cloudflare Tunnel is the intended public connectivity path. The user has an existing domain and connected tunnel; reuse that configuration.
- Simple and Advanced modes.
- Local hosting must work without a remote VPS/control-plane service.

## Workstream status

| Workstream | Source status | Remaining completion evidence |
|---|---|---|
| Android shell and lifecycle | Implemented | Emulator + Vivo Y95/Moto G54 physical lifecycle acceptance |
| Rust static engine | Implemented | Current API matrix, security and load tests |
| Multi-site registry and routing | Implemented | Host-conflict, unknown-host, path and multi-site end-to-end tests |
| Python WSGI | Implemented preview runtime | Device startup, dependency compatibility, failure recovery and resource tests |
| Node.js | Integrated using pinned mobile SDK | Current emulator execution, physical ABI tests, restart and memory-pressure acceptance |
| Local management API | Implemented | Full endpoint-level auth, parsing, abuse and DNS-rebinding review |
| TypeScript console | Connected to local API in source | Browser-origin/CORS, pairing, reconnect and UI end-to-end validation |
| Cloudflare Tunnel | Android connector source/build integrated | Configure token in app; verify existing Public Hostname origin and external HTTPS response |
| Optional remote control plane | Source implemented | Decide hosting location; deploy and run real phone pairing/command/revocation tests if retained in release |
| Backup/restore | Static-site-only feature implemented | Wrong passphrase, tamper, interruption and device-to-device restore tests |
| Diagnostics | Implemented in source | Inspect exported bundle for redaction and size/retention behavior |
| Release pipeline | Workflow and signing guide present | Configure signing secrets, build, install/upgrade and verify checksums/ELF compatibility |

## Critical product distinction

The **phone is the website server**. The Cloudflare tunnel connects visitors to the phone's Rust origin. The separate Rust/PostgreSQL control plane is an optional remote-management backend; it is not the website server and is not required for local hosting or the phone's Cloudflare Tunnel.

The current control-plane deployment workflow expects a separate Linux/Docker host. Do not make that a mandatory prerequisite for the basic PocketHost release. If remote management is included in the first public release, decide how its endpoint will be hosted and tested. Do not claim that the backend is deployed or that a VPS is already configured.

## Required execution order

1. Reconcile all source/documentation claims and keep a single status record.
2. Complete the current merged-source Android CI run: ABI APK build, APK content checks and API 26/33/35 instrumentation.
3. Fix all failures and rerun the full matrix on the final source SHA.
4. Run targeted integration tests for Node process lifecycle, Python routing, static hosting continuity during runtime configuration, multi-site host selection and remote command idempotency.
5. Perform the HTTP/management/control-plane security review. Resolve critical/high findings before any public release.
6. Validate the existing Cloudflare tunnel and domain end-to-end from a second network. Confirm the public hostname routes only to the website origin, never the local management API.
7. Run physical-device acceptance on Vivo Y95, Moto G54 and an ARMv7 Android 8 representative. Record exact OS build, ABI, RAM, APK SHA-256, test date and results.
8. Stress static + Python + Node workloads; record memory, CPU, thermal, storage, network transition and recovery behavior.
9. Validate static backup/restore, diagnostics redaction and app upgrade/reinstall paths.
10. Configure release signing, build signed ABI APKs, verify signatures and publish hashes only after acceptance.
11. Mark the release as production-ready only when all mandatory gates have evidence and no unresolved critical/high security issue remains.

## Release acceptance checklist

- [ ] Quality workflow green on the exact release commit.
- [ ] Android integration workflow green on the exact release commit.
- [ ] API 26, 33 and 35 emulator tests pass on that commit.
- [ ] ARMv7 and ARM64 physical-device tests pass.
- [ ] Vivo Y95 and Moto G54 lifecycle and network tests recorded.
- [ ] Static site deploy/rollback and multi-host routing pass.
- [ ] Python and Node startup, routing, stop/restart and recovery pass.
- [ ] Static hosting remains available when optional runtime configuration changes.
- [ ] Local console pairing, session expiry/revocation and CORS checks pass.
- [ ] Existing Cloudflare tunnel returns the correct website content over HTTPS from an external network.
- [ ] Public management endpoints are not exposed; Access policy is verified for private content.
- [ ] Remote control plane deployed and end-to-end tested, **only if included in release scope**.
- [ ] Backup restore and diagnostics redaction pass.
- [ ] Release APKs signed, upgrade-tested and checksummed.
- [ ] Known limitations and support matrix published.

## Non-negotiable honesty rules

- Source present ≠ runtime verified.
- Compilation passed ≠ instrumentation passed.
- Emulator passed ≠ physical phone passed.
- Tunnel connected ≠ public hostname verified.
- Signed APK ≠ production approved.
- A remote control-plane source tree ≠ deployed remote service.
- Mark each gate PASS, FAIL or NOT TESTED and attach evidence.
