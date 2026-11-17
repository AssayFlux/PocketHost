# Pocket Host — Master Engineering Plan V3

**Product:** AssayFlux Pocket Host  
**Version target:** 0.1.x pre-release  
**Canonical branch:** `main`  
**Status:** Engineering plan; no production approval  
**Supersedes:** the previous sequencing and runtime packaging guidance in this file. Existing subsystem specifications remain useful unless this plan explicitly changes them.

## 1. Mission and two completion tracks

Pocket Host evolves from an Android-first website host into a cross-platform, distributed self-hosting ecosystem. Android remains the first production host; the architecture must support capable phones, tablets, Linux/Windows/macOS computers, VPSs, servers, ARM single-board computers and constrained microcontroller/edge nodes through explicit capability tiers. The product must satisfy the existing reliability/security tracks and the universal-device expansion together:

1. **Product and Android reliability:** correct navigation, responsive layouts, accessibility, Android lifecycle/settings compliance, trustworthy status, installation, recovery and real-device behaviour.
2. **Secure modular execution:** optional independently installable language runtimes, per-project operating-system isolation, controlled capabilities, predictable resource use and a stable extension contract.

Neither track can compensate for failure in the other. A beautiful interface does not make an insecure or unreliable host; a technically capable engine does not make an unusable product.

## 2. Product and architecture decisions

### 2.1 One host app, optional runtime packs

The main Pocket Host APK is the only required user-facing application. Static hosting must work without any language runtime installed.

Python, Node.js and future languages are **optional runtime packs**. Users discover, install, update, disable and uninstall each language independently from Runtime Manager. A project declares its required runtime and compatible version range. The app must explain missing or incompatible runtimes and offer an explicit install action.

A runtime pack is a versioned, signed, ABI-aware deliverable with a manifest, dependency inventory, compatibility declaration, health check and rollback metadata. It must not bundle every language into the base app or force users to download unused runtimes.

### 2.2 Runtime package boundary is not project isolation

A distinct Android package/UID per language is useful for modular installation and separating language providers from the host. It does **not** isolate two projects using the same language package.

The security target remains one execution cell per project, with a distinct OS-enforced identity and narrowly brokered capabilities. The runtime provider supplies the interpreter/compiler and bootstrap; it does not own a shared, unrestricted project execution process.

The host/broker must not execute customer code. Project cells receive only explicitly granted project storage, scratch space, visitor listener and narrowly scoped network capabilities. No ambient host secrets, arbitrary filesystem access or unrestricted egress.

### 2.3 Android delivery constraints

Android does not allow a normal app to silently install an arbitrary companion APK. For independently installable package-based runtime packs, use Android's supported user-confirmed package installation/update flow and clearly explain the system confirmation. Handle unknown-source permission and installer cancellation gracefully; never bypass Android security controls.

Evaluate Play Feature Delivery only if distribution through Google Play and its delivery constraints fit the product. A dynamic feature/split shares the host app UID and is not a substitute for the separate package boundary. A downloaded runtime asset archive can avoid a second package only if the execution architecture still establishes the required isolated OS identity; merely extracting libraries into host storage is not isolation.

### 2.4 Language roadmap

Initial supported packs: Python and Node.js, preserving current pinned runtime choices where compatible with the cell design. Future candidates: PHP, Ruby, JVM/Java, Go/WASI and additional runtimes only after demand, Android feasibility, licensing, ABI, storage, security and maintenance review. Do not promise language support before a working runtime pack and project-cell integration pass acceptance.

### 2.5 Static hosting is independent

Static hosting remains available when runtime packs are absent, broken, updating or disabled. Runtime crashes or upgrades must not interrupt Rust static serving. Runtime manager operations are transactional and must preserve the last known-good pack and project deployment.

## 3. Target architecture

- **Android host (Kotlin/Compose):** UX, Android permissions, SAF, lifecycle, notification, telemetry and runtime manager.
- **Rust engine (Tokio/Hyper):** ingress, hostname/site routing, static serving, reverse proxy, admission, request metrics and policy enforcement.
- **Runtime manager:** catalog, signed manifest verification, install/update/rollback, ABI selection, compatibility and health state.
- **Capability broker:** authenticated versioned IPC; grants revocable descriptor-based access and typed network/listener capabilities. It never runs project code.
- **Runtime providers:** separately versioned language distributions, adapters and bootstrap contracts.
- **Execution-cell supervisor:** creates, monitors and destroys per-project isolated processes/UIDs; applies limits and revokes grants on death or lease expiry.
- **Project cells:** execute only their own project code under constrained storage, network and resource policy.
- **Tunnel providers:** first-party AssayFlux Relay and Cloudflare Tunnel are independent, optional, first-class connectivity providers. Users can enable either one or both concurrently, globally or per site/hostname where supported. Local-network access remains a separate option. The phone remains the origin; tunnel providers only transport visitor traffic and never expose management APIs.
- **Control plane:** optional identity/device registry and remote management; never the website origin.
- **Web console:** owner/operator client, not a second website request router.
- **Portable host contracts:** versioned host-agent, service-manifest, runtime-manifest, capability, identity, telemetry and desired-state contracts shared across platform adapters.
- **Platform host adapters:** Android, Linux, Windows, macOS and embedded Linux adapters own OS-specific lifecycle, filesystem, process, network, packaging and security integration.
- **Edge fleet adapter:** an optional, authenticated bridge to DemonCore/DCP and other reviewed device protocols. It is not a second website request router and does not turn MCUs into general-purpose hosts.

## 4. Runtime pack contract

Every runtime pack must publish a machine-readable, signed manifest containing at minimum:

- Stable runtime ID and human-readable name.
- Pack version, runtime engine version and supported Pocket Host protocol versions.
- Supported Android API range and ABI set.
- Download size and installed-size estimate.
- License and notices for runtime and bundled dependencies.
- Required native libraries, checksums and signature identity.
- Project types and entrypoint contract (for example WSGI or Node HTTP adapter).
- Health-check command/contract, startup timeout and graceful-stop behaviour.
- CPU, memory, process, file and network capability requirements.
- Upgrade compatibility, migration requirements and rollback support.
- Security support status and known limitations.

The host must reject unsigned, tampered, incompatible, revoked or downgrade-ineligible packs. Runtime installation must be resumable and atomic: download to staging, verify signature/checksums, validate ABI and protocol, install, health-check, then activate. On failure retain the prior known-good version.

Projects declare runtime ID, version range, entrypoint, environment variables and explicitly requested capabilities. Secrets are injected only through scoped handles; they must not be written into runtime manifests, logs or shared package storage.

## 5. Security model and acceptance rules

- One project per isolated execution identity; never equate package UID separation with per-project isolation.
- The runtime broker and host remain outside the project cell.
- No ambient access to host app data, other projects, Android account data or runtime-pack private state.
- Filesystem access is descriptor/capability-based and rooted to that project's data.
- Network access is denied by default and granted through explicit policy. Visitor response traffic is brokered; outbound destinations, DNS, ports, byte rates and connection counts are policy-controlled.
- Close inherited descriptors before project code starts. Do not rely on language monkey-patching, filename checks or a seccomp denylist with allow fallback as the security boundary.
- Apply memory, CPU/admission, process/thread, file-size, descriptor, disk, startup and execution limits. Android cannot promise hard CPU quotas or immortal background processes; document platform limits.
- Cell death, lease expiry, project stop, permission change or device shutdown revokes capabilities and closes brokered resources.
- Treat all project code as trusted-owner code until the full per-project isolation and adversarial test matrix passes. No visitor-submitted or marketplace code execution before independent review.
- Threat-model package update, rollback, compromised runtime pack, malicious project, malicious dependency, confused-deputy IPC, symlink/path traversal, inherited socket, DNS rebinding, egress and resource exhaustion.

## 6. Master implementation gates

A gate is closed only when implementation, tests and evidence meet its exit criteria. Source presence or a green non-Android workflow is not Android acceptance. Record exact commit SHA, test command, device/API/ABI, result and artifact for each verification level.

### Gate 0 — Repository truth and architecture freeze
**Purpose:** establish one authoritative plan and honest baseline.

Work:
- Reconcile README, North Star, master plan, roadmap, baseline, development context and UI spec.
- Inventory all modules, runtime paths, permissions, packages, workflows, tests, feature flags and known defects.
- Mark every claim as proposed, source-present, compiled, automated-tested, emulator-verified, physical-device-verified or production-approved.
- Keep work on `main`; avoid long-lived divergent branches and reconcile or retire stale branches deliberately.

Exit:
- No contradictory architecture or stale gate status.
- Every feature has an owner, dependency, acceptance test and evidence location.
- Security limitations are prominent and consistent.

### Gate 1 — Host foundation and runtime lifecycle
**Purpose:** make static hosting and the existing runtime path reliable before changing the sandbox.

Work:
- Prove static import, validation, preview, publish, update, rollback and serving independent of optional services.
- Fix companion discovery and service-start diagnostics; distinguish package absent, service absent, permission/signature failure, protocol mismatch, foreground-service restriction, bind failure, timeout, crash and incompatible version.
- Verify Binder caller identity, protocol negotiation, bounded transfer, cancellation, death recipient, reconnect and orderly shutdown.
- Route Python and Node requests through Rust and verify static-origin continuity during runtime failure/restart.
- Ensure foreground notification, process lifecycle, Doze/network changes and storage-full behaviour are truthful.

Exit:
- Repeatable clean install/upgrade and Python/Node-through-Rust tests on API 26/33/35 and supported ABIs.
- Static sites remain available during runtime failure.
- No generic error message hides the actual cause.

### Gate 2 — Production per-project execution cells
**Purpose:** replace the shared companion execution model with enforceable project isolation.

Work:
- Finalize threat model and Android API 26–28 fixed isolated-service slot strategy versus API 29+ isolated binding strategy.
- Build the cell supervisor, project lifecycle/lease model and death/revocation handling.
- Adapt Python and Node bootstrap/storage to descriptor-rooted cell-owned storage; do not assume Chaquopy or Node can use ordinary host app context inside an isolated process.
- Implement strict network capability broker and default-deny egress, descriptor hygiene, syscall policy and resource limits.
- Prove two projects of the same language cannot read/write each other's files, access host secrets, bind arbitrary ports or bypass egress policy.
- Add hostile fixtures and adversarial tests for inherited descriptors, path traversal, symlinks, IPC spoofing, cell reuse, process death and resource exhaustion.

Exit:
- Distinct OS identity per project, demonstrated at runtime.
- Python and Node both run inside real cells, not feasibility probes.
- Security tests pass on API 26/33/35 and each supported ABI.
- Independent security review approves the declared trust tier. Until then runtimes remain trusted-owner-code only.

### Gate 3 — Modular Runtime Manager and language packs
**Purpose:** let users install only the languages they need and expand the ecosystem safely.

Work:
- Define runtime manifest, signing, catalog API, compatibility/protocol negotiation and pack lifecycle.
- Convert Python and Node into independent optional packs, with ABI-specific payloads and dependency/licence inventory.
- Add Runtime Manager UI: available, installed, update available, unsupported, broken, in-use and uninstall states; storage estimates; version selection; project dependencies.
- Support Android-confirmed installation for package-based packs, or a reviewed isolated asset-pack design if it preserves cell isolation.
- Add resumable download, signature/checksum verification, atomic activation, rollback, update pinning and safe uninstall checks.
- Add a documented provider SDK/adapter contract and compatibility test harness for future PHP, Ruby, JVM and other runtimes.

Exit:
- Base app installs and hosts static sites without any runtime.
- User can install Python without Node and Node without Python.
- A project clearly reports missing/incompatible runtime and can request installation.
- Runtime update/removal cannot corrupt projects or interrupt static hosting.
- A sample third-party-style provider passes the contract tests before new languages are advertised.

### Gate 4 — AssayFlux Relay and networking
**Purpose:** production-grade optional public connectivity without exposing management.

Work:
- Deploy gateway, TLS/domain certificate lifecycle, verified domain registry and authenticated device enrollment.
- Harden outbound Android agent reconnect, network handoff, heartbeat, token rotation and revocation.
- Decide and implement DNSSEC validation or document a reviewed alternative threat mitigation.
- Add required streaming/WebSocket support only with bounded flow control, backpressure and quotas; retain bounded HTTP limits.
- Add abuse controls, per-device/site quotas, observability, audit, incident operations and clear TLS termination/privacy disclosure.
- Test from independent external networks and under load/soak.

Exit:
- External visitor can reach a verified site through Relay; management endpoints are unreachable.
- Certificate renewal, revocation, device deletion and network changes work.
- Load/soak and abuse tests meet published limits.
- Cloudflare Tunnel and local-only hosting remain independent alternatives.

### Gate 5 — Identity, owner and visitor access
**Purpose:** distinct and revocable authorization boundaries.

Work:
- Implement owner identity and passkey-first enrollment/recovery with device approval and role separation.
- Complete visitor Public/Private/Restricted flows, short-lived scoped credentials, invite/revoke and rate limiting.
- Enforce visitor policy before expensive runtime execution and at the appropriate trusted ingress.
- Ensure visitor credentials cannot authorize owner/management actions.
- Add audit, session expiry, revocation, fresh authorization for sensitive operations and secure storage.

Exit:
- End-to-end native and web flows pass; recovery and revocation are tested.
- Unauthorized visitor and owner actions are denied consistently across local and Relay ingress.
- No secret is exposed in logs, browser persistent storage or project environment by default.

### Gate 6 — Capacity, fairness and observability
**Purpose:** predictable degradation rather than device overload.

Work:
- Complete Rust global/per-site weighted fairness, bounded queues, admission and overload responses.
- Add truthful CPU, memory, storage, battery, thermal, network, runtime and request telemetry with unknown states represented as unknown.
- Define per-project runtime budgets and safe defaults; coordinate Android thermal/battery state with admission.
- Add Capacity Lab representative workloads and repeatable benchmark profiles.
- Verify metrics, alerts, logs and UI refresh under load without excessive battery use.

Exit:
- Fairness and overload tests pass under contention.
- Capacity claims cite device, Android version, workload, duration, network and thermal conditions.
- No fabricated live metrics or unsupported hard-quota claims.

### Gate 7 — Backup, operations, recovery and lifecycle
**Purpose:** survive failure and preserve owner data.

Work:
- Complete encrypted backup format, key handling, integrity verification, retention and restore.
- Test interrupted backup, low storage, corrupted archive, wrong key, app upgrade, runtime pack upgrade, project migration and partial restore.
- Implement transactional publish/rollback and safe backup/restore of runtime metadata without blindly backing up executable runtime binaries.
- Validate reboot, process death, force-stop, Doze, battery restrictions, network changes, permission revocation and Android storage changes.
- Provide actionable logs, support bundle redaction and recovery guidance.

Exit:
- Restore a verified site and configuration on a clean installation.
- Failure leaves previous known-good state intact.
- Recovery steps are documented and tested.

### Gate 8 — Product UX, onboarding and design acceptance
**Purpose:** ship a coherent, accessible Pocket Host experience.

Work:
- Implement authoritative Design Language V1 across login/unlock, dashboard, drawer, account menu and every feature page.
- Dashboard must not vertically scroll; only service carousel scrolls horizontally; information centre shows four or five compact non-scrolling bars.
- Use tall portrait service cards, active-card emphasis and adjacent peeks; responsive layout must respect font scale and display size without clipping.
- Implement ten palettes and persisted System/Light/Dark modes, licensed bundled calligraphic wordmark font, readable operational typography and monochrome icons.
- Give every drawer destination a distinct correct feature surface; no duplicate/cosmetic routes.
- Keep account menu separate from navigation drawer and expose only real identity/session capabilities.
- Respect insets, keyboard, back navigation, touch targets, TalkBack, contrast, landscape, compact screens and tablets.
- Add UI tests for every destination, drawer/account dismissal, font scaling, scrolling, themes, empty/error/loading states and accessibility; review screenshots on physical devices.

Exit:
- All V1 UI acceptance checks pass on API 26/33/35 and representative physical devices.
- No clipped or unreachable controls at supported font/display scales.
- No fake buttons, fabricated telemetry or unsupported account/auth claims.

### Gate 9 — Release and independent acceptance
**Purpose:** release only a reproducible, signed and supportable build.

Work:
- Stable protected signing identity for host and any package-based runtime providers; document key custody and rotation.
- ABI-specific APK/AAB/package artifacts, checksums, provenance, SBOM/licence notices and reproducible build evidence.
- Full exact-SHA CI, clean install, upgrade, downgrade prevention, uninstall/reinstall and runtime pack compatibility matrix.
- Physical-device battery/thermal/network/Doze and long-duration hosting tests.
- Independent security review, privacy review, release notes and support/recovery documentation.

Exit:
- All gates 0–8 accepted; signed artifacts verified; no critical/high unresolved security or data-loss defects.
- Publish only the capabilities actually tested and supported.

## 7. Product reliability and defect handling rules

Every defect is tracked with severity, affected API/ABI/device, reproduction steps, expected/actual behaviour, logs, owner and regression test.

- **P0:** data loss, privilege escape, cross-project access, remote management exposure, signing compromise. Block release and affected feature.
- **P1:** core hosting/runtime failure, broken install/upgrade, inaccessible navigation/control, major Android lifecycle failure. Block relevant gate.
- **P2:** degraded feature, visual/accessibility issue with workaround, telemetry inaccuracies. Must be resolved or explicitly accepted before release.
- **P3:** cosmetic or low-impact improvement. Schedule without masking higher severity work.

Do not “fix” flaky tests by broad timeout increases or weakening assertions. Diagnose the failure, preserve logs and add a regression test.

## 8. Verification ladder and reporting

Report each capability separately as:
1. Planned
2. Source implemented
3. Compiled/package built
4. Unit/integration tested
5. Android emulator verified (record API/ABI)
6. Physical device verified (record model/API)
7. External/soak verified where relevant
8. Independently reviewed / production approved

Every gate report includes exact SHA, commands, job links, artifacts, failures, unresolved risks and acceptance evidence. A green Rust/Node/control-plane workflow is not proof that Android UI or runtime IPC works.

## 9. Execution order and critical path

Recommended order:
1. Gate 0 documentation and defect baseline.
2. Gate 1 host/runtime lifecycle diagnostics and static-hosting invariants.
3. Gate 2 production execution-cell architecture and adversarial proof.
4. Gate 3 Python/Node optional packs and Runtime Manager; then add language providers incrementally.
5. Gates 4 and 5 Relay and identity/access, with shared threat-model review.
6. Gates 6 and 7 capacity and recovery.
7. Gate 8 complete UI and onboarding acceptance.
8. Gate 9 final release.

Work may proceed in parallel only where dependencies are explicit and tests can be independently accepted. Do not close a downstream gate on mock data or source-only scaffolding.

## 10. Current known baseline at plan creation

The repository contains useful Rust hosting, Python/Node companion, Relay, control-plane, backup, capacity and Compose UI foundations. The following are known open concerns and must be rechecked against current `main` before implementation claims:

- Python and Node share the companion UID; isolated-service/seccomp work is a feasibility probe, not production per-project isolation.
- Existing universal companion APK is large and creates a manual install/update burden; diagnostics can misclassify service-start errors as missing package.
- Debug signing identity may vary between ephemeral CI runners, causing update/signature mismatch across runs.
- Prior API 26 instrumentation reported Python and multi-site Binder timeouts and a navigation clickability failure; retrieve current run logs and reproduce before attributing root cause.
- UI source has known risks around global vertical scrolling, dashboard information-centre layout, destination routing and settings content leakage; verify current files and add regression tests.
- Relay, owner/passkey identity, capacity fairness and recovery have meaningful source foundations but require their gate-specific integration and acceptance evidence.

This baseline is not a claim that these issues remain unchanged after this plan's creation. Re-inspect the exact current SHA before modifying or closing a gate.

## 11. Non-negotiable release principles

- Static hosting works without optional services.
- Users install only the runtimes they need.
- Runtime packages are independently versioned and verifiable.
- Package separation is never misrepresented as per-project isolation.
- Customer code runs only in an OS-enforced project cell after Gate 2 acceptance.
- Android owns installation confirmation, permissions and lifecycle.
- The host, broker and management plane never execute project code.
- No fake feature, fake status, fabricated telemetry or unsupported security claim.
- Preserve working code; change architecture deliberately with migration and rollback plans.
- `main` is the source of truth; commits and verification evidence are traceable.


## 7. Universal device fabric expansion

The authoritative device-tier, cross-platform host, edge-node, DemonCore integration and expansion-gate specification is `docs/UNIVERSAL_DEVICE_FABRIC.md`. It extends this plan without weakening Android's existing gates.

### 7.1 Product scope

Pocket Host is a family of cooperating host agents and management clients. It must support:
- Android phones and tablets as the first host platform.
- Linux desktops, servers, VPSs and supported ARM/x86 systems.
- Windows and macOS hosts through native service/lifecycle/security adapters.
- Raspberry Pi-class and other single-board computers as exact-model, measured profiles.
- Microcontrollers and constrained devices as edge/device nodes; only devices with sufficient measured capability may be promoted to a constrained service-host profile.

"Any device" means any device that passes a declared hardware/OS capability and security profile. It does not mean every device can run every service.

### 7.2 Device and service capability model

Adopt four capability tiers:
- Tier A Full Host: Android, desktop/server OS, capable VPS and capable SBC; static and supported dynamic services.
- Tier B Compact Edge Host: embedded Linux/SBC/mini PC; resource-profiled static, API and selected runtime workloads.
- Tier C Microcontroller Service Node: ESP32-class MCU; bounded device APIs, telemetry, MQTT/DCP, local control and protocol bridges.
- Tier D Constrained Device: sensor/actuator/event node; not advertised as a website origin.

Every workload declares architecture, runtime, memory/storage/CPU, network, persistence, security and power requirements. The scheduler places only on compatible nodes. Runtime availability does not imply workload isolation; every host OS needs a separately accepted per-project execution boundary.

### 7.3 Cross-platform implementation boundaries

- Preserve Kotlin/Compose and Android lifecycle ownership on Android.
- Keep Rust as the portable engine foundation where dependencies and platform security permit.
- Add narrow OS-specific adapters for systemd/Linux, Windows Service/ACL/firewall, macOS launchd/signing/notarization/entitlements and embedded Linux.
- Keep management clients independent of host execution: Android app, responsive web console and future desktop clients.
- Do not rewrite the Android app or Rust engine just to obtain platform parity. Extract versioned contracts first.
- Do not introduce Kubernetes or a mandatory cloud dependency for phones or small devices.

### 7.4 DemonCore relationship

DemonCore remains a separate specialised MCU/fleet project. Pocket Host integrates through a versioned authenticated edge adapter; it must not import firmware internals or merge repositories. DemonCore owns board profiles, GPIO/peripheral safety, DCP, MCU provisioning and constrained mesh/fleet operations. Pocket Host owns websites, general-purpose workloads, host nodes and public website ingress.

DCP v1.0's JSON envelope is not authentication, is bounded to 4,096 bytes and defines only its initial operation catalogue. Do not expose it over public ingress or enable cross-network commands until DemonCore's claim, authentication, encryption, revocation and physical hardware gates pass. ESP32 devices are not assumed to run Pocket Host's Rust website engine, Node or Python workloads.

Current DemonCore repository documentation records an ESP-WROOM-32 4 MiB/no-PSRAM target and MicroPython firmware. Its Phase 1 is not hardware-accepted; board verification and physical boot/resource tests remain outstanding, the Flutter app workspace is documentation-only, and the hub is not implemented. Treat these as audit findings, not accepted integration capabilities.

### 7.5 Additional expansion gates

The original Gates 0–9 remain in force. The following gates extend them and must not be used to bypass Android security, runtime, UX or release acceptance.

- **U0 — Portable contracts and capability model:** host-agent API, service/runtime manifests, device identity, enrollment/revocation, capability taxonomy, telemetry, desired-state schema, offline behaviour and compatibility tests. Exit only when versioned contracts and threat model pass and Android compatibility is preserved.
- **U1 — Linux host node:** Rust host core, unprivileged service identity, systemd integration, filesystem/network policy, static hosting, logs, health, signed packages and rollback. Test x86_64 and ARM64; add ARMv7 only when dependencies and resources permit. Validate Raspberry Pi models separately.
- **U2 — Windows and macOS adapters:** Windows installer/service/ACL/firewall and macOS signed/notarized distribution/launchd/entitlement decisions. Pass named OS/architecture lifecycle, security, network and upgrade tests.
- **U3 — SBC/embedded Linux profiles:** exact-model Raspberry Pi and other ARM boards; headless onboarding, power-loss-safe updates, thermal/storage monitoring and measured workload profiles.
- **U4 — DemonCore MCU interoperability:** first complete DemonCore physical hardware, identity, claim, authenticated transport and OTA gates; then implement allow-listed DCP adapter operations, real-board tests and compromised-node containment.
- **U5 — Multi-host fleet and workload placement:** host registration/revocation, desired-vs-actual state, multi-component/multi-runtime placement, budgets and authenticated internal service routes. Replication/failover requires defined data consistency and restore semantics.

**Expansion exit rule:** no device, runtime, service, fleet scale or availability claim is advertised until its exact compatibility profile and verification evidence are recorded. Full criteria live in `docs/UNIVERSAL_DEVICE_FABRIC.md`.
