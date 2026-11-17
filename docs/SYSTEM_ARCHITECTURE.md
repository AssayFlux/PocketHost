# Pocket Host 0.1.0 — System Architecture

**Product version:** 0.1.0  
**Architecture status:** baseline specification  
**Primary interface:** Android and iOS mobile command-center applications  
**Primary principle:** independent components, authenticated interconnection, local autonomy

## 1. Product definition

Pocket Host is a mobile-first, distributed self-hosting system. A user manages a personal or team fleet from an Android or Apple mobile phone. The fleet may contain phones, tablets, desktop computers, laptops, servers, VPSs, single-board computers, embedded Linux devices and constrained microcontrollers.

The mobile application is the owner's command center: it discovers and enrolls devices, configures services, deploys workloads, monitors health, approves sensitive actions and coordinates the fleet. It is not a mandatory runtime dependency for workloads already running on host devices.

Every device is assigned capabilities based on its actual hardware, operating system, network, storage, power and security properties. A device is offered only workloads it can safely support.

## 2. Mobile command center

### 2.1 Android and iOS are first-class control clients

The product's primary human interface is mobile:
- **Pocket Host for Android:** native Kotlin/Jetpack Compose application.
- **Pocket Host for iOS:** native Swift/SwiftUI application, with the same versioned control API and interaction model.
- Both clients manage the same fleet, accounts, device identities, services, deployments, access policy, tunnels, backups, alerts and audit history.
- Both support local-network discovery/pairing and remote management through authenticated device/control-plane connections.
- Mobile clients are control clients. A mobile operating system's background execution rules determine whether that phone can also act as a host.

### 2.2 Controller independence

The phone is the primary command center, not the system's single point of failure:
- Hosts persist their desired configuration, active release, secrets and service state locally.
- Hosts supervise and restart their own services.
- The phone may disconnect, reboot, lose battery or uninstall the app without stopping independent workloads on other hosts.
- A host can continue its last owner-authorized configuration while offline.
- Remote commands are queued with expiry, idempotency keys, authorization scope and durable acknowledgement.
- Sensitive changes require an authenticated owner action and are applied by the target host.
- A new phone can recover control through account/device recovery and explicit re-enrollment; no single phone holds irreplaceable fleet authority.

### 2.3 Mobile capability boundaries

Android may run supported host workloads under its foreground-service, package, thermal, battery and storage constraints. iOS is primarily a fleet-control client: iOS background execution and app lifecycle restrictions mean it must not be promised as an always-on arbitrary website server. It may offer local discovery, pairing, monitoring and owner-authorized control while active, plus supported notification/background refresh features where Apple's platform permits them.

## 3. System topology

```
             ANDROID COMMAND CENTER       iOS COMMAND CENTER
                       |                         |
                       +---- authenticated ------+
                             control API
                                  |
                   OPTIONAL REMOTE CONTROL PLANE
                 identity · registry · queue · audit
                    (never required for local hosting)
                                  |
        +-------------------------+--------------------------+
        |                         |                          |
   HOST NODE A               HOST NODE B                EDGE GATEWAY
 Android / Linux             Windows / macOS             Linux SBC
 ARM / x86 / SBC              Server / VPS               DemonCore
        |                         |                          |
   Host supervisor           Host supervisor              DCP adapter
        |                         |                          |
   Service cells             Service cells                MCU fleet
        |                         |                          |
   Runtime providers          Runtime providers             ESP32 etc.
        +-------------------------+--------------------------+
                                  |
                         SERVICE DATA PLANE
            authenticated, scoped, bounded node-to-node links

Visitors -> Local ingress / AssayFlux Relay / Cloudflare Tunnel
          -> selected host's website ingress -> site router
          -> static content or isolated service component
```

Control plane and data plane are separate:
- **Control plane:** identity, enrollment, desired state, policy, scheduling intent, command delivery and audit.
- **Data plane:** visitor requests, internal service calls, replication, artifact transfer and edge-device messages.
- Public visitor ingress never exposes owner management, device administration, runtime brokers, debug interfaces or MCU control APIs.

## 4. Component model and fault domains

Each component owns its state, lifecycle, resource budget, health endpoint and versioned interface. Components communicate through authenticated APIs or bounded messages. No component receives unrestricted access to another component's files, credentials or process.

| Component | Responsibility | Failure containment |
|---|---|---|
| Mobile command center | Owner interaction, approvals, fleet view | App failure does not stop hosts |
| Identity and enrollment | Users, roles, device keys, pairing, revocation | Identity outage blocks new authorization; existing local workloads continue |
| Fleet registry/control plane | Device records, desired state, queued commands, audit | Optional; hosts continue cached authorized state |
| Host agent | Local reconciliation, supervisor, health and update | One host failure does not stop other hosts |
| Website ingress | HTTP/TLS listener, hostname and site routing | One listener/site failure does not crash host supervisor |
| Site component cell | One deployable application component | Cell crash/OOM/restart is isolated to that cell |
| Runtime provider | Language distribution and process adapter | Runtime failure does not stop static hosting or other runtimes |
| Storage provider | Site data, persistent volumes, backup snapshots | Volume/site failure is scoped; immutable release and backup recovery retained |
| Tunnel provider | Public visitor connectivity | Provider failure does not stop local ingress or another provider |
| Edge gateway | DCP/MQTT/BLE/Wi-Fi/mesh translation | Gateway failure does not stop website hosts |
| MCU node | Sensor, actuator and bounded edge action | Node failure does not stop gateway or unrelated devices |
| Observability | Metrics, logs, traces and alerts | Telemetry outage must not block service request paths |

### 4.1 Isolation rules

- One host agent per host device, with OS-specific least privilege.
- One independently supervised execution cell per website component (and per tenant/project boundary where required).
- Each cell receives only its own project data and explicitly granted capabilities.
- Runtime packages may be reused as immutable software, but runtime processes and project state are isolated.
- Website ingress is a separate component from the runtime broker.
- Tunnel connectors are separate from website application processes.
- Management credentials are never mounted into customer application cells.
- MCU device operations are exposed through explicit capabilities, never arbitrary remote shell or unrestricted GPIO.
- A process boundary alone is not claimed as a security boundary unless OS isolation and adversarial tests prove it.

## 5. Interconnected device fabric

### 5.1 Device classes

| Class | Examples | Role |
|---|---|---|
| Mobile command center | Android phone, iPhone | Fleet control and owner approval |
| Mobile host | Supported Android phone/tablet | Static and supported dynamic hosting within OS limits |
| Desktop host | Linux, Windows, macOS PC/laptop | Website, API and application workloads |
| Server host | VPS, dedicated server, home server | Always-on and higher-capacity workloads |
| Single-board host | Raspberry Pi, other ARM/x86 SBC | Compact Linux hosting and edge gateway |
| Microcontroller node | ESP32, RP2040-class and other supported MCUs | Telemetry, device APIs, control and bounded edge tasks |
| Constrained node | Sensor, actuator, tiny wireless device | Measurements and events |

A Raspberry Pi is a single-board computer, not a microcontroller. An ESP32-class MCU is not assumed to run general-purpose Node/Python workloads or the full host engine.

### 5.2 Capability discovery

Every host reports a signed, versioned capability profile:
- OS/firmware and version; CPU architecture, ABI and instruction features.
- RAM, storage, filesystem and available accelerators.
- Network interfaces, reachable peers and ingress support.
- Power source, battery, thermal and lifecycle constraints where available.
- Supported runtime/provider versions and service types.
- Isolation mechanisms, security posture and update capability.

Unknown capability values remain unknown. The scheduler must not infer support from a device name.

### 5.3 Device tiers

- **Full host:** general-purpose OS and sufficient resources for static and accepted application workloads.
- **Compact host:** resource-limited SBC/embedded Linux with a narrower tested workload catalog.
- **Microcontroller edge node:** bounded device APIs, telemetry, protocol bridges and control operations.
- **Constrained node:** sensor/actuator/event capability only.

The tier is a measured profile, not a permanent label. Battery, heat, network, memory and storage pressure can dynamically reduce available capability.

## 6. Workloads and multi-runtime websites

A website is a deployment composed of independently managed components. One website may include:
- Static frontend.
- Python API.
- Node.js realtime service.
- PHP or another accepted runtime component.
- Database, queue, scheduled task or object-storage dependency when an adapter is supported.
- Edge-device integration through a scoped service capability.

Each component declares runtime/version, architecture, CPU/memory/storage budget, persistence, network needs, health check and security capabilities. A site router maps hostname/path/service routes to healthy components. Components may run on one host or multiple compatible hosts. Inter-component calls are authenticated and explicitly allow-listed.

Runtime packs are versioned and reusable. Runtime process instances, secrets, filesystem mounts and network permissions remain scoped to each component. A language pack being installed does not mean its execution is isolated.

## 7. Device interconnection and networking

### 7.1 Control connectivity

- Local LAN discovery is a hint only; discovery never grants trust.
- Pairing uses short-lived one-time challenges and explicit owner approval.
- Each host owns a non-exportable device identity key where supported.
- Host-to-host and client-to-host control uses authenticated encrypted transport.
- The optional control plane brokers discovery across networks but does not own the only copy of desired state.
- Direct LAN links may be used when available; an authenticated relay path may be used when direct connectivity is unavailable.
- Every command has a typed schema, scope, expiry, idempotency key and audit record.

### 7.2 Website ingress

The owner may independently enable:
- Local Network ingress.
- First-party AssayFlux Relay.
- Cloudflare Tunnel.
- Multiple supported providers concurrently.

Each provider has independent credentials, lifecycle, hostname routes, health and diagnostics. One provider's failure must not interrupt another provider or local hosting. Provider routing targets only the website ingress.

### 7.3 Offline and degraded modes

- Existing services continue from local state when the command-center phone is unavailable.
- Hosts continue last-known-good deployments if the remote control plane is unavailable.
- New remote enrollment and sensitive changes may be unavailable during identity/control-plane outages.
- A disconnected host is not reported as healthy; its state is shown as stale/unreachable.
- No silent failover or credential weakening is allowed.
- Reconnection reconciles versioned desired state with observed state and resolves conflicts explicitly.

## 8. Independent failure and recovery

Each component implements:
- Separate process/service lifecycle where its platform supports it.
- Health/readiness/liveness reporting.
- Bounded CPU, memory, disk, request, queue and connection limits.
- Timeouts, cancellation, circuit breakers and backpressure.
- Durable local state and atomic configuration/release activation.
- Last-known-good rollback.
- Restart policy with crash-loop detection and rate limiting.
- Structured logs and scoped metrics.
- Explicit degraded mode and operator-visible reason.

A supervisor must not restart the whole host because one site, runtime, tunnel or telemetry exporter fails. Updates are staged per component and rolled back independently. Shared infrastructure (identity, database, ingress) receives its own availability and recovery plan.

## 9. Security model

- Distinguish human owner, team member, mobile client, host device, service component, visitor and MCU identities.
- Default-deny network and filesystem access; grant scoped, revocable capabilities.
- Use short-lived credentials, device-bound keys, rotation and immediate revocation.
- Never put fleet-wide secrets in firmware, images or runtime packs.
- Sign host agents, runtime packs, deployment manifests, firmware and update metadata.
- Enforce project isolation with OS-level mechanisms and test native escape paths.
- Keep management, visitor and device-control network planes separate.
- Record sensitive actions in tamper-evident audit storage.
- Treat LAN, mDNS, BLE advertisements and device names as untrusted.
- MCU operations require board-specific safe pin/peripheral profiles and explicit policy.
- Never invent cryptographic protocols; use reviewed standard TLS and established secure provisioning mechanisms.

## 10. Mobile user experience

The mobile command center is the primary product surface:
- Fleet overview with live/stale/offline state and health summaries.
- Device onboarding through QR, nearby discovery and manual secure pairing.
- Device detail with capabilities, resource use, workloads and diagnostics.
- Website/service deployment wizard and per-component controls.
- Runtime catalog and compatibility-aware installation.
- Network and tunnel provider configuration.
- Access, team, secrets and approval controls.
- Backup, restore, updates and recovery.
- Notifications for failure, degraded service, security events and pending approvals.
- Offline local control where the phone can directly reach a host.
- Accessible, responsive Android and iOS design using the Pocket Host design system.

A browser console is a secondary operator surface, not a replacement for the mobile command center.

## 11. Platform implementation boundaries

- Android: Kotlin/Compose app and host adapter; obey foreground service, battery, package, thermal and storage rules.
- iOS: Swift/SwiftUI control client; use supported background APIs and never promise persistent arbitrary hosting.
- Linux: Rust host core plus systemd, filesystem, networking, package and security adapters.
- Windows: native service lifecycle, ACL, firewall, installer, signing and update adapters.
- macOS: launchd, signing/notarization, entitlements, filesystem and network adapters.
- SBC/embedded Linux: exact board profiles, thermal/power/storage recovery and headless onboarding.
- MCU: DemonCore firmware/DCP or other board-specific agent; bounded capabilities only.

Portable Rust code is reused where dependencies and security models allow. OS-specific behavior stays behind explicit adapters.

## 12. AssayFlux DemonCore integration

DemonCore remains a specialised MCU and edge-device firmware/fleet project. Pocket Host integrates through a versioned authenticated adapter. DemonCore owns board profiles, GPIO safety, DCP, provisioning, MCU firmware, transports and OTA. Pocket Host owns mobile fleet control, general-purpose host nodes, websites and public visitor ingress.

DCP messages are not authentication. Cross-network operations require claimed device identity, authenticated/encrypted transport, replay protection, scoped authorization, revocation and signed OTA. Public visitor routes must never reach DCP administration or GPIO control.

A capable Linux SBC may run a Pocket Host host agent and a DemonCore gateway as separate supervised services. Failure or compromise of one must not grant access to the other.

## 13. Version and compatibility policy

The baseline release is 0.1.0. Each independently deployed component declares:
- Product version and build provenance.
- API/protocol version range.
- Required host-agent capabilities.
- Runtime/ABI/OS/board compatibility.
- Signed artifact digest and update channel.
- Configuration schema version and migration policy.

Components may release independently. Breaking protocol changes require explicit version negotiation and staged compatibility. Fleet upgrades are canary-first and rollback-capable.

## 14. Delivery sequence

### 0.1.0 — System foundation
- Freeze this system model and common contracts.
- Establish Android and iOS mobile command-center product specifications.
- Define host agent, device identity, service manifest, capability profile, desired-state and event schemas.
- Define fault domains, offline behavior, security threat model and upgrade compatibility.
- Keep local hosting autonomous and management APIs private.

### 0.1.x — Mobile fleet control
- Complete Android command-center navigation, fleet overview, pairing and local management.
- Build the iOS command-center client against the same API contract.
- Implement host enrollment, device registry, revocation, heartbeat, typed command queue and audit.
- Verify mobile app outage does not stop independent hosts.

### 0.2.x — Independent host services
- Refactor host supervision, ingress, runtime providers, storage and tunnels into independently supervised modules.
- Implement component health, resource budgets, circuit breakers and rollback.
- Complete per-project execution isolation on supported host platforms.
- Keep static hosting independent of dynamic runtime availability.

### 0.3.x — Linux and SBC hosts
- Linux x86_64 and ARM64 host agent.
- Raspberry Pi-class exact-model profiles.
- Headless onboarding, secure updates, power-loss recovery and remote mobile control.

### 0.4.x — Desktop hosts
- Windows and macOS host agents.
- Native installers, service lifecycle, security and recovery acceptance.

### 0.5.x — Edge and microcontroller fabric
- Complete DemonCore device identity, authenticated transport, board safety and OTA acceptance.
- Integrate through the Pocket Host edge adapter.
- Add additional boards only with exact hardware profiles and physical test evidence.

### 0.6.x — Multi-host deployments
- Multi-component, multi-runtime placement across compatible hosts.
- Authenticated internal service routes and content-addressed deployment.
- Stateful replication and failover only after consistency and recovery acceptance.

### 1.0.0 — Production platform
- Independent security review, documented support matrix, signed stable releases, upgrade/rollback, backup/restore, abuse controls, monitoring, incident response and public service-level commitments.

## 15. Acceptance principles

- No component is declared implemented from documentation or source presence alone.
- Every supported OS, ABI, board and runtime combination has exact-device evidence.
- Mobile control center outage does not interrupt autonomous workloads.
- A crash in one service cell does not interrupt sibling cells, static hosting or unrelated hosts.
- Tunnel provider failure does not interrupt local ingress or another provider.
- Control plane outage does not erase local state or stop running services.
- Device revocation takes effect within a defined bounded interval.
- Recovery and rollback are tested under interruption, low storage, network loss and corrupted artifacts.
- Security claims require adversarial testing and independent review.
