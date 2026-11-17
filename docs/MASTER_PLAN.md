# Pocket Host 0.1.0 — Master Plan

**Canonical branch:** main  
**Product baseline:** 0.1.0  
**Primary owner interface:** Android or iPhone mobile command center  
**System form:** mobile-controlled, role-assigned device fabric

## Product objective

Let a user install the appropriate Pocket Host software on different devices, verify each device through a mobile command center, assign it a bounded job and combine those devices into a resilient personal service system. The mobile device can act as a Website Server only, or combine Website Server and Command Center.

## Non-negotiable decisions

1. The mobile application is the primary owner command center.
2. Mobile has exactly two modes: Server only and Command Center + Server. Both serve websites; only the combined mode can enroll, verify, approve and assign fleet devices. There is no standalone Command Center-only mode.
3. A device must be explicitly verified and approved by the command center before integration.
4. Every device has a stable cryptographic identity, short-lived enrollment challenge and revocable role grants.
5. Different device classes receive separate installable software/firmware built for their OS, architecture and hardware.
6. Users assign jobs such as storage, traffic, build/publish, native backend jobs, backup and edge gateway.
7. Future optional language runtime add-ons, when introduced, execute on the designated mobile website-serving node; they are not bundled into the main app or required for its built-in web server. Worker devices do not silently become general-purpose language runtime hosts.
8. Static serving is independent of optional language runtimes.
9. Wi-Fi/LAN is the preferred high-bandwidth transport; Bluetooth is for discovery/proximity/small control; USB is used only where the OS and hardware expose supported APIs; remote links use authenticated encrypted sessions.
10. Discovery is never authorization. Verification code, device identity, role grant and credential binding are all required.
11. Each host, role, job, storage namespace, traffic route, runtime and MCU adapter has separate permissions, health, resource limits and recovery.
12. Control plane and mobile app are not required for already-authorized local jobs to continue.
13. Local Network, AssayFlux Relay and Cloudflare Tunnel remain independent, optionally simultaneous visitor ingress providers.
14. DemonCore is the MCU/edge integration foundation; SBC gateways and MCU firmware have separate trust and safety boundaries.
15. A device is supported only for the exact role, transport, OS, board and workload combination that passes acceptance.

## Product software deliverables

- Android mobile application with the website-server role always enabled and a selectable fleet-administration layer: Server only or Command Center + Server.
- iOS mobile application follows the same two-mode product contract only where website serving passes platform lifecycle acceptance; otherwise do not advertise an unsupported server mode or silently introduce a Command Center-only product.
- Android worker/host agent.
- Future optional server runtime pack manager, delivered after the five-step baseline.
- Linux host/worker agent packages.
- Windows host/worker agent installer and service.
- macOS host/worker agent installer and service.
- Raspberry Pi and other SBC packages/images, architecture and board-specific.
- DemonCore/MCU firmware and edge adapters.
- Optional remote fleet control plane and secondary browser console.
- Future expansion: separately installable language runtime packs for the mobile serving node (outside the five-step baseline).

## Five-step execution plan

### STEP 1 — Freeze contracts, trust and product boundaries

**Contract freeze delivered:** protocol `pockethost.device/1.0` now defines device hello, one-time enrollment challenge, signed role grants, typed job envelopes, role capability policy and per-job payload/role policy. `docs/STEP_1_TRUST_AND_PROTOCOL.md` freezes canonical signing input, identity derivation, challenge lifetime, transport limits, runtime/artifact boundaries, threat mitigations and DemonCore interop. Deterministic positive/negative trust-state simulations and schema/policy CI are committed. **Gate status: implementation complete; automated run evidence pending.** No production cryptographic verifier or platform enrollment implementation is claimed; those are Step 2 implementation deliverables.
- Make SYSTEM_ARCHITECTURE.md normative.
- Freeze device identity, enrollment, verification code, role grant, host-agent and job schemas.
- Define mobile operating modes and role/permission matrix.
- Define transports and platform limitations: Wi-Fi, BLE, USB and remote.
- Define built-in web serving, artifact handoff, storage and traffic contracts; keep future optional runtime add-ons separately versioned.
- Threat model replay, rogue devices, stolen phones, malicious workers, compromised storage, supply chain and MCU safety.
- Align DemonCore interop and version compatibility.
**Exit:** schemas reviewed; threat model and trust ceremony tested with protocol simulations; no implementation begins against ambiguous contracts.

### STEP 2 — Mobile command center and verified device integration

**Android trust foundation committed; gate open.** The mobile app now persists the two operating modes, gates the Devices workspace to Command Center + Server, creates a P-256 Android Keystore owner identity, signs protocol-shaped five-minute enrollment challenges, verifies candidate proof-of-possession, issues schema-shaped role grants with allow-listed capabilities and resource budgets, verifies grants, encrypts the local registry and revokes by generation. Android instrumentation coverage and compile-only CI are added. Current enrollment is manual payload exchange; LAN/BLE/USB/remote transport, remote control-plane integration, independently installed agent verification, owner re-authentication, durable jobs and physical-device acceptance remain open. See [Step 2 implementation record](STEP_2_DEVICE_TRUST_IMPLEMENTATION.md).

- Build Android and iOS fleet experiences with identical core capabilities.
- Implement discovery, QR/device-ID verification, one-time code, owner approval, secure key binding, role assignment and revocation.
- Implement Wi-Fi/LAN control first, BLE pairing/control second, USB only for verified platform paths.
- Show device capabilities, health, assigned job, permissions, connection and audit history.
- Implement exactly two mobile modes: Server only and Command Center + Server.
**Exit:** unknown devices cannot join; replay/expired codes fail; role changes and revocation work; mobile app outage does not stop accepted jobs.

### STEP 3 — Autonomous device software and role workers

**Linux agent preview foundation committed; Step 3 gate open.** A separately installable Python package at `agents/linux` now creates a P-256 device identity, signs enrollment challenge proofs, verifies owner-signed role grants and policy bounds, persists generation/job idempotency in SQLite, and runs only an allow-listed native health-check job. CI runs its unit tests. It is not yet a network-connected daemon or a production-supported worker: authenticated transport, service supervision, cgroups, role adapters, signed updates and Windows/macOS/SBC packages remain open. See [Step 3 implementation record](STEP_3_DEVICE_AGENTS.md).

- Build separate signed installers/agents for Android, Linux, Windows and macOS.
- Build Raspberry Pi/SBC packages and gateway images.
- Implement durable local assignment state, supervisor, resource budgets, health, updates and rollback.
- Deliver first worker roles: storage, backup, build/publish, traffic proxy/cache and bounded native backend jobs.
- Add DemonCore edge gateway and MCU enrollment/capability adapters.
- Ensure each role has separate scoped credentials, queue and data namespace.
**Exit:** each supported role passes device-specific lifecycle, reboot, network-loss, resource-pressure and isolation tests; one worker cannot compromise sibling roles.

### STEP 4 — Main Pocket Host mobile website server
**Partial-close boundary:** Step 4 is the main Pocket Host mobile application and its built-in web-serving stack. HTML and CSS are handled by the bundled Rust static server. TypeScript is a built-in authoring/build capability that must compile to browser JavaScript before publication. A user does not install a companion runtime to publish a basic HTML/CSS/JavaScript/TypeScript website. The current Rust engine already serves HTML/CSS; TypeScript compiler integration and app-bundling/acceptance evidence remain implementation work.
- Keep Server only and Command Center + Server as explicit modes; both continue serving websites.
- Keep Rust ingress, static serving, site routing and safe asset delivery in the main app.
- Bundle the TypeScript compiler/transpilation toolchain in the main app and emit JavaScript artifacts for browser execution. Raw `.ts` files are not browser-executable.
- Build workers produce immutable artifacts; the main app verifies, stages, previews and atomically activates approved output.
- Static website serving must work without any separately installed language runtime.
**Partial exit:** HTML/CSS static hosting is available from the bundled main app. **Remaining Step 4 exit:** TypeScript compilation is bundled and tested; main-app site lifecycle, artifact verification, preview/atomic publishing and physical-device acceptance pass.

### FUTURE PLAN — Optional Runtime Library (after the five-step baseline)
Optional server-side language runtimes are separate downloadable add-ons, not part of Step 4 and not bundled into the main Pocket Host app. Each language has its own independently versioned, signed, installable package. Users download only the languages they need. See [Future Runtime Library](FUTURE_RUNTIME_LIBRARY.md) for the 20-package catalog, package contract and rollout sequence.

### STEP 5 — Fleet resilience, connectivity and release acceptance
- Integrate Local Network, AssayFlux Relay and Cloudflare Tunnel as independent selectable providers.
- Add cross-device replication, encrypted backups, restore and device replacement.
- Add capacity-aware assignment, backpressure, fairness, observability and fleet audit.
- Test phone loss, worker loss, storage loss, tunnel outage, network partitions, stale credentials, rogue devices, update rollback and MCU gateway failure.
- Publish signed software for each supported platform and board, support matrix, SBOM, provenance and upgrade/recovery guides.
**Exit:** end-to-end fleet scenario passes on named physical devices; security and recovery review complete; no unsupported platform or role is claimed.

## Future expansion after the five-step baseline

The optional 20-language Runtime Library is a separate post-baseline expansion. It must not block Step 4 or be bundled into the main Pocket Host application. Each runtime is a separate downloadable, signed package selected by the user. See [Future Runtime Library](FUTURE_RUNTIME_LIBRARY.md).

## Dependency order

Step 1 is the contract gate. Step 2 establishes the trusted control path. Step 3 creates useful autonomous workers. Step 4 delivers the main app's built-in web server and publishing; optional language runtime add-ons are a later expansion. Step 5 hardens the distributed system and releases it.

Parallel work is permitted only after the relevant contract and compatibility tests are frozen. A successful compile, simulated device or documentation update alone never closes a step.

## Release and evidence policy

Every component release records product/component version, source SHA, target OS/architecture/board, contract compatibility, artifact digest, signature identity, SBOM, permissions, install/upgrade/rollback path and test evidence. Device roles and platform support are marked supported only after named acceptance tests.

0.1.0 is the baseline specification and engineering target, not a claim that every platform or five-step deliverable is already implemented.
