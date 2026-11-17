# Pocket Host 0.1.0 — Engineering Context

Read [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md), [MASTER_PLAN.md](MASTER_PLAN.md) and [HOST_AGENT_CONTRACT.md](HOST_AGENT_CONTRACT.md) before changing system boundaries.

## Non-negotiable product model

- Mobile command center is the primary owner interface.
- Mobile has exactly two mutually exclusive modes: Server only and Command Center + Server. Both serve the website; only the combined mode administers the fleet.
- Every external device must be cryptographically verified and explicitly approved by the mobile command center.
- Separate software/firmware is built for each supported OS, architecture and board.
- Device roles are assigned explicitly: Storage, Traffic, Build/Publish, Native Backend/Jobs, Backup/Recovery and Edge Gateway.
- In 0.1.0 customer-selected language runtimes execute only on the mobile website-serving node. Worker devices run bounded infrastructure jobs and signed fixed-purpose native tasks.
- Wi-Fi/LAN is preferred for control and bulk transfer; BLE is for discovery/small control; USB is only supported through documented platform APIs; remote channels are authenticated.
- Discovery, IP, MAC, Bluetooth visibility, QR or a pairing code alone never grants ongoing trust.
- Hosts persist approved assignments and continue local work during mobile/control-plane outages.
- Each host, role, job, runtime, storage namespace, traffic route and MCU gateway has separate permissions, health, limits and recovery.
- Local Network, AssayFlux Relay and Cloudflare Tunnel remain independent selectable visitor providers.
- DemonCore remains a separately deployable MCU firmware project connected through a scoped adapter.

## Five-step execution order

1. Freeze architecture, threat model, identity, role, job and transport contracts.
2. Implement mobile command center, device verification and role assignment.
3. Deliver autonomous platform-specific node agents and worker roles.
4. Deliver mobile website serving, selected language runtime packs and verified publishing.
5. Complete fleet resilience, connectivity, security, physical-device acceptance and signed releases.

Do not skip contract and threat-model approval to start incompatible platform implementations.

## Repository boundaries

- `app/`: Android mobile command center and optional mobile host.
- `runtime-worker/`: Android language runtime companion.
- `rust-engine/`: Rust website ingress and routing engine.
- `control-plane/`: optional remote identity, registry, command and audit service.
- `web-console/`: secondary browser operator client.
- DemonCore: separate MCU firmware, DCP and board profiles.

## Implementation rules

- Preserve native OS lifecycle, permissions and secure key storage.
- Prefer narrow, versioned adapters over shared mutable state.
- Do not create unnecessary microservices; isolate actual trust/failure boundaries.
- No arbitrary remote shell, unrestricted filesystem or generic process-execution API.
- Commands are typed, scoped, expiring, idempotent and audited.
- Each role has independent credentials, queue, resource budget, health and rollback.
- Build workers never activate a live website release directly.
- Customer runtimes are not silently moved to worker nodes.
- Every component update is staged and independently rollback-capable.
- Record exact-SHA tests on named target devices; never claim support from compilation alone.
