# Pocket Host Universal Device Fabric

**Status:** approved architectural direction; implementation not implied.  
**Authority:** this document extends `docs/MASTER_PLAN.md`, `docs/NORTH_STAR.md` and `docs/ROADMAP.md`.  
**Related project:** [AssayFlux/DemonCore](https://github.com/AssayFlux/DemonCore) provides a specialised microcontroller/edge-fleet workstream.

## 1. Mission

Pocket Host is evolving from an Android-first hosting app into a **cross-platform, distributed self-hosting ecosystem**. An owner should be able to turn capable devices they already own into hosting nodes, combine those nodes, publish services and manage the fleet through one coherent experience.

The product must discover what each device can safely do rather than claim that every device can run every workload. "Any device" means any device with sufficient compute, memory, storage, operating-system facilities, network access and power for a declared service profile.

## 2. Product model

Separate four concepts which must never be conflated:

1. **Control plane:** identity, owner/team roles, device registry, desired state, policy, fleet view, scheduling intent and audit. It is optional for a standalone local host; outage must not stop already-running local services.
2. **Host node:** a device that runs the Pocket Host host agent and one or more service workloads.
3. **Edge/device node:** a constrained device which contributes explicitly supported device capabilities, telemetry, protocol translation or small edge services. It need not run the full Pocket Host host.
4. **Service/workload:** a declared deployable unit with runtime, resource, architecture, network, storage, security and availability requirements.

A phone, PC, server or SBC can be a host node. An MCU can be a device/edge node and, only where its measured resources permit, a constrained service node.

## 3. Device capability tiers

Tiers describe capabilities, not marketing promises. A device may move between tiers as its hardware, OS, power and network conditions change.

| Tier | Typical hardware | Suitable workloads | Explicit limitations |
|---|---|---|---|
| **A — Full host** | Android phone/tablet; Linux, Windows or macOS PC/server; capable VPS; ARM/x86 single-board computer | Static sites, supported multi-runtime applications, APIs, reverse proxies and other adapters that pass host acceptance | OS lifecycle, thermal, storage, network, licensing and security constraints still apply |
| **B — Compact edge host** | Raspberry Pi-class SBC, low-power ARM board, embedded Linux gateway, mini PC | Static sites, small APIs, selected language runtimes, MQTT/edge gateways and bounded background services | Runtime and concurrency support is profile-specific; benchmark memory, thermal and storage before advertising |
| **C — Microcontroller service node** | ESP32-class MCU, including DemonCore-supported ESP-WROOM-32 profile | Sensor/actuator APIs, telemetry, MQTT/DCP endpoints, local control, bounded protocol bridges and small edge actions | Not a general Python/Node host; severe RAM/flash/CPU limits; TLS, storage and public HTTP capability must be proven per board |
| **D — Constrained device** | Low-memory MCU, sensor module, tiny wireless board | Telemetry, event production, actuator control and protocol participation | Not advertised as a website origin or arbitrary service host |

Raspberry Pi and similar Linux SBCs are not microcontrollers. They are first-class candidates for Tier A or B depending on model and measured profile.

### 3.1 Service compatibility is explicit

The service catalog must classify workloads independently of device tier. Examples:

- Static websites and file delivery.
- Multi-runtime websites and APIs (for example Python + Node + PHP components).
- MQTT brokers/bridges, device APIs and automation.
- WebSocket, streaming and realtime services.
- Databases, queues, game servers, media, AI/ML and GPU workloads only where supported adapters and resource/security acceptance exist.

A catalog entry is not a support claim. Every service/runtime/device combination needs a signed compatibility profile and test evidence. Services with persistent storage, public ingress, high availability or privileged hardware access require additional placement and recovery policy.

## 4. Cross-platform host architecture

Keep a portable host core and narrow platform adapters:

- **Portable core (Rust):** service registry, configuration model, ingress/site routing, workload supervisor interfaces, health reporting, policy evaluation and telemetry contracts where portability is demonstrated.
- **Platform host adapters:** Android/Kotlin lifecycle and foreground service; Linux systemd/user services and filesystem/network adapters; Windows Service/installer/firewall adapters; macOS launchd/notarization/entitlements adapters; embedded Linux init/service adapters.
- **Management clients:** existing Android app, responsive web console and later desktop/tablet experiences. A UI client is not itself a host.
- **Runtime providers:** separately versioned runtime packs/providers, selected by OS, CPU architecture, ABI, libc/toolchain and security profile.
- **Edge adapters:** DCP, MQTT, BLE, Wi-Fi, ESP-NOW and board/peripheral drivers behind least-privilege capability interfaces.
- **Ingress/connectivity:** local LAN, first-party AssayFlux Relay, Cloudflare Tunnel and future explicitly reviewed providers. Tunnel providers forward visitor traffic only to the website/service ingress, never the management plane.

Do not force a single UI framework or Android lifecycle onto desktop/server hosts. Preserve the current Kotlin Android app and Rust engine while extracting stable, versioned portable contracts. Avoid a premature rewrite or monorepo split.

## 5. Host and device agent contract

Every host agent must expose a versioned local management interface and report:

- Stable device identity, owner/tenant association and revocable enrollment.
- OS, kernel/runtime, CPU architecture, ABI, available memory/storage and relevant accelerators.
- Network interfaces/reachability and supported ingress providers.
- Power, thermal and lifecycle constraints where the platform exposes them.
- Installed host-agent, runtime-provider and protocol versions.
- Supported service/runtime capability IDs and compatibility ranges.
- Observed health and resource budgets; unknown values remain unknown.
- Signed update channel, last-known-good version and rollback state.

Agents authenticate to the control plane with short-lived, revocable credentials and mutually authenticated transport. Enrollment, ownership transfer, lost-device revocation, key rotation and offline recovery must be designed before fleet deployment. Never put a fleet-wide secret into firmware or a shared image.

## 6. Workload placement and multi-device operation

A website/service deployment may contain multiple components and multiple runtime types. A placement plan maps each component to one or more eligible host nodes based on declared requirements and owner policy.

Required capabilities:

- Per-component runtime/version/architecture constraints.
- Explicit CPU, memory, disk, process, network and power budgets.
- Site-level service graph and authenticated internal service-to-service routes.
- Placement preferences, pinning, anti-affinity and optional replication.
- Health-aware restart and failover only where persistent state and consistency semantics support it.
- Deployment staging, signed artifacts, health checks, rollback and audit.
- Local operation when the control plane or Internet is unavailable.
- Clear distinction between desired, scheduled, running, degraded, stopped and unreachable state.

Do not imply that one website is automatically replicated or highly available merely because multiple devices are registered. Stateful services need storage replication, consistency, backup and recovery design. Stateless components are the first placement target.

## 7. DemonCore integration boundary

DemonCore remains a specialised microcontroller/fleet project with its own firmware, DCP contract, app workspace and hub. Pocket Host should integrate through a versioned adapter, not import DemonCore firmware internals or merge the repositories.

Target relationship:

```
Pocket Host control plane / operator console
                 |
          Edge fleet adapter
                 |
       DemonCore hub / DCP gateway
                 |
       MQTT / BLE / Wi-Fi / ESP-NOW
                 |
       ESP32 and other MCU nodes
```

- Pocket Host owns website hosting, general-purpose workloads, host-node lifecycle and public website ingress.
- DemonCore owns board-aware MCU firmware, GPIO/peripheral safety, DCP device operations, provisioning and constrained fleet/mesh behaviour.
- A Raspberry Pi may run Pocket Host and optionally host the DemonCore hub/gateway if the hub becomes independently deployable and passes its own security and load acceptance.
- ESP32 devices can expose approved, bounded device capabilities through DemonCore. They are not assumed to run Pocket Host's full Rust website engine, Python/Node runtimes or arbitrary customer code.
- The integration adapter translates authenticated, allow-listed service/device operations. It must not tunnel arbitrary unauthenticated DCP messages from the public Internet to GPIO or firmware.
- DCP v1's envelope is not an authentication mechanism. Cross-network integration remains disabled until DemonCore's authentication/encryption and claim gates pass. The current 4 KiB message/reassembly limits and operation allow-list must be respected.
- Device identity, keys, ownership, revocation, OTA authorization and audit remain explicit security boundaries. Do not reuse device secrets as Pocket Host account credentials.
- Keep both repositories independently buildable and licensed; share only reviewed versioned protocol/adapter contracts.

### 7.1 Current DemonCore audit baseline (2026-11-17)

Repository reviewed: [AssayFlux/DemonCore](https://github.com/AssayFlux/DemonCore), default branch `main`.

Observed from its current documentation:
- Target board is ESP-WROOM-32, 38-pin, 4 MiB flash, no PSRAM, with a pinned MicroPython ESP32_GENERIC build.
- DCP v1.0 defines a UTF-8 JSON envelope, node/group/tag/cell addressing, a 4,096-byte message/reassembly bound and initial system/GPIO operations. The protocol document explicitly says the envelope does not authenticate traffic.
- The firmware build has a recorded CI image-size margin, but README says Phase 1 is not hardware-accepted; physical boot, heap/GC/filesystem checks, board pinout confirmation and live transport remain outstanding.
- The task backlog shows board verification blocked, claim/provisioning work incomplete, and the Flutter app workspace documentation-only. The hub workspace is not yet implemented.
- MQTT, ESP-NOW mesh, jobs, OTA, rules and fleet scheduling are roadmap work, not accepted production capabilities.

These facts are a planning baseline, not independent verification of hardware or code. Re-audit exact source and CI before implementation.

## 8. Security and trust boundaries

- Device nodes are not automatically trusted because they are on the LAN or enrolled in a fleet.
- Separate owner identity, host identity, service identity and visitor identity.
- Default-deny management access and inter-node traffic; issue narrowly scoped, revocable capabilities.
- MCU operations require board-profile validation and owner policy; dangerous GPIO/peripheral operations need explicit authorization.
- Signed firmware/runtime updates, anti-rollback policy, staged rollout and recovery are required.
- A compromised edge node must not gain host/root privileges or read unrelated sites, runtime secrets or other nodes' credentials.
- Public ingress must never expose DCP control, debug, broker, OTA administration or management APIs.
- Treat customer code as untrusted until each host platform's isolation design passes adversarial review.
- Do not promise hard isolation on a microcontroller where the hardware/RTOS does not provide it; expose its trust limitations and restrict it to owner-authorized workloads.

## 9. Compatibility and release evidence

A device profile is supported only after recording exact model/revision, OS/firmware, CPU/ABI, memory/storage, network, power/thermal conditions, supported service matrix, build artifact, test commands and results.

Verification ladder:
1. Schema/static validation.
2. Unit and simulator tests.
3. Cross-platform compile/package.
4. Integration tests on virtual devices.
5. Physical-device functional tests.
6. Long-duration, load, power/thermal and network-fault tests.
7. Security review and recovery/rollback tests.
8. External acceptance for public connectivity.

A successful compile on one architecture does not prove another architecture or hardware profile. Keep unsupported and unknown distinct from failed.

## 10. Expansion gates

The existing Android reliability, runtime isolation, Relay/Cloudflare, identity, capacity, backup, UX and release gates remain mandatory. The universal-device expansion adds these gates after portable contracts are frozen:

### U0 — Portable contracts and capability model
- Define versioned Host Agent API, device identity/enrollment, service manifest, runtime manifest extensions, capability taxonomy, telemetry schema and control-plane desired-state model.
- Define the device tiers and service compatibility matrix without claiming unsupported devices.
- Specify offline/local autonomy, remote revocation, audit and upgrade/rollback semantics.
- Add schema fixtures and compatibility tests; no platform implementation is implied.

**Exit:** contracts are versioned, threat-modelled, documented and tested; Android behaviour remains backward-compatible.

### U1 — Linux host node
- Extract/build the Rust host core for supported Linux x86_64 and ARM64 targets; add ARMv7 only if dependencies and measured resources permit.
- Implement unprivileged service account, systemd integration, filesystem sandbox/capability adapter, graceful shutdown, logs, health and upgrade/rollback.
- Support local static hosting first; add runtimes only after the per-project isolation design is accepted on Linux.
- Validate Raspberry Pi-class hardware profiles separately from generic Linux PCs and VPSs.

**Exit:** signed packages, install/uninstall/upgrade, reboot recovery, local hosting, isolation tests and resource profiles pass on named Linux targets.

### U2 — Windows and macOS host adapters
- Windows: signed installer, Windows Service lifecycle, ACL-based storage isolation, firewall/network policy, updates and recovery.
- macOS: signed/notarized distribution, launchd lifecycle, App Sandbox/entitlement decision, filesystem and network policy, updates and recovery.
- Preserve the same host/service contracts while respecting each OS security model.

**Exit:** named OS versions and architectures pass packaging, lifecycle, networking, security and upgrade acceptance. No cross-platform claim from shared Rust compilation alone.

### U3 — SBC and embedded Linux profiles
- Validate Raspberry Pi models and other ARM SBCs by exact CPU, RAM, storage, network and OS image.
- Provide headless onboarding, removable-storage recovery, power-loss-safe updates, thermal monitoring and local admin fallback.
- Offer tiered profiles (small static/edge; general host; runtime-enabled) based on measured resources.

**Exit:** each advertised board profile passes cold boot, storage failure, network interruption, sustained load, update/rollback and restore tests.

### U4 — DemonCore MCU/edge interoperability
- Complete DemonCore's own physical hardware, identity/claim, authentication, transport and OTA acceptance first.
- Define a Pocket Host edge-fleet adapter against a versioned, authenticated DCP contract; implement allow-listed operations and least-privilege service exposure.
- Test ESP32 nodes through simulator and real hardware; verify compromised-node containment and key revocation.
- Add further MCUs only with their own board profiles, memory reports, drivers, security model and hardware tests.

**Exit:** no unauthenticated DCP path; device claim/revocation and signed OTA pass; gateway cannot expose arbitrary GPIO/control operations; hardware acceptance is recorded for each board.

### U5 — Multi-host fleet and workload placement
- Register and revoke multiple hosts; show per-host health/capability and desired-vs-actual state.
- Deploy multi-component, multi-runtime websites across eligible nodes.
- Add explicit placement, resource budgets, service discovery and authenticated internal routes.
- Add replication/failover only for workloads with defined data consistency and recovery semantics.

**Exit:** end-to-end deployment, host loss, offline control plane, credential revocation, rollback and state recovery tests pass.

## 11. Non-goals and guardrails

- Do not rewrite the current Android app or Rust engine merely to chase platform parity.
- Do not merge Pocket Host and DemonCore repositories or make either mandatory for the other.
- Do not run arbitrary Node/Python/web workloads on ESP32-class MCUs.
- Do not advertise a microcontroller as a public web host without measured HTTP/TLS, memory, power and security acceptance.
- Do not promise "any service on any device"; promise transparent capability discovery and safe placement.
- Do not make a cloud control plane mandatory for local hosting.
- Do not introduce Kubernetes/container requirements on phones or tiny devices.
- Do not claim high availability, fleet scale or automatic failover without evidence.

## 12. Immediate implementation order

1. Freeze this architecture and reconcile all Pocket Host documents.
2. Keep current Android gates moving; they remain the first production host.
3. Extract portable service/device/runtime manifests and Host Agent API without destabilising Android.
4. Build Linux host node and validate Raspberry Pi-class SBC profiles.
5. Complete DemonCore hardware/security gates, then build the authenticated edge adapter.
6. Add Windows and macOS host adapters.
7. Add multi-host placement, replication and advanced service catalog only after the underlying host and storage contracts are proven.
