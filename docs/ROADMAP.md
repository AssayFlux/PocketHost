# Pocket Host 0.1.0 — Roadmap

This roadmap follows the five-step master plan. The mobile command center is the owner-facing control surface; enrolled devices run separate, role-specific software.

## Step 1 — Contracts and trust

**Contract baseline frozen:** versioned schemas, default-deny role/job policies, trust ceremony, threat model, transport matrix, runtime/artifact boundaries and DemonCore interop are committed. Schema/policy validator and trust-state simulation tests run in a dedicated workflow. Confirm the latest Alpha run before closing the gate; production crypto/platform pairing remains Step 2.
- Normative device/role/job model.
- Mobile modes: Server only and Command Center + Server; no standalone Command Center-only mode.
- Device identity, verification code, enrollment and revocation.
- Wi-Fi, BLE, USB and remote transport constraints.
- Runtime-centralization and build/publish handoff.
- DemonCore protocol and safety alignment.

## Step 2 — Mobile command center

**Android trust foundation implemented; end-to-end gate remains open.** Persisted two-mode selection, gated device workspace, P-256 Keystore identity, metadata-bound challenge proof, role-policy-checked signed grants, encrypted registry and generation revocation exist. A preview Control Plane client can register a worker and queue signed grant/health-check commands. Manual enrollment, credential delivery, transport authentication, live status/acknowledgement UX, revocation propagation, per-use key authentication and physical acceptance remain. See [Step 2 implementation record](STEP_2_DEVICE_TRUST_IMPLEMENTATION.md).

- Android and iOS native clients.
- Device discovery, QR/device ID and one-time verification.
- Owner approval, role selection, permission review and revocation.
- Fleet dashboard, health, job status, audit and recovery.
- Local LAN communication first; BLE and supported USB flows next.
- One mutually exclusive mobile operating-mode selector: **Server only** or **Command Center + Server**. Website serving remains enabled in both modes; only fleet administration is added or hidden.

## Step 3 — Device agents and workers

**Linux preview is more than a local stub:** the current agent supports HTTPS polling and typed signed jobs, but still lacks production mutual authentication, grant renewal/revocation polling and full mobile provisioning. See `STEP_3_DEVICE_AGENTS.md`.
- Separate Android, Linux, Windows, macOS and SBC packages.
- Storage, backup, build/publish, traffic and native backend job roles.
- Host supervisor, durable job state, quotas, updates and rollback.
- Raspberry Pi and SBC capability profiles.
- DemonCore gateway and MCU adapters.

## Step 4 — Main Pocket Host mobile website server

**TypeScript compiler is specified but not present in the main Android dependency graph.** React/Vite are only the separate browser console.
**Partial close boundary:** Step 4 is the main Pocket Host application and its built-in web-serving stack only. HTML and CSS are served by the bundled Rust engine. The required built-in TypeScript authoring/build capability must compile to browser JavaScript before publication; the compiler is not yet bundled or verified. The existing repository contains the Rust static-serving foundation; TypeScript compiler integration and acceptance remain implementation work.
- Built-in main-app baseline: HTML, CSS, browser JavaScript, TypeScript-to-JavaScript build support, static assets and site routing.
- Main-app website lifecycle, secure staging, preview and atomic publish approval.
- Build worker artifact handoff without bypassing owner approval.
- Static serving must remain independent of any future optional runtime add-ons.

**Step 4 does not include optional server-side language runtimes or a runtime companion.** Those are moved to the post-five-step Future Runtime Library plan in [FUTURE_RUNTIME_LIBRARY.md](FUTURE_RUNTIME_LIBRARY.md). Do not hold Step 4 open for those future add-ons. The Step 4 gate remains open only for the main-app baseline and publishing/physical-device acceptance.

## Step 5 — Resilience and release
- Independent Local Network, AssayFlux Relay and Cloudflare providers.
- Replication, backup, restore and migration.
- Scheduling, fairness, capacity, telemetry and audit.
- Chaos, security, physical-device, board and platform acceptance.
- Signed releases, SBOM, support matrix and runbooks.

## Definition of done

Each feature requires implementation, tests on its named target, security review where relevant, permissions documentation, lifecycle/recovery behavior and observed evidence. No step closes on source presence or build success alone.
