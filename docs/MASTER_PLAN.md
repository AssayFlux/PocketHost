# Pocket Host 0.1.0 — Master Plan

**Release baseline:** 0.1.0  
**Canonical branch:** main  
**Product form:** mobile-first distributed self-hosting ecosystem  
**Primary command center:** Android and iOS mobile applications  
**System architecture:** [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md)

## Product objective

Enable people to use the devices they own as interconnected, independently operating hosts for websites and services, all managed primarily from an Android or Apple mobile phone.

The phone is the primary command center, not a central runtime dependency. Each host must continue its already-authorized workloads if the phone, app, Internet connection or optional control plane becomes unavailable.

## Non-negotiable design rules

1. **Mobile-first management:** Android and iOS are first-class owner command clients. A responsive browser console is secondary.
2. **Independent components:** each host, service component, runtime, tunnel, storage provider and edge gateway owns its lifecycle, permissions, health, resource limits and recovery.
3. **Interconnected by contracts:** components communicate through authenticated, encrypted, versioned and bounded APIs/events; no shared unrestricted process or filesystem.
4. **No single point of failure:** command-center, control-plane, telemetry, tunnel and individual workload failures must not stop unrelated running services.
5. **Local autonomy:** hosts retain signed desired state and last-known-good deployments; remote management is optional for continued operation.
6. **Capability truth:** each device advertises measured capabilities. No promise that every device can run every workload.
7. **Security by OS boundary:** customer code runs in per-project/per-component isolation with scoped storage and network capabilities. A language package boundary alone is not isolation.
8. **Separate control and data planes:** visitor ingress cannot reach management, runtime brokers or MCU control.
9. **No silent failover:** degraded state, route changes, credential revocation and recovery are visible and auditable.
10. **Evidence-based support:** every platform, architecture, board, runtime and service combination requires verification.

## Product components

- Android mobile command center (Kotlin/Compose).
- iOS mobile command center (Swift/SwiftUI).
- Optional browser operator console.
- Optional remote control plane for identity, enrollment, desired state, command delivery and audit.
- Platform host agents: Android, Linux, Windows, macOS and embedded Linux.
- Host supervisor and independent website/service components.
- Rust website ingress and site router.
- Runtime providers and per-component execution cells.
- Storage, backup and deployment providers.
- Independent ingress connectors: Local Network, AssayFlux Relay and Cloudflare Tunnel.
- Edge gateway and DemonCore adapter for MCU fleets.

Detailed ownership and fault behavior are in SYSTEM_ARCHITECTURE.md.

## Version 0.1.0 baseline gates

### G0 — Architecture and contracts
- Freeze component boundaries and threat model.
- Define host-agent API, device identity, enrollment, service manifests, runtime manifests, capability profiles, desired state, events and audit schemas.
- Define compatibility negotiation, offline behavior and revocation.
- Specify Android/iOS mobile command-center parity.
- Define failure domains and recovery guarantees.

### G1 — Mobile command center
- Android fleet overview, onboarding, QR pairing, host details, service deployment and operations.
- iOS command-center application using the same contracts.
- Local LAN control and remote control.
- Secure recovery and device replacement.
- Verify the app can be unavailable without affecting host workloads.

### G2 — Host agent and supervisor
- Durable local desired state, independent service lifecycle, health and resource budgets.
- Reconciliation, restart policy, circuit breakers, bounded queues and graceful shutdown.
- Signed updates, staged activation and per-component rollback.
- Host remains operational while command plane is unavailable.

### G3 — Website and service components
- Rust ingress, hostname/site routing and static delivery.
- Multi-component websites with Python, Node and later runtime adapters.
- Per-component health, secrets, storage, resource budgets and internal routes.
- Static delivery independent of runtime failures.

### G4 — Security isolation
- Distinct OS-enforced execution identity per project/component as supported by host OS.
- Brokered filesystem and network capabilities.
- Deny-by-default inter-component communication.
- Adversarial native-code, credential, path, network and resource-exhaustion tests.
- MCU operations have explicit board-level capability and authorization policies.

### G5 — Connectivity
- Local Network, AssayFlux Relay and Cloudflare Tunnel are independent selectable providers.
- Providers may run concurrently with separate hostnames/routes.
- Independent health, credentials, revocation and diagnostics.
- Only website ingress is exposed; management plane remains private.
- Failure of one provider leaves other providers and local hosting intact.

### G6 — Storage, backup and recovery
- Per-site/component data ownership and quotas.
- Immutable deployment releases and atomic activation.
- Encrypted backups, integrity verification, restore rehearsal and rollback.
- One component's storage exhaustion must not corrupt unrelated services.

### G7 — Linux and SBC hosts
- Linux x86_64 and ARM64 host agent.
- Exact Raspberry Pi and other SBC capability profiles.
- Headless installation, systemd lifecycle, secure updates, thermal/power/storage management.
- Mobile enrollment and remote operation.

### G8 — Windows and macOS hosts
- Native installers and service lifecycle.
- OS-specific ACL/entitlement, firewall, signing and update policy.
- Mobile command-center parity.

### G9 — DemonCore and edge devices
- DemonCore board identity, safe pin profile, provisioning, authenticated transport and signed OTA.
- Pocket Host edge adapter with allow-listed DCP capabilities.
- ESP32 and other MCUs remain bounded edge nodes unless a board-specific workload profile proves more.
- Gateway and MCU failures remain isolated from website hosts.

### G10 — Multi-host services
- Host registration, revocation and compatibility-aware placement.
- Multi-runtime service graphs across eligible hosts.
- Authenticated internal service routing and signed artifact transfer.
- Stateless replication first; stateful failover only with tested consistency, backup and recovery.

### G11 — Release acceptance
- Exact-SHA CI, OS/device/ABI/board matrix and physical-device verification.
- Threat-model review, adversarial isolation tests and independent security review.
- Upgrade, rollback, restore, network partition and power-loss tests.
- Published support matrix and operational runbooks.

## Implementation order

1. G0 contracts and threat model.
2. G1 mobile command center and G2 autonomous host supervisor in parallel only after interfaces are frozen.
3. G3 website components and G4 security isolation.
4. G5–G6 connectivity and recovery.
5. G7 Linux/SBC, then G8 Windows/macOS.
6. G9 DemonCore integration after MCU security acceptance.
7. G10 distributed workloads and G11 release acceptance.

## Release policy

0.1.0 is the architecture and engineering baseline, not a claim that every listed platform is production-ready. Components version independently but declare compatibility with the system contracts. Every release must carry build provenance, artifact digest, protocol compatibility, supported device matrix and rollback instructions.

No milestone closes because documentation or source code exists. Each gate closes only with repeatable evidence on an exact commit and named target devices.
