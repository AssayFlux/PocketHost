# Pocket Host 0.1.0 — Roadmap

This roadmap follows the five-step master plan. The mobile command center is the owner-facing control surface; enrolled devices run separate, role-specific software.

## Step 1 — Contracts and trust
- Normative device/role/job model.
- Mobile modes: Command Center, Server, Both.
- Device identity, verification code, enrollment and revocation.
- Wi-Fi, BLE, USB and remote transport constraints.
- Runtime-centralization and build/publish handoff.
- DemonCore protocol and safety alignment.

## Step 2 — Mobile command center
- Android and iOS native clients.
- Device discovery, QR/device ID and one-time verification.
- Owner approval, role selection, permission review and revocation.
- Fleet dashboard, health, job status, audit and recovery.
- Local LAN communication first; BLE and supported USB flows next.
- Independent mobile Command Center and Server switches.

## Step 3 — Device agents and workers
- Separate Android, Linux, Windows, macOS and SBC packages.
- Storage, backup, build/publish, traffic and native backend job roles.
- Host supervisor, durable job state, quotas, updates and rollback.
- Raspberry Pi and SBC capability profiles.
- DemonCore gateway and MCU adapters.

## Step 4 — Mobile website server and language runtimes
- Rust ingress and static hosting.
- Runtime Manager with separate signed language packs.
- Runtime selection and install only on mobile serving node.
- Per-project/per-component isolation.
- Build worker artifact handoff and atomic publish approval.
- Distributed storage and traffic worker integration.

## Step 5 — Resilience and release
- Independent Local Network, AssayFlux Relay and Cloudflare providers.
- Replication, backup, restore and migration.
- Scheduling, fairness, capacity, telemetry and audit.
- Chaos, security, physical-device, board and platform acceptance.
- Signed releases, SBOM, support matrix and runbooks.

## Definition of done

Each feature requires implementation, tests on its named target, security review where relevant, permissions documentation, lifecycle/recovery behavior and observed evidence. No step closes on source presence or build success alone.
