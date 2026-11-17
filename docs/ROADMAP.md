# Pocket Host 0.1.0 — Roadmap

This roadmap follows the five-step master plan. The mobile command center is the owner-facing control surface; enrolled devices run separate, role-specific software.

## Step 1 — Contracts and trust

**Contract baseline frozen:** versioned schemas, default-deny role policy, typed job catalogue, trust ceremony, threat model, transport matrix, runtime/artifact boundaries and DemonCore interop are documented under `protocol/` and `docs/STEP_1_TRUST_AND_PROTOCOL.md`. Schema and trust-state simulation CI is committed; do not mark the gate passed until the workflow has a green run. Production crypto/platform pairing remains Step 2 implementation work.
- Normative device/role/job model.
- Mobile modes: Server only and Command Center + Server; no standalone Command Center-only mode.
- Device identity, verification code, enrollment and revocation.
- Wi-Fi, BLE, USB and remote transport constraints.
- Runtime-centralization and build/publish handoff.
- DemonCore protocol and safety alignment.

## Step 2 — Mobile command center

**Android trust foundation committed; end-to-end gate remains open.** Persisted two-mode selection, gated device workspace, P-256 Android Keystore owner identity, signed one-time challenge, proof-of-possession, role-policy-checked signed grants, encrypted registry and generation-based revocation are implemented. Android CI compiles app/test Kotlin without APK packaging and checks role-policy parity. The current flow is manual payload exchange; transport discovery, control-plane integration, agent-side durable verification, owner step-up authentication and physical-device acceptance remain. See [Step 2 implementation record](STEP_2_DEVICE_TRUST_IMPLEMENTATION.md).

- Android and iOS native clients.
- Device discovery, QR/device ID and one-time verification.
- Owner approval, role selection, permission review and revocation.
- Fleet dashboard, health, job status, audit and recovery.
- Local LAN communication first; BLE and supported USB flows next.
- One mutually exclusive mobile operating-mode selector: **Server only** or **Command Center + Server**. Website serving remains enabled in both modes; only fleet administration is added or hidden.

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
