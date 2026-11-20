# Pocket Host — Preliminary Five-Step Completion Plan

**Target:** Pocket Host v0.1.0 Alpha  
**Working branch:** Alpha only  
**Current status:** Steps 1–2 implementation and automated gates passed. Steps 3–4 implementation and historical automated checks are present, but device/operational acceptance is open. Step 5 post-fix Android/Chaquopy acceptance job is pending; no APK packaging is authorized.  
**Rule:** preserve the existing codebase, complete and verify each gate, and never claim device/network success from source or CI alone.

## Delivery boundary

The first complete system is an Android phone that independently hosts websites. Public access through the native relay is part of the release goal and must be proven on a real network path. Remote management is secondary and limited to the minimum secure owner operations. Full fleet operations are not a v0.1.0 gate.

## Step 1 — Engineering baseline and reproducible build pipeline

**Status: PASS — historical automated CI.**

Restore and run quality/native/source workflows; pin toolchains and lockfiles; record failures and artifact provenance.

Evidence: quality run [44721249398](https://github.com/AssayFlux/PocketHost/actions/runs/44721249398) passed the recorded Rust, PostgreSQL control-plane, Python, protocol, Node, web-console and script checks. Android native/source run [44721249324](https://github.com/AssayFlux/PocketHost/actions/runs/44721249324) passed three-ABI native/ELF checks and Android source compilation. This does not prove emulator/device execution or APK install acceptance.

## Step 2 — Independent local website hosting

**Status: PASS — implementation and automated CI; physical-device acceptance open.**

Scope includes site creation/import, path and symlink protection, sensitive-file handling, site isolation, quota/size limits, route validation, rollback and backup/restore.

Evidence: quality run [44726189328](https://github.com/AssayFlux/PocketHost/actions/runs/44726189328) and Android run [44726189341](https://github.com/AssayFlux/PocketHost/actions/runs/44726189341) passed recorded hosting/relay tests, Android source compilation and three-ABI native builds. Physical-device import, loopback/LAN, multi-site and interrupted-restore tests remain open.

## Step 3 — Android lifecycle and native public relay

**Status: IMPLEMENTATION PRESENT; AUTOMATED HISTORY PASSED; DEVICE/OPERATIONAL GATE OPEN.**

Implemented source includes:
- Native Android foreground-service and Rust/JNI relay direction; no Termux relay.
- Transactional relay configuration/state and boot recovery.
- Owner-approved pairing flow with proof-of-possession.
- Keystore-protected host identity/credentials and separate visitor-ingress credentials.
- Credential issuance/rotation and revocation paths.
- Authenticated host sessions, site-bound route handling and durable revocation state.
- DNS ownership checks and ACME HTTP-01 certificate issuance foundations.
- TLS certificate reuse/startup checks and bounded network-change reconnection.

Evidence: quality run [44730596020](https://github.com/AssayFlux/PocketHost/actions/runs/44730596020) passed the recorded Rust hosting/relay/control-plane checks; Android run [44730595964](https://github.com/AssayFlux/PocketHost/actions/runs/44730595964) passed three-ABI native builds and Android app/instrumentation/runtime-worker source compilation.

Still required: physical-device lifecycle/reboot/process-death/network tests; live DNS ownership and real hostname certificate issuance/renewal; pairing and credential rotation/revocation across restart; cross-site route isolation; independent external visitor acceptance. The source and CI evidence do not satisfy those operational exit gates.

## Step 4 — Supported runtimes and minimum remote operations

**Status: IMPLEMENTATION PRESENT; AUTOMATED HISTORY PASSED; DEVICE WORKLOAD GATE OPEN.**

Implemented/documented scope:
- Python 3.11 WSGI contract and Node.js mobile runtime 24.22.1-0.
- Single supported Python WSGI application and one Node application; no arbitrary package build pipeline or ASGI promise.
- Bounded project transfer: 64 MiB per file, 256 MiB expanded aggregate, 10,000 expanded tree nodes, depth 32 and path length 1,024; symlinks, sensitive paths and case collisions rejected.
- Companion Binder caller identity/signature/protocol checks and stale-connection invalidation.
- Node dedicated process, documented heap bounds and Python concurrency/request bounds.
- Owner-trusted code only; no hardened hostile-code or per-project UID sandbox claim.
- Remote commands use bounded allowlists, durable start/result idempotency and fail-closed persistence.
- Local static hosting remains independent of optional runtime-worker/control-plane services.

Evidence: quality run [44730596020](https://github.com/AssayFlux/PocketHost/actions/runs/44730596020) and Android source/native run [44730595964](https://github.com/AssayFlux/PocketHost/actions/runs/44730595964) passed their recorded gates. Runtime-worker source compilation is not Python/Node execution on a device.

Still required: emulator instrumentation; physical Python/Node start/request/stop/crash/recovery and resource-bound tests; companion upgrade/signature/protocol mismatch tests; pairing, capability denial, session expiry, revocation and remote retry acceptance. Keep these as open until evidence exists.

## Step 5 — Integrated acceptance and owner handoff

**Status: PRE-PACKAGE CHECKS IN PROGRESS; APK PACKAGING HELD.**

Latest head: `3b127e785224165c9f2415df9ce013e60b2160a7`.

- Latest quality workflow [44737757283](https://github.com/AssayFlux/PocketHost/actions/runs/44737757283) passed.
- The acceptance workflow [44737757242](https://github.com/AssayFlux/PocketHost/actions/runs/44737757242) was observed pending/queued with no jobs reported. It contains post-fix Android lint/JVM checks and a non-packaging Chaquopy runtime asset task. These are not yet evidenced as passed.
- Chaquopy's Maven repository has been added after the previous bootstrap dependency resolution failure.
- APK assembly, emulator instrumentation and APK verification are conditional on explicit manual `confirm_build=true`; this pass does not authorize or run those steps.
- No APK is built in this pass.

Remaining:
1. Obtain completed post-fix Android lint/JVM and Chaquopy runtime asset gate results.
2. Review dependency/licence/security audit outputs and disposition npm advisories.
3. Execute API 35 x86_64 emulator instrumentation when authorized and report every failure/skip.
4. Complete physical-device install, lifecycle, local hosting, import, isolation, backup/restore and runtime workloads.
5. Complete real-network pairing, revocation, DNS/TLS issuance/renewal, route isolation and independent visitor acceptance.
6. Review permissions, exported components, secrets/log redaction and signing configuration.
7. Only after explicit owner authorization, build and verify app/worker APKs, metadata, ABI payloads, matching signer and SHA-256.

**Exit rule:** v0.1.0 is not declared complete until all required evidence exists. CI success or a future APK build alone does not prove device, public-network or security acceptance.

## Scope summary

Included: Android host, local static multi-site hosting, native relay, bounded supported Python/Node workloads, essential owner security/remote operations, automated and device/network acceptance, and owner-authorized Alpha APK.

Deferred: full fleet console, distributed hosting, arbitrary untrusted-code sandboxing, arbitrary package build pipelines, broad runtime catalogue execution, billing, SaaS multi-tenancy and marketplace.

Do not begin a later-version implementation track until the owner has installed and tested v0.1.0 and decided what comes next.
