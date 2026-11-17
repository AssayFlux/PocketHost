# AssayFlux Pocket Host 0.1.0

<p align="center">
  <img src="Assets/Pocket Host Logo.png" alt="Pocket Host" width="420">
</p>

<p align="center"><strong>Your devices. Your services. Your control.</strong></p>
<p align="center">A mobile command center for a verified, role-assigned device fleet.</p>

## What is Pocket Host?

Pocket Host lets an owner install separate software on different devices, verify each device from an Android or iPhone command center, and assign it a specific job: website serving, storage, traffic handling, builds and publishing, native backend jobs, backup or edge gateway.

The mobile device has three independent modes:
- **Command Center:** manages the fleet; mobile website serving is off.
- **Server:** serves websites from the mobile device.
- **Command Center + Server:** manages the fleet and serves websites from the same mobile device.

The mobile command center approves every device integration and every role grant. Unknown devices cannot join the trusted fleet.

## Core architecture

- **Mobile command center:** Android and iOS owner apps, device discovery, verification, approvals, role assignments, health and recovery.
- **Separate node software:** Android, Linux, Windows, macOS and supported SBC agents; MCU firmware for supported boards.
- **Role-based workers:** storage, traffic, build/publish, native backend/job, backup and edge gateway.
- **Central runtime placement:** in 0.1.0, user-selected language runtime packs execute only on the mobile website-serving node. Worker nodes handle bounded infrastructure jobs and do not silently become arbitrary Python/Node hosting nodes.
- **Device transports:** Wi-Fi/LAN for secure control and bulk transfer; Bluetooth for discovery/proximity/small control messages; USB only through supported OS APIs; authenticated remote links when needed.
- **DemonCore:** MCU and edge-device integration through a board-aware gateway.

## Device roles

| Role | Example device |
|---|---|
| Command Center | Android phone or iPhone |
| Mobile Website Server | Supported Android phone/tablet |
| Storage / Backup | NAS, PC, server, Raspberry Pi |
| Traffic | Linux server, SBC, eligible desktop |
| Build / Publish | Desktop, laptop, server |
| Native Backend / Jobs | Approved server or SBC worker |
| Edge Gateway | Raspberry Pi, embedded Linux |
| MCU / Sensor / Actuator | DemonCore-supported boards, ESP32-class devices |

Role assignment is capability-aware and explicitly approved. A device is not automatically trusted or eligible because it is visible on the network.

## Repository components

- `app/` — Android command center and optional mobile host.
- `runtime-worker/` — Android language runtime companion.
- `rust-engine/` — Rust website ingress and routing engine.
- `control-plane/` — optional remote fleet identity, registry and command service.
- `web-console/` — secondary browser operator console.
- [AssayFlux/DemonCore](https://github.com/AssayFlux/DemonCore) — MCU firmware and device-fleet project.

The iOS command center and additional desktop/server/SBC node packages are product deliverables; their implementation is not claimed complete in this baseline.

## Normative 0.1.0 documents

1. [System Architecture](docs/SYSTEM_ARCHITECTURE.md)
2. [Master Plan — five execution steps](docs/MASTER_PLAN.md)
3. [Host Agent and Role Contract](docs/HOST_AGENT_CONTRACT.md)
4. [Roadmap](docs/ROADMAP.md)
5. [Repository Baseline](docs/PROJECT_BASELINE.md)
6. [Mobile UI Design](docs/UI_DESIGN.md)
7. [Security Isolation](docs/SECURITY_ISOLATION_DESIGN.md)
8. [Remote Control Acceptance](docs/REMOTE_CONTROL_ACCEPTANCE.md)
9. [Documentation Index](docs/INDEX.md)

## Build and verification

Android:
```bash
./gradlew :app:assembleDebug :runtime-worker:assembleDebug
```

Rust engine:
```bash
cargo test --manifest-path rust-engine/Cargo.toml --locked
```

Control plane:
```bash
cargo test --manifest-path control-plane/Cargo.toml --locked
```

Browser console:
```bash
cd web-console && npm ci && npm run build
```

A successful build does not prove a device role, transport or platform is supported. Use the named acceptance matrix in the documentation.

## License

Apache License 2.0. See [LICENSE](LICENSE).
