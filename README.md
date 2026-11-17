# AssayFlux Pocket Host 0.1.0

<p align="center">
  <img src="Assets/Pocket Host Logo.png" alt="Pocket Host" width="420">
</p>

<p align="center"><strong>Your devices. Your services. Your control.</strong></p>
<p align="center">A mobile-first, distributed self-hosting ecosystem managed from Android or iPhone.</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-0.1.0-8B9EAD?style=flat-square" alt="Version 0.1.0">
  <img src="https://img.shields.io/github/license/AssayFlux/PocketHost?style=flat-square" alt="Apache 2.0">
  <img src="https://img.shields.io/github/actions/workflow/status/AssayFlux/PocketHost/quality.yml?branch=main&style=flat-square&label=quality" alt="Quality CI">
</p>

## What is Pocket Host?

Pocket Host lets people manage interconnected devices as their own hosting and service infrastructure. Android and iOS mobile applications are the primary command centers. The fleet may include Android devices, Linux/Windows/macOS computers, VPSs, dedicated servers, Raspberry Pi-class boards, embedded Linux gateways and microcontrollers.

The phone is the owner's control center, not the system's single point of failure. Each host independently supervises its services and continues its last authorized configuration if the phone or remote control plane is unavailable.

## Product principles

- **Mobile-first:** manage the fleet from Android or iPhone.
- **Device freedom:** use capable devices you already own.
- **Independent components:** separate identities, processes, permissions, state, health and recovery.
- **Secure interconnection:** authenticated, encrypted, versioned and bounded communication.
- **Local autonomy:** hosts keep working through mobile/control-plane outages.
- **Multi-runtime websites:** one website can contain static, Python, Node and other supported components.
- **Fault containment:** one broken service must not break unrelated sites, runtimes, hosts or tunnels.
- **Honest compatibility:** each exact device and workload combination must pass verification.

## System at a glance

```text
Android command center        iOS command center
          \                       /
           authenticated control API
                      |
          optional remote control plane
                      |
      +---------------+----------------+
      |               |                |
  Android host     Linux/PC host     SBC/edge gateway
      |               |                |
  service cells   service cells     DemonCore / MCU
      +---------------+----------------+
                      |
              authenticated data plane

Visitors -> Local ingress / AssayFlux Relay / Cloudflare Tunnel
         -> website ingress -> site router -> isolated components
```

## Device roles

| Device | Role |
|---|---|
| Android phone/tablet | Command center and supported host |
| iPhone/iPad | Command center |
| Linux, Windows, macOS | General-purpose host agents |
| VPS/dedicated server | Always-on host |
| Raspberry Pi and SBC | Compact Linux host or gateway |
| ESP32 and other MCUs | Bounded edge services, telemetry and device control |
| Sensors and constrained devices | Events and measurements |

## Repository components

- `app/` — Android mobile application and host client.
- `runtime-worker/` — Android runtime companion.
- `rust-engine/` — Rust website ingress and routing engine.
- `control-plane/` — optional remote identity, fleet and command service.
- `web-console/` — secondary browser operator console.
- [AssayFlux/DemonCore](https://github.com/AssayFlux/DemonCore) — separate MCU firmware and device-fleet project.

The iOS command-center app and additional desktop/server host agents are defined as product deliverables; this repository baseline does not claim them implemented.

## Version 0.1.0 documentation

1. [System Architecture](docs/SYSTEM_ARCHITECTURE.md)
2. [Master Plan](docs/MASTER_PLAN.md)
3. [Roadmap](docs/ROADMAP.md)
4. [Baseline inventory](docs/PROJECT_BASELINE.md)
5. [Mobile and product UI design](docs/UI_DESIGN.md)
6. [Security isolation](docs/SECURITY_ISOLATION_DESIGN.md)
7. [Remote control acceptance](docs/REMOTE_CONTROL_ACCEPTANCE.md)
8. [Documentation index](docs/INDEX.md)

## Build and verification

Android build:
```bash
./gradlew :app:assembleDebug :runtime-worker:assembleDebug
```

Rust website engine:
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

Use the exact target/device matrix and acceptance gates in the documentation. A successful build does not imply every platform or workload is supported.

## License

Apache License 2.0. See [LICENSE](LICENSE).
