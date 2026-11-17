# Pocket Host 0.1.0 — Architecture Decisions

## ADR-001 — Mobile command center with selectable server role

**Decision:** Android/iPhone mobile app is the primary owner command center. Mobile has two modes: Server only and Command Center + Server. The roles are independently controlled.

**Reason:** mobile is readily available, but hosting and fleet control have different lifecycle and permission requirements.

## ADR-002 — Explicit verified device integration

**Decision:** every device must present a cryptographic identity and short-lived one-time verification challenge. The mobile owner reviews capabilities and explicitly approves each role grant. Discovery never grants trust.

## ADR-003 — Separate software per device class

**Decision:** Android, iOS, Linux, Windows, macOS, SBC and MCU targets receive platform-appropriate software/firmware and role capabilities. No universal binary or blanket platform support is assumed.

## ADR-004 — Role-based worker fleet

**Decision:** devices may be assigned Storage, Traffic, Build/Publish, Native Backend/Jobs, Backup/Recovery or Edge Gateway roles. Each role has separate permissions, resources, health and revocation.

## ADR-005 — Central mobile runtime placement for 0.1.0

**Decision:** user-selected language runtime packs execute only on the mobile website-serving node. Other devices perform bounded infrastructure jobs and fixed-purpose native worker tasks; they do not silently become customer-language runtime hosts.

**Reason:** this preserves the requested central runtime manager while distributing storage, build, traffic and operations work. Static hosting remains independent of optional language runtimes.

## ADR-006 — Multi-transport integration

**Decision:** Wi-Fi/LAN is preferred for control and bulk data; Bluetooth is for discovery/proximity/small control; USB uses only supported platform APIs; remote transport uses authenticated encrypted sessions. All transports share the same identity and authorization policy.

## ADR-007 — Independent components and fault domains

**Decision:** host, role, job, runtime, storage, traffic, tunnel, control plane and edge gateway have distinct lifecycle, state, health, permissions and recovery.

## ADR-008 — Control/data plane separation and autonomy

**Decision:** owner management and device administration are never exposed through visitor ingress. Devices retain accepted assignments and continue during mobile/control-plane outage.

## ADR-009 — Independent tunnel providers

**Decision:** Local Network, AssayFlux Relay and Cloudflare Tunnel are separate choices; Relay and Cloudflare may run concurrently. Failure in one does not stop the others.

## ADR-010 — DemonCore remains separately deployable

**Decision:** DemonCore owns MCU firmware, DCP, board safety and constrained device behavior. Pocket Host integrates through an authenticated allow-listed edge adapter.

## ADR-011 — Version 0.1.0 baseline

**Decision:** 0.1.0 is the product and architecture baseline. Each component declares its own release version, protocol compatibility, provenance and rollback path.
