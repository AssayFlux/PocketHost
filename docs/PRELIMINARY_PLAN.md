# Pocket Host — Preliminary Five-Step Completion Plan

**Target:** Pocket Host v0.1.0 Alpha  
**Working branch:** `Alpha` only  
**Plan status:** Steps 1–2 automated gates passed; Step 3 implementation in progress; Step 4 runtime/management hardening in progress  
**Rule:** finish and verify each step before treating the next as complete. Preserve the existing codebase; refactor only where evidence shows it is necessary.

## Delivery boundary

The first complete system is an Android phone that can independently host websites. Public access through the native relay is part of the first release goal and must be proven on a real network path. Remote management is secondary: only the minimum integration needed to preserve a coherent product and secure owner control is in scope. Full fleet operations are not a v0.1.0 gate.

## Step 1 — Establish the engineering baseline and trustworthy build pipeline

<badge>PASS — automated CI</badge>

**Purpose:** know exactly what is being built and make every subsequent change reproducible.

Work:
- Inspect the current Android, Rust, relay, runtime-worker and supporting modules before changing behavior.
- Restore concise CI workflows for quality checks, native ABI builds, Android compilation/APK packaging and explicitly opted-in emulator tests.
- Pin and verify toolchains/dependencies; validate Gradle and Cargo lockfile behavior.
- Run existing Rust, protocol, script and web-console checks; compile Android app and instrumentation sources.
- Record existing failures and distinguish regressions from pre-existing issues.
- Establish artifact naming, ABI verification, APK metadata/signature checks and build provenance.

Exit gate:
- A clean checkout of Alpha can run the documented build commands.
- Main app and worker APKs build reproducibly for the declared supported ABIs, or unsupported ABIs are explicitly removed from the supported matrix.
- CI runs on Alpha and reports real failures; emulator execution is not described as passed when skipped.
- Baseline test failures are tracked with an owner and resolution plan.

**Deliverable:** reproducible engineering baseline and restored CI. No feature expansion in this step.

## Step 2 — Complete and harden independent local website hosting

<badge>PASS — implementation and automated CI</badge>

**Purpose:** make the phone useful as a host even when relay, cloud services and optional runtimes are unavailable.

Work:
- Complete site creation/registration, folder and ZIP import, file management and multi-site serving.
- Audit path traversal, symlinks, MIME/content handling, index/fallback behavior, host routing and site isolation.
- Apply consistent file-count, nesting, compressed/uncompressed size, disk quota and request limits across import, serving, backup/restore and management APIs.
- Verify backup integrity, interrupted restore recovery and last-known-good behavior.
- Test local loopback and LAN serving, concurrent requests, large files, malformed requests and storage exhaustion.
- Keep static hosting operational when the runtime-worker companion or control plane is absent.

Exit gate:
- A clean install can import a representative website and serve it correctly over loopback and LAN.
- Multiple sites cannot read or overwrite each other's data.
- Invalid paths, oversized inputs and interrupted operations fail safely.
- Backup/restore produces a verified usable site.
- Automated tests cover the security and failure cases, not just the happy path.

**Deliverable:** dependable local static hosting with documented limits.

**Verification update (2026-11-20):** path/symlink and sensitive-file protections, dedicated managed roots, non-overlapping site isolation, persisted-route fail-closed validation, ZIP tree validation, quota accounting, deployment rollback/recovery and backup staging hardening are committed. Quality CI and Android source/native validation passed on `fe2af2277bf85eba16e2d3b67ec95d86acf56637` (runs `44726189328` and `44726189341`). Physical-device import, loopback/LAN and backup acceptance remains explicitly reserved for Step 5.

## Step 3 — Make Android hosting lifecycle and native public access dependable

**Purpose:** ensure the host stays understandable and recoverable, then prove one secure public route.

Work:
- Validate foreground-service startup/stop, Android notification/permission behavior, scheduling, reboot recovery, process death and battery/background restrictions.
- Handle Wi-Fi/mobile transitions, IP changes, DNS changes, sleep/wake and temporary offline states.
- Complete native relay owner-approved pairing and identity binding.
- Complete credential issuance, rotation, expiry and revocation; persist security-critical state transactionally.
- Enforce authenticated route ownership and cross-site isolation; test revoked credentials and stale routes end to end.
- Validate DNS ownership proof and actual TLS certificate issuance/renewal, including failure and recovery.
- Validate one supported public path end to end. Where direct inbound access is blocked by CGNAT/firewall, use the supported outbound relay/tunnel path and clearly report network constraints.
- Do not reintroduce Termux as a relay runtime.

Exit gate:
- Physical-device lifecycle tests pass across reboot, process death and network changes.
- Pairing requires explicit owner approval; credentials are scoped and revocable.
- Revocation removes access in a tested end-to-end scenario and survives restart.
- TLS is valid for a real hostname through the supported path, with renewal/failure behavior tested.
- An independent visitor on an external network can reach the intended site, and unauthorized routes cannot reach other sites.
- The app accurately reports unavailable network/public-access conditions.

**Deliverable:** resilient Android host service and one operational, secured public-access path.

## Step 4 — Integrate essential runtimes and minimum remote operations without widening the release

**Purpose:** make the existing supporting components coherent while keeping the Android host independently useful.

**Implementation update (2026-11-20):** Python and Node scope has been defined from the actual embedded runtime implementations. Runtime project transfer now applies matching 64 MiB per-file, 256 MiB aggregate, 10,000-entry, 32-level and 1,024-character path limits on the host transfer side; the companion rejects deep paths and oversized individual files. Case-colliding paths are rejected before transfer. Remote command handling now validates command UUIDs and fails closed if idempotency state is corrupt or cannot be durably committed. The Android instrumentation regression source covers transfer path and file-size boundaries. Automated and device acceptance are still pending.

Work:
- Define which Python/Node use cases are actually supported in this Alpha and verify install, start, stop, logs, resource ceilings and recovery.
- Treat imported application code as trusted owner code unless a separately tested isolation boundary proves otherwise. Do not describe the current companion as a hardened hostile-code sandbox.
- Verify runtime-worker IPC authentication, package/signature assumptions, lifecycle and failure containment.
- Validate essential local owner management and the minimum remote status/control integration already present in the app/control-plane contract.
- Verify device enrollment, authorization, session expiry/revocation and audit behavior only for the narrow flows retained in v0.1.0.
- Ensure the host remains usable if the control plane, web console, Linux agent or runtime-worker is offline.
- Mark incomplete screens/actions as unavailable rather than implying they work.

Exit gate:
- Every runtime shown as supported passes a defined workload and lifecycle test on a physical device.
- Resource limits are enforced and failures do not corrupt static hosting.
- No unsupported isolation/security promise is made.
- Essential management flows are authenticated, owner-authorized and revocable.
- Remote services are optional to local hosting; no full fleet-management feature set is required.

**Deliverable:** explicitly bounded runtime support and minimum coherent owner/remote operations.

## Step 5 — Integrated acceptance, Alpha APK and owner handoff

**Purpose:** prove the whole v0.1.0 system rather than collecting disconnected passing builds.

Work:
- Run all Rust, Kotlin, protocol, control-plane, web and script checks relevant to the retained scope.
- Run Android instrumentation on the declared API matrix; record any skipped jobs and reasons.
- Test on at least one physical Android device, recording model, Android version, ABI, app/worker versions and test results.
- Run a real-network public visitor journey, credential revocation, TLS renewal/failure recovery and network-change recovery.
- Check data migration, clean install, upgrade install, uninstall/data-retention expectations, backup/restore and crash recovery.
- Review permissions, exported components, secrets, logs/redaction, dependency/licence inventory and release configuration.
- Build and verify the v0.1.0 Alpha APK artifact(s); record SHA-256 checksums and signing status.
- Publish a concise release report with known limitations and exact device-testing instructions.

Exit gate:
- No unresolved release-blocking security, data-loss, build or core hosting defects.
- All required acceptance checks have evidence; skipped tests remain explicitly open.
- The APK installs and passes the documented owner smoke test.
- Known limitations are visible and no incomplete feature is represented as complete.
- Hand the APK and evidence to the owner. Do not begin a later-version upgrade until the owner has tested and decided the next direction.

**Deliverable:** v0.1.0 Alpha candidate APK(s), verification report, known-limitations list and owner handoff.

## Scope summary

### Included in this preliminary plan
- Android app and native Rust/JNI build stability.
- Local static multi-site hosting and file/site management.
- Android service lifecycle, schedules and recovery.
- Native relay security, credential lifecycle, route isolation, TLS/DNS and one real public-access path.
- Narrow, explicitly supported Python/Node runtime behavior only where it can be verified.
- Essential owner security and minimum management integration.
- Reproducible CI, automated/emulator/device/network acceptance and Alpha APK delivery.

### Explicitly deferred
- Full connected-device fleet platform and feature-complete remote console.
- Distributed hosting, clustering, orchestration and autoscaling.
- Arbitrary untrusted code execution and hardened multi-tenant sandboxing.
- Broad runtime/language expansion and arbitrary package build pipelines.
- Full Workbench production deployment platform.
- Universal DNS-provider automation and advanced access-policy management.
- Billing, SaaS multi-tenancy, marketplace and plugin ecosystem.

## Completion tracking

For each gate, record:
- commit SHA;
- command/workflow and toolchain versions;
- pass/fail/skipped status;
- test device/API/network details where relevant;
- artifact name and SHA-256 where applicable;
- open defects and owner decision.

A source implementation is **present**, not **complete**, until its exit gate is evidenced. The Alpha release must not be called complete based solely on a successful APK build.
