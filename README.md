# AssayFlux Pocket Host 0.1.0

<p align="center">
  <img src="Assets/Pocket Host Logo.png" alt="Pocket Host" width="420">
</p>

<p align="center"><strong>Your devices. Your services. Your control.</strong></p>
<p align="center">Build a personal infrastructure from the devices you own.</p>

## What is Pocket Host?

Pocket Host is a mobile-first personal infrastructure builder. It is designed to let an owner connect supported devices, verify each device, grant it specific roles and combine their capabilities into a coordinated service system.

The mobile device has exactly two operating modes:

- **Server only:** the mobile device serves websites. Fleet discovery, device approval and role assignment are unavailable.
- **Command Center + Server:** the same mobile device serves websites and administers the verified device fleet.

Website serving stays enabled in both modes. There is no standalone Command Center-only mode. Only Command Center + Server can approve external devices and grant or revoke fleet roles.

Other devices use separate, platform-appropriate Pocket Host software. A device is not trusted or assigned work just because it appears on the network.

## Core architecture

- **Mobile serving node:** the website-serving role remains on the designated mobile device in both operating modes.
- **Mobile fleet administration:** available only in Command Center + Server mode. Android is the current application foundation; native iOS is a planned deliverable, not present in this repository.
- **Separate node software:** platform-specific Android, Linux, Windows, macOS and supported SBC agents are planned as separate installable products. Production desktop/SBC installers are not yet delivered.
- **Verified device fabric:** explicit device identity, short-lived enrollment challenge, owner approval, scoped role grants, revocation and audit.
- **Role-based workers:** target roles include storage, backup, traffic, build/publish, bounded native backend jobs and edge gateway. Each role is subject to exact platform and hardware acceptance.
- **Built-in web baseline:** the mobile server app includes HTML/CSS static hosting by default. TypeScript is part of the built-in web toolchain and is compiled to browser JavaScript before publication; its app-bundled compiler integration remains an implementation/acceptance item.
- **Optional language runtimes:** additional server-side languages are separately selectable and installable on the mobile serving node. They are not required for basic HTML/CSS/TypeScript websites. Worker devices do not silently become arbitrary Python/Node hosting nodes.
- **Website delivery:** Rust ingress and HTML/CSS static serving are present; TypeScript build integration, optional runtime isolation and the publish pipeline still require hardening and acceptance.
- **Device transports:** Wi-Fi/LAN is preferred for secure control and bulk transfer; Bluetooth supports discovery/proximity and small control messages; USB is platform/API-specific; remote links use authenticated sessions.
- **Visitor ingress:** Local Network, AssayFlux Relay and Cloudflare Tunnel are independent connectivity options.
- **DemonCore:** separate MCU firmware and edge-device integration through board-aware adapters.

## Device roles

| Role | Example device |
|---|---|
| Mobile Server / Command Center + Server | Supported Android phone or tablet |
| Storage / Backup | NAS, PC, server, Raspberry Pi |
| Traffic | Linux server, SBC, eligible desktop |
| Build / Publish | Desktop, laptop, server |
| Native Backend / Jobs | Approved server or SBC worker |
| Edge Gateway | Raspberry Pi, embedded Linux |
| MCU / Sensor / Actuator | DemonCore-supported boards, ESP32-class devices |

The mobile device's two modes are operating modes, not separate external worker roles. iOS is planned as a mobile command client; always-on iOS website hosting is not promised. Role assignment is capability-aware and explicitly approved. A device is eligible only for roles supported by its measured OS, architecture, hardware, resources, transport and security capabilities.

## Repository components

- `app/` — Android application: website-serving foundation, local management API, remote-control integration, persisted two-mode selector and a manual cryptographic device-trust workspace. Authenticated transport and end-to-end fleet workflows remain incomplete.
- `runtime-worker/` — separate Android runtime companion for Python/Node integration. Package separation does not yet provide per-project OS-level isolation.
- `rust-engine/` — Rust website ingress/routing engine hosted by the Android app.
- `control-plane/` — Rust remote fleet-control service foundation, including API, database migrations and deployment assets; optional for local operation.
- `web-console/` — React/TypeScript/Vite secondary operator client.
- `agents/linux/` — separately installable Linux Node Agent preview with P-256 identity, signed grant/job verification, durable local state, a default-deny health-check executor and bounded local storage/backup snapshot jobs.
- `tests/` and `scripts/` — repository test and engineering support assets.
- [AssayFlux/DemonCore](https://github.com/AssayFlux/DemonCore) — separate MCU firmware, board profiles and device-fleet project.

The repository currently contains foundations, specifications and partial implementations. It does not yet contain a native iOS app or production Linux, Windows, macOS and SBC node-agent installers.

## Normative and planning documents

1. [System Architecture](docs/SYSTEM_ARCHITECTURE.md)
2. [Master Plan — five execution steps](docs/MASTER_PLAN.md)
3. [Host Agent and Role Contract](docs/HOST_AGENT_CONTRACT.md)
4. [Device Software Matrix](docs/DEVICE_SOFTWARE_MATRIX.md)
5. [Roadmap](docs/ROADMAP.md)
6. [Repository Baseline](docs/PROJECT_BASELINE.md)
7. [Mobile UI Design](docs/UI_DESIGN.md)
8. [Security Isolation](docs/SECURITY_ISOLATION_DESIGN.md)
9. [Remote Control Acceptance](docs/REMOTE_CONTROL_ACCEPTANCE.md)
10. [Competitive Landscape and Product Gap Analysis](docs/COMPETITIVE_LANDSCAPE.md)
11. [Step 2 Device Trust Implementation](docs/STEP_2_DEVICE_TRUST_IMPLEMENTATION.md)
12. [Step 3 Device Agents](docs/STEP_3_DEVICE_AGENTS.md)
13. [Documentation Index](docs/INDEX.md)

## Five-step delivery plan and current position

Pocket Host is being delivered in five gated steps. The architecture and documentation define the target; a step is complete only when its implementation, security checks and named-device acceptance evidence pass.

| Step | Scope | Current position |
|---|---|---|
| 1. Contracts and trust | Architecture, threat model, identity, enrollment, roles, jobs, transports and DemonCore boundaries | **Contract implementation delivered; automated gate evidence pending.** Machine-readable schemas, default-deny role/job policies, trust simulations and CI validation are committed. Production trust code is Step 2. |
| 2. Mobile command center | Android modes, discovery, cryptographic verification, owner approval, role assignment and revocation | **Android trust foundation implemented; gate open.** Persisted Server only / Command Center + Server modes, Android Keystore P-256 identity, signed five-minute challenges, proof-of-possession, scoped signed grants, local encrypted registry and revocation are implemented with instrumentation tests. LAN/BLE/USB/remote transport, control-plane integration, independently installed agent verification and physical-device acceptance remain. Native iOS app is not present. |
| 3. Device agents and workers | Separate platform agents and storage, traffic, build/publish, native-job, backup and edge roles | **Linux preview foundation committed; gate open.** Independent package, P-256 identity, grant/job signature checks, durable state and one default-deny health-check job are implemented and CI-tested. Authenticated transport, daemon supervision, cgroups, role adapters, signed updates and other platform packages remain open. See `docs/STEP_3_DEVICE_AGENTS.md`. |
| 4. Mobile website server and runtimes | Built-in HTML/CSS/TypeScript web baseline, plus optional language packs, isolation and verified publishing | **Partial close.** HTML/CSS static serving is bundled in the Android server app. TypeScript compiler bundling is specified but not yet implemented/verified. Additional server-side languages remain separate optional packs; isolation, fault containment and artifact approval remain open. See `docs/STEP_4_WEB_BASELINE.md`. |
| 5. Resilience and release | Replication, recovery, connectivity, security, physical-device acceptance, signed releases and support evidence | **Not closed.** Tunnel and control-plane foundations exist, but end-to-end resilience, physical-device matrix, security acceptance and release evidence remain. |

### What “complete” means

- A document, design, interface or repository folder is not proof that a capability is implemented.
- A successful compile is not proof that a device role, transport or platform is supported.
- Each gate requires automated tests plus acceptance on its named OS, hardware and workload.
- Unsupported platforms and roles remain labelled planned or conditional until evidence is recorded.
- The mobile device has exactly two modes: **Server only** and **Command Center + Server**. Both serve websites; only the combined mode administers the fleet.
- Accepted worker jobs must have defined local lifecycle and recovery behavior; the mobile app or optional remote control plane must not be treated as the only runtime authority for work already accepted by a worker.

See [Master Plan](docs/MASTER_PLAN.md), [Project Baseline](docs/PROJECT_BASELINE.md), [Roadmap](docs/ROADMAP.md) and [Competitive Landscape](docs/COMPETITIVE_LANDSCAPE.md) for detailed scope, evidence requirements and product positioning.

## Build and verification

Run commands from the repository root.

Android application and runtime companion:

```bash
./gradlew :app:assembleDebug :runtime-worker:assembleDebug
```

Rust website engine:

```bash
cargo test --manifest-path rust-engine/Cargo.toml --locked
```

Remote control plane:

```bash
cargo test --manifest-path control-plane/Cargo.toml --locked
```

Browser console:

```bash
cd web-console && npm ci && npm run build
```

These are build/test entry points, not a claim that every command has passed in the current checkout or that the product is release-ready. A successful build does not prove a device role, transport or platform is supported. Use the named acceptance matrix and record evidence for each target.

## License

Apache License 2.0. See [LICENSE](LICENSE).
